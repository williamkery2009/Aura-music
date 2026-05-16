/**
 * Aura v25 build script
 * Injects native-bridge.js into index.html just before </body>
 * Run: node scripts/build.js
 */
const fs   = require('fs');
const path = require('path');

const SRC_HTML   = path.join(__dirname, '../src/index.html');
const BRIDGE_JS  = path.join(__dirname, '../src/native-bridge.js');
const OUT_DIR    = path.join(__dirname, '../android/app/src/main/assets/public');
const OUT_HTML   = path.join(OUT_DIR, 'index.html');

console.log('🎵 Aura v25 build starting...');

// Read files
let html   = fs.readFileSync(SRC_HTML,  'utf8');
let bridge = fs.readFileSync(BRIDGE_JS, 'utf8');

// Inject bridge as inline script before </body>
const injection = `\n<script>\n/* === AURA NATIVE BRIDGE === */\n${bridge}\n</script>`;
if (html.includes('<!-- NATIVE_BRIDGE_INJECTED -->')) {
  console.log('ℹ  Bridge already injected — skipping duplicate');
} else {
  html = html.replace('</body>', `<!-- NATIVE_BRIDGE_INJECTED -->${injection}\n</body>`);
}

// Ensure output dir exists
fs.mkdirSync(OUT_DIR, { recursive: true });

// Write
fs.writeFileSync(OUT_HTML, html, 'utf8');

console.log(`✓ index.html → ${OUT_HTML}`);
console.log(`  Size: ${(fs.statSync(OUT_HTML).size / 1024).toFixed(1)} KB`);
console.log('✓ Build complete. Run: npm run sync && npm run apk:debug');
