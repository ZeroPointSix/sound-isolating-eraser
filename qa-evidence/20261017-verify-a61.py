"""Test-only verification; never starts Minecraft or writes either product JAR."""
import fcntl
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile

ROOT = Path('/data2/keeper-camera-20261016')
OUT = ROOT / 'qa-evidence/20261017-a61-test-contract'
RUNTIME = ROOT / 'build/keeper-runtime/keeper-runtime.jar'
DELIVERY = ROOT / 'build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(name, command):
    with (OUT / name).open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, text=True, stdout=stream,
                                stderr=subprocess.STDOUT)
    if result.returncode:
        raise AssertionError(f'{name}: exit {result.returncode}')
    return (OUT / name).read_text()


def processes():
    rows = []
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():
            continue
        try:
            comm = (path / 'comm').read_text().strip()
            if comm not in ('java', 'Xvfb', 'python3'):
                continue
            command = (path / 'cmdline').read_bytes().decode(errors='replace').split('\0')
            cwd = str((path / 'cwd').resolve())
            rows.append({'pid': int(path.name), 'comm': comm, 'cwd': cwd,
                         'keeper_workload': comm == 'java' and str(ROOT) in ' '.join(command),
                         'harness': any('keeper-e2e.py' in arg for arg in command),
                         'gametest': any('gametest' in arg.lower() for arg in command),
                         'stat': (path / 'stat').read_text().strip()})
        except (OSError, PermissionError):
            pass
    return rows


def main():
    OUT.mkdir(exist_ok=False)
    result = {'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
              'checks': {}, 'visual_e2e': {name: 'NOT_VERIFIED' for name in
                 ('flash_200ms', 'sepia', 'hud_countdown', 'exit_cleanup')},
              'no_client_started': True, 'no_product_jar_rebuilt': True}
    lock = open('/tmp/keeper-camera-20261016-validation.lock', 'a')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        result['head_before'] = run('head-before.txt', ['git', 'rev-parse', 'HEAD']).strip()
        result['branch'] = run('branch.txt', ['git', 'branch', '--show-current']).strip()
        assert result['branch'] == 'feat/keeper-camera-20261016'
        result['processes_before'] = processes()
        assert not any(row['keeper_workload'] or row['harness'] for row in result['processes_before'])
        result['checks']['no_existing_camera_client'] = 'PASS'
        result['jar_sha256_before'] = {str(p.relative_to(ROOT)): sha(p) for p in (RUNTIME, DELIVERY)}
        assert sha(DELIVERY) == '2dc4f746ebeea31eed48cc6040d15f9c5ad31bfc675adeeec69edb42aa6d36c0'
        assert sha(RUNTIME) == '5d39ee45df3ce5977419407b7593df13c83106a356ad35cc4756b25e54fc8fe6'
        result['checks']['locked_product_hashes'] = 'PASS'
        sampler = ROOT / 'tools/KeeperX11Sampler.java'
        client = ROOT / 'src/keeperTest/java/com/zeropointsix/keepertest/KeeperTestClient.java'
        result['source_sha256'] = {str(p.relative_to(ROOT)): sha(p) for p in (sampler, client)}
        classes = OUT / 'qa-classes'
        classes.mkdir()
        run('sampler-compile.log', ['javac', '--release', '17', '-d', str(classes), str(sampler)])
        result['checks']['sampler_java17_compile'] = 'PASS'
        cp = (ROOT / 'build/keeper-test-classpath.txt').read_text().strip()
        sources = sorted(str(p) for p in (ROOT / 'src/keeperTest/java').rglob('*.java'))
        run('keeper-test-compile.log', ['javac', '--release', '17', '-proc:none', '-cp', cp,
                                     '-d', str(classes), *sources])
        result['checks']['keeper_test_forge_classpath_compile'] = 'PASS'
        bytecode = run('sampler-bytecode.txt', ['javap', '-classpath', str(classes), '-c', '-p',
                                             'KeeperX11Sampler'])
        main_code = bytecode.split('public static void main(', 1)[1]
        assert main_code.index('String sampler.failed') < main_code.index('javax/imageio/ImageIO.write')
        assert main_code.index('java/nio/file/Files.writeString', main_code.index('String sampler.failed')) < main_code.index('javax/imageio/ImageIO.write')
        assert 'long 250000000l' in main_code and 'double 0.08d' in main_code
        assert 'java/lang/AssertionError.addSuppressed' in main_code
        result['checks']['compiled_failure_before_png_io'] = 'PASS'
        result['checks']['unchanged_250ms_008_pixel_guard'] = 'PASS'
        result['checks']['evidence_io_does_not_replace_overrun'] = 'PASS'
        client_code = run('client-bytecode.txt', ['javap', '-classpath', str(classes), '-c', '-p',
                                                'com.zeropointsix.keepertest.KeeperTestClient'])
        rendered = client_code.split('public static void rendered(', 1)[1].split('public static void message(', 1)[0]
        assert rendered.index('double 650.0d') < rendered.index('String flash-deadline.txt')
        assert 'double 250.0d' in rendered and 'double 100.0d' in rendered
        result['checks']['compiled_client_defers_flash_io'] = 'PASS'
        result['checks']['client_timing_limits_unchanged'] = 'PASS'
        prior_path = ROOT / 'qa-evidence/20261016-a39-final-provenance/artifact-provenance.json'
        prior = json.loads(prior_path.read_text())
        mapped_path = prior_path.parent / 'remapped-runtime-check.jar'
        assert prior['runtime_sha256'] == sha(RUNTIME) and prior['delivery_sha256'] == sha(DELIVERY)
        result['mapping_method'] = 'Fresh read of unchanged JARs and hash-bound archived same-mapping artifact; no remap or repackage'
        result['mapping_reference'] = str(prior_path.relative_to(ROOT))
        result['mapping_reference_sha256'] = sha(prior_path)
        result['mapped_archive_sha256'] = sha(mapped_path)
        result['classes'] = {}
        with zipfile.ZipFile(RUNTIME) as runtime, zipfile.ZipFile(DELIVERY) as delivery, zipfile.ZipFile(mapped_path) as mapped:
            names = [n for n in runtime.namelist() if n.startswith('com/zeropointsix/eraser/keeper/') and n.endswith('.class')]
            assert len(names) == 13
            for name in names:
                a = hashlib.sha256(runtime.read(name)).hexdigest()
                b = hashlib.sha256(delivery.read(name)).hexdigest()
                assert a == prior['classes'][name]['runtime_sha256']
                assert b == prior['classes'][name]['delivery_sha256']
                assert mapped.read(name) == delivery.read(name)
                result['classes'][name] = {'runtime_sha256': a, 'delivery_sha256': b,
                                           'mapped_byte_equality': True}
            assert not any('keepertest/' in n or 'KeeperX11Sampler' in n for n in delivery.namelist())
        result['checks']['13_camera_classes_same_mapping'] = 'PASS'
        result['checks']['qa_classes_excluded_from_delivery'] = 'PASS'
        result['resource_samples'] = []
        for _ in range(6):
            result['resource_samples'].append({'epoch': time.time(),
                'cpu': Path('/proc/stat').read_text().splitlines()[0],
                'pressure': Path('/proc/pressure/cpu').read_text().strip(),
                'load': Path('/proc/loadavg').read_text().strip(), 'processes': processes()})
            time.sleep(1)
        result['jar_sha256_after'] = {str(p.relative_to(ROOT)): sha(p) for p in (RUNTIME, DELIVERY)}
        assert result['jar_sha256_before'] == result['jar_sha256_after']
        run('diff-check.txt', ['git', 'diff', '--check'])
        run('test-change.patch', ['git', 'diff', '--', 'tools/KeeperX11Sampler.java'])
        changed = run('changed-files.txt', ['git', 'diff', '--name-only']).splitlines()
        assert changed == ['tools/KeeperX11Sampler.java'], changed
        result['checks']['test_only_and_jars_unchanged'] = 'PASS'
        result['overall'] = 'STATIC_COMPILE_PASS; VISUAL_E2E_NOT_VERIFIED'
        result['blocker'] = 'No existing camera client/session; latest task forbids starting one. No client started.'
    except BaseException as error:
        result['overall'] = 'VERIFIER_FAILED'
        result['error'] = repr(error)
        raise
    finally:
        result['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        (OUT / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps({'overall': result['overall'], 'checks': result['checks'],
                          'evidence': str(OUT), 'error': result.get('error')}, indent=2))
        fcntl.flock(lock, fcntl.LOCK_UN)
        lock.close()


if __name__ == '__main__':
    main()
