"""Copy original artwork into conventional Forge resource paths and synthesize original sounds."""
from pathlib import Path
import json
import shutil
import subprocess
import wave
import numpy as np
from PIL import Image

WORK = Path('/workspace')
ART = WORK / 'art-build/fenglei_art_v01'
PROJECT = WORK / 'fenglei-wings'
ASSETS = PROJECT / 'src/main/resources/assets/fanren_wings'
for folder in ['textures/item','textures/gui','textures/particle','textures/misc','models/item','particles','lang','sounds','models']:
    (ASSETS/folder).mkdir(parents=True,exist_ok=True)

def data(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

for palette in ['silver','indigo']:
    for resolution in [16,32]:
        src=ART/f'textures/item/fenglei_chi_{palette}_{resolution}.png'
        shutil.copy2(src, ASSETS/f'textures/item/{src.name}')
shutil.copy2(ART/'textures/item/fenglei_chi_silver_16.png',ASSETS/'textures/item/fenglei_chi.png')
shutil.copy2(ART/'textures/gui/hud_atlas.png',ASSETS/'textures/gui/hud_atlas.png')
Image.new('RGBA',(16,16),(255,255,255,255)).save(ASSETS/'textures/misc/white.png')
Image.new('RGBA',(16,16),(255,255,255,0)).save(ASSETS/'textures/misc/transparent.png')
for name in ['wind_ribbon','thunder_arc','shock_ring']:
    sheet=Image.open(ART/f'textures/particle/{name}.png')
    for frame in range(8):
        sheet.crop((0,frame*32,32,(frame+1)*32)).save(ASSETS/f'textures/particle/{name}_{frame}.png')
    data(ASSETS/f'particles/{name}.json',{'textures':[f'fanren_wings:{name}_{i}' for i in range(8)]})
data(ASSETS/'models/item/fenglei_chi.json',{
    'parent':'builtin/entity','gui_light':'front','textures':{'particle':'fanren_wings:item/fenglei_chi'},
    'display':{'ground':{'translation':[0,3,0],'scale':[1,1,1]},
               'thirdperson_righthand':{'rotation':[0,90,0],'scale':[1,1,1]},
               'firstperson_righthand':{'rotation':[0,30,0],'scale':[1,1,1]}}})

zh={
 'item.fanren_wings.fenglei_chi':'风雷翅',
 'item.fanren_wings.fenglei_chi.desc1':'以上古雷鹏骨翅炼成的飞行法宝',
 'item.fanren_wings.fenglei_chi.desc2':'御风而行，挟雷而遁',
 'item.fanren_wings.hunger':'飞行与雷遁消耗饥饿值',
 'message.fanren_wings.mode':'飞行档位：%s',
 'message.fanren_wings.hungry':'饥饿值不足，已收翼缓降',
 'key.categories.fanren_wings':'风雷翅',
 'key.fanren_wings.category':'风雷翅',
 'key.fanren_wings.toggle':'展开 / 收起风雷翅',
 'key.fanren_wings.cycle_mode':'切换遁速档位',
 'key.fanren_wings.hover':'一键悬停',
 'key.fanren_wings.thunder_blink':'雷遁术',
 'hud.fanren_wings.hunger':'饥饿消耗',
 'hud.fanren_wings.low_hunger':'饥饿值偏低',
 'hud.fanren_wings.ready':'雷遁就绪',
 'hud.fanren_wings.cooldown':'雷遁冷却',
 'hud.fanren_wings.folded':'已收翼',
 'hud.fanren_wings.combo':'连闪',
 'mode.fanren_wings.0':'悬停', 'mode.fanren_wings.1':'御风',
 'mode.fanren_wings.2':'疾风', 'mode.fanren_wings.3':'风雷',
 'subtitles.fanren_wings.wind_loop':'风雷翅：风声',
 'subtitles.fanren_wings.thunder_boom':'风雷翅：音爆',
 'subtitles.fanren_wings.thunder_blink':'风雷翅：雷遁'
}
en={
 'item.fanren_wings.fenglei_chi':'Wind-Thunder Wings',
 'item.fanren_wings.fenglei_chi.desc1':'A flying treasure forged from ancient thunder wings',
 'item.fanren_wings.fenglei_chi.desc2':'Ride the wind. Cross the sky in thunder.',
 'item.fanren_wings.hunger':'Flight and blink consume hunger',
 'message.fanren_wings.mode':'Flight mode: %s',
 'message.fanren_wings.hungry':'Too hungry. Wings folded; slow falling active.',
 'key.categories.fanren_wings':'Wind-Thunder Wings',
 'key.fanren_wings.category':'Wind-Thunder Wings',
 'key.fanren_wings.toggle':'Deploy / fold wings',
 'key.fanren_wings.cycle_mode':'Cycle flight mode',
 'key.fanren_wings.hover':'Toggle hover',
 'key.fanren_wings.thunder_blink':'Thunder blink',
 'hud.fanren_wings.hunger':'Hunger powered',
 'hud.fanren_wings.low_hunger':'Low hunger',
 'hud.fanren_wings.ready':'Blink ready',
 'hud.fanren_wings.cooldown':'Blink cooldown',
 'hud.fanren_wings.folded':'Folded',
 'hud.fanren_wings.combo':'Combo',
 'mode.fanren_wings.0':'Hover','mode.fanren_wings.1':'Wind',
 'mode.fanren_wings.2':'Gale','mode.fanren_wings.3':'Thunder',
 'subtitles.fanren_wings.wind_loop':'Wings: wind',
 'subtitles.fanren_wings.thunder_boom':'Wings: sonic burst',
 'subtitles.fanren_wings.thunder_blink':'Wings: thunder blink'
}
data(ASSETS/'lang/zh_cn.json',zh)
data(ASSETS/'lang/en_us.json',en)

rate=24000
rng=np.random.default_rng(20261006)
sound_defs={}
for name,duration in [('wind_loop',1.0),('thunder_boom',0.75),('thunder_blink',0.38)]:
    t=np.arange(round(rate*duration))/rate
    noise=rng.normal(0,1,len(t))
    low=np.convolve(noise,np.ones(48)/48,mode='same')
    if name=='wind_loop':
        envelope=np.sin(np.pi*t/duration)**2
        signal=(low*1.6+0.03*np.sin(2*np.pi*140*t))*envelope
    else:
        envelope=np.minimum(t/0.008,1)*np.exp(-t*(7 if name=='thunder_boom' else 13))
        signal=(low*2+0.22*np.sin(2*np.pi*(72*t-15*t*t))+noise*0.04)*envelope
    signal=np.clip(signal,-0.8,0.8)
    wav=WORK/f'art-build/{name}.wav'
    with wave.open(str(wav),'wb') as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
        out.writeframes((signal*32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(ASSETS/f'sounds/{name}.ogg')],check=True)
    sound_defs[name]={'subtitle':f'subtitles.fanren_wings.{name}','sounds':[{'name':f'fanren_wings:{name}','stream':False}]}
data(ASSETS/'sounds.json',sound_defs)
mesh=ART/'model/wind_thunder_wings.mesh.json'
if mesh.exists(): shutil.copy2(mesh,ASSETS/'models/wind_thunder_wings.mesh.json')
data(ART/'metadata/game_resources.json',{
    'namespace':'fanren_wings','mc_version':'1.20.1','loader':'Forge 47.4.0 provisional',
    'artwork':'Original generated pixel geometry and procedural audio; no original-video files copied.',
    'hunger_defaults':{'per_second':[0.02,0.05,0.15,0.4],'blink_points':1,'minimum_to_fly':2},
    'hunger_note':'Provisional balancing defaults, configurable; Notion did not specify hunger conversion rates.',
    'files':[str(p.relative_to(ASSETS)) for p in sorted(ASSETS.rglob('*')) if p.is_file()]
})
print('Minecraft resources:', len(list(ASSETS.rglob('*'))), 'entries; mesh ready:',mesh.exists())
