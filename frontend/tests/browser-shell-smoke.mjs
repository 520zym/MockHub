// Real HTTP + Chromium smoke test. Run ONLY against a fresh temporary-data instance.
// node frontend/tests/browser-shell-smoke.mjs --isolated --state=target/ui-smoke-state.json
import { spawn } from 'node:child_process'
import { readFile, writeFile, mkdir, access } from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { randomBytes } from 'node:crypto'

if (!process.argv.includes('--isolated')) throw new Error('Requires --isolated and a disposable database')
const statePath = process.argv.find(a => a.startsWith('--state='))?.slice(8)
if (!statePath) throw new Error('Pass --state=path/to/temporary-instance-state.json')
const state = JSON.parse(await readFile(statePath, 'utf8'))
const base = new URL(state.url)
if (!['127.0.0.1', 'localhost'].includes(base.hostname) || !state.dataDirectory.includes('/target/ui-smoke.')) throw new Error('Refusing non-isolated instance')
// Wait for HTTP readiness, not for the long-running server job to finish.
let ready = false
for (let attempt = 0; attempt < 15 && !ready; attempt++) {
  try { const response = await fetch(state.url + '/', { signal: AbortSignal.timeout(1500) }); ready = response.ok } catch {}
  if (!ready) await new Promise(resolve => setTimeout(resolve, 100 + attempt * 50))
}
if (!ready) throw new Error('Isolated application did not become HTTP-ready')
const output = path.resolve('target/browser-shell-smoke')
await mkdir(output, { recursive: true })
const chrome = process.env.CHROME || path.join(os.homedir(), 'Library/Caches/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-mac-arm64/chrome-headless-shell')
await access(chrome)
const browser = spawn(chrome, ['--headless', '--no-sandbox', '--disable-gpu', '--no-first-run', '--disable-background-networking', '--remote-debugging-pipe'], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] })
const pending = new Map(), exceptions = [], report = { url: state.url, checks: [], screenshots: [] }
let sequence = 0, buffer = Buffer.alloc(0), sessionId
browser.stdio[4].on('data', chunk => {
  buffer = Buffer.concat([buffer, chunk]); let end
  while ((end = buffer.indexOf(0)) >= 0) {
    const data = buffer.subarray(0, end).toString(); buffer = buffer.subarray(end + 1)
    if (!data) continue
    const message = JSON.parse(data)
    if (message.id) { const p = pending.get(message.id); pending.delete(message.id); message.error ? p?.reject(new Error(message.error.message)) : p?.resolve(message.result) }
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
  }
})
function cdp(method, params = {}, target = sessionId) {
  const id = ++sequence
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)) }, 25000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject: error => { clearTimeout(timer); reject(error) } })
    browser.stdio[3].write(JSON.stringify({ id, method, params, ...(target ? { sessionId: target } : {}) }) + '\0')
  })
}
async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function waitFor(expression, label) {
  await evaluate('new Promise((resolve,reject)=>{const end=Date.now()+18000;function tick(){try{if(' + expression + ')return resolve(true)}catch{} if(Date.now()>end)return reject(new Error(' + JSON.stringify('Timed out: ' + label) + '));requestAnimationFrame(tick)}tick()})')
}
async function visit(hash, ready) { await cdp('Page.navigate', { url: state.url + '/#' + hash }); await waitFor(ready, hash) }
function check(name, condition) { report.checks.push({ name, passed: !!condition }); if (!condition) throw new Error('Failed: ' + name) }
async function setInput(selector, value) { await evaluate('(()=>{const e=document.querySelector(' + JSON.stringify(selector) + ');if(!e)throw new Error("Input missing");Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set.call(e,' + JSON.stringify(value) + ');e.dispatchEvent(new Event("input",{bubbles:true}))})()') }
async function button(label) { await evaluate('(()=>{const e=[...document.querySelectorAll("button")].find(e=>e.textContent.trim()===' + JSON.stringify(label) + ');if(!e)throw new Error("Button missing");e.click()})()') }
async function screenshot(name, width, root) {
  await waitFor('!document.querySelector(".el-message")', 'transient messages dismissed')
  await cdp('Emulation.setDeviceMetricsOverride', { width, height: width < 600 ? 850 : 900, deviceScaleFactor: 1, mobile: false })
  await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
  if (width >= 900 && await evaluate('!!document.querySelector(".sidebar") && getComputedStyle(document.querySelector(".sidebar__brand .sidebar__copy")).display === "none"')) await evaluate('document.querySelector(".topbar__toggle").click()')
  await evaluate('document.activeElement?.blur();document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))')
  const layout = await evaluate('(()=>{const root=document.querySelector(' + JSON.stringify(root) + ');return{overflow:document.documentElement.scrollWidth>innerWidth,decorations:[root,...root.querySelectorAll("*")].filter(e=>{const r=e.getBoundingClientRect();if(!r.width||!r.height)return false;const s=getComputedStyle(e);return s.boxShadow!=="none"||s.backgroundImage.includes("gradient")||s.backdropFilter!=="none"}).map(e=>e.className?.toString()).slice(0,12)}})()')
  const image = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  const filename = name + '-' + width + '.png'
  await writeFile(path.join(output, filename), Buffer.from(image.data, 'base64'))
  report.screenshots.push({ name: filename, ...layout })
  check(filename + ': no document overflow', !layout.overflow)
  check(filename + ': flat feature surfaces', layout.decorations.length === 0)
}
async function api(route, method = 'GET', data, token) {
  const response = await fetch(state.url + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
  const body = await response.json()
  if (!response.ok || body.code !== 0) throw new Error(method + ' ' + route + ': ' + (body.msg || response.status))
  return body.data
}
try {
  const target = await cdp('Target.createTarget', { url: 'about:blank' }, null)
  sessionId = (await cdp('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)).sessionId
  await cdp('Page.enable'); await cdp('Runtime.enable')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  await visit('/login', 'document.querySelector(".login-card")')
  for (const width of [1440, 768, 375]) await screenshot('login', width, '.login-page')
  let saved = null
  try { saved = JSON.parse(await readFile(path.join(output, 'local-credentials.json'), 'utf8')); if (saved.dataDirectory !== state.dataDirectory) saved = null } catch {}
  await setInput('input[autocomplete="username"]', 'admin')
  await setInput('input[autocomplete="current-password"]', saved?.password || 'admin123')
  await button('登录')
  if (!saved) {
    await waitFor('document.querySelector(".password-note")', 'initial password required')
    saved = { username: 'admin', password: 'Smoke#' + randomBytes(10).toString('hex'), dataDirectory: state.dataDirectory }
    await writeFile(path.join(output, 'local-credentials.json'), JSON.stringify(saved))
    await setInput('.el-dialog input[autocomplete="new-password"]', saved.password)
    await evaluate('(()=>{const e=[...document.querySelectorAll(".el-dialog input[autocomplete=new-password]")][1];e.value=' + JSON.stringify(saved.password) + ';e.dispatchEvent(new Event("input",{bubbles:true}))})()')
    await button('保存并继续')
  }
  await waitFor('document.querySelector(".sidebar__account") && !document.querySelector(".shell-loading")', 'authenticated shell')
  const token = await evaluate('localStorage.getItem("token")')
  check('initial password change reauthenticates instead of retaining revoked JWT', !!token && !(await api('/auth/me', 'GET', undefined, token)).firstLogin)
  check('one bottom-left account entry and no topbar avatar', await evaluate('document.querySelectorAll(".sidebar__account").length===1&&!document.querySelector(".topbar__user,.topbar__avatar")'))
  await visit('/account?tab=profile', 'document.querySelector("input[name=displayName]")')
  const profileName = '联调管理员 ' + randomBytes(2).toString('hex')
  await setInput('input[name=displayName]', '  ' + profileName + '  '); await button('保存资料')
  await waitFor('document.querySelector(".sidebar__account-text b")?.textContent===' + JSON.stringify(profileName), 'saved profile in shell')
  check('profile real HTTP save trims display name', (await api('/auth/me', 'GET', undefined, token)).displayName === profileName)
  for (const width of [1440, 768, 375]) await screenshot('account', width, '.account-page')
  await visit('/account?tab=sessions', 'document.querySelector(".account-sessions") || document.body.innerText.includes("当前会话")')
  check('current real session is visible', (await api('/me/sessions', 'GET', undefined, token)).some(s => s.current))
  const other = await api('/auth/login', 'POST', { username: saved.username, password: saved.password })
  const sessions = await api('/me/sessions', 'GET', undefined, token)
  const otherSession = sessions.find(s => !s.current)
  check('second login creates a separate durable session', !!otherSession)
  await api('/me/sessions/' + otherSession.id, 'DELETE', undefined, token)
  const denied = await fetch(state.url + '/api/auth/me', { headers: { Authorization: 'Bearer ' + other.token } })
  check('revoked other session fails real HTTP with 401', denied.status === 401)
  check('revoking other session does not revoke current login', (await api('/auth/me', 'GET', undefined, token)).username === 'admin')
  await visit('/account?tab=teams', 'document.body.innerText.includes("团队身份") || document.body.innerText.includes("尚未加入")')
  check('no invented personal team', (await api('/auth/me', 'GET', undefined, token)).teams.length === 0)
  check('no browser JavaScript exceptions', exceptions.length === 0)
  report.exceptions = exceptions; report.passed = true
  console.log(JSON.stringify({ passed: true, checks: report.checks.length, screenshots: report.screenshots.length, output }, null, 2))
} catch (error) {
  report.passed = false; report.error = error.message; report.exceptions = exceptions
  try { const image = await cdp('Page.captureScreenshot', { format: 'png' }); await writeFile(path.join(output, 'failure.png'), Buffer.from(image.data, 'base64')); report.pageText = (await evaluate('document.body.innerText')).slice(-5000) } catch {}
  console.error(JSON.stringify({ passed: false, error: report.error, exceptions, output }, null, 2)); process.exitCode = 1
} finally {
  await writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2))
  try { await cdp('Browser.close', {}, null) } catch {} browser.kill()
}
