import test from 'node:test'
import assert from 'node:assert/strict'
import { reactive, nextTick } from 'vue'
import { loadSetup, deferred, settle } from './helpers/sfc-unit.mjs'
import * as samples from '../src/utils/request-samples.js'

const props = (extra = {}) => ({ modelValue: '', readOnly: false, bodySampleKind: 'json', teamId: 'team-a', apiId: 'api-a', apiContext: {}, responseName: '支付宝渠道', responses: [{ id: 'one', name: '支付宝渠道', responseCode: 200, isActive: true, conditions: '' }, { name: '默认响应', responseCode: 200, isActive: true, conditions: '' }], selectedResponseIndex: 0, selectionMode: 'CONDITION', operationName: null, ...extra })
const ai = { capabilities: { conditions: true }, load: async () => {} }
const messages = { info() {}, warning() {}, error() {}, success() {} }
async function panel(input = props(), preview = async () => ({ reason: 'FALLBACK', matchedIndex: 1, mode: 'CONDITION', enabledCount: 2 })) {
  const calls = [], unit = await loadSetup('frontend/src/components/match/ConditionPanel.vue', {
    '@/stores/ai': { useAiStore: () => ai }, 'element-plus': { ElMessage: messages },
    '@/utils/request-samples': samples, '@/api/matchPreview': { previewApiMatch: (payload, config) => { calls.push({ payload, config }); return preview(payload, config) } }
  })
  const reactiveProps = reactive(input), instance = unit.setup(reactiveProps)
  return { unit, calls, p: reactiveProps, ...instance, s: instance.state }
}

test('Query uses URLSearchParams first same-name value including plus and prototype-like keys', () => {
  const query = samples.parseQuerySample('https://test/path?a=first&a=second&space=one+two&__proto__=safe#ignored')
  assert.equal(query.a, 'first'); assert.equal(query.space, 'one two'); assert.equal(query.__proto__, 'safe'); assert.equal(Object.getPrototypeOf(query), Object.prototype)
})
test('Header samples split first colon and deduplicate names case-insensitively', () => {
  assert.deepEqual(samples.parseHeaderSample('X-Env: first\nx-env: second\nReferer: https://test:8080/a'), { 'X-Env': 'first', Referer: 'https://test:8080/a' })
  assert.deepEqual(samples.parseHeaderSample('{"X-Env":1,"x-env":2,"Flag":true}'), { 'X-Env': '1', Flag: 'true' })
  for (const text of ['missing colon', 'bad name: value', '{"x":{}}', '{"x":"a\nInjected: b"}']) assert.throws(() => samples.parseHeaderSample(text))
})
test('Sample limits are explicit and never silently truncate', () => {
  assert.throws(() => samples.parseQuerySample(Array.from({ length: 101 }, (_, i) => 'k' + i + '=v').join('&')), /100/)
  assert.throws(() => samples.buildPreviewSample({ BODY: '中'.repeat(350000) }), /1 MiB/)
  assert.throws(() => samples.parseHeaderSample('X: ' + 'v'.repeat(8193)), /8192/)
  assert.equal(samples.buildPreviewSample({ BODY: '{}', HEADER: 'content-type: application/custom' }).contentType, 'application/custom')
})
test('Payload maps active, original indexes, stable keys and SOAP scope without response content', () => {
  const result = samples.previewResponses([{ isActive: false, responseBody: 'secret', filePath: 'never-open' }, { id: 'persisted', isActive: true, conditions: 'old' }], 'GetUser', 1, '{"conditions":[]}')
  assert.equal(result[0].clientKey, 'draft-0'); assert.equal(result[0].active, false); assert.equal(result[1].active, true)
  assert.equal(result[1].conditions, '{"conditions":[]}'); assert.equal(result[1].sortOrder, 1); assert.equal(result[0].soapOperationName, 'GetUser')
  assert.ok(!JSON.stringify(result).includes('secret')); assert.ok(!JSON.stringify(result).includes('filePath'))
})
test('Real preview client uses exactly the read-only endpoint', async () => {
  const source = await (await import('node:fs/promises')).readFile(new URL('../src/api/matchPreview.js', import.meta.url), 'utf8')
  const calls = [], changed = source.replace("import request from './request'", 'const request = globalThis.__previewTransport')
  globalThis.__previewTransport = { post: (...args) => { calls.push(args); return Promise.resolve({}) } }
  const { previewApiMatch } = await import('data:text/javascript;base64,' + Buffer.from(changed).toString('base64'))
  await previewApiMatch({ teamId: 'a' }); assert.equal(calls[0][0], '/apis/preview-match'); delete globalThis.__previewTransport
})
test('Three sample sources coexist and selected response changes preserve them', async () => {
  const c = await panel()
  c.s.sampleInputs.BODY = '{"items":[{"amount":1}]}'; c.s.source.value = 'QUERY'; c.s.sampleInputs.QUERY = '?q=first&q=second'
  c.s.source.value = 'HEADER'; c.s.sampleInputs.HEADER = 'X-Env: test'; await settle()
  c.s.applyPasted(); c.p.selectedResponseIndex = 1; c.p.modelValue = '{"conditions":[]}'; await settle()
  assert.equal(c.s.sampleInputs.BODY, '{"items":[{"amount":1}]}'); assert.equal(c.s.sampleInputs.QUERY, '?q=first&q=second')
  await c.s.runPreview(); assert.equal(c.calls.length, 1); assert.equal(c.calls[0].payload.sample.query.q, 'first'); assert.equal(c.calls[0].payload.sample.headers['X-Env'], 'test')
  assert.equal(c.calls[0].payload.responses[0].active, true); c.unit.unmount()
})
test('Source + path deduplication preserves Header source and existing conditions', async () => {
  const c = await panel(); c.s.source.value = 'HEADER'; c.s.onTreePick('X-Env', 'test', 'STRING'); c.s.onTreePick('x-env', 'other', 'STRING')
  assert.equal(c.events.length, 1); assert.equal(c.s.conditions.value[0].source, 'HEADER')
  c.s.source.value = 'QUERY'; c.s.onTreePick('X-Env', 'query', 'STRING'); assert.equal(c.s.conditions.value.length, 2); c.unit.unmount()
})
test('All six server reasons retain their actual meaning without a client match evaluator', async () => {
  for (const [reason, title, explanation] of [
    ['CONDITION_MATCH', '条件命中', '首个'], ['FALLBACK', '使用兜底', '均未命中'], ['SINGLE_ENABLED', '唯一启用', '不执行条件'],
    ['NO_MATCH', '未命中', '没有可用兜底'], ['NO_ENABLED_RESPONSE', '无启用', '没有可参与'], ['RANDOM_EQUAL_PROBABILITY', '等概率', '不固定命中']
  ]) {
    const c = await panel(props(), async () => ({ reason, mode: reason.startsWith('RANDOM') ? 'RANDOM' : 'CONDITION', enabledCount: 2 }))
    await c.s.runPreview(); assert.ok(c.s.resultTitle.value.includes(title), reason); assert.ok(c.s.resultExplanation.value.includes(explanation), reason); c.unit.unmount()
  }
})
test('Readonly rejects every model mutation but may preview saved configuration', async () => {
  const c = await panel(props({ readOnly: true }), async () => ({ reason: 'SINGLE_ENABLED', mode: 'CONDITION', usingSavedConfiguration: true, matchedIndex: 0 }))
  c.s.onConditionsUpdate([{ path: 'forbidden' }]); c.s.onTreePick('forbidden', 'x', 'STRING'); c.s.applyAiRules({ json: '{"conditions":[{}]}' }); c.s.openAi()
  assert.equal(c.events.length, 0); assert.equal(c.s.aiDrawerVisible.value, false)
  await c.s.runPreview(); assert.deepEqual(c.calls[0].payload.responses, []); assert.equal(c.s.previewResult.value.usingSavedConfiguration, true)
  c.p.apiId = null; await settle(); await c.s.runPreview(); assert.equal(c.calls.length, 1); c.unit.unmount()
})
test('Team/API identity switches clear samples and fence late result/error even when abort is ignored', async () => {
  for (const key of ['teamId', 'apiId']) {
    const delayed = deferred(), c = await panel(props(), () => delayed.promise)
    c.s.sampleInputs.BODY = '{"a":1}'; c.s.sampleInputs.HEADER = 'X: private'; const pending = c.s.runPreview()
    c.p[key] = 'new-resource'; await settle(); assert.equal(c.s.sampleInputs.BODY, ''); assert.equal(c.s.sampleInputs.HEADER, '')
    delayed.resolve({ reason: 'CONDITION_MATCH', matchedResponseName: 'old-team' }); await pending
    assert.equal(c.s.previewResult.value, null); assert.equal(c.s.previewLoading.value, false); assert.equal(c.calls[0].config.signal.aborted, true); c.unit.unmount()
  }
})
test('Readonly revocation and sample edits invalidate pending previews', async () => {
  for (const change of [c => { c.p.readOnly = true }, c => { c.s.sampleInputs.QUERY = 'new=value' }]) {
    const delayed = deferred(), c = await panel(props(), () => delayed.promise); const pending = c.s.runPreview(); change(c); await settle()
    delayed.resolve({ reason: 'CONDITION_MATCH' }); await pending; assert.equal(c.s.previewResult.value, null); c.unit.unmount()
  }
})
test('Invalid conditions JSON remains visibly invalid and is sent unchanged', async () => {
  const c = await panel(props({ modelValue: 'not-json' })); assert.ok(c.s.parseError.value.includes('当前条件 JSON 无效')); assert.equal(c.events.length, 0)
  await c.s.runPreview(); assert.equal(c.calls[0].payload.responses[0].conditions, 'not-json'); c.unit.unmount()
})
test('Condition table never mutates prop objects and exposes numeric operators only for inferred NUMBER', async () => {
  const unit = await loadSetup('frontend/src/components/match/ConditionTable.vue'), row = { source: 'BODY', path: 'items[0].amount', operator: 'EQ', value: '10', valueType: 'NUMBER' }
  const instance = unit.setup({ modelValue: [row], readOnly: false, defaultSource: 'HEADER' }); instance.state.patchAt(0, { value: '12' })
  assert.equal(row.value, '10'); assert.equal(instance.events[0][1][0].valueType, 'NUMBER')
  assert.ok(instance.state.availableOperators(row).some(op => op.value === 'GT')); assert.ok(!instance.state.availableOperators({ valueType: 'STRING' }).some(op => op.value === 'GT')); unit.unmount()
})
test('JSON arrays infer NUMBER while Header/Query flat names remain exact STRING keys', async () => {
  const unit = await loadSetup('frontend/src/components/match/SampleTree.vue')
  const json = unit.setup({ modelValue: '{"items":[{"amount":1}]}', sampleKind: 'json', usedPaths: [], readOnly: false, flatFields: false }).state.rootNode.value
  assert.equal(json.children[0].children[0].children[0].path, 'items[0].amount'); assert.equal(json.children[0].children[0].children[0].valueType, 'NUMBER')
  const flat = unit.setup({ modelValue: '{"X.Env":"1"}', sampleKind: 'json', usedPaths: [], readOnly: false, flatFields: true }).state.rootNode.value
  assert.equal(flat.children[0].path, 'X.Env'); assert.equal(flat.children[0].valueType, 'STRING'); unit.unmount()
})
test('AI drawer denies readonly generation/apply and fences revocation during generation', async () => {
  const result = deferred(), calls = [], unit = await loadSetup('frontend/src/components/ai/AiConditionsDrawer.vue', { '@/api/ai': { generateAiConditions: payload => { calls.push(payload); return result.promise } }, 'element-plus': { ElMessage: messages, ElMessageBox: { confirm: async () => {} } } })
  const p = reactive({ modelValue: true, readOnly: false, teamId: 'a', apiContext: {}, responseName: 'R', conditions: '', bodySample: '', sampleKind: 'json' }), c = unit.setup(p)
  c.state.instruction.value = 'a rule'; const pending = c.state.generate(); p.readOnly = true; await settle(); result.resolve({ rules: [{ valid: true }] }); await pending
  assert.equal(c.state.result.value, null); await c.state.apply(); await c.state.generate(); assert.equal(calls.length, 1); assert.ok(!c.events.some(e => e[0] === 'apply')); unit.unmount()
})
