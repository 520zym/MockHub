import { spawn } from 'node:child_process'
import { access, writeFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'

export async function browserDriver() {
  const executable = process.env.CHROME || path.join(os.homedir(), 'Library/Caches/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-mac-arm64/chrome-headless-shell')
  await access(executable)
  const processHandle = spawn(executable, ['--headless', '--no-sandbox', '--disable-gpu', '--no-first-run', '--disable-background-networking', '--remote-debugging-pipe'], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] })
  const pending = new Map(), exceptions = [], consoleErrors = []
  let id = 0, buffer = Buffer.alloc(0), session
  processHandle.stdio[4].on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]); let end
    while ((end = buffer.indexOf(0)) >= 0) {
      const raw = buffer.subarray(0, end).toString(); buffer = buffer.subarray(end + 1); if (!raw) continue
      const message = JSON.parse(raw)
      if (message.id) { const task = pending.get(message.id); pending.delete(message.id); if (task) { clearTimeout(task.timer); message.error ? task.reject(new Error(message.error.message)) : task.resolve(message.result) } }
      if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
      if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') consoleErrors.push(message.params.args.map(a => typeof a.value === 'string' ? a.value : a.description || a.type).join(' ').slice(0, 1000))
    }
  })
  function cdp(method, params = {}, target = session) {
    const requestId = ++id
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(requestId); reject(new Error('CDP timeout: ' + method)) }, 25000)
      pending.set(requestId, { resolve, reject, timer })
      processHandle.stdio[3].write(JSON.stringify({ id: requestId, method, params, ...(target ? { sessionId: target } : {}) }) + '\0')
    })
  }
  const target = await cdp('Target.createTarget', { url: 'about:blank' }, null)
  session = (await cdp('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)).sessionId
  await cdp('Page.enable'); await cdp('Runtime.enable')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  async function evaluate(expression) { const result = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text); return result.result.value }
  async function waitFor(expression, name = expression) { return evaluate('new Promise((resolve,reject)=>{const end=Date.now()+20000;function tick(){try{if(' + expression + ')return resolve(true)}catch{}if(Date.now()>end)return reject(new Error(' + JSON.stringify('Timed out: ' + name) + '));requestAnimationFrame(tick)}tick()})') }
  async function input(selector, value) { return evaluate('(()=>{const e=document.querySelector(' + JSON.stringify(selector) + ');if(!e)throw new Error("Input not found");Object.getOwnPropertyDescriptor(e.tagName==="TEXTAREA"?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,"value").set.call(e,' + JSON.stringify(value) + ');e.dispatchEvent(new Event("input",{bubbles:true}))})()') }
  async function click(text) { return evaluate('(()=>{const e=[...document.querySelectorAll("button")].find(e=>e.textContent.trim()===' + JSON.stringify(text) + ');if(!e)throw new Error("Button not found");e.click()})()') }
  async function screenshot(file, width, height = 900) { await mkdir(path.dirname(file), { recursive: true }); await cdp('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false }); await evaluate('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))'); const data = await cdp('Page.captureScreenshot', { format: 'png' }); await writeFile(file, Buffer.from(data.data, 'base64')) }
  async function close() { try { await cdp('Browser.close', {}, null) } catch {} processHandle.kill(); for (const task of pending.values()) { clearTimeout(task.timer); task.reject(new Error('Browser closed')) } pending.clear() }
  return { cdp, evaluate, waitFor, input, click, screenshot, close, exceptions, consoleErrors }
}
