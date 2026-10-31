// Real app + real backend: request generation, numeric editing, preview, save, RANDOM and guide.
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { randomBytes } from 'node:crypto'
import path from 'node:path'
import { browserDriver } from './helpers/browser-driver.mjs'
if (!process.argv.includes('--isolated')) throw new Error('Requires --isolated disposable database')
const statePath = process.argv.find(a => a.startsWith('--state='))?.slice(8)
if (!statePath) throw new Error('Pass --state=...')
const state = JSON.parse(await readFile(statePath, 'utf8')), url = new URL(state.url)
if (!['127.0.0.1', 'localhost'].includes(url.hostname) || !state.dataDirectory.includes('/target/ui-smoke.')) throw new Error('Refusing non-isolated database')
const credentials = JSON.parse(await readFile('target/browser-shell-smoke/local-credentials.json', 'utf8'))
if (credentials.dataDirectory !== state.dataDirectory) throw new Error('Credentials belong to another isolated database')
const output = path.resolve('target/match-redesign'); await mkdir(output, { recursive: true })
const browser = await browserDriver(), checks = [], screenshots = []
const check = (name, result) => { checks.push({ name, passed: !!result }); if (!result) throw new Error(name) }
let token
async function api(route, method = 'GET', data) {
  const response = await fetch(state.url + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
  const result = await response.json(); if (!response.ok || result.code !== 0) throw new Error(route + ': ' + result.msg); return result.data
}
async function settle() { await browser.evaluate('Promise.all(document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{}))).then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))') }
async function pointer(selector) {
  await browser.evaluate('document.querySelector(' + JSON.stringify(selector) + ').scrollIntoView({block:"center",behavior:"instant"})'); await settle()
  const point = await browser.evaluate('(()=>{const e=document.querySelector(' + JSON.stringify(selector) + ');if(!e)throw new Error("Missing control");const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()')
  await browser.cdp('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
  await browser.cdp('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point }); await settle()
}
async function option(text) {
  await browser.waitFor('[...document.querySelectorAll(".el-select-dropdown__item")].some(e=>e.getBoundingClientRect().width&&e.textContent.trim()===' + JSON.stringify(text) + ')')
  const p = await browser.evaluate('(()=>{const e=[...document.querySelectorAll(".el-select-dropdown__item")].find(e=>e.getBoundingClientRect().width&&e.textContent.trim()===' + JSON.stringify(text) + ');const r=e.getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2}})()')
  for (const type of ['mousePressed', 'mouseReleased']) await browser.cdp('Input.dispatchMouseEvent', { type, button: 'left', clickCount: 1, ...p })
  await settle()
}
async function snap(name, width) {
  const file = path.join(output, name + '-' + width + '.png'); await browser.screenshot(file, width, width < 600 ? 812 : 950); await settle(); await browser.screenshot(file, width, width < 600 ? 812 : 950); screenshots.push(file)
  check(name + ': no document overflow at ' + width, await browser.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
}
async function trial(query, body, expected) {
  await browser.evaluate('[...document.querySelectorAll(".source-tabs button")].find(e=>e.textContent.trim()==="Query").click()')
  await browser.input('.sample-card textarea', query)
  await browser.evaluate('[...document.querySelectorAll(".source-tabs button")].find(e=>e.textContent.trim()==="Body").click()')
  await browser.input('.sample-card textarea', body)
  await pointer('.run-preview'); await browser.waitFor('document.querySelector(".preview-result")?.textContent.includes(' + JSON.stringify(expected) + ')')
  check('real server chooses ' + expected, await browser.evaluate('document.querySelector(".preview-result").textContent.includes(' + JSON.stringify(expected) + ')'))
}
try {
  const login = await api('/auth/login', 'POST', { username: credentials.username, password: credentials.password }); token = login.token
  const team = (await api('/teams'))[0], suffix = randomBytes(4).toString('hex')
  const rule = (source, path, operator, value, valueType = 'STRING') => JSON.stringify({ conditions: [{ source, path, operator, value, valueType }] })
  const response = (name, conditions, sortOrder) => ({ name, conditions, sortOrder, active: true, responseCode: 200, bodyType: 'TEXT', contentType: 'application/json', responseBody: JSON.stringify({ result: name }) })
  const saved = await api('/apis', 'POST', { teamId: team.id, name: '条件编辑体验 ' + suffix, method: 'POST', path: '/rules-design-' + suffix, type: 'REST', responseMode: 'CONDITION', enabled: true, responses: [response('支付成功', rule('QUERY', 'status', 'EQ', 'paid'), 0), response('大额订单', rule('BODY', 'amount', 'GT', '100', 'NUMBER'), 1), response('默认返回', '', 2)] })
  await browser.cdp('Page.navigate', { url: state.url + '/#/login' }); await browser.waitFor('!!document.querySelector(".login-card")')
  await browser.evaluate('localStorage.setItem("token",' + JSON.stringify(token) + ');localStorage.setItem("user",' + JSON.stringify(JSON.stringify(login.user)) + ');location.hash=' + JSON.stringify('#/apis/' + saved.id + '/edit'))
  await browser.cdp('Page.reload', { ignoreCache: true }); await browser.waitFor('document.querySelectorAll(".condition-row").length===1 && document.querySelector(".basic-card input")?.value.includes("条件编辑体验")'); await settle()
  check('default has no sample editor, duplicated heading or long instructions', await browser.evaluate('!document.querySelector(".sample-card,.eyebrow,.local-note,.match-help") && !!document.querySelector(".from-request")'))
  const initialHeight = await browser.evaluate('Math.round(document.querySelector(".conditions-workspace").getBoundingClientRect().height)')
  check('one-condition editor stays compact', initialHeight < 240)
  await pointer('.add-row'); check('adding a condition focuses its field', await browser.evaluate('document.activeElement.getAttribute("aria-label")==="条件字段路径"'))
  await browser.input('.condition-row:last-child .path-control input', 'amount'); await browser.input('.condition-row:last-child .value-group input', '100')
  await pointer('.condition-row:last-child .value-type .el-select__wrapper'); await option('数字')
  await pointer('.condition-row:last-child .operator-control .el-select__wrapper')
  check('manual numeric choice exposes greater-than without an imported sample', await browser.evaluate('[...document.querySelectorAll(".el-select-dropdown__item")].some(e=>e.getBoundingClientRect().width&&e.textContent.trim()==="大于")'))
  await option('等于')
  await pointer('.from-request'); await browser.waitFor('document.querySelector(".sample-card textarea")')
  await browser.evaluate('[...document.querySelectorAll(".source-tabs button")].find(e=>e.textContent.trim()==="Query").click()')
  await browser.input('.sample-card textarea', '?status=paid&gateway=web'); await pointer('.extract-fields'); await browser.waitFor('!!document.querySelector(' + JSON.stringify('button[aria-label="添加条件 gateway"]') + ')')
  await pointer('button[aria-label="添加条件 gateway"]'); await browser.waitFor('document.querySelectorAll(".condition-row").length===3')
  check('field pick confirms addition and preserves selected source', await browser.evaluate('document.querySelector(".sample-added").textContent.includes("gateway") && document.querySelectorAll(".condition-row")[2].textContent.includes("Query")'))
  await snap('from-request', 1440)
  await snap('from-request', 375)
  check('request field generator fits a phone', await browser.evaluate('(()=>{const e=document.querySelector(".match-sample-dialog"),b=e.querySelector(".el-dialog__body");return e.getBoundingClientRect().right<=innerWidth+1&&b.scrollWidth<=b.clientWidth+1})()'))
  await browser.cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 950, deviceScaleFactor: 1, mobile: false }); await settle()
  await trial('?status=paid&gateway=web', '{"amount":100}', '支付成功')
  await trial('?status=failed&gateway=web', '{"amount":101}', '大额订单')
  await trial('?status=failed', '{"amount":0}', '默认返回')
  await browser.click('完成，返回编辑'); await settle()
  for (const width of [1440, 768, 375]) {
    await browser.evaluate('document.querySelector(".response-tabs").scrollIntoView({block:"start"})'); await snap('editor', width)
    check('condition controls stay inside container at ' + width, await browser.evaluate('[...document.querySelectorAll(".condition-row")].every(e=>e.scrollWidth<=e.clientWidth+1)'))
  }
  await browser.cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 950, deviceScaleFactor: 1, mobile: false }); await pointer('.editor-page-actions .el-button--primary'); await browser.waitFor('location.hash==="#/apis"')
  const detail = await api('/apis/' + saved.id), rules = JSON.parse(detail.responses[0].conditions).conditions
  check('real save retains three conditions and numeric type', rules.length === 3 && rules[1].valueType === 'NUMBER' && rules[2].source === 'QUERY')
  await browser.evaluate('location.hash=' + JSON.stringify('#/apis/' + saved.id + '/edit')); await browser.waitFor('document.querySelectorAll(".condition-row").length===3')
  await browser.evaluate('[...document.querySelectorAll(".response-mode-bar .el-radio-button")].find(e=>e.textContent.trim()==="等概率随机").click()'); await browser.waitFor('!!document.querySelector(".random-mode-summary")')
  await browser.evaluate('document.querySelectorAll(".tab-item")[2].click()'); await pointer('.response-enable-row .el-switch__core'); check('random can disable fallback without an irrelevant confirmation', await browser.evaluate('document.querySelector(".response-enable-row").textContent.includes("未启用") && !document.querySelector(".el-message-box")'))
  await browser.click('查看随机候选'); await browser.waitFor('document.querySelector(".run-preview")?.getBoundingClientRect().width>0'); await pointer('.run-preview'); await browser.waitFor('document.querySelectorAll(".random-candidates li").length===2')
  check('random preview reports actual enabled candidates', await browser.evaluate('document.querySelector(".preview-result").textContent.includes("1 / 2")'))
  await browser.click('返回编辑'); await settle(); await pointer('.match-guide-entry'); await browser.waitFor('document.querySelector(".match-guide-dialog")?.getBoundingClientRect().width>0')
  check('guide follows random mode without editing selection mode', await browser.evaluate('document.querySelector(".guide-tabs [aria-selected=true]").textContent==="随机返回"'))
  await browser.click('条件匹配')
  for (const width of [1440, 375]) { await snap('guide', width); check('illustrated help stays within viewport at ' + width, await browser.evaluate('(()=>{const e=document.querySelector(".match-guide-dialog"),b=e.querySelector(".el-dialog__body"),r=e.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1&&b.scrollWidth<=b.clientWidth+1})()')) }
  await browser.click('我明白了'); check('help tab changes did not alter actual random configuration', await browser.evaluate('!!document.querySelector(".random-mode-summary")'))
  check('no browser exceptions', browser.exceptions.length === 0)
  await writeFile(path.join(output, 'report.json'), JSON.stringify({ passed: true, backendIntegration: true, apiStub: false, initialHeight, checks, screenshots, exceptions: browser.exceptions }, null, 2))
  console.log(JSON.stringify({ passed: true, checks: checks.length, initialHeight, output }, null, 2))
} catch (error) {
  await browser.screenshot(path.join(output, 'failure.png'), 1440).catch(() => {})
  await writeFile(path.join(output, 'report.json'), JSON.stringify({ passed: false, error: error.message, checks, exceptions: browser.exceptions, consoleErrors: browser.consoleErrors }, null, 2)); throw error
} finally { await browser.close() }
