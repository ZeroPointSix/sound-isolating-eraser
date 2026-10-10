"""Deterministic original pixel assets and review plates for the art prototype."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageFont

ROOT = Path('/workspace')
PACK = ROOT / 'art-build' / 'fenglei_art_v01'
PREV = PACK / 'previews'
FONT = '/workspace/art-build/ZCOOLXiaoWei-Regular.ttf'
for directory in ('textures/item', 'textures/gui', 'textures/particle', 'metadata', 'previews', 'source'):
    (PACK / directory).mkdir(parents=True, exist_ok=True)


def save_json(name, data):
    (PACK / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')


def font(size):
    return ImageFont.truetype(FONT, size)


def icon(palette='silver', resolution=16):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    dark, shade, mid, light, gold = ('#253e48', '#779fae', '#c2dce4', '#f5ffff', '#eac46e')
    if palette == 'indigo':
        dark, shade, mid, light, gold = ('#182834', '#24596c', '#3c95a1', '#9de5e4', '#bc9657')
    left = [(1,2),(5,4),(7,7),(7,13),(5,12),(4,10),(2,10),(3,8),(1,7),(2,6),(1,4)]
    for mirror in (False, True):
        points = [(15-x if mirror else x,y) for x,y in left]
        draw.polygon(points, fill=dark)
        def line(points, color):
            draw.line([(15-x if mirror else x,y) for x,y in points], fill=color, width=1)
        line([(2,3),(5,5),(6,7),(6,11)], light)
        line([(1,4),(4,6),(5,8),(5,10)], mid)
        line([(2,6),(4,8),(5,10),(6,12)], shade)
        line([(2,8),(4,9)], light)
        line([(3,9),(5,11)], mid)
        line([(5,5),(5,6),(6,7)], gold)
    draw.line([(8,5),(7,7),(8,7),(7,10),(8,10),(7,13)], fill=gold)
    if resolution == 32:
        image = image.resize((32,32), Image.Resampling.NEAREST)
        draw = ImageDraw.Draw(image)
        draw.line([(4,8),(9,12),(11,15)], fill=gold, width=1)
        draw.line([(27,8),(22,12),(20,15)], fill=gold, width=1)
    return image


ICONS = {}
for palette in ('silver','indigo'):
    for res in (16,32):
        value = icon(palette,res)
        filename = f'fenglei_chi_{palette}_{res}.png'
        value.save(PACK/'textures/item'/filename)
        ICONS[(palette,res)] = value


def mode_glyph(index):
    im=Image.new('RGBA',(16,16))
    d=ImageDraw.Draw(im)
    colors=['#c3e7ec','#7ed9d1','#e4cd91','#f7e6a4']
    c=colors[index]
    if index==0:
        d.line([(2,6),(5,4),(8,6),(11,4),(14,6)],fill=c,width=1)
        d.line([(3,9),(13,9)],fill=c,width=1)
        d.line([(6,12),(10,12)],fill=c,width=1)
    elif index==1:
        for y in (4,8,12):
            d.line([(2,y),(11,y),(13,y-2)],fill=c,width=1)
    elif index==2:
        for x in (3,8):
            d.line([(x,3),(x+4,7),(x,12)],fill=c,width=2)
    else:
        d.polygon([(9,1),(4,8),(8,8),(6,15),(13,6),(9,6)],fill=c)
    return im


atlas=Image.new('RGBA',(256,128))
atlas_frames={}
for i,name in enumerate(('hover','wind','gale','thunder')):
    atlas.alpha_composite(mode_glyph(i),(i*16,0))
    atlas_frames[name]={'x':i*16,'y':0,'width':16,'height':16}
for i in range(16):
    im=Image.new('RGBA',(16,16))
    d=ImageDraw.Draw(im)
    d.ellipse((1,1,14,14),outline='#46616b',width=1)
    if i:
        d.arc((1,1,14,14),start=-90,end=-90+360*i/15,fill='#e7ca79',width=2)
    atlas.alpha_composite(im,(i*16,24))
    atlas_frames[f'cooldown_{i:02}']={'x':i*16,'y':24,'width':16,'height':16}
atlas.save(PACK/'textures/gui/hud_atlas.png')
save_json('metadata/hud_atlas.json',{'texture_size':[256,128],'filter':'nearest','cooldown_semantics':'序号表示已完成冷却的比例；00=0%，15=100%就绪','frames':atlas_frames})


def particles(kind):
    width=32
    count=8
    sheet=Image.new('RGBA',(width,width*count))
    for i in range(count):
        frame=Image.new('RGBA',(width,width))
        d=ImageDraw.Draw(frame)
        t=i/(count-1)
        if kind=='wind_ribbon':
            alpha=int(190*(1-t))
            points=[(2,22),(7,18-i//2),(15,19-i//2),(22,12),(29,10)]
            d.line(points,fill=(130,221,217,alpha),width=max(1,3-i//3))
        elif kind=='thunder_arc':
            x=(i*3)%5-2
            points=[(8+x,2),(5+x,10),(14,8),(11,17),(23-x,14),(20-x,28)]
            d.line(points,fill=(182,152,71,int(210*(1-t))),width=3)
            d.line(points,fill=(255,247,201,int(255*(1-t))),width=1)
        else:
            r=3+int(12*t)
            d.ellipse((16-r,16-r,16+r,16+r),outline=(166,235,233,int(220*(1-t))),width=2)
        sheet.alpha_composite(frame,(0,i*width))
    sheet.save(PACK/f'textures/particle/{kind}.png')
    save_json(f'textures/particle/{kind}.png.mcmeta',{'animation':{'frametime':1,'interpolate':False}})


for name in ('wind_ribbon','thunder_arc','shock_ring'):
    particles(name)
save_json('metadata/particle_sequences.json',{'frame_size':[32,32],'frame_count':8,'layout':'vertical','fps_reference':20,'playback':'one_shot','final_frame':'transparent','duration_seconds':0.4,'note':'mcmeta只提供帧组织；单次寿命、触发时机与混合方式由游戏渲染器实现，不能当无限循环贴图。'})


def plate(title,subtitle):
    image=Image.new('RGB',(1440,900),'#eef1f0')
    d=ImageDraw.Draw(image)
    d.text((56,34),title,font=font(40),fill='#1f3137')
    d.text((58,98),subtitle,font=font(20),fill='#5e747a')
    d.line((56,141,1384,141),fill='#bac9c9',width=1)
    return image,d


sheet,d=plate('物品栏识别测试','实际输出 16×16 / 32×32 透明 PNG；放大只用最近邻，不用模糊缩放。')
for col,(name,bg) in enumerate((('原版灰底','#8b8b8b'),('深色背景','#24373b'),('浅色背景','#fafcf9'))):
    x=58+col*444
    d.text((x,172),name,font=font(25),fill='#1f3137')
    d.rectangle((x,215,x+404,619),fill=bg)
    sheet.paste(ICONS[('silver',16)].resize((256,256),Image.Resampling.NEAREST),(x+74,286),ICONS[('silver',16)].resize((256,256),Image.Resampling.NEAREST))
    for n,res in enumerate((16,32)):
        im=ICONS[('silver',res)]
        d.text((x+30+n*172,655),f'{res}×{res} 原尺寸',font=font(18),fill='#496169')
        d.rectangle((x+55+n*172,688,x+122+n*172,750),fill=bg)
        sheet.paste(im,(x+75+n*172,701),im)
d.text((58,807),'轮廓固定：双翼 + 中央金色雷纹。靛青备选仅换色，不改变识别形。',font=font(23),fill='#1f3137')
sheet.save(PREV/'inventory_readability.png')


sheet,d=plate('物品栏与状态界面','界面样张，非游戏截图。采用饥饿值消耗口径，不添加独立灵力条。')
# Inventory panel is a familiar framed tool, not a marketing card.
d.rectangle((58,180,829,744),fill='#c6c6c6',outline='#3f4549',width=4)
d.line((62,184,825,184),fill='#ffffff',width=5)
d.text((87,207),'风雷翅',font=font(29),fill='#353b3e')
for row in range(3):
    for col in range(9):
        x,y=88+col*78,338+row*78
        d.rectangle((x,y,x+70,y+70),fill='#8b8b8b',outline='#373737',width=3)
        d.line((x+2,y+69,x+69,y+69),fill='#eeeeee',width=3)
for col in range(9):
    x,y=88+col*78,600
    d.rectangle((x,y,x+70,y+70),fill='#8b8b8b',outline='#373737',width=3)
    d.line((x+2,y+69,x+69,y+69),fill='#eeeeee',width=3)
small=ICONS[('silver',16)].resize((64,64),Image.Resampling.NEAREST)
sheet.paste(small,(91,341),small)
d.rectangle((86,336,160,410),outline='#fff3b0',width=3)
d.text((89,690),'已选中  /  背部饰品',font=font(21),fill='#48595e')
d.rectangle((518,240,795,320),fill='#202a30',outline='#678991',width=2)
d.text((535,252),'风雷翅',font=font(23),fill='#f1d786')
d.text((535,287),'银羽金纹  /  飞行法宝',font=font(16),fill='#e0e6e4')
d.text((890,187),'右下角 HUD',font=font(26),fill='#21353d')
d.text((890,238),'档位用形状与文字双重区分',font=font(19),fill='#5d747b')
for i,name in enumerate(('悬停','御风','疾风','风雷')):
    x,y=890+(i%2)*238,295+(i//2)*176
    d.rectangle((x,y,x+206,y+136),fill='#27363b',outline='#748e92',width=2)
    glyph=mode_glyph(i).resize((48,48),Image.Resampling.NEAREST)
    sheet.paste(glyph,(x+18,y+16),glyph)
    d.text((x+84,y+24),name,font=font(23),fill='#f6e7bc')
    d.text((x+20,y+87),'雷遁  就绪' if i<2 else '雷遁  1.2s',font=font(18),fill='#c8dbdc')
    progress=15 if i<2 else 7
    ring=atlas.crop((progress*16,24,progress*16+16,40)).resize((32,32),Image.Resampling.NEAREST)
    sheet.paste(ring,(x+164,y+82),ring)
d.text((890,670),'不遮挡准星、原版饥饿条和快捷栏。',font=font(18),fill='#4a666f')
d.text((890,713),'低饥饿：文字提示 + 静态橙边；不闪屏。',font=font(18),fill='#4a666f')
d.text((58,807),'建议交互：常驻信息精简；切档显示短提示；冷却结束仅做一次轻量脉冲。',font=font(23),fill='#1f3137')
sheet.save(PREV/'inventory_hud_mock.png')


sheet,d=plate('风 / 雷 / 音爆  /  粒子样张','三组原创透明序列贴图；每组 8 帧，32×32 像素；不包含音频。')
for row,(kind,label) in enumerate((('wind_ribbon','翼尖风带'),('thunder_arc','细金电弧'),('shock_ring','青白冲击环'))):
    y=215+row*180
    d.text((58,y+30),label,font=font(26),fill='#1f3137')
    data=Image.open(PACK/f'textures/particle/{kind}.png')
    for i in range(8):
        x=293+i*131
        d.rectangle((x,y,x+112,y+112),fill='#273c42')
        frame=data.crop((0,i*32,32,(i+1)*32)).resize((96,96),Image.Resampling.NEAREST)
        sheet.paste(frame,(x+8,y+8),frame)
        d.text((x+43,y+123),str(i+1),font=font(17),fill='#677d82')
d.text((58,807),'克制发光：先看清羽片结构，再加粒子。雷遁闪白默认关闭，避免全屏频闪。',font=font(23),fill='#1f3137')
sheet.save(PREV/'particle_sheet.png')

frames=[]
for f in range(40):
    motion=Image.new('RGB',(960,480),'#eef1f0')
    md=ImageDraw.Draw(motion)
    md.text((36,28),'风雷翅 / 粒子节奏预览',font=font(31),fill='#21383c')
    md.text((36,77),'20 fps / 单次 0.4 秒 / 末帧透明',font=font(18),fill='#617d82')
    for i,(kind,label) in enumerate((('wind_ribbon','翼尖风带'),('thunder_arc','细金电弧'),('shock_ring','冲击环'))):
        x=37+312*i
        md.rectangle((x,129,x+265,374),fill='#273c42')
        active=f-10
        if 0<=active<8:
            source=Image.open(PACK/f'textures/particle/{kind}.png')
            sprite=source.crop((0,active*32,32,active*32+32)).resize((192,192),Image.Resampling.NEAREST)
            motion.paste(sprite,(x+37,155),sprite)
        md.text((x+84,390),label,font=font(22),fill='#304e56')
    md.text((37,443),'离线素材预览 / 非游戏实机 / 不含音效',font=font(16),fill='#617d82')
    frames.append(motion)
frames[0].save(PREV/'particle_motion.gif',save_all=True,append_images=frames[1:],duration=50,loop=0,optimize=False)

save_json('metadata/art_direction.json',{
    '名称':'风雷翅', '版本':'美术首版 v0.1',
    '定位':'原创同人美术样机，不是可运行模组，不是实机截图',
    '主配色':{'银白':'#f5ffff','银影':'#779fae','细金':'#eac46e','风青':'#7ed9d1'},
    '消耗口径':'遵循 Notion 文末补充，使用原版饥饿值；不新增独立灵力条',
    'UI原则':['16像素轮廓优先','原尺寸、浅底、深底都可读','模式文字与图形双通道','低饥饿不全屏闪烁','避开原版快捷栏和饥饿条'],
    '集成状态':'加载器/目标仓库待确认，GLB并非Minecraft原生模型格式。需要游戏渲染层/骨骼转换和客户端实测。',
    '透明材质':'本轮结构渲染采用不透明低模，0.8透明度和emissive分层为下一步客户端材质调优，不冒称已实现',
    '音效':'未交付音效；后续使用原创或CC0音源',
    'IP说明':'同人非官方，风雷翅设定属于《凡人修仙传》原作者及相应权利方；本包几何与像素资产重新制作，不含动画/视频提取模型、贴图或音轨。发布授权另行核查。'
})
save_json('metadata/sources.json',[
    {'类型':'用户需求','标题':'风雷翅 Minecraft 物品设计文档','链接':'https://app.notion.com/p/Minecraft-3f1463aed7e681e8a6e1cc9842ba7dc9','用途':'形态、配色、动画、边界；文末饥饿值补充优先'},
    {'类型':'网上视觉参考','标题':'Bilibili：Han Li Wind and Thunder Wings Test Flight','链接':'https://www.bilibili.tv/en/video/4796494997493770','用途':'分层大翼轮廓与青金能量参考，不提取资产'},
    {'类型':'网上视觉参考','标题':'韩立炼化风雷翅','链接':'https://www.sohu.com/a/865806123_568249','用途':'银蓝/青金电弧观感参考，不作设定事实依据'},
    {'类型':'工具','标题':'Blender 4.2 LTS','链接':'https://www.blender.org/download/lts/4-2/','用途':'真实模型和动画生成'}
])
print(json.dumps({'pack':str(PACK),'png_count':len(list(PACK.rglob('*.png'))),'json_count':len(list(PACK.rglob('*.json')))},ensure_ascii=False))
