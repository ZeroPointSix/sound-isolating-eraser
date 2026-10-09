// 导出杀夏刀贴图与模型，只用 node 内置模块。
// 用法：node tools/shaxia/export-assets.mjs [--review 审图PNG路径]
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(here, '../../src/main/resources/assets/sound_isolating_eraser');
const scope = {};
vm.runInNewContext(fs.readFileSync(path.join(here, 'pixelart.js'), 'utf8'), scope);
const art = scope.ShaxiaArt;

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(kind, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(kind, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(w, h, rgba) {
  const rows = [];
  for (let y = 0; y < h; y++) rows.push(Buffer.from([0]), Buffer.from(rgba.subarray(y * w * 4, (y + 1) * w * 4)));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(Buffer.concat(rows), { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

for (const name of art.names) {
  const img = art.get(name);
  fs.writeFileSync(path.join(assets, 'textures/item', name + '.png'), png(img.w, img.h, img.data));
  const model = { parent: 'minecraft:item/handheld', textures: { layer0: `sound_isolating_eraser:item/${name}` } };
  fs.writeFileSync(path.join(assets, 'models/item', name + '.json'), JSON.stringify(model, null, 2) + '\n');
  console.log(`exported ${name} (${img.w}x${img.h})`);
}

// 审图：物品栏灰底 / 暗底各放大 12 倍，旁边附 1 倍和 2 倍实际大小
const reviewAt = process.argv.indexOf('--review');
if (reviewAt > 0) {
  const img = art.get('shaxiadao'), Z = 12, PAD = 16, T = img.w * Z;
  const W = PAD * 4 + T * 2 + img.w * 3, H = PAD * 2 + T;
  const out = new Uint8ClampedArray(W * H * 4);
  const fill = (x0, y0, w, h, c) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) out.set([...c, 255], (y * W + x) * 4); };
  const blit = (x0, y0, z) => {
    for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
      const o = (y * img.w + x) * 4;
      if (img.data[o + 3]) fill(x0 + x * z, y0 + y * z, z, z, [img.data[o], img.data[o + 1], img.data[o + 2]]);
    }
  };
  fill(0, 0, W, H, [32, 32, 32]);
  fill(PAD, PAD, T, T, [139, 139, 139]); blit(PAD, PAD, Z);
  fill(PAD * 2 + T, PAD, T, T, [24, 26, 30]); blit(PAD * 2 + T, PAD, Z);
  const sx = PAD * 3 + T * 2;
  fill(sx, PAD, img.w * 2, img.w * 2, [139, 139, 139]); blit(sx, PAD, 2);
  fill(sx, PAD * 2 + img.w * 2, img.w, img.w, [139, 139, 139]); blit(sx, PAD * 2 + img.w * 2, 1);
  fs.writeFileSync(process.argv[reviewAt + 1], png(W, H, out));
  console.log('review ->', process.argv[reviewAt + 1]);
}
