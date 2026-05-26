// 生成 PWA 图标——玉绿色圆角方形 + "印" 字
const { createCanvas } = require('canvas') || {};
const fs = require('fs');
const path = require('path');

// 手写一个最简单的 PNG 生成（192x192 纯色方块 + 白色文字）
// 为了免依赖，采用 BMP 格式作为备选，或者直接用 data URL
// 实际上：用内置方法生成简单图标

const { createCanvas } = (() => {
  try { return require('canvas'); } catch { return null; }
})();

if (createCanvas) {
  [192, 512].forEach((size) => {
    const canvas = createCanvas(size, size);
    const ctx = canvas.getContext('2d');
    // 玉绿背景
    ctx.fillStyle = '#4a7c62';
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2 - 2, 0, Math.PI * 2);  // 使用 arc 作为背景，实际上应使用 fillRect
    ctx.fill();
    // 正式：使用 roundRect
    const r = size * 0.18;
    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#4a7c62';
    ctx.beginPath();
    ctx.moveTo(r, 0);
    ctx.lineTo(size - r, 0);
    ctx.quadraticCurveTo(size, 0, size, r);
    ctx.lineTo(size, size - r);
    ctx.quadraticCurveTo(size, size, size - r, size);
    ctx.lineTo(r, size);
    ctx.quadraticCurveTo(0, size, 0, size - r);
    ctx.lineTo(0, r);
    ctx.quadraticCurveTo(0, 0, r, 0);
    ctx.fill();
    // 白色 "印" 字
    ctx.fillStyle = '#ffffff';
    ctx.font = `bold ${size * 0.45}px "KaiTi", "楷体", serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('印', size / 2, size / 2);
    fs.writeFileSync(path.join(__dirname, `icon-${size}.png`), canvas.toBuffer('image/png'));
  });
  console.log('Icons generated: icon-192.png, icon-512.png');
} else {
  console.log('canvas module not available, generating SVG fallback...');
  // 生成 SVG 图标作为 fallback
  [192, 512].forEach((size) => {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">
  <rect width="${size}" height="${size}" rx="${size * 0.18}" fill="#4a7c62"/>
  <text x="${size/2}" y="${size/2}" font-size="${size * 0.45}" font-family="KaiTi,楷体,serif" fill="white" text-anchor="middle" dominant-baseline="central" font-weight="bold">印</text>
</svg>`;
    fs.writeFileSync(path.join(__dirname, `icon-${size}.svg`), svg);
  });
  console.log('SVG icons generated: icon-192.svg, icon-512.svg');
}
