import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import zipfile

root = Path('/data2/keeper-camera-20261016')
attempt = sys.argv[1] if len(sys.argv) == 2 else 'a55c'
assert attempt in ('a55b', 'a55c')
evidence = root / ('qa-evidence/20261017-200ms-e2e-' + attempt)
build = root / ('qa-evidence/20261017-200ms-testbuild-' + attempt)
assert (build / 'runner.exit').exists(), 'Collect terminal run only; do not imply completion'
failures = {p.name: p.read_text() for p in evidence.glob('*.failed')}
harness_log = (evidence / 'harness.log').read_text() if (evidence / 'harness.log').exists() else ''
failure_stage = 'see_logs'
if 'did not become ready within 900s' in harness_log:
    failures['harness_startup'] = 'Keeper dedicated server did not become ready within 900s; no current-run client started.'
    failure_stage = 'server_startup_timeout'
gates = {
    'flash_200ms': ['flash.pass'],
    'sepia': ['sepia.pass'],
    'hud_countdown': ['hud.pass', 'countdown.pass'],
    'exit_cleanup': ['exit.pass'],
}
result = {}
failure_text = '\n'.join(failures.values())
failure_markers = {
    'flash_200ms': ('Real X11 shutter pixels still bright beyond 250ms',
                    'insufficient real rendered frames around the 200ms deadline'),
    'sepia': ('photo world is not brown/sepia',),
    'hud_countdown': ('remaining-time color', 'remaining seconds decrease', 'later HUD still rendered'),
    'exit_cleanup': ('exit removes countdown HUD', 'exit removes sepia filter'),
}
for gate, names in gates.items():
    passed = all((evidence / name).exists() for name in names)
    observed_failure = any(marker in failure_text for marker in failure_markers[gate])
    result[gate] = {
        'status': 'FAIL' if observed_failure else ('PASS' if passed else 'NOT_VERIFIED'),
        'evidence': {name: (evidence / name).read_text() for name in names if (evidence / name).exists()},
    }
hashes = {}
for name in ['build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar', 'build/keeper-runtime/keeper-runtime.jar']:
    hashes[name] = hashlib.sha256((root / name).read_bytes()).hexdigest()
previous = json.loads((root / 'qa-evidence/20261017-200ms-e2e-a55b/artifact-provenance-a55.json').read_text())
assert hashes['build/keeper-runtime/keeper-runtime.jar'] == previous['runtime_sha256']
assert hashes['build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar'] == previous['delivery_sha256']
with zipfile.ZipFile(root / 'build/keeper-runtime/keeper-runtime.jar') as runtime, \
        zipfile.ZipFile(root / 'build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar') as delivery, \
        zipfile.ZipFile(root / 'qa-evidence/20261016-a39-final-provenance/remapped-runtime-check.jar') as remapped:
    for name, expected in previous['classes'].items():
        assert hashlib.sha256(runtime.read(name)).hexdigest() == expected['runtime_sha256'], name
        assert hashlib.sha256(delivery.read(name)).hexdigest() == expected['delivery_sha256'], name
        assert remapped.read(name) == delivery.read(name), name
    assert len(previous['classes']) == 13
previous['utc'] = time.strftime('%FT%TZ', time.gmtime())
(evidence / 'artifact-provenance-a55.json').write_text(json.dumps(previous, indent=2) + '\n')
loaded = {'status': 'NOT_VERIFIED', 'reason': 'No current-run loaded class record'}
record = evidence / 'runtime-classes.txt'
if record.exists():
    lines = record.read_text().splitlines()
    expected_names = [
        'com/zeropointsix/eraser/keeper/client/KeeperVisuals.class',
        'com/zeropointsix/eraser/keeper/client/KeeperVisuals$Registration.class',
    ]
    sources = [line for line in lines if line.startswith('source=')]
    loaded_hashes = [line.split('=', 1)[1] for line in lines if line.startswith('sha256=')]
    assert len(sources) == 2 and all('keeper-runtime.jar' in line for line in sources)
    assert loaded_hashes == [previous['classes'][name]['runtime_sha256'] for name in expected_names]
    assert 'duration_nanos=200000000' in lines
    loaded = {'status': 'PASS', 'loaded_classes_match_unchanged_runtime_jar': True,
              'default_duration_nanos': 200000000,
              'note': 'Loaded-byte verification only; actual visual timing is a separate gate.'}
(evidence / 'loaded-runtime-a55.json').write_text(json.dumps(loaded, indent=2) + '\n')
summary = {
    'collected_utc': time.strftime('%FT%TZ', time.gmtime()),
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'tested_head': (evidence / 'head.txt').read_text().strip() if (evidence / 'head.txt').exists() else None,
    'harness_exit': int((evidence / 'harness.exit').read_text()) if (evidence / 'harness.exit').exists() else None,
    'runner_exit': int((build / 'runner.exit').read_text()),
    'same_mapping_classes': 'PASS: 13/13 unchanged classes, archived remap bytes equal delivery',
    'loaded_runtime': loaded,
    'jar_hashes': hashes,
    'four_visual_gates': result,
    'failures': failures,
    'failure_stage': failure_stage,
    'earlier_attempt': {
        'directory': 'qa-evidence/20261017-200ms-e2e-a55b',
        'result': 'NOT_VERIFIED: 90s pre-shutter render readiness failed; no shutter sent.',
        'loaded_runtime_evidence': 'qa-evidence/20261017-200ms-e2e-a55b/loaded-runtime-a55.json',
        'timing_evidence': 'qa-evidence/20261017-200ms-e2e-a55b/readiness-failure-analysis.json',
    },
    'next_step': 'Reserve an uncontended window: no other forgeclientuserdev/forgeserveruserdev test workloads, not just GameTest. Reuse the unchanged JAR and this QA fix for one serial run. Do not relax visual timing limits or kill unrelated services.',
    'evidence_path': str(evidence),
    'reproduce': 'python3 -u /data2/keeper-camera-20261016/qa-evidence/20261017-run-a55.py ' + attempt,
    'rerun_note': 'The recorded evidence directory is immutable. Inspect existing job before choosing a fresh attempt. Never launch a second client.',
    'limits': 'No product JAR rebuilt. No push, PR, merge, deployment, watch changes, or independent review.',
}
(evidence / 'a55-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
archive = root / 'qa-evidence/keeper-camera-a55-evidence.zip'
assert not archive.exists(), 'Preserve existing archive'
paths = []
directories = {evidence, build, root / 'qa-evidence/20261017-a55-preflight',
               root / 'qa-evidence/20261017-200ms-e2e-a55b',
               root / 'qa-evidence/20261017-200ms-testbuild-a55b',
               root / 'qa-evidence/20261017-200ms-testbuild-a55'}
for directory in directories:
    paths.extend(p for p in directory.iterdir() if p.is_file() and (
        p.suffix in ['.txt', '.json', '.csv', '.pass', '.failed', '.exit', '.md', '.png', '.patch']
        or p.name in ['build.log', 'harness.log', 'client.log', 'server.log', 'sampler.log']))
paths.extend((evidence / 'flash-frames').glob('*.png'))
paths.append(root / 'qa-evidence/20261017-run-a55.py')
paths.append(root / 'qa-evidence/20261017-collect-a55.py')
paths.append(root / 'src/keeperTest/java/com/zeropointsix/keepertest/KeeperTestClient.java')
paths.append(root / 'tools/KeeperX11Sampler.java')
paths.append(root / 'tools/keeper-e2e.py')
paths.append(root / 'qa-evidence/20261016-a39-final-provenance/artifact-provenance.json')
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as output:
    for path in sorted(set(paths)):
        if path.name.endswith('-launch.json'):
            continue
        output.write(path, str(path.relative_to(root)))
print(json.dumps(summary, indent=2))
print('ARCHIVE', str(archive), archive.stat().st_size, hashlib.sha256(archive.read_bytes()).hexdigest())
