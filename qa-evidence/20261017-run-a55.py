import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path('/data2/keeper-camera-20261016')
ATTEMPT = sys.argv[1] if len(sys.argv) == 2 else 'a55'
assert ATTEMPT in ('a55', 'a55b', 'a55c'), 'Use a bounded fresh evidence directory'
BUILD = ROOT / ('qa-evidence/20261017-200ms-testbuild-' + ATTEMPT)
EV = ROOT / ('qa-evidence/20261017-200ms-e2e-' + ATTEMPT)
EXPECTED = {
    'build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar': '2dc4f746ebeea31eed48cc6040d15f9c5ad31bfc675adeeec69edb42aa6d36c0',
    'build/keeper-runtime/keeper-runtime.jar': '5d39ee45df3ce5977419407b7593df13c83106a356ad35cc4756b25e54fc8fe6',
}
os.chdir(ROOT)
lock = open('/tmp/keeper-camera-20261016-validation.lock', 'w')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
BUILD.mkdir()
EV.mkdir()


def run(args, log=None, env=None):
    print('COMMAND', json.dumps(args), flush=True)
    if log:
        with log.open('w') as stream:
            return subprocess.run(args, stdout=stream, stderr=subprocess.STDOUT, env=env).returncode
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def fingerprint(path):
    hashes = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in EXPECTED}
    path.write_text(json.dumps(hashes, indent=2) + '\n')
    assert hashes == EXPECTED, 'Existing product JAR changed; do not rebuild or test a different artifact'


def no_competitors(name):
    found = []
    for p in Path('/proc').iterdir():
        if not p.name.isdigit():
            continue
        try:
            command = (p / 'cmdline').read_bytes().replace(b'\0', b' ').decode()
            exe = (p / 'exe').resolve().name
            relevant = exe == 'java' and any(s in command for s in (
                'runGameTestServer', 'forgegametestserveruserdev', str(ROOT), 'KeeperX11Sampler'))
            relevant |= exe.startswith('python') and 'keeper-e2e.py' in command
            if relevant:
                found.append({'pid': int(p.name), 'command': command})
        except (OSError, UnicodeError):
            pass
    (BUILD / name).write_text(json.dumps(found, indent=2) + '\n')
    assert not found, 'Another test is active; no new client launched and no process killed'


rc = 1
try:
    (BUILD / 'started-at.txt').write_text(time.strftime('%FT%TZ', time.gmtime()) + '\n')
    assert run(['git', 'branch', '--show-current']).strip() == 'feat/keeper-camera-20261016'
    assert not run(['git', 'diff', '--cached', '--name-only']).strip(), 'Pre-existing staged changes preserved'
    (BUILD / 'parent-head.txt').write_text(run(['git', 'rev-parse', 'HEAD']))
    (BUILD / 'test.patch').write_text(run(['git', 'diff', '--',
        'src/keeperTest/java/com/zeropointsix/keepertest/KeeperTestClient.java', 'tools/KeeperX11Sampler.java']))
    run(['git', 'diff', '--check'])
    fingerprint(BUILD / 'jars-before.json')
    no_competitors('processes-before.json')
    (BUILD / 'load-before.txt').write_text(Path('/proc/loadavg').read_text())
    command = ['./gradlew', 'compileKeeperTestJava', 'writeKeeperTestClasspath', '-PkeeperE2E',
        '--offline', '--no-daemon', '--max-workers=1', '-Dnet.minecraftforge.gradle.check.certs=false',
        '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2']
    (BUILD / 'build-command.json').write_text(json.dumps(command, indent=2))
    brc = run(command, BUILD / 'build.log')
    (BUILD / 'build.exit').write_text(str(brc) + '\n')
    assert brc == 0, 'QA compilation failed; see build.log'
    fingerprint(BUILD / 'jars-after-compile.json')
    run(['git', 'add', '--', 'src/keeperTest/java/com/zeropointsix/keepertest/KeeperTestClient.java',
         'tools/KeeperX11Sampler.java'])
    message = ('test(keeper): retain prior warmup readiness with strict pixel timing gate'
               if ATTEMPT == 'a55c' else
               'test(keeper): defer flash IO and fail on real overrun; await native photo entry')
    print(run(['git', 'commit', '-m', message]), flush=True)
    (EV / 'head.txt').write_text(run(['git', 'rev-parse', 'HEAD']))
    no_competitors('processes-before-client.json')
    fingerprint(EV / 'jar-sha256.json')
    (EV / 'started-at.txt').write_text(time.strftime('%FT%TZ', time.gmtime()) + '\n')
    env = {**os.environ, 'KEEPER_QA_RESULTS': str(EV), 'KEEPER_QA_STARTUP_SECONDS': '900',
           'KEEPER_QA_TIMEOUT_SECONDS': '1200'}
    rc = run(['python3', '-u', 'tools/keeper-e2e.py'], EV / 'harness.log', env)
    (EV / 'harness.exit').write_text(str(rc) + '\n')
    print('HARNESS_EXIT', rc, flush=True)
finally:
    fingerprint(BUILD / 'jars-after.json')
    (BUILD / 'runner.exit').write_text(str(rc) + '\n')
    (BUILD / 'ended-at.txt').write_text(time.strftime('%FT%TZ', time.gmtime()) + '\n')
    print('RESULTS', str(EV), flush=True)
sys.exit(rc)
