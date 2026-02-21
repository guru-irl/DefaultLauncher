#!/usr/bin/env node
/**
 * Scales an SVG/VectorDrawable path around a pivot point.
 *
 * Usage:
 *   node scripts/scale-icon-path.js <scale> [pivotX] [pivotY]
 *
 * The path data is read from stdin. Pivot defaults to (54, 54) for a
 * 108x108dp adaptive-icon viewport.
 *
 * Example:
 *   echo "M33.69,79.8L33.69,48.55..." | node scripts/scale-icon-path.js 0.84
 */

const args = process.argv.slice(2);
if (args.length < 1) {
  console.error("Usage: scale-icon-path.js <scale> [pivotX] [pivotY]");
  process.exit(1);
}

const scaleFactor = parseFloat(args[0]);
const pivotX = parseFloat(args[1] || "54");
const pivotY = parseFloat(args[2] || "54");

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => (input += chunk));
process.stdin.on("end", () => {
  const path = input.trim();

  // Track coordinate index to alternate X/Y scaling pivot
  let coordIndex = 0;
  const scaled = path.replace(/-?\d+\.?\d*/g, (match) => {
    const val = parseFloat(match);
    const pivot = coordIndex % 2 === 0 ? pivotX : pivotY;
    coordIndex++;
    const newVal = Math.round((pivot + (val - pivot) * scaleFactor) * 100) / 100;
    let s = newVal.toFixed(2).replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
    return s;
  });

  console.log(scaled);

  // Print bounding-box analysis to stderr
  const nums = scaled.match(/-?\d+\.?\d*/g).map(Number);
  const xs = nums.filter((_, i) => i % 2 === 0);
  const ys = nums.filter((_, i) => i % 2 === 1);
  const viewport = pivotX * 2; // assumes square viewport centered on pivot
  console.error(`\nBounding box: X [${Math.min(...xs).toFixed(2)} .. ${Math.max(...xs).toFixed(2)}]  Y [${Math.min(...ys).toFixed(2)} .. ${Math.max(...ys).toFixed(2)}]`);
  console.error(`Center: (${((Math.min(...xs) + Math.max(...xs)) / 2).toFixed(2)}, ${((Math.min(...ys) + Math.max(...ys)) / 2).toFixed(2)})`);
  console.error(`Padding: top ${(Math.min(...ys) / viewport * 100).toFixed(1)}%  bottom ${((viewport - Math.max(...ys)) / viewport * 100).toFixed(1)}%  left ${(Math.min(...xs) / viewport * 100).toFixed(1)}%  right ${((viewport - Math.max(...xs)) / viewport * 100).toFixed(1)}%`);
});
