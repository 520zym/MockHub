import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { reactive } from 'vue'
import { loadSetup, deferred } from './helpers/sfc-unit.mjs'

// Actual SFC setup units with explicit request, confirmation, message and icon stubs.
// These are not browser/layout integration tests and do not claim real backend coverage.
const file = 'frontend/src/components/ai/AiResponseDrawer.vue'
const props = (extra = {}) => ({
  modelValue: true, readOnly: false, teamId: 'team-a', operationName: null,
  apiContext: { id: 'api-a', type: 'REST', name: '用户详情', method: 'GET', path: '/users/:id' },
  response: { id: 'response-a', name: '成功响应', contentType: 'application/json', responseBody: '' },
  ...extra
})
async function drawer(t, { input = props(), transport = async () => ({ content: '{"ok":true}' }), confirm = async () => {} } = {}) {
  const calls = [], confirmations = [], notices = []
  const unit = await loadSetup(file, {
    '@/api/request': { default: { post: (url, payload, config) => { calls.push({ url, payload, config }); return transport(payload, config) } } },
    'element-plus': {
      ElMessage: Object.fromEntries(['warning', 'success', 'error', 'info'].map(type => [type, message => notices.push([type, message])])),
      ElMessageBox: { confirm: (...args) => { confirmations.push(args); return confirm(...args) } }
    },
    '@element-plus/icons-vue': Object.fromEntries(['MagicStick', 'Close', 'Refresh', 'DocumentCopy', 'CircleCheck', 'Lock', 'Loading'].map(name => [name, {}]))
  })
  const p = reactive(input), instance = unit.setup(p)
  t.after(() => unit.unmount())
  return { unit, p, ...instance, s: instance.state, calls, confirmations, notices }
}
const inserts = c => c.events.filter(event => event[0] === 'insert')
const successes = c => c.notices.filter(notice => notice[0] === 'success')

test('readonly setup denies generation, insertion and confirmation even via direct handlers (API stub)', async t => {
  const c = await drawer(t, { input: props({ readOnly: true }) })
  c.s.result.value = { content: 'must not apply' }
  await c.s.generate(); await c.s.insert()
  assert.equal(c.s.canInteract.value, false)
  assert.equal(c.s.canGenerate.value, false)
  assert.equal(c.s.canInsert.value, false)
  assert.equal(c.calls.length, 0)
  assert.equal(c.confirmations.length, 0)
  assert.deepEqual(inserts(c), [])
  assert.deepEqual(successes(c), [])
})

test('revocation aborts pending generation and rejects its result after same-tick permission restoration (API stub)', async t => {
  const pending = deferred(), c = await drawer(t, { transport: () => pending.promise })
  c.s.instruction.value = '生成用户详情'
  const generation = c.s.generate()
  await c.s.generate() // A shortcut cannot start a duplicate request.
  assert.equal(c.calls.length, 1)
  c.p.readOnly = true
  assert.equal(c.calls[0].config.signal.aborted, true)
  assert.equal(c.s.loading.value, false)
  c.p.readOnly = false
  pending.resolve({ content: 'former permission result' }); await generation
  assert.equal(c.s.result.value, null)
  assert.equal(c.s.error.value, '')
  await c.s.insert()
  assert.deepEqual(inserts(c), [])
  assert.deepEqual(successes(c), [])
})

test('replacement confirmation rechecks role after await and cannot be reused after revocation (API stub)', async t => {
  const answer = deferred(), c = await drawer(t, {
    input: props({ response: { name: 'R', contentType: 'application/json', responseBody: '{"old":true}' } }),
    confirm: () => answer.promise
  })
  await c.s.generate()
  const applying = c.s.insert()
  assert.equal(c.s.confirming.value, true)
  await c.s.insert(); await c.s.generate()
  assert.equal(c.confirmations.length, 1)
  assert.equal(c.calls.length, 1)
  c.p.readOnly = true; c.p.readOnly = false
  answer.resolve(); await applying
  assert.equal(c.s.result.value, null)
  assert.equal(c.s.confirming.value, false)
  assert.deepEqual(inserts(c), [])
  assert.deepEqual(successes(c), [])
})

const boundaries = [
  ['parent close/reopen', c => { c.p.modelValue = false; c.p.modelValue = true }],
  ['local close', c => { c.s.visible.value = false }],
  ['Escape/Element Plus close', c => { c.s.onDrawerClose() }],
  ['team switch and return', c => { c.p.teamId = 'team-b'; c.p.teamId = 'team-a' }],
  ['API path edit', c => { c.p.apiContext.path = '/different' }],
  ['API identity edit', c => { c.p.apiContext.id = 'api-b' }],
  ['API context replacement', c => { c.p.apiContext = { ...c.p.apiContext } }],
  ['response identity edit', c => { c.p.response.id = 'response-b' }],
  ['identical response-tab replacement', c => { c.p.response = { ...c.p.response } }],
  ['response body edit', c => { c.p.response.responseBody = 'changed locally' }],
  ['response name edit', c => { c.p.response.name = '另一个响应' }],
  ['response format edit', c => { c.p.response.contentType = 'text/plain' }],
  ['SOAP operation switch', c => { c.p.operationName = 'GetOrder' }],
  ['readonly change', c => { c.p.readOnly = true }],
  ['unmount', c => { c.unit.unmount() }]
]

test('all scope boundaries abort and reject late success/error even when transport ignores abort (API stub)', async t => {
  for (const [label, change] of boundaries) for (const outcome of ['success', 'error']) {
    await t.test(label + ' / late ' + outcome, async sub => {
      const answer = deferred(), c = await drawer(sub, { transport: () => answer.promise })
      const pending = c.s.generate()
      change(c)
      assert.equal(c.calls[0].config.signal.aborted, true)
      assert.equal(c.s.result.value, null)
      assert.equal(c.s.loading.value, false)
      if (outcome === 'success') answer.resolve({ content: 'stale team/private result', warnings: ['stale warning'] })
      else answer.reject({ msg: 'stale failure' })
      await pending
      assert.equal(c.s.result.value, null)
      assert.equal(c.s.error.value, '')
      await c.s.insert()
      assert.deepEqual(inserts(c), [])
      assert.deepEqual(successes(c), [])
    })
  }
})

test('a completed preview is also cleared on close, team, context and readonly changes (API stub)', async t => {
  for (const [label, change] of boundaries.filter(([label]) => label !== 'unmount')) {
    await t.test(label, async sub => {
      const c = await drawer(sub)
      await c.s.generate(); assert.ok(c.s.result.value)
      change(c)
      assert.equal(c.s.result.value, null)
      assert.equal(c.s.error.value, '')
      await c.s.insert(); assert.deepEqual(inserts(c), [])
    })
  }
})

test('context/close changes during replacement confirmation never apply the captured body (API stub)', async t => {
  for (const [label, change] of boundaries) await t.test(label, async sub => {
    const answer = deferred(), c = await drawer(sub, {
      input: props({ response: { name: 'R', contentType: 'application/json', responseBody: 'existing body' } }),
      confirm: () => answer.promise
    })
    await c.s.generate()
    const applying = c.s.insert()
    change(c); answer.resolve(); await applying
    assert.deepEqual(inserts(c), [])
    assert.deepEqual(successes(c), [])
  })
})

test('a stale rejection cannot clear loading or replace the new context result (API stub)', async t => {
  const first = deferred(), second = deferred()
  const c = await drawer(t, { transport: payload => payload.teamId === 'team-a' ? first.promise : second.promise })
  const oldGeneration = c.s.generate()
  c.p.teamId = 'team-b'
  const newGeneration = c.s.generate()
  first.reject({ msg: 'old team failure' }); await oldGeneration
  assert.equal(c.s.loading.value, true)
  assert.equal(c.s.error.value, '')
  second.resolve({ content: 'new team content' }); await newGeneration
  assert.equal(c.s.loading.value, false)
  assert.equal(c.s.result.value.content, 'new team content')
})

test('generation controls invalidate stale results and cannot bypass a replacement confirmation (API stub)', async t => {
  for (const [name, value] of [['mode', 'COMPLETE'], ['size', 'RICH'], ['instruction', 'new instructions']]) {
    await t.test(name, async sub => {
      const c = await drawer(sub)
      await c.s.generate(); c.s[name].value = value
      assert.equal(c.s.result.value, null)
      assert.equal(c.s.canInsert.value, false)
      await c.s.insert(); assert.deepEqual(inserts(c), [])
    })
  }
})

test('REST JSON/XML/text and SOAP preserve the existing authenticated payload and AI request options (API stub)', async t => {
  for (const [type, contentType, label, operationName] of [
    ['REST', 'application/json', 'JSON', null], ['REST', 'application/xml', 'XML', null],
    ['REST', 'text/plain', '文本', null], ['SOAP', 'text/xml; charset=UTF-8', 'XML', 'GetUser']
  ]) await t.test(type + ' ' + contentType, async sub => {
    const existingBody = '  full existing body\n{{dynamic.value}}\n', input = props()
    input.apiContext.type = type; input.operationName = operationName
    input.response.contentType = contentType; input.response.responseBody = existingBody
    const c = await drawer(sub, { input })
    c.s.instruction.value = '  保持完整内容\n'; c.s.mode.value = 'COMPLETE'; c.s.size.value = 'RICH'
    await c.s.generate()
    assert.equal(c.s.formatLabel.value, label)
    assert.deepEqual(c.calls[0].payload, {
      teamId: 'team-a', apiType: type, apiName: '用户详情', method: 'GET', path: '/users/:id', soapOperationName: operationName,
      responseName: '成功响应', contentType, existingBody, instruction: '  保持完整内容\n', mode: 'COMPLETE', size: 'RICH'
    })
    assert.equal(c.calls[0].url, '/ai/generate-response')
    assert.equal(c.calls[0].config.timeout, 120000)
    assert.equal(c.calls[0].config.skipGlobalNetworkError, true)
    assert.ok(c.calls[0].config.signal instanceof AbortSignal)
    assert.deepEqual(successes(c), []) // Generating never implies saving/applying.
  })
})

test('full body including long lines, CRLF, dynamic values and trailing newline survives preview/copy/COMPLETE insertion (API stub)', async t => {
  const body = '  {{uuid}}\r\n' + 'very-long-line:'.repeat(5000) + '\n' + Array.from({ length: 800 }, (_, i) => i + ': 保留这一行').join('\n') + '\n'
  const c = await drawer(t, {
    input: props({ response: { name: 'R', contentType: 'text/plain', responseBody: 'existing content is sent to the model' } }),
    transport: async () => ({ content: body, warnings: ['check fixture content'], model: 'unit-api-stub' })
  })
  const navigatorBefore = Object.getOwnPropertyDescriptor(globalThis, 'navigator'), copies = []
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async text => copies.push(text) } } })
  t.after(() => { if (navigatorBefore) Object.defineProperty(globalThis, 'navigator', navigatorBefore); else delete globalThis.navigator })
  c.s.mode.value = 'COMPLETE'; await c.s.generate()
  assert.equal(c.s.result.value.content, body)
  assert.equal(c.s.lines.value.join('\n'), body)
  assert.equal(c.s.lines.value.at(-1), '')
  assert.equal(c.s.lines.value.length, body.split('\n').length)
  assert.deepEqual(c.s.result.value.warnings, ['check fixture content'])
  await c.s.copyContent(); assert.deepEqual(copies, [body])
  await c.s.insert()
  assert.deepEqual(inserts(c), [['insert', body]])
  assert.equal(c.confirmations.length, 0) // COMPLETE returns a complete body, never concatenate it again.
  assert.equal(c.s.result.value, null)
  assert.equal(c.s.visible.value, false)
})

test('NEW replacement only emits after affirmative confirmation and preserves every byte (API stub)', async t => {
  const answer = deferred(), body = '  {"new":true}\n'
  const c = await drawer(t, {
    input: props({ response: { name: 'R', contentType: 'application/json', responseBody: '{"old":true}' } }),
    transport: async () => ({ content: body }), confirm: () => answer.promise
  })
  await c.s.generate()
  const applying = c.s.insert()
  assert.equal(c.confirmations.length, 1)
  assert.deepEqual(inserts(c), [])
  answer.resolve(); await applying
  assert.deepEqual(inserts(c), [['insert', body]])
  assert.equal(successes(c).length, 1)
  assert.equal(c.s.visible.value, false)
})

test('canceling replacement keeps the complete preview without inserting or success notices (API stub)', async t => {
  const c = await drawer(t, {
    input: props({ response: { name: 'R', contentType: 'application/json', responseBody: 'existing' } }),
    confirm: async () => { throw new Error('cancel') }
  })
  await c.s.generate(); await c.s.insert()
  assert.deepEqual(inserts(c), [])
  assert.deepEqual(successes(c), [])
  assert.equal(c.s.visible.value, true)
  assert.equal(c.s.result.value.content, '{"ok":true}')
  assert.equal(c.s.confirming.value, false)
  assert.equal(c.s.canInsert.value, true)
})

test('missing team, empty generation, backend errors and clipboard failure never manufacture success (API stub)', async t => {
  await t.test('missing team', async sub => {
    const c = await drawer(sub, { input: props({ teamId: null }) }); await c.s.generate()
    assert.equal(c.calls.length, 0); assert.ok(c.notices.some(([type]) => type === 'warning'))
  })
  for (const [label, transport, expected] of [
    ['empty', async () => ({ content: '' }), '模型没有返回内容'],
    ['backend rejection', async () => { throw { msg: 'provider unavailable' } }, 'provider unavailable']
  ]) await t.test(label, async sub => {
    const c = await drawer(sub, { transport }); await c.s.generate(); await c.s.insert()
    assert.ok(c.s.error.value.includes(expected)); assert.equal(c.s.result.value, null); assert.equal(c.s.loading.value, false)
    assert.deepEqual(inserts(c), []); assert.deepEqual(successes(c), [])
  })
  await t.test('clipboard failure', async sub => {
    const c = await drawer(sub), before = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async () => { throw new Error('denied') } } } })
    sub.after(() => { if (before) Object.defineProperty(globalThis, 'navigator', before); else delete globalThis.navigator })
    await c.s.generate(); await c.s.copyContent()
    assert.deepEqual(successes(c), []); assert.ok(c.notices.some(([type]) => type === 'warning'))
  })
})

test('source-level UI contract uses bounded drawer sizing, flat tokens, natural scrollers and readonly control bindings (not DOM coverage)', async () => {
  const source = await readFile(new URL('../src/components/ai/AiResponseDrawer.vue', import.meta.url), 'utf8')
  for (const binding of ['readOnly: { type: Boolean, default: false }', 'size="min(600px, 100vw)"', ':disabled="!canGenerate"', ':disabled="!canInsert"', ':disabled="!canInteract || loading || confirming"', 'v-for="(line, idx) in lines"']) assert.ok(source.includes(binding), binding)
  assert.match(source, /\.ai-panel__body\s*\{[^}]*min-height: 0;[^}]*overflow: auto;[^}]*overscroll-behavior: contain;/s)
  assert.match(source, /\.ai-code__body\s*\{[^}]*max-height: 46dvh;[^}]*overflow: auto;/s)
  for (const token of ['#EEF1F4', '#FFFFFF', '#F4F6F9', '#17191D', '#3568D4']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /gradient\(|backdrop-filter|filter:\s*blur|scale\(|zoom\s*:/)
  assert.doesNotMatch(source, /box-shadow:(?!\s*none\s*;)[^;]+;/)
  assert.doesNotMatch(source, /border(?:-(?:top|right|bottom|left))?:(?!\s*0\s*;)[^;]+;/)
})
