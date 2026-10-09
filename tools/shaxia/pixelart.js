/* Unapproved generated concept only; not user-supplied production art or an accepted visual deliverable.
   杀夏刀 32×32 物品贴图源。浏览器和 node vm 都能跑，导出见 export-assets.mjs。
   外观依据原著第 43/44 章：细长、通体暗银、扁平金属条；一侧略厚，另一侧急剧收窄到纸一样薄；
   表面细密纹路泛浅蓝（矿结统一质感）。Notion 2.5：无 glint、不发光、贴图 32×32。
   刀形与 3D 参考模型一致：方脊、等厚段 + 抛亮研磨面、刃口在前段上扬收尖、无柄无护手。 */
(function (root) {
  'use strict';

  var S = 32;
  // 刀身几何（cm），与参考模型同一套参数
  var L = 20, W = 2.0, YP = W / 2 - 0.3 * W, CLIP = 2.6, BELLY = 5.5, FLAT = 0.45;
  function spineY(x) {
    if (x <= L - CLIP) return W / 2;
    var t = (x - (L - CLIP)) / CLIP;
    return W / 2 - (W / 2 - YP) * Math.pow(t, 1.8);
  }
  function edgeY(x) {
    if (x <= L - BELLY) return -W / 2;
    var t = (x - (L - BELLY)) / BELLY;
    return -W / 2 + (YP + W / 2) * t * t;
  }

  // 贴图上的摆法：尾端左下、刀尖右上（同原版剑），刃背朝左上、刃口朝右下
  var A = [3.9, 28.1], B = [30.2, 1.9];
  var LEN = Math.hypot(B[0] - A[0], B[1] - A[1]);
  var KX = LEN / L;          // 沿长度 px/cm
  var KY = 6.8 / W;          // 横向 px/cm：真实比例只有 4 px 宽，像素图里放宽到 6.8 px 才看得清分层
  var D = [(B[0] - A[0]) / LEN, (B[1] - A[1]) / LEN];
  var N = [D[1], -D[0]];     // 指向刃背（左上）

  function toBlade(px, py) {
    var qx = px - A[0], qy = py - A[1];
    return { x: (qx * D[0] + qy * D[1]) / KX, y: (qx * N[0] + qy * N[1]) / KY };
  }
  function inside(b) { return b.x >= 0 && b.x <= L && b.y <= spineY(b.x) && b.y >= edgeY(b.x); }

  function hex(c) { return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)]; }
  var C = {
    outline: hex('#1d2025'),     // 刃背 / 尾端描边
    spineHi: hex('#9aa1a9'),     // 刃背倒角的亮线
    body: [hex('#565c64'), hex('#60666e'), hex('#6a7078')],   // 等厚段：暗银，三档轻微起伏
    shoulder: hex('#474c53'),    // 肩线
    bevel: [hex('#848a92'), hex('#9ca2aa'), hex('#b4bac1')],  // 研磨面：越靠刃口越亮
    edge: hex('#dfe5ea'),        // 纸一样薄的刃口
    edgeShade: hex('#4b5661'),   // 刃口外侧一圈深色，保证在物品栏灰底上也有轮廓
    vein: hex('#8fc5dd'),        // 纹路芯：浅蓝（不发光，只是颜色）
    veinDeep: hex('#3f5f74')     // 纹路较深的段落
  };

  // 纹路：在等厚段里“爬”的脉络。位置用 s 表示（0 刃背 → FLAT 肩线）
  // 主脉浅蓝、断续；副脉深蓝，靠近肩线，偶尔和主脉连上
  function vein1(x) { return 0.27 + 0.07 * Math.sin(x * 1.05 + 0.3) + 0.03 * Math.sin(x * 3.1); }
  function vein2(x) { return 0.37 + 0.04 * Math.sin(x * 1.6 + 2.0); }
  function lit(x) { return Math.sin(x * 2.2 + 0.7) < 0.45 && x > 1.2 && x < L - 2.2; }
  function deep(x) { return Math.sin(x * 1.3 + 2.6) > -0.1 && x > 0.8 && x < L - 3; }

  function build() {
    var cov = new Float32Array(S * S), sAvg = new Float32Array(S * S), xAvg = new Float32Array(S * S), yAvg = new Float32Array(S * S);
    var SS = 5, px, py, i, j;
    for (py = 0; py < S; py++) for (px = 0; px < S; px++) {
      var hit = 0, ss = 0, xs = 0, ys = 0;
      for (j = 0; j < SS; j++) for (i = 0; i < SS; i++) {
        var b = toBlade(px + (i + 0.5) / SS, py + (j + 0.5) / SS);
        if (!inside(b)) continue;
        hit++; xs += b.x; ys += b.y;
        var sp = spineY(b.x), ed = edgeY(b.x);
        ss += sp - ed > 1e-4 ? (sp - b.y) / (sp - ed) : 1;
      }
      var o = py * S + px;
      cov[o] = hit / (SS * SS);
      if (hit) { sAvg[o] = ss / hit; xAvg[o] = xs / hit; yAvg[o] = ys / hit; }
    }
    // 刀尖附近刀身不足一像素宽，放低覆盖阈值，免得尖被吃成钝头
    function on(x, y) {
      if (x < 0 || y < 0 || x >= S || y >= S) return false;
      var o = y * S + x;
      return cov[o] >= (xAvg[o] > L - 1.6 ? 0.22 : 0.42);
    }

    var data = new Uint8ClampedArray(S * S * 4);
    function put(x, y, c) { var o = (y * S + x) * 4; data[o] = c[0]; data[o + 1] = c[1]; data[o + 2] = c[2]; data[o + 3] = 255; }

    for (py = 0; py < S; py++) for (px = 0; px < S; px++) {
      if (!on(px, py)) continue;
      var o2 = py * S + px, s = sAvg[o2], x = xAvg[o2], y = yAvg[o2];
      // 看哪一侧露在外面：刃背/尾端 → 深色描边；刃口 → 亮刃
      var outSpine = false, outEdge = false;
      [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(function (dv) {
        if (on(px + dv[0], py + dv[1])) return;
        var nb = toBlade(px + dv[0] + 0.5, py + dv[1] + 0.5);
        if (nb.x < 0.3 || nb.y > spineY(Math.max(0, Math.min(L, nb.x)))) outSpine = true; else outEdge = true;
      });
      var c;
      if (outSpine) c = C.outline;
      else if (outEdge) c = C.edge;
      else if (s < 0.13) c = C.spineHi;
      else if (s < FLAT) {
        var band = (px * 7 + py * 3) % 5 === 0 ? 2 : (px + py) % 3 === 0 ? 0 : 1;
        c = C.body[band];
        var wpx = (spineY(x) - edgeY(x)) * KY;   // 当地刀宽（像素）
        var d1 = Math.abs(s - vein1(x)) * wpx, d2 = Math.abs(s - vein2(x)) * wpx;
        if (d1 < 0.36 && lit(x)) c = C.vein;
        else if (d2 < 0.36 && deep(x)) c = C.veinDeep;
      } else if (s < FLAT + 0.07) c = C.shoulder;
      else c = C.bevel[Math.max(0, Math.min(2, Math.floor((s - FLAT - 0.07) / (1 - FLAT - 0.07) * 3)))];
      put(px, py, c);
    }
    // 刃口外侧补一圈深色像素：只在刃口一侧，避免整把刀变粗
    var extra = [];
    for (py = 0; py < S; py++) for (px = 0; px < S; px++) {
      if (on(px, py)) continue;
      var bb = toBlade(px + 0.5, py + 0.5);
      if (bb.x < 1.0 || bb.x > L - 0.6 || bb.y > edgeY(bb.x)) continue;
      if (on(px - 1, py) || on(px, py - 1)) extra.push([px, py]);
    }
    extra.forEach(function (p) { var o3 = (p[1] * S + p[0]) * 4; if (!data[o3 + 3]) put(p[0], p[1], C.edgeShade); });
    return { w: S, h: S, data: data };
  }

  var TEXTURES = { shaxiadao: build };
  var cache = {};
  function get(name) { return cache[name] || (cache[name] = TEXTURES[name]()); }

  root.ShaxiaArt = { names: Object.keys(TEXTURES), get: get };
})(typeof window !== 'undefined' ? window : this);
