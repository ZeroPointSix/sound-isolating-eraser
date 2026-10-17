"""Continue the existing guarded runner against the immutable locked artifact."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import traceback
import zipfile

ROOT = Path('/data2/keeper-camera-20261016')
ATTEMPT = 'a67'
assert len(sys.argv) == 1, 'This runner owns exactly one fresh attempt'
EV = ROOT / ('qa-evidence/20261017-' + ATTEMPT + '-locked-jar-e2e')
BASE = 'qa-evidence/20261016-200ms-rebuild-a3/prebuild/'
EXPECTED = {
    BASE + 'delivery.jar': '6e5250ceec0abf5bbd0a013308d768e41fc3028be2204ee4e52597823b32ee52',
    BASE + 'runtime.jar': '5ba9520754e5cfbde43030a99d235950637423cd268e86283711d559ac27c5f9',
    'build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar': '2dc4f746ebeea31eed48cc6040d15f9c5ad31bfc675adeeec69edb42aa6d36c0',
    'build/keeper-runtime/keeper-runtime.jar': '5d39ee45df3ce5977419407b7593df13c83106a356ad35cc4756b25e54fc8fe6',
}
os.chdir(ROOT)
lock = open('/tmp/keeper-camera-20261016-validation.lock', 'a')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
EV.mkdir()


def save(name, value):
    (EV / name).write_text(json.dumps(value, indent=2) + '\n')


def fingerprints():
    values = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in EXPECTED}
    assert values == EXPECTED, 'Immutable product artifact mismatch'
    return values


def check_processes(name, wait=False):
    active = []
    for p in Path('/proc').iterdir():
        if not p.name.isdigit() or int(p.name) == os.getpid():
            continue
        try:
            args = (p / 'cmdline').read_bytes().split(b'\0')
            exe = Path(args[0].decode()).name
            cmd = b' '.join(args)
            relevant = exe == 'java' and any(x in cmd for x in (
                b'forgeclientuserdev', b'forgeserveruserdev', b'forgegametestserveruserdev',
                b'runGameTestServer', b'keeper.qa.', b'KeeperX11Sampler'))
            relevant |= exe.startswith('python') and b'keeper-e2e.py' in cmd
            if relevant:
                active.append({'pid': int(p.name), 'exe': exe, 'cwd': str((p / 'cwd').resolve())})
        except (OSError, UnicodeError, IndexError):
            continue
    save(name, active)
    if wait:
        return active
    assert not active, 'Existing Forge/keeper test is active; no new client launched'


rc = 1
try:
    save('start.json', {'utc': time.strftime('%FT%TZ', time.gmtime()), 'pid': os.getpid(),
                        'load_average': os.getloadavg(), 'purpose': 'A67 native input after rendered frame; independent visible-pixel timing; immutable JARs'})
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    save('head.json', {'head': head, 'branch': subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip()})
    save('jars-before.json', fingerprints())
    end = time.monotonic() + 600
    quiet_since = None
    while True:
        active = check_processes('processes-before.json', wait=True)
        with (EV / 'contention-wait.jsonl').open('a') as stream:
            stream.write(json.dumps({'utc': time.strftime('%FT%TZ', time.gmtime()),
                                     'active': active, 'load': os.getloadavg()}) + '\n')
        quiet_since = None if active else (quiet_since if quiet_since is not None else time.monotonic())
        if quiet_since is not None and time.monotonic() - quiet_since >= 30:
            break
        assert time.monotonic() < end, 'No serial Forge test window within 600s; no client started'
        time.sleep(15)
    provenance = json.loads((ROOT / 'qa-evidence/20261016-200ms-build/artifact-provenance.json').read_text())
    with zipfile.ZipFile(ROOT / (BASE + 'runtime.jar')) as runtime, zipfile.ZipFile(ROOT / (BASE + 'delivery.jar')) as delivery, zipfile.ZipFile(ROOT / 'qa-evidence/20261016-200ms-build/remapped-runtime-check.jar') as remapped:
        classes = []
        for name, old in provenance['classes'].items():
            row = {'class': name, 'runtime_sha256': hashlib.sha256(runtime.read(name)).hexdigest(),
                   'delivery_sha256': hashlib.sha256(delivery.read(name)).hexdigest(),
                   'same_mapping_byte_equal': delivery.read(name) == remapped.read(name)}
            assert row['runtime_sha256'] == old['runtime_sha256'] and row['delivery_sha256'] == old['delivery_sha256']
            assert row['same_mapping_byte_equal']
            classes.append(row)
        assert len(classes) == 13
        save('locked-artifact-13-classes.json', classes)
    excluded = {str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/resources/main'),
                str(ROOT / 'build/keeper-runtime/keeper-runtime.jar')}
    cp = ':'.join(x for x in (ROOT / 'build/keeper-test-classpath.txt').read_text().strip().split(':') if x not in excluded)
    cp += ':' + str(ROOT / (BASE + 'runtime.jar'))
    command = ['javac', '-J-Xmx384m', '-proc:none', '-encoding', 'UTF-8', '-cp', cp,
               '-d', str(ROOT / 'build/classes/java/keeperTest')]
    command += [str(p) for p in sorted((ROOT / 'src/keeperTest/java').rglob('*.java'))]
    save('qa-compile-command.json', command)
    with (EV / 'qa-compile.log').open('w') as stream:
        compile_rc = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=180).returncode
    save('qa-compile-exit.json', compile_rc)
    assert compile_rc == 0, 'QA-only compilation failed'
    check_processes('processes-before-client.json')
    save('jars-after-qa-compile.json', fingerprints())
    env = {**os.environ, 'KEEPER_QA_RESULTS': str(EV), 'KEEPER_QA_STARTUP_SECONDS': '900',
           'KEEPER_QA_TIMEOUT_SECONDS': '1200', 'KEEPER_QA_RUNTIME_JAR': str(ROOT / (BASE + 'runtime.jar')),
           'KEEPER_QA_DELIVERY_JAR': str(ROOT / (BASE + 'delivery.jar')),
           'KEEPER_QA_RUNTIME_SHA256': EXPECTED[BASE + 'runtime.jar'],
           'KEEPER_QA_DELIVERY_SHA256': EXPECTED[BASE + 'delivery.jar']}
    save('reproduction.json', {'argv': ['python3', '-u', 'tools/keeper-e2e.py'],
                              'env': {k: v for k, v in env.items() if k.startswith('KEEPER_QA_')}})
    with (EV / 'harness.log').open('w') as stream:
        p = subprocess.Popen(['python3', '-u', 'tools/keeper-e2e.py'], stdout=stream, stderr=subprocess.STDOUT, env=env)
        save('harness-pid.json', p.pid)
        rc = p.wait()
    save('harness-exit.json', rc)
except BaseException:
    (EV / 'runner-error.txt').write_text(traceback.format_exc())
    raise
finally:
    if (EV / 'flash-x11-times.csv').exists():
        visible_rc = subprocess.run(['python3', 'tools/keeper-visible-timing.py', str(EV)],
                                    timeout=30).returncode
        save('visible-analysis-exit.json', visible_rc)
    save('jars-after.json', fingerprints())
    save('terminal.json', {'utc': time.strftime('%FT%TZ', time.gmtime()), 'exit_code': rc,
                           'client_launch_recorded': (EV / 'client-launch.json').exists(), 'load_average': os.getloadavg()})
    print(json.dumps({'results': str(EV), 'exit_code': rc}), flush=True)
sys.exit(rc)
