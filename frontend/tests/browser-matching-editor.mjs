import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { spawn } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'vite'

// Real components/Element Plus; request transport is intentionally stubbed. No backend or HTTP server starts here.
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.join(frontend, 'tests/fixtures/matching-editor')
const out = '/tmp/mockhub-match-editor-fixture'
const output = process.argv.find(arg => arg.startsWith('--output='))?.slice(9) || '/tmp/mockhub-match-editor-artifacts'
if (path.resolve(out) !== '/tmp/mockhub-match-editor-fixture') throw new Error('Unexpected fixture build output')
console.log('Rebuild isolated fixture output:', out)
await fs.mkdir(output, { recursive: true })
const stub = path.join(root, 'request-stub.js'), realRequest = path.join(frontend, 'src/api/request')
await build({ configFile: false, root, base: './', plugins: [(await import('@vitejs/plugin-vue')).default(), { name: 'explicit-api-fixture', enforce: 'pre', resolveId(source, importer) { const candidate = source.startsWith('.') && importer ? path.resolve(path.dirname(importer.split('?')[0]), source) : source; if (source === '@/api/request' || candidate === realRequest || candidate === realRequest + '.js') return stub } }], resolve: { alias: [{ find: '@', replacement: path.join(frontend, 'src') }, { find: 'vue', replacement: path.join(frontend, 'node_modules/vue/dist/vue.esm-bundler.js') }] }, build: { outDir: out, emptyOutDir: true, chunkSizeWarningLimit: 1400 }, logLevel: 'warn' })
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
async function inputSample(text) { await evaluate('(()=>{const input=document.querySelector(".sample-card textarea");Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,"value").set.call(input,' + JSON.stringify(text) + ');input.dispatchEvent(new Event("input",{bubbles:true}));return true})()') }
async function pickField(path) { const selector = 'button[aria-label="添加条件 ' + path + '"]'; await evaluate('document.querySelector(' + JSON.stringify(selector) + ').click()') }
async function source(name) { await evaluate('[...document.querySelectorAll(".source-tabs button")].find(e=>e.textContent.trim()===' + JSON.stringify(name) + ').click()') }
async function trial(reason, title, random = false) { await evaluate('window.fixture.setReason(' + JSON.stringify(reason) + ')'); await evaluate('document.querySelector(".run-preview").click()'); await waitFor('document.querySelector(".preview-result")?.textContent.includes(' + JSON.stringify(title) + ')'); check(reason + ' displays truthful server reason', await evaluate('document.querySelector(".preview-result").textContent.includes(' + JSON.stringify(title) + ')')) }
try {
  const target = await cdp('Target.createTarget', { url: 'about:blank' }, null); session = (await cdp('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)).sessionId
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Network.enable')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  expectedUrl = pathToFileURL(path.join(out, 'index.html')).href
  const loaded = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error('Fixture page did not load')), 15000); loadedResolve = () => { clearTimeout(timer); resolve() } })
  await cdp('Page.navigate', { url: expectedUrl }); await loaded
  await waitFor('window.fixture && document.querySelectorAll(".condition-row").length === 2')
  check('real component fixture explicitly declares API stub', await evaluate('window.fixture.apiStub === true && document.body.innerText.includes("非后端联调")'))
  check('sample dialog starts closed; generation and trial have distinct entries', await evaluate('!document.querySelector(".sample-card") && !!document.querySelector(".from-request") && document.querySelector(".rules-tools").textContent.includes("试匹配")'))
  await click('从请求生成条件'); await waitFor('document.querySelector(".sample-card textarea")');
  await source('Query'); await inputSample('?channel=alipay&orderId=202610280001&orderId=second'); await click('提取字段 →'); await waitFor('document.querySelectorAll(".tree-body .leaf").length === 2')
  check('duplicate Query field marked added rather than fake satisfied', await evaluate('document.querySelector(".leaf.used")?.textContent.includes("channel") && !document.body.innerText.includes("示例实际值")'))
  await source('Header'); await inputSample('X-Mock-Sandbox: 0\nx-mock-sandbox: 1\nReferer: https://test:8080'); await click('提取字段 →')
  await source('Body'); await inputSample('{"items":[{"amount":100}]}'); await click('提取字段 →')
  await pickField('items[0].amount'); await waitFor('document.querySelectorAll(".condition-row").length === 3')
  check('array field generates an editable NUMBER comparison type', await evaluate('JSON.parse(window.fixture.state.responses[0].conditions).conditions.some(c=>c.path==="items[0].amount"&&c.valueType==="NUMBER") && document.querySelectorAll(".value-type").length === 3'))
  await source('Query'); await trial('CONDITION_MATCH', '条件命中')
  check('all three sources coexist in real preview transport payload', await evaluate('(()=>{const body=window.fixture.calls.filter(c=>c.url==="/apis/preview-match").at(-1).body;return body.sample.body.includes("items")&&body.sample.query.orderId==="202610280001"&&body.sample.headers["X-Mock-Sandbox"]==="0"&&body.sample.headers.Referer==="https://test:8080"&&body.responses[0].active===true})()'))
  await click('完成，返回编辑'); await waitFor('!document.querySelector(".match-sample-dialog") || document.querySelector(".match-sample-dialog").getBoundingClientRect().width===0')
  for (const width of [1440,768,375]) {
    await cdp('Emulation.setDeviceMetricsOverride', { width, height: 1050, deviceScaleFactor: 1, mobile: false }); await screenshot('conditions-' + width)
    check('no document horizontal overflow at ' + width, await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'))
    check('condition inputs reflow without internal horizontal scroll at ' + width, await evaluate('document.querySelector(".table-scroll").scrollWidth <= document.querySelector(".table-scroll").clientWidth + 1 && document.querySelector(".table-scroll").getBoundingClientRect().right <= innerWidth'))
    check('no shadow gradient or blur on editor cards at ' + width, await evaluate('[...document.querySelectorAll(".rules-card,.sample-card,.el-input__wrapper,.el-select__wrapper,.el-textarea__inner")].every(e=>{const s=getComputedStyle(e);return s.boxShadow==="none"&&s.backgroundImage==="none"&&s.backdropFilter==="none"})'))
  }
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await click('从请求生成条件'); await waitFor('document.querySelector(".match-sample-dialog")?.getBoundingClientRect().width > 0')
  await evaluate('window.fixture.select(2)'); await waitFor('document.querySelector(".fallback-notice")');
  check('response selection preserves the local Query sample', await evaluate('document.querySelector(".sample-card textarea").value.includes("channel=alipay")'))
  await trial('FALLBACK', '使用兜底'); await screenshot('fallback-1440')
  await trial('SINGLE_ENABLED', '唯一启用'); check('single enabled explains runtime short circuit', await evaluate('document.querySelector(".preview-result").textContent.includes("不执行条件判断")'))
  await trial('NO_MATCH', '未命中'); check('no match never fabricates fallback', await evaluate('document.querySelector(".preview-result.neutral")?.textContent.includes("没有可用兜底") && !document.querySelector(".preview-result .result-meta")'))
  await trial('NO_ENABLED_RESPONSE', '无启用')
  await evaluate('window.fixture.setMode("RANDOM")'); await waitFor('document.querySelector(".mode-notice")?.textContent.includes("等概率随机")'); await trial('RANDOM_EQUAL_PROBABILITY', '等概率随机候选', true)
  check('random shows candidates and no invented selected response', await evaluate('document.querySelectorAll(".random-candidates li").length===3 && document.querySelector(".preview-result").textContent.includes("不固定命中")'))
  await screenshot('random-1440')
  await evaluate('window.fixture.setMode("CONDITION");window.fixture.setReadonly(true);window.fixture.setSaved(true)'); await waitFor('document.body.innerText.includes("规则只读")')
  await trial('CONDITION_MATCH', '保存的返回体'); check('viewer saved-only notice is explicit', await evaluate('document.querySelector(".preview-result").textContent.includes("按已保存配置试配，未提交草稿不参与")'))
  check('readonly hides AI and all condition mutation buttons', await evaluate('!document.querySelector(".add-row,.delete-row") && ![...document.querySelectorAll("button")].some(e=>e.textContent.includes("AI 生成规则"))'))
  check('readonly sends no draft responses', await evaluate('window.fixture.calls.filter(c=>c.url==="/apis/preview-match").at(-1).body.responses.length===0'))
  await screenshot('readonly-1440')
  await evaluate('window.fixture.setReadonly(false);window.fixture.setSaved(false);window.fixture.hold(true)'); await evaluate('document.querySelector(".run-preview").click()'); await evaluate('window.fixture.setTeam("team-b");window.fixture.release()'); await waitFor('document.querySelector(".sample-card textarea").value===""')
  check('team switch fences late result and clears old local samples', await evaluate('!document.querySelector(".preview-result")'))
  await evaluate('window.fixture.setKind("xml")'); await click('从请求生成条件'); await waitFor('document.querySelector(".match-sample-dialog")?.getBoundingClientRect().width > 0'); await source('Body'); await inputSample('<s:Envelope xmlns:s="urn:s"><s:Body><u:GetUser xmlns:u="urn:user"><u:id>42</u:id></u:GetUser></s:Body></s:Envelope>'); await click('提取字段 →')
  await waitFor('[...document.querySelectorAll(".tree-body .leaf")].some(e=>e.getAttribute("aria-label")==="添加条件 Envelope.Body.GetUser.id")'); await pickField('Envelope.Body.GetUser.id'); await waitFor('document.querySelectorAll(".condition-row").length === 1')
  check('namespace XML paths use safe existing parser and inferred numeric type', await evaluate('JSON.parse(window.fixture.state.responses[2].conditions).conditions[0].path==="Envelope.Body.GetUser.id" && document.querySelector(".value-type").textContent.includes("数字")'))
  await inputSample('<!DOCTYPE root [<!ENTITY x SYSTEM "file:///never-open">]><root>&x;</root>'); await click('提取字段 →'); await waitFor('document.body.innerText.includes("不支持 DOCTYPE")')
  check('DTD sample rejected locally without field import', await evaluate('document.querySelectorAll(".tree-body .leaf").length===0'))
  await click('完成，返回编辑');
  await click('图解用法'); await waitFor('document.querySelector(".match-guide-dialog")?.getBoundingClientRect().width > 0');
  check('guide illustrates requests, conditions and response rather than long paragraphs', await evaluate('!!document.querySelector(".condition-flow svg") && document.querySelector(".condition-tokens").textContent.includes("status") && document.querySelector(".result-card").textContent.includes("ok")'));
  for (const width of [1440,375]) { await cdp('Emulation.setDeviceMetricsOverride',{width,height:850,deviceScaleFactor:1,mobile:false}); await screenshot('visual-guide-' + width); check('visual guide fits ' + width, await evaluate('document.documentElement.scrollWidth<=innerWidth && document.querySelector(".match-guide-dialog").getBoundingClientRect().right<=innerWidth')) }
  await click('我明白了'); await cdp('Emulation.setDeviceMetricsOverride',{width:1440,height:1050,deviceScaleFactor:1,mobile:false});
  await click('编辑返回体 →'); await waitFor('document.querySelector("#edit-count").textContent==="1"')
  await click('AI 生成'); await waitFor('document.querySelector(".ai-rules-drawer")?.getBoundingClientRect().width > 0')
  for (const width of [1440,375]) { await cdp('Emulation.setDeviceMetricsOverride',{width,height:1050,deviceScaleFactor:1,mobile:false}); await screenshot('ai-drawer-' + width); check('AI drawer fits flat viewport at ' + width,await evaluate('(()=>{const e=document.querySelector(".ai-rules-drawer");return e.getBoundingClientRect().width<=innerWidth+1&&getComputedStyle(e).boxShadow==="none"&&document.documentElement.scrollWidth<=innerWidth+1})()')) }
  await evaluate('window.fixture.setReadonly(true)'); await waitFor('!document.querySelector(".ai-rules-drawer")')
  check('revoking write unmounts the owned AI drawer', await evaluate('!document.querySelector(".ai-rules-drawer")'))
  check('all preview requests use the read-only endpoint, never dispatch/save/history', await evaluate('window.fixture.calls.filter(c=>c.method==="POST").every(c=>c.url==="/apis/preview-match")'))
  check('no browser JavaScript exceptions', exceptions.length===0); check('no failed fixture resources', resourceErrors.length===0)
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify({ apiStub:true,backendIntegration:false,passed:true,checks,screenshots,exceptions,resourceErrors },null,2)); console.log(JSON.stringify({passed:true,apiStub:true,checks:checks.length,screenshots,output},null,2))
} catch(error) {
  try { await screenshot('failure') } catch {}
  let text=''; try { text=await evaluate('document.body.innerText') } catch {}
  await fs.writeFile(path.join(output,'report.json'),JSON.stringify({apiStub:true,backendIntegration:false,passed:false,error:error.message,checks,screenshots,exceptions,resourceErrors,text},null,2)); throw error
} finally { try { await cdp('Browser.close',{},null) } catch {} browser.kill() }