// Real packaged MockHub + real Chromium: measures rendered geometry, contrast and hit areas.
// It discovers defects instead of asserting a fixed snapshot, so it stays useful as pages change.
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import { browserDriver } from './helpers/browser-driver.mjs'

if (!process.argv.includes('--isolated')) throw new Error('Requires --isolated with a disposable database')
const statePath = process.argv.find(a => a.startsWith('--state='))?.slice(8)
if (!statePath) throw new Error('Pass isolated instance --state=...')
const state = JSON.parse(await readFile(statePath, 'utf8'))
if (!['127.0.0.1', 'localhost'].includes(new URL(state.url).hostname) || !state.dataDirectory.includes('/target/ui-smoke.')) throw new Error('Refusing non-isolated data')
const credentials = JSON.parse(await readFile('target/browser-shell-smoke/local-credentials.json', 'utf8'))
if (credentials.dataDirectory !== state.dataDirectory) throw new Error('Credentials belong to another instance')

const output = path.resolve('target/layout-audit')
await mkdir(output, { recursive: true })
const WIDTHS = (process.argv.find(a => a.startsWith('--widths='))?.slice(9) || '1440,768,375').split(',').map(Number)
const only = process.argv.find(a => a.startsWith('--pages='))?.slice(8)
const DIALOGS = process.argv.includes('--dialogs')

async function request(route, method = 'GET', data, token, allowFailure = false) {
  const response = await fetch(state.url + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
  const body = await response.json().catch(() => ({}))
  if (!allowFailure && (!response.ok || body.code !== 0)) throw new Error(method + ' ' + route + ': ' + (body.msg || response.status))
  return allowFailure ? { status: response.status, ...body } : body.data
}

const admin = await request('/auth/login', 'POST', { username: credentials.username, password: credentials.password })
const token = admin.token
const teams = await request('/teams', 'GET', undefined, token)
let team = teams.find(t => t.name === '布局压测团队')
if (!team) team = await request('/teams', 'POST', { name: '布局压测团队', identifier: 'LAYOUT', color: '#3568D4', description: '真实内容长度下的布局缺陷审计', initialAdminUserId: admin.user.id }, token)
const groups = await request('/groups?teamId=' + team.id, 'GET', undefined, token)
if (!groups.length) for (const name of ['订单中心（含历史遗留的非常长分组名称用于验证截断）', '支付', '用户']) await request('/groups', 'POST', { teamId: team.id, name }, token)
const current = await request('/apis?teamId=' + team.id + '&page=1&size=100', 'GET', undefined, token)
const existing = Array.isArray(current) ? current : (current.items || current.list || current.records || [])
if (existing.length < 6) {
  const specs = [
    { name: '查询订单详情（包含较长的中文接口名称用于验证列表与详情页截断是否正确）', path: '/orders/detail', method: 'GET', type: 'REST' },
    { name: '创建订单', path: '/orders', method: 'POST', type: 'REST' },
    { name: '取消订单', path: '/orders/{id}/cancel', method: 'PUT', type: 'REST' },
    { name: '极长的接口路径用于观察溢出是否出现横向滚动条与破版', path: '/very/long/path/segment/that/should/wrap/nicely/without/breaking/layout', method: 'DELETE', type: 'REST' },
    { name: 'SOAP 订单服务', path: '/orders/soap', method: 'POST', type: 'SOAP' },
    { name: '文件下载', path: '/orders/export', method: 'GET', type: 'REST' }
  ]
  // Tolerate a previously interrupted seed: an existing path is a valid outcome here.
  for (const spec of specs) await request('/apis', 'POST', { teamId: team.id, type: spec.type, name: spec.name, path: spec.path, method: spec.method, enabled: true, contentType: 'application/json', responseCode: 200, responseBody: '{"code":0,"message":"用于验证长正文排版与滚动的返回体","data":{"id":123,"items":[1,2,3]}}', responses: [{ name: 'Default', active: true, bodyType: 'TEXT', responseBody: '{"code":0,"message":"用于验证长正文排版与滚动的返回体","data":{"id":123}}', contentType: 'application/json', responseCode: 200 }] }, token, true)
  for (let i = 0; i < 18; i++) await request('/mock/LAYOUT/orders/detail?seq=' + i, 'GET').catch(() => {})
}

const browser = await browserDriver()
const origin = new URL(state.url).origin
const auditRoutes = only ? only.split(',') : ['/apis', '/apis/trash', '/apis/new', '/team', '/teams', '/users', '/variables', '/files', '/stats', '/logs/operation', '/logs/request', '/settings', '/account/tokens', '/account']
const sampleApi = await request('/apis?teamId=' + team.id + '&page=1&size=1', 'GET', undefined, token)
const firstId = (Array.isArray(sampleApi) ? sampleApi[0] : (sampleApi.items || sampleApi.list || sampleApi.records || [])[0])?.id
if (firstId) auditRoutes.splice(3, 0, '/apis/' + firstId + '/edit')

const report = { generatedAt: new Date().toISOString(), url: state.url, dataDirectory: state.dataDirectory, teamId: team.id, widths: WIDTHS, pages: [], dialogs: [], totals: {} }

async function viewport(width) {
  await browser.cdp('Emulation.setDeviceMetricsOverride', { width, height: width < 600 ? 812 : 900, deviceScaleFactor: 1, mobile: false })
}
async function settle() {
  for (let i = 0; i < 80; i++) {
    const busy = await browser.evaluate("!!document.querySelector('.el-loading-mask') || !!document.querySelector('.el-skeleton') || document.readyState !== 'complete'")
    if (!busy) { await new Promise(r => setTimeout(r, 300)); return }
    await new Promise(r => setTimeout(r, 150))
  }
}
async function measure(route, width) {
  await new Promise(r => setTimeout(r, 250))
  await settle()
  await browser.evaluate('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))')
  return browser.evaluate('(' + pageAudit.toString() + ')()')
}
async function capture(route, width) {
  const name = (route.replace(/\//g, '_').replace(/^_/, '') || 'root').slice(0, 60)
  try {
    const shot = await browser.cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(path.join(output, width + '-' + name + '.png'), Buffer.from(shot.data, 'base64'))
  } catch {}
}
function record(route, width, result, note) {
  report.pages.push({ route, width, ...result, ...(note ? { note } : {}) })
  console.log(String(width).padStart(4) + ' ' + route.padEnd(26) + ' scrollW=' + String(result.scrollWidth).padStart(5) + ' h=' + String(result.scrollHeight).padStart(5) + ' issues=' + String(result.problems.length).padStart(3) + ' ' + Object.entries(result.byRule).map(([k, v]) => k + ':' + v).join(' ') + (note ? ' [' + note + ']' : ''))
}

// Login has no session; audit it before injecting credentials.
await viewport(WIDTHS[0])
await browser.cdp('Page.navigate', { url: state.url + '/#/login' })
await browser.waitFor('location.origin === ' + JSON.stringify(origin) + ' && document.readyState === "complete" && !!document.querySelector("#app .login-card")', 'login page')
await browser.evaluate('localStorage.removeItem("token");localStorage.removeItem("user")')
for (const width of WIDTHS) {
  await viewport(width)
  await browser.evaluate('location.hash = "#/login"')
  await browser.waitFor('!!document.querySelector(".login-card")', 'login mount')
  const result = await measure('/login', width)
  await capture('/login', width)
  record('/login', width, result)
}

// Boot the authenticated shell once, then switch routes in-document.
await browser.evaluate('localStorage.setItem("token",' + JSON.stringify(token) + ');localStorage.setItem("user",' + JSON.stringify(JSON.stringify(admin.user)) + ')')
await browser.cdp('Page.reload')
await browser.waitFor('location.origin === ' + JSON.stringify(origin) + ' && document.readyState === "complete" && document.querySelector("#app").children.length > 0', 'shell boot')
await new Promise(r => setTimeout(r, 1200))

for (const width of WIDTHS) {
  await viewport(width)
  for (const route of auditRoutes) {
    try {
      await browser.evaluate('location.hash = ' + JSON.stringify('#' + route))
      await browser.waitFor('location.hash === ' + JSON.stringify('#' + route), 'hash ' + route)
      const result = await measure(route, width)
      await capture(route, width)
      const actual = await browser.evaluate('location.hash')
      record(route, width, result, actual === '#' + route ? '' : 'redirected to ' + actual)
    } catch (error) {
      report.pages.push({ route, width, error: String(error.message).slice(0, 200), byRule: {} })
      console.log(String(width).padStart(4) + ' ' + route.padEnd(26) + ' FAILED ' + String(error.message).slice(0, 100))
      try {
        await browser.cdp('Page.navigate', { url: state.url + '/#/apis' })
        await browser.waitFor('!!document.querySelector("#app .app-page")', 'recover shell')
      } catch {}
    }
  }
}

// Dialogs and drawers carry their own layout and never appear on a page route.
// Each pass opens one overlay by visible label and measures only its subtree.
const DIALOG_PASSES = [
  { page: '/teams', labels: ['新建团队', '创建团队', '＋ 新建团队'], name: 'teams-create' },
  { page: '/users', labels: ['新建账号', '创建账号'], name: 'users-create' },
  { page: '/variables', labels: ['新建变量', '添加变量'], name: 'variables-create' },
  { page: '/apis', labels: ['导入'], name: 'apis-import' },
  { page: '/apis', labels: ['分组 / 标签', '分组'], name: 'apis-manage' },
  { page: '/account/tokens', labels: ['新建凭证', '新建', '创建'], name: 'tokens-create' }
]
if (DIALOGS) {
  await viewport(WIDTHS[0])
  for (const pass of DIALOG_PASSES) {
    try {
      await browser.evaluate('location.hash = ' + JSON.stringify('#' + pass.page))
      await browser.waitFor('location.hash === ' + JSON.stringify('#' + pass.page), 'hash ' + pass.page)
      await settle()
      const opened = await browser.evaluate(`(() => {
        const labels = ${JSON.stringify(pass.labels)}
        const buttons = [...document.querySelectorAll('button, .el-button, [role="button"]')].filter(b => b.offsetParent && b.getBoundingClientRect().width > 0)
        for (const label of labels) {
          const hit = buttons.find(b => (b.textContent || '').trim().includes(label))
          if (hit) { hit.click(); return (hit.textContent || '').trim().slice(0, 24) }
        }
        return ''
      })()`)
      if (!opened) {
        report.dialogs.push({ page: pass.page, name: pass.name, error: 'trigger not found' })
        console.log('     dialog ' + pass.name.padEnd(18) + ' SKIPPED (trigger not found)')
        continue
      }
      await browser.waitFor('!!document.querySelector(".el-dialog, .el-drawer")', 'dialog ' + pass.name)
      await settle()
      const result = await browser.evaluate('(' + pageAudit.toString() + ')({ dialogOnly: true })')
      report.dialogs.push({ page: pass.page, name: pass.name, trigger: opened, ...result })
      console.log('     dialog ' + pass.name.padEnd(18) + ' issues=' + String(result.issues).padStart(3) + ' ' + Object.entries(result.byRule || {}).map(([k, v]) => k + ':' + v).join(' '))
      await browser.evaluate('(() => { const b = document.querySelector(".el-dialog__headerbtn, .el-drawer__close-btn"); if (b) b.click() })()')
      await new Promise(r => setTimeout(r, 300))
    } catch (error) {
      report.dialogs.push({ page: pass.page, name: pass.name, error: String(error.message).slice(0, 200) })
      console.log('     dialog ' + pass.name.padEnd(18) + ' FAILED ' + String(error.message).slice(0, 90))
      await browser.evaluate('(() => { const b = document.querySelector(".el-dialog__headerbtn, .el-drawer__close-btn"); if (b) b.click() })()').catch(() => {})
    }
  }
}

for (const page of [...report.pages, ...(report.dialogs || []).map(d => ({ route: 'dialog:' + d.name + ' (' + d.page + ')', width: WIDTHS[0], ...d }))]) for (const [rule, count] of Object.entries(page.byRule || {})) {
  report.totals[rule] = report.totals[rule] || { count: 0, pages: [] }
  report.totals[rule].count += count
  const key = page.route + '@' + page.width
  if (!report.totals[rule].pages.includes(key)) report.totals[rule].pages.push(key)
}
await writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2))
console.log('\nSUMMARY ' + JSON.stringify(Object.fromEntries(Object.entries(report.totals).map(([k, v]) => [k, v.count]))))
console.log('report: ' + path.join(output, 'report.json'))
await browser.close()

function pageAudit(options) {
  const only = (options && options.dialogOnly) ? '.el-dialog, .el-drawer' : null
  const vw = innerWidth, vh = innerHeight
  const problems = []
  const path = el => {
    const parts = []
    let node = el
    for (let i = 0; i < 4 && node && node.nodeType === 1 && node !== document.body; i++) {
      let s = node.tagName.toLowerCase()
      const cls = [...node.classList].filter(c => !/^el-|^is-|^v-/.test(c)).slice(0, 2)
      if (cls.length) s += '.' + cls.join('.')
      if (node.parentElement) {
        const same = [...node.parentElement.children].filter(c => c.tagName === node.tagName)
        if (same.length > 1) s += ':nth-of-type(' + (same.indexOf(node) + 1) + ')'
      }
      parts.unshift(s); node = node.parentElement
    }
    return parts.join(' > ')
  }
  const push = (rule, el, detail, extra) => {
    if (!el || !el.isConnected) return
    if (only && (rule === 'doc-overflow' || !el.closest(only))) return
    problems.push(Object.assign({ rule, selector: path(el), text: (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 70), detail }, extra || {}))
  }
  // Monaco and wangEditor own their rendering, type scale and hit areas.
  const inThirdParty = el => !!(el && el.closest && el.closest('.monaco-editor, .rich-text-editor, .rich-text-toolbar, [class*="w-e-"]'))
  const thirdPartyOf = el => inThirdParty(el)
  const visible = el => {
    const cs = getComputedStyle(el), r = el.getBoundingClientRect()
    return cs.display !== 'none' && cs.visibility !== 'hidden' && parseFloat(cs.opacity) > 0.05 && r.width > 0 && r.height > 0 && r.bottom > -vh * 3 && r.top < vh * 4
  }
  const all = [...document.querySelectorAll('body *')].filter(visible)
  const scrollers = el => {
    const out = []
    for (let n = el.parentElement; n && n !== document.body; n = n.parentElement) {
      const cs = getComputedStyle(n)
      if (/(auto|scroll)/.test(cs.overflowX) || /(auto|scroll)/.test(cs.overflowY)) out.push(n)
    }
    return out
  }
  if (document.documentElement.scrollWidth > vw + 1) {
    for (const el of all) {
      if (scrollers(el).length) continue
      const r = el.getBoundingClientRect()
      if (r.right > vw + 1 || r.left < -1) push('doc-overflow', el, 'right=' + Math.round(r.right) + ' left=' + Math.round(r.left) + ' viewport=' + vw, { over: Math.round(Math.max(r.right - vw, -r.left)) })
    }
    if (!problems.length) problems.push({ rule: 'doc-overflow', selector: 'html', text: '', detail: 'scrollWidth=' + document.documentElement.scrollWidth + ' viewport=' + vw })
  }
  const TEXT = new Set(['P','SPAN','DIV','A','BUTTON','LABEL','TD','TH','LI','H1','H2','H3','H4','H5','H6','SMALL','STRONG','EM','CODE','PRE','SUMMARY','FIGCAPTION'])
  for (const el of all) {
    if (!TEXT.has(el.tagName)) continue
    const cs = getComputedStyle(el), r = el.getBoundingClientRect()
    if (r.width < 4 || r.height < 4) continue
    if (!(el.textContent || '').trim()) continue
    // Only leaf text: a container whose children flow normally legitimately contains
    // taller content. Text inside a horizontally scrollable region is also not clipped.
    const leafText = ![...el.children].some(child => { const d = getComputedStyle(child).display; return d !== 'inline' && d !== 'inline-block' && d !== 'contents' })
    const horizontallyScrolled = scrollers(el).some(node => /(auto|scroll)/.test(getComputedStyle(node).overflowX))
    if (leafText && !horizontallyScrolled && !el.closest('.monaco-editor')) {
      const range = document.createRange(); range.selectNodeContents(el)
      const rects = [...range.getClientRects()].filter(x => x.width > 0 && x.height > 0)
      if (rects.length) {
        const bottom = Math.max(...rects.map(x => x.bottom)), right = Math.max(...rects.map(x => x.right))
        const top = Math.min(...rects.map(x => x.top)), left = Math.min(...rects.map(x => x.left))
        const clippedY = cs.overflowY === 'hidden' || cs.overflowY === 'clip'
        const clippedX = cs.overflowX === 'hidden' || cs.overflowX === 'clip'
        if (bottom > r.bottom + 2 && (clippedY || bottom > r.bottom + 12)) push(clippedY ? 'text-clipped' : 'text-spill', el, 'text bottom=' + Math.round(bottom) + ' box=' + Math.round(r.bottom) + ' overflowY=' + cs.overflowY, { over: Math.round(bottom - r.bottom) })
        if (right > r.right + 2 && (clippedX || right > r.right + 12)) push(clippedX ? 'text-clipped' : 'text-spill', el, 'text right=' + Math.round(right) + ' box=' + Math.round(r.right) + ' overflowX=' + cs.overflowX, { over: Math.round(right - r.right) })
        if (top < r.top - 3 || left < r.left - 3) push('text-spill', el, 'text starts outside box', { over: Math.round(Math.max(r.top - top, r.left - left)) })
        const lines = new Set(rects.map(x => Math.round(x.top)))
        if (lines.size > 1 && (cs.whiteSpace === 'nowrap' || cs.whiteSpace === 'pre')) push('nowrap-wrap', el, lines.size + ' lines with white-space:' + cs.whiteSpace)
      }
    }
    if (!horizontallyScrolled && cs.textOverflow === 'ellipsis' && el.scrollWidth > el.clientWidth + 2) push('truncated', el, 'ellipsis hides ' + (el.scrollWidth - el.clientWidth) + 'px', { over: el.scrollWidth - el.clientWidth })
    const fs = parseFloat(cs.fontSize)
    // 11px is the allowed floor for a table header, a monospaced counter or a badge;
    // anything below it is unreadable product type. Rich-text and code editors ship
    // their own type scale, and a 0px wrapper renders through its children.
    if (!inThirdParty(el) && !(fs === 0 && el.children.length)) {
      if (fs < 11) {
        const monogram = (el.textContent || '').trim().length <= 2 || /mark|avatar|monogram/i.test(String(el.className))
        push(monogram ? 'small-font-minor' : 'small-font', el, fs + 'px')
      } else if (fs < 12) push('small-font-minor', el, fs + 'px')
    }
    // Disabled controls are exempt from WCAG contrast, and editor syntax themes are not ours.
    const muted = el.closest('[disabled], .is-disabled, [aria-disabled="true"]')
    if (!inThirdParty(el) && !muted && !el.querySelector('p,div,span,a,button,ul,li,table')) {
      const ratio = contrast(el)
      const bold = parseInt(cs.fontWeight, 10) >= 600
      const large = fs >= 24 || (fs >= 18.66 && bold)
      if (ratio !== null && ratio < (large ? 3 : 4.5)) push('low-contrast', el, ratio.toFixed(2) + ':1 ' + cs.color + ' on ' + bgOf(el))
    }
  }
  for (const el of all) {
    if (inThirdParty(el)) continue
    if (!el.matches('button, a, [role="button"], [role="tab"], .el-switch, .el-checkbox__inner, .el-radio__inner, .el-radio-button__inner, .el-select, .el-input__wrapper')) continue
    if (el.matches('a') && getComputedStyle(el).display === 'inline') continue
    if (thirdPartyOf(el, 'icon, hit')) continue
    // A 14px glyph inside a 28px label is a 28px target: measure the control's box.
    const target = el.closest('label, .el-radio, .el-radio-button, .el-checkbox, button, a, [role="button"], .el-switch') || el
    const r = target.getBoundingClientRect()
    if (r.height < 24 || r.width < 24) push('tap-target', el, Math.round(r.width) + 'x' + Math.round(r.height))
  }
  const groups = new Map()
  for (const el of all) {
    if (!el.matches('button, a, input, textarea, [role="button"], .el-select, .el-checkbox, .el-switch, .el-tag')) continue
    const key = el.parentElement
    if (!key) continue
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(el)
  }
  for (const siblings of groups.values()) {
    for (let i = 0; i < siblings.length; i++) for (let j = i + 1; j < siblings.length; j++) {
      const a = siblings[i].getBoundingClientRect(), b = siblings[j].getBoundingClientRect()
      const w = Math.min(a.right, b.right) - Math.max(a.left, b.left), h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
      if (w > 3 && h > 3) {
        const ratio = (w * h) / Math.min(a.width * a.height, b.width * b.height)
        if (ratio > 0.35) push('overlap', siblings[i], 'overlaps sibling by ' + Math.round(ratio * 100) + '%')
      }
    }
  }
  for (const table of all.filter(e => e.tagName === 'TABLE')) {
    const head = table.querySelector('thead tr'), body = table.querySelector('tbody tr')
    if (!head || !body) continue
    const hs = [...head.children], bs = [...body.children]
    if (hs.length !== bs.length) { push('table-columns', table, 'header ' + hs.length + ' vs row ' + bs.length + ' cells'); continue }
    hs.forEach((th, i) => {
      const a = th.getBoundingClientRect(), b = bs[i].getBoundingClientRect()
      const drift = Math.abs((a.left + a.right) / 2 - (b.left + b.right) / 2)
      if (drift > 3) push('table-columns', th, 'column ' + i + ' drifts ' + Math.round(drift) + 'px')
    })
  }
  const overlays = all.filter(el => { const cs = getComputedStyle(el); return (cs.position === 'fixed' || cs.position === 'sticky') && parseInt(cs.zIndex || 0, 10) > 0 && el.getBoundingClientRect().height > 20 })
  for (const overlay of overlays) {
    const o = overlay.getBoundingClientRect()
    if (o.width > vw * 0.9 && o.height > vh * 0.6) continue
    for (const el of all) {
      if (overlay.contains(el) || el.contains(overlay) || el.matches('.el-loading-mask, .el-overlay, .el-popper, [class*="drawer"], [class*="dialog"], [class*="topbar"], [class*="sidebar"]')) continue
      if (!el.matches('button, a, input, h1, h2, h3, .page-title, .app-page-title')) continue
      const r = el.getBoundingClientRect()
      const w = Math.min(o.right, r.right) - Math.max(o.left, r.left), h = Math.min(o.bottom, r.bottom) - Math.max(o.top, r.top)
      if (w > 4 && h > 4 && (w * h) / (r.width * r.height) > 0.4) { push('covered', el, 'covered by ' + path(overlay)); break }
    }
  }
  for (const el of all) {
    const cs = getComputedStyle(el)
    if (!/(auto|scroll)/.test(cs.overflowY)) continue
    if (el.scrollHeight <= el.clientHeight + 4) continue
    if (el === document.documentElement || el === document.body) continue
    if (el.clientHeight > vh * 0.75) continue
    if (el.closest('.el-table__body-wrapper, .el-scrollbar__wrap, .monaco-editor, .el-select-dropdown, .el-textarea, [class*="scroll"], [class*="preview"], [class*="log"], [class*="editor"], [class*="code"]')) continue
    push('nested-scroll', el, 'inner ' + el.clientHeight + 'px of ' + el.scrollHeight + 'px')
  }
  const cards = all.filter(el => parseFloat(getComputedStyle(el).borderRadius) >= 8 && el.parentElement && getComputedStyle(el).backgroundColor !== 'rgba(0, 0, 0, 0)')
  for (const card of cards) {
    // Only cards sharing a row: a vertical stack is allowed to size to content.
    const siblings = [...card.parentElement.children].filter(x => x.tagName === card.tagName && x.className === card.className && visible(x))
    const top = card.getBoundingClientRect().top
    const row = siblings.filter(x => Math.abs(x.getBoundingClientRect().top - top) < 4)
    if (row.length < 2) continue
    const hs = row.map(x => x.getBoundingClientRect().height)
    if (Math.max(...hs) - Math.min(...hs) > 16) push('ragged-cards', card, 'same-row heights ' + hs.map(Math.round).join('/'))
  }
  for (const el of all) {
    if (el.children.length === 0) continue
    const r = el.getBoundingClientRect()
    if (r.height <= 1 && (el.textContent || '').trim()) push('collapsed', el, 'height=' + Math.round(r.height))
  }
  // 13. Icon and label must share a centre line inside a control.
  for (const el of all) {
    if (!el.matches('button, a, [role="button"], .el-button, .el-tag')) continue
    const icon = el.querySelector(':scope > svg, :scope > i, :scope > .filled-icon, :scope > span > svg')
    if (!icon) continue
    if (el.getBoundingClientRect().height > 56) continue
    const label = [...el.childNodes].find(n => n.nodeType === 3 && n.textContent.trim()) || [...el.children].find(c => c !== icon && (c.textContent || '').trim() && !c.querySelector('svg'))
    if (!label) continue
    const ir = icon.getBoundingClientRect()
    if (ir.width < 1 || ir.height < 1) continue
    const range = document.createRange(); range.selectNodeContents(label.nodeType === 3 ? label.parentElement : label)
    const rects = [...range.getClientRects()].filter(x => x.width > 0 && x.height > 0)
    if (!rects.length) continue
    const textCenter = (Math.min(...rects.map(x => x.top)) + Math.max(...rects.map(x => x.bottom))) / 2
    const iconCenter = (ir.top + ir.bottom) / 2
    if (Math.abs(textCenter - iconCenter) > 2.5) push('icon-misaligned', el, 'icon centre ' + Math.round(iconCenter) + ' vs label centre ' + Math.round(textCenter))
  }

  // 14. A label and its control should share a centre line.
  for (const el of all) {
    if (!el.matches('label, .el-form-item__label, .field-label')) continue
    const control = el.querySelector('input, textarea, select, .el-select, .el-input') ||
      (el.parentElement && el.parentElement.querySelector('input, textarea, .el-select, .el-input, select'))
    if (!control || !visible(control)) continue
    const a = el.getBoundingClientRect(), b = control.getBoundingClientRect()
    if (a.bottom < b.top || a.top > b.bottom) continue
    // Stacked label-above-control is a layout, not a misalignment.
    if (!(a.right <= b.left + 10 || b.right <= a.left + 10)) continue
    const drift = Math.abs((a.top + a.bottom) / 2 - (b.top + b.bottom) / 2)
    if (drift > 3) push('label-misaligned', el, 'label centre off by ' + Math.round(drift) + 'px')
  }

  // 15. Controls sharing one toolbar should share one height.
  for (const parent of new Set(all.filter(el => el.matches('button, .el-button, [role="button"]')).map(el => el.parentElement))) {
    if (!parent) continue
    const controls = [...parent.children].filter(c => c.matches('button, .el-button, .el-dropdown, [role="button"], .el-input, .el-select') && visible(c))
    if (controls.length < 2) continue
    const hs = controls.map(c => c.getBoundingClientRect().height)
    if (Math.max(...hs) - Math.min(...hs) > 3) push('control-heights', controls[0], 'toolbar heights ' + hs.map(Math.round).join('/'))
  }

  const unique = [], seen = new Set()
  for (const p of problems) { const k = p.rule + '|' + p.selector + '|' + p.detail; if (!seen.has(k)) { seen.add(k); unique.push(p) } }
  const byRule = {}
  for (const p of unique) byRule[p.rule] = (byRule[p.rule] || 0) + 1
  return { vw, vh, scrollWidth: document.documentElement.scrollWidth, scrollHeight: document.documentElement.scrollHeight, byRule, problems: unique.slice(0, 150) }
  function bgOf(el) {
    for (let n = el; n; n = n.parentElement) {
      const c = getComputedStyle(n).backgroundColor
      const m = c.match(/rgba?\(([^)]+)\)/)
      if (!m) continue
      const p = m[1].split(',').map(Number)
      if (p.length === 4 && p[3] === 0) continue
      return c
    }
    return 'rgb(255,255,255)'
  }
  function contrast(el) {
    const parse = c => { const m = String(c).match(/rgba?\(([^)]+)\)/); if (!m) return null; const p = m[1].split(',').map(Number); return p.length >= 3 ? [p[0], p[1], p[2]] : null }
    const fg = parse(getComputedStyle(el).color), bg = parse(bgOf(el))
    if (!fg || !bg) return null
    const lum = c => { const s = c.map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4) }); return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2] }
    const a = lum(fg), b = lum(bg)
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
  }
}