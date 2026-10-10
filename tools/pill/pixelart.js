/*
 * 强化药片 / 空药板 像素贴图生成器（唯一数据源）。
 * 预览页直接调用它绘制贴图；tools/export.ps1 通过无头 Edge 调用它导出 PNG，
 * 保证预览里看到的和资源包里的贴图逐像素一致。
 */
(function (root) {
  'use strict';

  var SIZE = 32;

  function hex(h, a) {
    return [
      parseInt(h.substr(1, 2), 16),
      parseInt(h.substr(3, 2), 16),
      parseInt(h.substr(5, 2), 16),
      a == null ? 255 : a
    ];
  }

  // 药板外框（含描边），四角各切掉一格做圆角
  var PLATE = { x0: 4, y0: 1, x1: 26, y1: 30 };
  // 3 列 × 4 行凹槽，每个 5×5；顶部留 3 像素蚀刻带
  var COLS = [7, 13, 19];
  var ROWS = [5, 11, 17, 23];
  var SLOT_COUNT = COLS.length * ROWS.length;

  var THEMES = {
    full: {
      outline: hex('#24272c'),
      outlineShadow: hex('#191b1f'),
      foil: ['#3c4047', '#4d525a', '#60666e', '#757b84', '#8c929a', '#a6acb4', '#bfc5cc'].map(function (c) { return hex(c); }),
      etch: ['#38414c', '#45505c', '#53606d', '#63717f', '#748493', '#8698a8', '#9aacbc'].map(function (c) { return hex(c); }),
      rimLight: hex('#c3ccd4'),
      rimShadow: hex('#363b42'),
      hole: [17, 21, 27, 150],
      holeEdge: hex('#7b838d'),
      crumple: [224, 232, 238, 225],
      pillRim: hex('#d4dee6'),
      pill: {
        w: [240, 251, 255, 235],
        p: [196, 230, 242, 220],
        P: [159, 208, 228, 215],
        d: [116, 170, 196, 220]
      }
    },
    empty: {
      outline: hex('#1c1e22'),
      outlineShadow: hex('#131417'),
      foil: ['#303338', '#3c4045', '#4a4e54', '#595e64', '#6a6f75', '#7d8288', '#91969c'].map(function (c) { return hex(c); }),
      etch: ['#2f363e', '#39424b', '#454f59', '#525d68', '#616d79', '#727f8b', '#84919d'].map(function (c) { return hex(c); }),
      rimLight: hex('#959ea6'),
      rimShadow: hex('#2b2f35'),
      hole: [12, 15, 20, 165],
      holeEdge: hex('#5f666e'),
      crumple: [186, 194, 201, 205],
      pillRim: null,
      pill: null
    }
  };

  // 空凹槽：被顶破的箔口 + 压瘪的塑料泡。L 亮边 / s 暗边 / H 破口 / c 塑料褶皱反光 / f 撕开的箔片
  var EMPTY_STAMPS = [
    ['.LLL.', 'LcHHs', 'LHHHs', 'LHHfs', '.sss.'],
    ['.LLL.', 'LHcHs', 'LHHHs', 'LfHHs', '.sss.'],
    ['.LLL.', 'LcHfs', 'LHHHs', 'LHHHs', '.sss.'],
    ['.LLL.', 'LHHHs', 'LcHHs', 'LHfHs', '.sss.']
  ];
  // 最后一粒：完整的泡罩 + 淡蓝半透明药片。w 高光 / p 亮部 / P 主色 / d 暗部
  var PILL_STAMP = ['.LLL.', 'LwpPs', 'LpPPs', 'LPPds', '.sss.'];

  function clampIdx(v, n) {
    v = Math.round(v);
    return v < 0 ? 0 : (v >= n ? n - 1 : v);
  }

  function isCutCorner(x, y) {
    var p = PLATE;
    return (x === p.x0 || x === p.x1) && (y === p.y0 || y === p.y1);
  }

  function isOutline(x, y) {
    var p = PLATE;
    if (x === p.x0 || x === p.x1 || y === p.y0 || y === p.y1) return true;
    // 圆角内侧再补一格描边，让切角更圆
    return (x === p.x0 + 1 || x === p.x1 - 1) && (y === p.y0 + 1 || y === p.y1 - 1);
  }

  // 暗银金属箔：左上亮、右下暗，加一条斜向高光带营造金属光泽
  function foilLevel(x, y) {
    var p = PLATE;
    var d = (x - p.x0) + (y - p.y0);
    var v = 4.4 - d / 51 * 3.2;
    if (d >= 11 && d <= 14) v += 1.0;
    if (d === 10 || d === 15) v += 0.5;
    if (d >= 34 && d <= 35) v += 0.55;
    if (y === p.y0 + 1 || x === p.x0 + 1) v += 0.7;
    if (y === p.y1 - 1 || x === p.x1 - 1) v -= 0.8;
    return v;
  }

  // 细密蚀刻纹：低对比、偏冷色的波浪细线
  function isEtch(x, y) {
    var w = Math.round(Math.sin(x * 0.85 + 1.3) * 1.2);
    return (((y + w) % 3) + 3) % 3 === 0;
  }

  function makeImage(w, h) {
    return { w: w, h: h, data: new Uint8ClampedArray(w * h * 4) };
  }

  function setPx(img, x, y, c) {
    if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
    var i = (y * img.w + x) * 4;
    img.data[i] = c[0];
    img.data[i + 1] = c[1];
    img.data[i + 2] = c[2];
    img.data[i + 3] = c[3];
  }

  function stampCavity(img, theme, cx, cy, rows, isPill) {
    for (var j = 0; j < 5; j++) {
      for (var i = 0; i < 5; i++) {
        var ch = rows[j].charAt(i);
        var c = null;
        switch (ch) {
          case 'L': c = isPill ? theme.pillRim : theme.rimLight; break;
          case 's': c = theme.rimShadow; break;
          case 'H': c = theme.hole; break;
          case 'c': c = theme.crumple; break;
          case 'f': c = theme.holeEdge; break;
          case 'w': case 'p': case 'P': case 'd': c = theme.pill[ch]; break;
          default: c = null;
        }
        if (c) setPx(img, cx + i, cy + j, c);
      }
    }
  }

  function renderPack(themeName, pillsLeft) {
    var theme = THEMES[themeName];
    var img = makeImage(SIZE, SIZE);
    var p = PLATE;
    for (var y = p.y0; y <= p.y1; y++) {
      for (var x = p.x0; x <= p.x1; x++) {
        if (isCutCorner(x, y)) continue;
        if (isOutline(x, y)) {
          var shadowSide = x === p.x1 || y === p.y1 || (x === p.x1 - 1 && y === p.y1 - 1);
          setPx(img, x, y, shadowSide ? theme.outlineShadow : theme.outline);
          continue;
        }
        var v = foilLevel(x, y);
        if (isEtch(x, y)) {
          setPx(img, x, y, theme.etch[clampIdx(v - 1, theme.etch.length)]);
        } else {
          setPx(img, x, y, theme.foil[clampIdx(v, theme.foil.length)]);
        }
      }
    }
    for (var r = 0; r < ROWS.length; r++) {
      for (var c = 0; c < COLS.length; c++) {
        // 按“左上 → 右下”逐行抠药，剩 1 粒时正好是原著里右下角那一粒
        var slot = r * COLS.length + c;
        var pill = slot >= SLOT_COUNT - pillsLeft;
        var stamp = pill ? PILL_STAMP : EMPTY_STAMPS[(c * 5 + r * 3) % EMPTY_STAMPS.length];
        stampCavity(img, theme, COLS[c], ROWS[r], stamp, pill);
      }
    }
    return img;
  }

  // 吞服时的原版进食碎屑会从这张图里随机取块：用药片色，避免掉出“银箔碎片”
  function renderParticle() {
    var img = makeImage(16, 16);
    var cols = ['#eef9fd', '#c8e7f3', '#a9d7ea', '#8fc5dd', '#77abc5'].map(function (c) { return hex(c); });
    var seed = 1337;
    function rnd() {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed / 0x7fffffff;
    }
    for (var y = 0; y < 16; y++) {
      for (var x = 0; x < 16; x++) {
        var t = rnd();
        var idx = t < 0.08 ? 0 : t < 0.35 ? 1 : t < 0.7 ? 2 : t < 0.9 ? 3 : 4;
        setPx(img, x, y, cols[idx]);
      }
    }
    return img;
  }

  // enhancement_pill_pack_12 … _1：按剩余粒数各一张；吃完换 empty_pill_pack
  var TEXTURES = {};
  for (var n = SLOT_COUNT; n >= 1; n--) {
    (function (left) {
      TEXTURES['enhancement_pill_pack_' + left] = function () { return renderPack('full', left); };
    })(n);
  }
  TEXTURES.empty_pill_pack = function () { return renderPack('empty', 0); };
  TEXTURES.enhancement_pill_pack_particle = renderParticle;

  function packTexture(pillsLeft) {
    return pillsLeft > 0 ? 'enhancement_pill_pack_' + pillsLeft : 'empty_pill_pack';
  }

  var cache = {};
  function get(name) {
    if (!cache[name]) cache[name] = TEXTURES[name]();
    return cache[name];
  }

  function toCanvas(name, scale) {
    scale = scale || 1;
    var img = get(name);
    var src = document.createElement('canvas');
    src.width = img.w;
    src.height = img.h;
    src.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(img.data), img.w, img.h), 0, 0);
    if (scale === 1) return src;
    var out = document.createElement('canvas');
    out.width = img.w * scale;
    out.height = img.h * scale;
    var g = out.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(src, 0, 0, out.width, out.height);
    return out;
  }

  root.PillArt = {
    names: Object.keys(TEXTURES),
    get: get,
    toCanvas: toCanvas,
    packTexture: packTexture,
    slotCount: SLOT_COUNT
  };
})(typeof window !== 'undefined' ? window : this);
