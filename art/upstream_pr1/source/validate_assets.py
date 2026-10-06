"""Fail-closed validation for the deliverable rather than for a game runtime."""
from pathlib import Path
import hashlib
import json
import struct
from PIL import Image, ImageStat

ROOT=Path('/workspace/art-build/fenglei_art_v01')
checks=[]

def check(condition,note):
    if not condition:
        raise AssertionError(note)
    checks.append(note)

for color in ('silver','indigo'):
    for size in (16,32):
        image=Image.open(ROOT/f'textures/item/fenglei_chi_{color}_{size}.png')
        check(image.size==(size,size) and image.mode=='RGBA',f'{color}_{size}: 尺寸与RGBA正确')
        check(image.getchannel('A').getextrema()==(0,255),f'{color}_{size}: 同时含透明与不透明像素')
        check(image.getbbox() is not None,f'{color}_{size}: 图像非空')

for name in ('wind_ribbon','thunder_arc','shock_ring'):
    image=Image.open(ROOT/f'textures/particle/{name}.png')
    check(image.size==(32,256) and image.mode=='RGBA',f'{name}: 8帧32像素竖向图集')
    check(image.crop((0,224,32,256)).getchannel('A').getextrema()==(0,0),f'{name}: 最后一帧完全透明')

atlas=json.loads((ROOT/'metadata/hud_atlas.json').read_text())
check(len(atlas['frames'])==20,'HUD: 4种档位与16档冷却进度')
for name,rect in atlas['frames'].items():
    check(0<=rect['x'] and 0<=rect['y'] and rect['x']+rect['width']<=256 and rect['y']+rect['height']<=128,f'HUD {name}: 未超出图集边界')

for path in list(ROOT.rglob('*.json'))+list(ROOT.rglob('*.mcmeta')):
    json.loads(path.read_text())
check(True,'所有JSON与mcmeta可解析')

model=ROOT/'model'
manifest=json.loads((model/'asset_manifest.json').read_text())
check(manifest['geometry']['triangles']<10000,'模型三角面低于本轮一万面的预算')
check(all(view['fully_in_frame'] for view in manifest['view_validation'].values()),'七个相机均通过变形后顶点边界检查')
ortho=[manifest['view_validation'][name]['orthographic_scale'] for name in ('orthographic_front','orthographic_side','orthographic_top')]
check(max(ortho)-min(ortho)<1e-5,'正侧俯相机尺度一致')
check(len(manifest['clips'])==8,'模型含8个命名动作')
data=(model/'wind_thunder_wings.glb').read_bytes()
magic,version,total=struct.unpack_from('<4sII',data)
check(magic==b'glTF' and version==2 and total==len(data),'GLB头、版本与字节长度正确')
chunk_length,chunk_type=struct.unpack_from('<II',data,12)
check(chunk_type==0x4e4f534a,'GLB包含有效JSON首块')
doc=json.loads(data[20:20+chunk_length])
animations={entry['name'] for entry in doc['animations']}
check(set(manifest['clips']).issubset(animations),'GLB中保留全部8个动作')

renders = list((model/'renders').glob('*.png'))
check(len(renders)==7,'恰好包含7张状态与视角渲染')
for path in renders:
    im=Image.open(path).convert('RGB')
    check(im.size==(1600,900),f'{path.name}: 渲染尺寸正确')
    check(max(ImageStat.Stat(im).stddev)>8,f'{path.name}: 渲染非空白')
    clipped=sum(1 for pixel in im.resize((160,90)).getdata() if min(pixel)>249)
    check(clipped < 160*90*0.5,f'{path.name}: 无大面积高光溢出')

playback=json.loads((model/'playback_verification.json').read_text())
check(len(playback['clips'])==8,'包含8段视频验证记录')
for clip in playback['clips']:
    check(clip['decoded'] and clip['frames']==int(clip['nb_read_frames']),f"{clip['name']}: 完整解码且帧数一致")
    check(clip['distinct_source_frames']==clip['frames'],f"{clip['name']}: 源帧确实变化")

hashes={str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(ROOT.rglob('*')) if path.is_file() and path.name!='validation.json'}
report={'范围':'文件、模型结构与离线预览检查；不等于Minecraft实机验收','passed':len(checks),'checks':checks,'sha256':hashes}
(ROOT/'metadata/validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'passed':len(checks),'files':len(hashes)},ensure_ascii=False))
