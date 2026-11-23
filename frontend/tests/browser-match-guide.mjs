import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileStyle } from 'vue/compiler-sfc'
import { compileString } from 'sass'
import { build } from 'vite'
import { browserDriver } from './helpers/browser-driver.mjs'

// Isolated component only: no app build, server, backend, saved configuration or shared fixture changes.
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const filename = path.join(frontend, 'src/components/match/MatchGuide.vue')

test('MatchGuide: real dialog, keyboard tabs, model contract and responsive diagram', { timeout: 90000 }, async () => {
  const source = await readFile(filename, 'utf8')
  const { descriptor, errors } = parse(source, { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'match-guide-check', inlineTemplate: true })
  const style = compileStyle({ id: 'data-v-match-guide-check', source: descriptor.styles[0].content, scoped: true })
  assert.deepEqual(style.errors, [])
  const entry = script.content.replace('export default', 'const Guide =') +
    '\nGuide.__scopeId="data-v-match-guide-check";\n' +
    'import { createApp, h, reactive } from "vue"; import { ElDialog } from "element-plus";\n' +
    'const state=reactive({open:true,mode:undefined,kind:undefined});const events=[];window.guideCheck={state,events};\n' +
    'createApp({setup(){return()=>h(Guide,{modelValue:state.open,mode:state.mode,bodySampleKind:state.kind,"onUpdate:modelValue":value=>{events.push(value);state.open=value}})}}).component("ElDialog",ElDialog).mount("#app");'
  const result = await build({ configFile: false, root: frontend, logLevel: 'error', define: { 'process.env.NODE_ENV': JSON.stringify('production') }, plugins: [{ name: 'isolated-guide-check', enforce: 'pre', resolveId(id) { if (id === filename) return '\0guide-check' }, load(id) { if (id === '\0guide-check') return entry } }], build: { write: false, minify: false, lib: { entry: filename, name: 'GuideCheck', formats: ['iife'] } } })
  const js = (Array.isArray(result) ? result[0] : result).output.find(item => item.type === 'chunk').code
  const base = await readFile(path.join(frontend, 'node_modules/element-plus/dist/index.css'), 'utf8')
  const theme = (await Promise.all(['variables.scss', 'global.scss', 'element-overrides.scss'].map(file => readFile(path.join(frontend, 'src/styles', file), 'utf8')))).map(text => compileString(text).css).join('\n')
  const browser = await browserDriver()
  let checks = 0
  const check = (value, message) => { checks++; assert.ok(value, message) }
  try {
    await browser.cdp('Page.setDocumentContent', { frameId: (await browser.cdp('Page.getFrameTree')).frameTree.frame.id, html: '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><style>' + base + theme + style.code + '</style><body><div id="app"></div></body></html>' })
    await browser.evaluate(js)
    await browser.waitFor('document.querySelector(".match-guide-dialog")?.getBoundingClientRect().width > 0')
    await browser.evaluate('Promise.all(document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{})))')
    check(await browser.evaluate('document.querySelector("[role=tab][aria-selected=true]").textContent === "条件匹配"'), 'default mode is CONDITION')
    check(await browser.evaluate('!document.querySelector("#app .match-guide-dialog") && !!document.querySelector("body > .el-overlay .match-guide-dialog")'), 'dialog appends to body')
    check(await browser.evaluate('document.querySelector(".condition-flow").textContent.includes("?status=paid") && document.querySelector(".response-choice.selected").textContent.includes("status") && JSON.parse(document.querySelector(".result-card code").textContent).ok === true'), 'real Query -> choice -> success JSON diagram')
    check(await browser.evaluate('document.querySelectorAll(".guide-steps li").length === 3 && !document.querySelector(".match-guide img,.match-guide iframe")'), 'three steps and native diagram, not a screenshot')
    check(await browser.evaluate('document.querySelector(".selection-rules").textContent.includes("第一个命中") && document.querySelector(".selection-rules").textContent.includes("唯一") && document.querySelector(".single-note").textContent.includes("直接返回")'), 'ordering, unique fallback and single-enabled semantics')
    for (const width of [1440, 768, 375]) {
      await browser.cdp('Emulation.setDeviceMetricsOverride', { width, height: 812, deviceScaleFactor: 1, mobile: false })
      await browser.evaluate('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))')
      const geometry = await browser.evaluate('(()=>{const d=document.querySelector(".match-guide-dialog"),b=d.querySelector(".el-dialog__body"),r=d.getBoundingClientRect();return {width:r.width,left:r.left,right:r.right,bottom:r.bottom,scroll:b.scrollWidth,client:b.clientWidth,page:document.documentElement.scrollWidth,buttons:[...d.querySelectorAll("button")].filter(e=>e.getBoundingClientRect().width).map(e=>e.getBoundingClientRect().height),small:[...d.querySelectorAll("p,code,strong,figcaption,button,.card-label,.selected-badge,.fallback > span,.guide-steps li > div > span")].filter(e=>e.getBoundingClientRect().width).filter(e=>parseFloat(getComputedStyle(e).fontSize)<12).map(e=>e.textContent),flat:[d,...d.querySelectorAll("button,.guide-figure,.response-choice,.guide-steps li")].every(e=>{const s=getComputedStyle(e);return s.boxShadow==="none"&&s.backgroundImage==="none"})}})()')
      check(geometry.left >= 0 && geometry.right <= width + 1 && geometry.bottom <= 813 && geometry.page <= width + 1 && geometry.scroll <= geometry.client + 1, 'fits viewport without horizontal scrolling at ' + width + ': ' + JSON.stringify(geometry))
      check(geometry.buttons.every(height => height >= 44), 'buttons have 44px targets at ' + width + ': ' + JSON.stringify(geometry.buttons))
      check(!geometry.small.length && geometry.flat, 'readable >=12px type, flat theme at ' + width)
      if (process.argv.includes('--screenshots')) await browser.screenshot('/tmp/mockhub-match-guide-component-' + width + '.png', width, 812)
    }
    const contrast = await browser.evaluate('(()=>{const rgb=s=>s.match(/[0-9.]+/g).slice(0,3).map(Number),lum=a=>a.map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4}).reduce((s,x,i)=>s+x*[.2126,.7152,.0722][i],0);return [...document.querySelectorAll(".guide-header h2,.guide-header p,.guide-tabs button,.card-label,.selected-badge,.result-status,.guide-steps strong,.guide-steps li > div > span")].filter(e=>e.getBoundingClientRect().width).map(e=>{let p=e;while(p.parentElement&&getComputedStyle(p).backgroundColor==="rgba(0, 0, 0, 0)")p=p.parentElement;const a=lum(rgb(getComputedStyle(e).color)),b=lum(rgb(getComputedStyle(p).backgroundColor));return {text:e.textContent,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)}})})()')
    check(contrast.every(item => item.ratio >= 4.5), 'representative text contrast >=4.5:1: ' + JSON.stringify(contrast))
    await browser.click('随机返回')
    await browser.waitFor('document.querySelector(".random-figure").getBoundingClientRect().width > 0')
    check(await browser.evaluate('[...document.querySelectorAll(".probability")].every(e=>e.textContent==="50%") && document.querySelector(".random-figure").textContent.includes("不看条件，不看顺序") && guideCheck.events.length === 0 && guideCheck.state.mode === undefined'), 'random explanation is equal and cannot mutate mode or model')
    await browser.evaluate('document.querySelector("[role=tab][aria-selected=true]").dispatchEvent(new KeyboardEvent("keydown",{key:"ArrowLeft",bubbles:true}))')
    check(await browser.evaluate('document.activeElement.textContent === "条件匹配" && document.activeElement.getAttribute("aria-selected")==="true"'), 'arrow keys select and focus tabs')
    await browser.evaluate('guideCheck.state.kind="xml";guideCheck.state.mode="RANDOM"')
    await browser.waitFor('document.querySelector("[role=tab][aria-selected=true]").textContent === "随机返回"')
    await browser.click('条件匹配')
    check(await browser.evaluate('document.querySelector(".guide-facts").textContent.includes("Body XML")'), 'bodySampleKind adapts the path hint')
    await browser.click('关闭')
    await browser.waitFor('!guideCheck.state.open')
    check(await browser.evaluate('guideCheck.events.length === 1 && guideCheck.events[0] === false'), 'Chinese close emits Boolean model update only')
    await browser.evaluate('guideCheck.state.open=true')
    await browser.waitFor('document.querySelector("[role=tab][aria-selected=true]").textContent === "随机返回" && document.querySelector(".match-guide-dialog").getBoundingClientRect().width > 0')
    check(await browser.evaluate('document.querySelector("[role=tab][aria-selected=true]").textContent === "随机返回"'), 'reopening follows actual mode, not last help tab')
    await browser.evaluate('Promise.all(document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{})))')
    check(await browser.evaluate('(()=>{const d=document.querySelector(".match-guide-dialog"),b=d.querySelector(".el-dialog__body"),cards=[...d.querySelectorAll(".random-choice")].map(e=>e.getBoundingClientRect());return b.scrollWidth<=b.clientWidth+1&&Math.abs(cards[0].width-cards[1].width)<1&&d.getBoundingClientRect().right<=innerWidth})()'), 'random candidates fit mobile and have equal visual weight')
    if (process.argv.includes('--screenshots')) await browser.screenshot('/tmp/mockhub-match-guide-random-375.png', 375, 812)
    await browser.click('我明白了')
    await browser.waitFor('!guideCheck.state.open')
    check(await browser.evaluate('guideCheck.events.length === 2 && guideCheck.events.every(value=>value===false)'), 'confirmation also uses v-model')
    check(browser.exceptions.length === 0 && browser.consoleErrors.length === 0, 'no browser errors: ' + JSON.stringify([browser.exceptions,browser.consoleErrors]))
    console.log('MatchGuide isolated browser checks passed: ' + checks)
  } finally { await browser.close() }
})
