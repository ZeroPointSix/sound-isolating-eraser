"""Package the actual Blender outputs and verify every encoded motion clip."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

root = Path('/tmp/fenglei-art/model')
manifest = json.loads((root / 'asset_manifest.json').read_text())
assert len(manifest['renders']) == 7 if 'renders' in manifest else len(list((root/'renders').glob('*.png'))) == 7
videos = root / 'videos'
videos.mkdir(exist_ok=True)
verification = {'kind': 'Blender model and decoded video, not Minecraft gameplay', 'clips': []}
for clip in manifest['animation_renders']:
    name = clip['clip']
    directory = Path(clip['directory'])
    frames = sorted(directory.glob('*.png'))
    assert len(frames) == clip['frames'], name
    hashes = {hashlib.sha256(p.read_bytes()).hexdigest() for p in frames}
    assert len(hashes) > 1, name + ' is static'
    path = videos / (name + '.mp4')
    subprocess.run(['ffmpeg','-v','error','-y','-framerate','24','-i',str(directory/'%04d.png'),
                    '-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(path)], check=True)
    probe = json.loads(subprocess.check_output(['ffprobe','-v','error','-count_frames','-select_streams','v:0',
                    '-show_entries','stream=nb_read_frames,width,height,r_frame_rate','-of','json',str(path)]))
    stream = probe['streams'][0]
    assert int(stream['nb_read_frames']) == len(frames), name
    subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','null','-'],check=True)
    verification['clips'].append({'name':name,'frames':len(frames),'distinct_source_frames':len(hashes),'decoded':True,**stream})
assert len(verification['clips']) == 8
(root/'playback_verification.json').write_text(json.dumps(verification,indent=2))
output = Path('/tmp/B_model_preview.zip')
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        if path.is_file() and not any(part.startswith('frames') for part in path.relative_to(root).parts) and path.suffix != '.blend1':
            archive.write(path, 'model/'+str(path.relative_to(root)))
print(json.dumps({'zip':str(output),'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'verification':verification}),flush=True)
