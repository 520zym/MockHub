import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { spawn } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'vite'
import { runListChecks } from './browser-api-list-checks.mjs'

// Real components/Element Plus; request transport is intentionally stubbed. No backend or HTTP server starts here.
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.join(frontend, 'tests/fixtures/api-lifecycle')
const view = process.argv.find(arg => arg.startsWith('--view='))?.slice(7) || 'history'
const out = '/tmp/mockhub-api-ui-fixture'
const output = process.argv.find(arg => arg.startsWith('--output='))?.slice(9) || '/tmp/mockhub-api-ui-artifacts'
if (path.resolve(out) !== '/tmp/mockhub-api-ui-fixture') throw new Error('Unexpected fixture build output')
console.log('Rebuild isolated fixture output:', out)
await fs.mkdir(output, { recursive: true })
const stub = path.join(root, 'request-stub.js'), realRequest = path.join(frontend, 'src/api/request'), settingsStub = path.join(root, 'settings-stub.js'), realSettings = path.join(frontend, 'src/api/settings')
await build({ configFile: false, root, base: './', plugins: [(await import('@vitejs/plugin-vue')).default(), { name: 'explicit-api-fixture', enforce: 'pre', resolveId(source, importer) { const candidate = source.startsWith('.') && importer ? path.resolve(path.dirname(importer.split('?')[0]), source) : source; if (source === '@/api/request' || candidate === realRequest || candidate === realRequest + '.js') return stub; if (source === '@/api/settings' || candidate === realSettings || candidate === realSettings + '.js') return settingsStub } }], resolve: { alias: [{ find: '@', replacement: path.join(frontend, 'src') }, { find: 'vue', replacement: path.join(frontend, 'node_modules/vue/dist/vue.esm-bundler.js') }] }, build: { outDir: out, emptyOutDir: true, chunkSizeWarningLimit: 1400 }, logLevel: 'warn' })
const chrome = process.env.CHROME || path.join(os.homedir(), 'Library/Caches/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-mac-arm64/chrome-headless-shell')
const browser = spawn(chrome, ['--no-sandbox', '--headless', '--disable-gpu', '--allow-file-access-from-files', '--remote-debugging-pipe', '--no-first-run', '--no-default-browser-check'], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] })
let nextId = 0, buffer = Buffer.alloc(0), stderr = '', session, expectedUrl = '', frameUrl = '', loadedResolve
const pending = new Map(), exceptions = [], resourceErrors = []
browser.stderr.on('data', chunk => { stderr += chunk })
browser.stdio[4].on('data', chunk => { buffer = Buffer.concat([buffer, chunk]); let zero; while ((zero = buffer.indexOf(0)) >= 0) { const raw = buffer.subarray(0, zero).toString(); buffer = buffer.subarray(zero + 1); if (!raw) continue; const message = JSON.parse(raw); if (message.id) { const promise = pending.get(message.id); if (promise) { pending.delete(message.id); clearTimeout(promise.timer); message.error ? promise.reject(new Error(JSON.stringify(message.error))) : promise.resolve(message.result) } } else if (message.method === 'Page.frameNavigated' && !message.params.frame.parentId) frameUrl = message.params.frame.url; else if (message.method === 'Page.loadEventFired' && frameUrl === expectedUrl) loadedResolve?.(); else if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails?.exception?.description || message.params.exceptionDetails?.text); else if (message.method === 'Network.loadingFailed' && !message.params.canceled) resourceErrors.push(message.params.errorText) } })
browser.on('exit', code => { for (const promise of pending.values()) { clearTimeout(promise.timer); promise.reject(new Error('Chrome exited ' + code + ' ' + stderr.slice(-500))) } pending.clear() })
function cdp(method, params = {}, target = session) { return new Promise((resolve, reject) => { const id = ++nextId, timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method + ' ' + stderr.slice(-400))) }, 30000); pending.set(id, { resolve, reject, timer }); browser.stdio[3].write(JSON.stringify({ id, method, params, ...(target ? { sessionId: target } : {}) }) + '\0') }) }
async function evaluate(expression) { const result = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text); return result.result?.value }
async function waitFor(expression) { return evaluate('new Promise((resolve,reject)=>{let observer;const check=()=>{if((' + expression + ')){observer?.disconnect();clearTimeout(timer);resolve(true)}};const timer=setTimeout(()=>{observer?.disconnect();reject(new Error("DOM condition timed out"))},12000);observer=new MutationObserver(check);observer.observe(document,{subtree:true,childList:true,attributes:true,characterData:true});check()})') }
async function click(text, prefix = false) { return evaluate('(()=>{const button=[...document.querySelectorAll("button")].find(e=>e.getBoundingClientRect().width>0&&!e.disabled&&' + (prefix ? 'e.textContent.trim().startsWith(' : 'e.textContent.trim()===(') + JSON.stringify(text) + '));if(!button)throw new Error("Missing enabled visible button: '+text+'");button.click();return true})()') }
const checks = [], screenshots = []
function check(name, passed) { checks.push({ name, passed: !!passed }); if (!passed) throw new Error(name) }
async function screenshot(name) { await evaluate('document.fonts.ready.then(()=>Promise.all(document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{})))).then(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))'); const image = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }); const file = path.join(output, name + '.png'); await fs.writeFile(file, Buffer.from(image.data, 'base64')); screenshots.push(file) }
try {
  const target = await cdp('Target.createTarget', { url: 'about:blank' }, null); session = (await cdp('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)).sessionId
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Network.enable')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 960, deviceScaleFactor: 1, mobile: false })
  const pageUrl = pathToFileURL(path.join(out, 'index.html')); if(view!=='history')pageUrl.searchParams.set('view',view); expectedUrl = pageUrl.href
  const loaded = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error('Fixture page did not load')), 15000); loadedResolve = () => { clearTimeout(timer); resolve() } })
  await cdp('Page.navigate', { url: expectedUrl }); await loaded
  if(view === 'history') {
  await waitFor('window.fixture && document.querySelectorAll(".comparison-group").length >= 4')
  check('fixture explicitly declares API stubbing', await evaluate('window.fixture.apiStub === true'))
  check('actual history groups include body, rules, SOAP and attachments', await evaluate('["响应正文","conditions","SOAP","附件元信息"].every(t=>document.body.innerText.includes(t))'))
  for (const width of [1440, 768, 375]) { await cdp('Emulation.setDeviceMetricsOverride', { width, height: 960, deviceScaleFactor: 1, mobile: false }); await screenshot('history-' + width); check('no document horizontal overflow at ' + width, await evaluate('document.documentElement.scrollWidth <= innerWidth + 1')); console.log('drawer metrics', await evaluate('JSON.stringify({innerWidth,client:document.documentElement.clientWidth,rect:document.querySelector(".api-history-drawer").getBoundingClientRect().toJSON(),width:getComputedStyle(document.querySelector(".api-history-drawer")).width,min:getComputedStyle(document.querySelector(".api-history-drawer")).minWidth,inline:document.querySelector(".api-history-drawer").getAttribute("style")})')); check('drawer fits viewport at ' + width, await evaluate('document.querySelector(".api-history-drawer").getBoundingClientRect().width <= innerWidth + 1')) }
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 960, deviceScaleFactor: 1, mobile: false })
  await evaluate('window.fixture.setRole("VIEWER")'); await waitFor('document.body.innerText.includes("当前为只读") && document.querySelectorAll(".comparison-group").length >= 4')
  check('viewer can compare but rollback remains disabled', await evaluate('[...document.querySelectorAll("button")].find(e=>e.textContent.trim().startsWith("回滚到"))?.disabled === true'))
  await evaluate('window.fixture.setRole("EDITOR")'); await waitFor('[...document.querySelectorAll("button")].some(e=>e.textContent.trim().startsWith("回滚到")&&!e.disabled)')
  await click('回滚到', true); await waitFor('document.querySelector(".rollback-confirm-copy")?.getBoundingClientRect().width > 0')
  const postsBefore = await evaluate('window.fixture.calls.filter(c=>c.method==="POST").length')
  await evaluate('window.fixture.setRole("VIEWER")'); await waitFor('![...document.querySelectorAll(".rollback-confirm-copy")].some(e=>e.getBoundingClientRect().width>0)')
  check('write revocation closes owned confirmation without submitting', await evaluate('window.fixture.calls.filter(c=>c.method==="POST").length') === postsBefore)
  await evaluate('window.fixture.setRole("EDITOR")'); await waitFor('[...document.querySelectorAll("button")].some(e=>e.textContent.trim().startsWith("回滚到")&&!e.disabled)')
  await click('回滚到', true); await evaluate('window.fixture.setTeam("team-b")'); await waitFor('document.body.innerText.includes("PAY-2") && document.body.innerText.includes("支付中心")')
  check('team switch cancels the frozen old-team confirmation', await evaluate('window.fixture.calls.filter(c=>c.method==="POST").length') === postsBefore)
  await evaluate('window.fixture.holdHistory(true);window.fixture.setTeam("team-a");window.fixture.setTeam("team-b");window.fixture.release()'); await waitFor('document.body.innerText.includes("PAY-2")')
  check('late history responses cannot repaint the previous team', await evaluate('!document.body.innerText.includes("用户详情与状态")'))
  await evaluate('window.fixture.setTeam("team-a")'); await waitFor('document.body.innerText.includes("用户详情与状态")')
  await click('回滚到', true); await evaluate('window.fixture.conflict(true)'); const getsBefore = await evaluate('window.fixture.calls.filter(c=>c.method==="GET").length'); await click('确认回滚')
  await waitFor('document.body.innerText.includes("当前编辑未被替换")')
  check('conflict never fetches a fresh version and retries silently', await evaluate('window.fixture.calls.filter(c=>c.method==="GET").length') === getsBefore)
  check('frozen revision submitted was the originally loaded one', await evaluate('window.fixture.calls.filter(c=>c.method==="POST").at(-1)?.body.expectedRevision === 3'))
  check('conflict emitted no change', await evaluate('document.querySelector("#fixture-changed").textContent.trim()==="0"'))
  await evaluate('window.fixture.close();window.fixture.conflict(false)'); await waitFor('!document.querySelector(".comparison-heading")'); await evaluate('window.fixture.open()'); await waitFor('document.querySelectorAll(".comparison-group").length >= 4')
  await click('回滚到', true); await click('确认回滚'); await waitFor('document.querySelector("#fixture-changed").textContent.trim()==="1"')
  check('successful rollback emits new revision and closes the drawer', await evaluate('window.fixture.state.revisions["api-a"]===4'))
  } else { await runListChecks({ cdp, evaluate, waitFor, click, check, screenshot }) }
  check('no browser JavaScript exceptions', exceptions.length === 0); check('no failed fixture resources', resourceErrors.length === 0)
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify({ apiStub: true, backendIntegration: false, passed: true, checks, screenshots, exceptions, resourceErrors }, null, 2))
  console.log(JSON.stringify({ passed: true, apiStub: true, checks: checks.length, screenshots, output }, null, 2))
} catch (error) {
  try { await screenshot('failure') } catch {}
  let text = ''; try { text = await evaluate('document.body.innerText') } catch {}
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify({ apiStub: true, backendIntegration: false, passed: false, error: error.message, checks, screenshots, exceptions, resourceErrors, text }, null, 2)); throw error
} finally { try { await cdp('Browser.close', {}, null) } catch {} browser.kill() }
