"""Lay out the art handoff as a visually verified Chinese PDF."""
from pathlib import Path
import json
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from PIL import Image

ROOT=Path('/workspace')
PACK=ROOT/'art-build/fenglei_art_v01'
MODEL=PACK/'model'
OUTPUT=ROOT/'output'
OUTPUT.mkdir(exist_ok=True)
pdfmetrics.registerFont(TTFont('CN',str(ROOT/'art-build/ZCOOLXiaoWei-Regular.ttf')))
pdfmetrics.registerFont(TTFont('Latin','/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'))
W,H=960,600
C=canvas.Canvas(str(OUTPUT/'风雷翅_美术首版.pdf'),pagesize=(W,H))
C.setTitle('风雷翅 / 美术与核心工程验收 v0.1')
C.setAuthor('测试agent')
INK='#21383c'; MUTED='#667d80'; TEAL='#4c8e8c'; GOLD='#cfb06e'; PAPER='#f0f3f1'
PAGE=0

def text(x,y,value,size=15,color=INK,face='CN'):
    C.setFillColor(HexColor(color)); C.setFont(face,size); C.drawString(x,y,value)

def lines(x,y,value,width,size=15,leading=24,color=INK):
    result=[]; current=''
    for char in value:
        if char=='\n':
            result.append(current); current=''; continue
        if pdfmetrics.stringWidth(current+char,'CN',size)>width and current:
            result.append(current); current=char
        else: current+=char
    result.append(current)
    for row in result:
        text(x,y,row,size,color); y-=leading
    return y

def start(title,kicker):
    global PAGE
    PAGE+=1
    C.setFillColor(HexColor(PAPER)); C.rect(0,0,W,H,fill=1,stroke=0)
    text(42,H-39,'风雷翅  /  美术与核心工程验收',12,TEAL)
    text(42,H-80,title,30)
    text(42,H-110,kicker,13,MUTED)
    C.setStrokeColor(HexColor('#c3d0ce')); C.line(42,H-126,W-42,H-126)

def finish():
    C.setStrokeColor(HexColor('#c3d0ce')); C.line(42,35,W-42,35)
    text(42,18,'同人非官方  /  原创美术样机  /  非游戏实机',10,MUTED)
    text(W-103,18,f'{PAGE:02d} / v0.1',10,MUTED,'Latin')
    C.showPage()

def pic(path,x,y,width,height):
    image=Image.open(path)
    scale=min(width/image.width,height/image.height)
    dw,dh=image.width*scale,image.height*scale
    C.drawImage(str(path),x+(width-dw)/2,y+(height-dh)/2,dw,dh,mask='auto')

manifest=json.loads((MODEL/'asset_manifest.json').read_text())
geo=manifest['geometry']

start('银羽金纹，先把视觉做好','2026-10-06  /  A 概念方向、B 模型预览、C 游戏资源，分别验收')
text(45,398,'风雷翅',62)
text(47,355,'分层长羽 / 淡青风带 / 细金雷纹',20,TEAL)
lines(47,304,'从设定中提炼翼形和风雷特征，重新制作低模和像素素材。默认采用银白金纹，靛青作为概念配色备选。',425,18,29)
lines(47,192,'本包覆盖：真实模型、三视图、物品栏图标、掉落与穿戴状态、命名动画及粒子素材。',425,17,28)
lines(47,100,'右图为 AI 美术方向稿，不是三维渲染或游戏截图；以下页面单独展示实际模型与制作结果。',425,13,21,MUTED)
pic(ROOT/'output/fenglei-direction-v1.png',506,52,410,411)
finish()

start('实际模型 / 完整翼形','Blender 真实建模与渲染；同一个源模型用于所有视角和状态。')
pic(MODEL/'renders/hero_45.png',43,64,874,400)
text(48,47,f"{geo['triangles']:,} 三角面  /  {geo['bones']} 骨骼  /  3 层羽片  /  单位 1 = 1 格",12,MUTED)
finish()

start('正交三视图','正、右、俯使用正交相机；原始 PNG 保留统一相机尺度。本页为排版缩放。')
views=[('orthographic_front','正视','正面观察羽片分层、对称轮廓与背挂位置。'),('orthographic_side','右视','观察翼根厚度、与背部间隙和前后层次。'),('orthographic_top','俯视','观察左右展开角、后掠方向与玩家身位。')]
for index,(name,label,note) in enumerate(views):
    y=330-index*140
    pic(MODEL/f'renders/{name}.png',46,y,550,132)
    text(625,y+93,label,24,TEAL)
    lines(625,y+58,note,280,15,23)
    if index<2:
        C.setStrokeColor(HexColor('#d2dcd9')); C.line(42,y-5,918,y-5)
finish()

start('三种状态 / 同一物品','收起时约 0.4 格小翼，展开时约 6.4 格翼展；掉落形态保留核心双翼轮廓。')
for index,(name,label,detail) in enumerate([('worn_idle','穿戴 / 收起','背后保留小翼，不让整副翅膀一直挡住角色。'),('dropped','地面 / 掉落','小翼浮动、缓慢旋转；金纹用于近距离识别。'),('worn_flight','穿戴 / 展开','完整分层翼形，风带从翼尖向后，雷弧只作点缀。')]):
    x=42+index*300
    pic(MODEL/f'renders/{name}.png',x,197,278,259)
    text(x,168,label,23,TEAL)
    lines(x,135,detail,270,15,25)
text(43,57,'提示：穿模、第一人称遮挡及多人同步仍需在目标 Minecraft 客户端测试。',12,MUTED)
finish()

start('物品栏 / 原尺寸可读性','16×16 为基础版，32×32 为高清备选；都是独立、透明、硬边像素图。')
pic(PACK/'previews/inventory_readability.png',38,45,884,422)
finish()

start('UI / 只保留需要的信息','依据最新补充采用饥饿值消耗，不额外堆叠灵力条。下图是静态样张。')
pic(PACK/'previews/inventory_hud_mock.png',38,43,884,423)
finish()

start('动效 / 从收起到雷遁','命名动作保存在源模型中；视频用于查看节奏，不等同于游戏运行时验收。')
labels={'deploy':'展开','hover':'悬停','glide':'御风滑翔','boost':'疾风后掠','thunder_boost':'风雷后掠','blink':'雷遁','retract':'收起','drop_spin_bob':'掉落旋转'}
notes={'deploy':'由 0.4 格小翼展开；目标时长 0.3 秒。','hover':'0.8 Hz，小幅摆动；60 帧为两周期。','glide':'保持展翼，轻微呼吸感。','boost':'约 30 度后掠，轮廓收紧。','thunder_boost':'约 60 度后掠，强调高速姿态。','blink':'瞬时最大展翼；全屏闪白默认关闭。','retract':'回到背后小翼，不留下两片大翅。','drop_spin_bob':'小翼缓慢旋转和上下浮动。'}
for i,(name,entry) in enumerate(manifest['clips'].items()):
    x=43+(i%2)*455; y=421-(i//2)*85
    text(x,y,labels.get(name,name),20,TEAL)
    frames=entry.get('frames','?')
    text(x+198,y,f'{frames} frames / 24 fps',11,MUTED,'Latin')
    lines(x,y-27,notes.get(name,''),416,14,21)
    C.setStrokeColor(HexColor('#d0dad7')); C.line(x,y-54,x+414,y-54)
text(43,57,'动作采用帧量化；最终按客户端 tick、骨骼坐标与渲染插值重新适配。',12,MUTED)
finish()

start('粒子 / 结构优先，发光克制','风带、电弧、冲击环各 8 帧；第 8 帧透明。另含 3 段原创合成 OGG 音效。')
pic(PACK/'previews/particle_sheet.png',38,43,884,423)
finish()

start('资源已接入核心工程','离线模型预览与客户端运行分别验收；本报告不冒充 Minecraft 实机截图。')
text(43,423,'已交付',24,TEAL)
lines(43,384,'可编辑 Blender 源文件与 GLB\n正 / 侧 / 俯三视图与三种状态\n8 个命名动作及可播放预览\n16 / 32 图标、HUD、3 组粒子、3 音效\nForge 核心源代码与自动构建检查',405,17,33)
text(513,423,'仍需游戏验证',24,TEAL)
lines(513,384,'两名玩家的独立服务器同步\n悬停漂移与四档速度实测\n穿模、第一人称遮挡与帧率\nGUI 尺寸、物品掉落和图标实机对照\n未确认的配方、饰品兼容不擅自添加',405,17,33)
text(43,177,'来源与权利说明',21,TEAL)
sources=[('用户需求与设定','https://app.notion.com/p/Minecraft-3f1463aed7e681e8a6e1cc9842ba7dc9'),('网上翼形参考','https://www.bilibili.tv/en/video/4796494997493770'),('青金电弧参考','https://www.sohu.com/a/865806123_568249')]
for i,(title,url) in enumerate(sources):
    x=43+i*292; text(x,142,title,15,TEAL); C.linkURL(url,(x,137,x+250,158),relative=0,thickness=0)
lines(43,112,'参考图片仅用于观察造型，不随包再分发。本包几何与像素素材重新制作；风雷翅设定属于《凡人修仙传》相关权利方，同人非官方。商用或公开发布前另行确认授权。',870,12,21,MUTED)
finish()

def requirement_page(title, rows):
    start(title,'唯一需求源：Notion 最新正文；消耗按文末饥饿值补充，数值缺项不当作已确认需求。')
    y=430
    for requirement, result in rows:
        text(43,y,requirement,18,TEAL)
        lines(335,y,result,577,14,22)
        C.setStrokeColor(HexColor('#d0dad7')); C.line(43,y-49,917,y-49)
        y-=75
    finish()

requirement_page('Notion 对照 / 美术验收',[
    ('银白金纹、分层羽翼','42 片羽片、52 骨骼、2340 三角面；使用原创几何，概念图不作为渲染证据。'),
    ('三视图、收起与展开','7 张真实渲染。正/侧/俯同一相机尺度；展开约 6.4 格，收起宽约 0.4 格。'),
    ('16 / 32 像素物品栏','两种配色各 16/32 PNG，硬边透明像素。游戏 GUI 默认 16 版。'),
    ('动作与风雷反馈','8 段 MP4 共 215 帧完整解码；3 组粒子含退场透明帧，动画预览不等同实机。'),
    ('精致 HUD、仅耗饥饿','4 档状态图标、16 档冷却环和饥饿警告；不新增灵力条。文字与数值独立绘制。'),
])
requirement_page('Notion 对照 / 工程验收',[
    ('穿戴、掉落、物品栏','胸甲位物品、无防御无耐久、金色品质名；客户端模型与物品渲染器已接入源代码。'),
    ('5 / 30 / 60 / 120 m/s','服务端四档目标速度、加速平滑、未加载区块限速；具体速度误差待实机测量。'),
    ('饥饿消耗与低值保护','使用原版 foodLevel。0.02/0.05/0.15/0.4 点每秒及雷遁 1 点是可调暂定值，不是 Notion 原数值。'),
    ('24 格雷遁与碰撞','服务端玩家体积路径检查；0.6 秒三连窗口，单次 2 秒、三连 8 秒冷却。需联机回归。'),
    ('状态恢复与边界','保存冷却与饥饿欠账，恢复本模组持有的重力状态；禁止乘骑/睡眠时控制飞行。'),
])

start('分包、复现与明确缺口','源码、原始证据与验收结果均可继续复用，不需要重新建立模型或工程。')
rows=[
    ('A / 概念方向','本 PDF 第 1 页：银白金纹与靛青方向稿，只用于外观讨论。'),
    ('B / 模型渲染预览','B_model_preview.zip：.blend、.glb、7 PNG、8 MP4、尺寸和解码记录。'),
    ('C / 游戏资源与工程','C_game_resources.zip：assets/fanren_wings；源码位于独立私有分支 feat/art-first-core。'),
    ('本地可复用位置','/workspace/fenglei-wings\n/workspace/art-build/fenglei_art_v01\n/workspace/art-src/blender_scene.py'),
]
y=431
for label,note in rows:
    text(43,y,label,18,TEAL); lines(312,y,note,604,14,22); y-=72
lines(43,116,'尚未完成：双人真实 E2E、视野/灵敏度可选反馈、饰品兼容。配方与平衡值等待确认。未合并 main、未发布生产版；构建结果以随包验证记录和 PR 为准。',872,14,23)
finish()
C.save()
print(json.dumps({'pdf':str(OUTPUT/'风雷翅_美术首版.pdf'),'pages':PAGE},ensure_ascii=False))
