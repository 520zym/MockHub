import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawn} from 'node:child_process';
import {pathToFileURL, fileURLToPath} from 'node:url';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const browser = process.env.CHROME || path.join(os.homedir(), 'Library/Caches/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-mac-arm64/chrome-headless-shell');
if (!fs.existsSync(browser)) throw new Error('Set CHROME to a Chrome/Chromium executable.');

const manifest = JSON.parse(fs.readFileSync(path.join(root, 'pages.json'), 'utf8'));
const auditJs = fs.readFileSync(path.join(root, 'build', 'audit-in-page.js'), 'utf8');
const args = process.argv.slice(2);
const checkOnly = args.includes('--check');
const wanted = args.filter((a) => !a.startsWith('--'));
let pages = wanted.length ? manifest.filter((p) => wanted.includes(p.id)) : manifest;
if (!pages.length) throw new Error('No page matched: ' + wanted.join(', '));

const cp = spawn(browser, ['--no-sandbox', '--headless', '--disable-gpu', '--hide-scrollbars', '--remote-debugging-pipe', '--no-first-run', '--no-default-browser-check'], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] });
let exceptions = [], resourceErrors = [], stderr = '';
cp.stderr.on('data', (c) => { stderr += c; });
let pending = new Map(), id = 0, buf = Buffer.alloc(0), loadWaits = new Map();
cp.stdio[4].on('data', (chunk) => {
  buf = Buffer.concat([buf, chunk]);
  let zero;
  while ((zero = buf.indexOf(0)) >= 0) {
    const raw = buf.subarray(0, zero).toString(); buf = buf.subarray(zero + 1);
    if (!raw) continue;
    const msg = JSON.parse(raw);
    if (msg.id) { const p = pending.get(msg.id); if (p) { clearTimeout(p.t); pending.delete(msg.id); msg.error ? p.reject(new Error(JSON.stringify(msg.error))) : p.resolve(msg.result); } }
    else if (msg.method === 'Page.loadEventFired') loadWaits.get(msg.sessionId)?.();
    else if (msg.method === 'Runtime.exceptionThrown') exceptions.push(msg.params.exceptionDetails?.exception?.description || msg.params.exceptionDetails?.text);
    else if (msg.method === 'Network.loadingFailed' && !msg.params.canceled) resourceErrors.push(msg.params.errorText);
  }
});
cp.on('exit', (code) => { if (pending.size) for (const p of pending.values()) p.reject(new Error('Browser exited ' + code + ' ' + stderr.slice(-800))); });
const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
  const n = ++id;
  const t = setTimeout(() => { pending.delete(n); reject(new Error('Timed out: ' + method + ' ' + stderr.slice(-300))); }, 25000);
  pending.set(n, { resolve, reject, t });
  cp.stdio[3].write(JSON.stringify({ id: n, method, params, ...(sessionId ? { sessionId } : {}) }) + '\0');
});

const results = [];
try {
  const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
  await send('Page.enable', {}, sessionId);
  await send('Runtime.enable', {}, sessionId);
  await send('Network.enable', {}, sessionId);
  for (const p of pages) {
    exceptions = []; resourceErrors = [];
    const file = path.join(root, p.file);
    if (!fs.existsSync(file)) { console.log(p.id + ' MISSING ' + p.file); results.push({ id: p.id, missing: true }); continue; }
    await send('Emulation.setDeviceMetricsOverride', { width: p.width || 1440, height: p.height || 900, deviceScaleFactor: p.scale || 2, mobile: false }, sessionId);
    const loaded = new Promise((resolve) => { const t = setTimeout(resolve, 10000); loadWaits.set(sessionId, () => { clearTimeout(t); resolve(); }); });
    await send('Page.navigate', { url: pathToFileURL(file).href }, sessionId);
    await loaded;
    await send('Runtime.evaluate', { expression: 'document.fonts.ready', awaitPromise: true }, sessionId);
    await new Promise((r) => setTimeout(r, 400)); // let entrance motion settle before measuring

    const info = await send('Runtime.evaluate', { expression: auditJs, returnByValue: true }, sessionId);
    const a = { ...info.result.value, exceptions: [...exceptions], resourceErrors: [...resourceErrors] };
    if (!checkOnly) {
      const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }, sessionId);
      fs.mkdirSync(path.join(root, 'screens'), { recursive: true });
      fs.writeFileSync(path.join(root, 'screens', p.id + '.png'), Buffer.from(shot.data, 'base64'));
    }
    results.push({ id: p.id, ...a });
    const issues = [];
    if (a.docOverflow) issues.push('overflow-x+' + a.overflowPx + 'px');
    if (a.docVertical) issues.push('overflow-y+' + a.verticalPx + 'px ' + JSON.stringify(a.tall));
    if (a.clipped.length) issues.push('clipped:' + a.clipped.map((c) => c.cls).join('|'));
    if (a.lowContrast.length) issues.push('contrast:' + a.lowContrast.map((c) => c.cls + '(' + c.ratio + ')').join('|'));
    if (a.smallText.length) issues.push('tiny:' + a.smallText.map((c) => c.cls + '(' + c.size + ')').join('|'));
    if (a.unnamed.length) issues.push('unnamed:' + a.unnamed.join('|'));
    if (a.exceptions.length) issues.push('js:' + a.exceptions.length);
    if (a.resourceErrors.length) issues.push('net:' + a.resourceErrors.join('|'));
    if (a.external.length) issues.push('external:' + a.external.join('|'));
    console.log(p.id.padEnd(14) + (issues.length ? 'CHECK ' + issues.join('  ') : 'OK'));
  }
  fs.mkdirSync(path.join(root, 'build'), { recursive: true });
  fs.writeFileSync(path.join(root, 'build', 'audit.json'), JSON.stringify(results, null, 2) + '\n');
  const bad = results.filter((r) => !r.missing && (r.docOverflow || r.docVertical || r.clipped.length || r.lowContrast.length || r.smallText.length || r.unnamed.length || r.exceptions.length || r.resourceErrors.length || r.external.length));
  if (bad.length) process.exitCode = 1;
} finally {
  try { await send('Browser.close'); } catch {}
  cp.kill();
}
