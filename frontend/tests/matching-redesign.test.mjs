import test from 'node:test'
import assert from 'node:assert/strict'
import { reactive } from 'vue'
import { loadSetup } from './helpers/sfc-unit.mjs'
import * as samples from '../src/utils/request-samples.js'
const ai = { capabilities: {}, load: async () => {} }
const messages = { warning() {}, info() {}, error() {} }
const row = (active, conditions = '') => ({ name: '返回体', isActive: active, conditions, responseBody: '{}', bodyType: 'TEXT', contentType: 'application/json' })
const rule = JSON.stringify({ conditions: [{ source: 'QUERY', path: 'status', operator: 'EQ', value: 'paid', valueType: 'STRING' }] })
async function panel(rows, selected = 0) {
  const unit = await loadSetup('frontend/src/components/match/ConditionPanel.vue', { '@/stores/ai': { useAiStore: () => ai }, 'element-plus': { ElMessage: messages }, '@/utils/request-samples': samples })
  const props = reactive({ responses: rows, selectedResponseIndex: selected, modelValue: rows[selected].conditions, selectionMode: 'CONDITION', bodySampleKind: 'json', teamId: 'a', apiId: 'a', readOnly: false })
  return { unit, props, ...unit.setup(props) }
}
test('generation and trial are explicit intents sharing one local sample, never editing rules by opening', async () => {
  const c = await panel([row(true), row(true, rule)], 1)
  assert.equal(c.state.samplesOpen.value, false)
  c.state.openSamples('build'); c.state.sampleInputs.QUERY = '?status=paid'
  c.state.samplesOpen.value = false; c.state.openSamples('trial')
  assert.equal(c.state.sampleIntent.value, 'trial'); assert.equal(c.state.sampleInputs.QUERY, '?status=paid'); assert.equal(c.events.length, 0)
  c.unit.unmount()
})
test('sample examples only populate the sample; adding a field is an explicit mutation', async () => {
  const c = await panel([row(true)])
  c.state.source.value = 'QUERY'; c.state.fillExample()
  assert.match(c.state.sampleInputs.QUERY, /status=paid/); assert.equal(c.events.length, 0)
  c.state.onTreePick('status', 'paid', 'STRING'); assert.equal(c.events.length, 1)
  c.unit.unmount()
})
test('empty-state copy distinguishes disabled, only enabled, and true fallback', async () => {
  for (const [rows, selected, text] of [[[row(false), row(true)], 0, '未启用'], [[row(true), row(false)], 0, '直接返回'], [[row(true), row(true, rule)], 0, '其他返回体都没命中']]) {
    const c = await panel(rows, selected); assert.ok(c.state.selectionHint.value.includes(text)); c.unit.unmount()
  }
})
test('manual type change unlocks numeric comparisons without mutating props or coercing value', async () => {
  const unit = await loadSetup('frontend/src/components/match/ConditionTable.vue')
  const props = reactive({ modelValue: [{ source: 'BODY', path: 'amount', operator: 'EQ', value: '001', valueType: 'STRING' }], readOnly: false })
  const { state: s, events } = unit.setup(props)
  s.changeValueType(0, 'NUMBER'); assert.equal(props.modelValue[0].valueType, 'STRING')
  props.modelValue = events.at(-1)[1]; assert.equal(props.modelValue[0].value, '001'); assert.ok(s.availableOperators(props.modelValue[0]).some(op => op.value === 'GT'))
  s.changeOperator(0, 'GT'); props.modelValue = events.at(-1)[1]
  s.changeValueType(0, 'STRING'); assert.equal(events.at(-1)[1][0].operator, 'EQ')
  assert.ok(s.availableOperators({ valueType: 'number' }).some(op => op.value === 'GT'))
  const count = events.length; props.readOnly = true; s.changeValueType(0, 'BOOLEAN'); assert.equal(events.length, count)
  unit.unmount()
})
async function tabs(rows, mode = 'CONDITION') {
  const confirmations = [], warnings = []
  const unit = await loadSetup('frontend/src/components/ResponseTabs.vue', { '@/stores/ai': { useAiStore: () => ai }, '@/constants/dynamicVariables': { DYNAMIC_VARIABLES: [] }, 'element-plus': { ElMessage: { ...messages, warning: m => warnings.push(m) }, ElMessageBox: { confirm: (...args) => { confirmations.push(args); return Promise.resolve() } } } })
  return { unit, confirmations, warnings, rows, ...unit.setup({ modelValue: rows, selectionMode: mode, readOnly: false, operationName: null, teamId: 'a' }) }
}
test('enabling a second response does not interrupt editing with a text-heavy confirmation', async () => {
  const c = await tabs([row(true), row(false, rule)]); await c.state.toggleEnabled(1)
  assert.equal(c.rows[1].isActive, true); assert.equal(c.confirmations.length, 0); c.unit.unmount()
})
test('random mode has no fallback restriction, but cannot disable its last enabled response', async () => {
  const c = await tabs([row(true), row(true, rule), row(true, rule)], 'RANDOM')
  await c.state.toggleEnabled(0); assert.equal(c.rows[0].isActive, false)
  await c.state.toggleEnabled(1); await c.state.toggleEnabled(2)
  assert.equal(c.rows[2].isActive, true); assert.equal(c.warnings.length, 1); c.unit.unmount()
})
test('condition mode protects fallback only when multiple enabled responses remain', async () => {
  const c = await tabs([row(true), row(true, rule), row(true, rule)])
  await c.state.toggleEnabled(0); assert.equal(c.rows[0].isActive, true)
  await c.state.toggleEnabled(1); await c.state.toggleEnabled(0)
  assert.equal(c.rows[0].isActive, false); c.unit.unmount()
})
test('invalid conditional drafts show one actionable hint, not a modal, and random never requests fallback', async () => {
  const c = await tabs([row(true), row(false)])
  assert.equal(c.state.selectionIssue.value, '')
  await c.state.toggleEnabled(1); assert.match(c.state.selectionIssue.value, /2 份无条件/)
  assert.equal(c.confirmations.length, 0); c.unit.unmount()
  const random = await tabs([row(true), row(true)], 'RANDOM')
  assert.equal(random.state.selectionIssue.value, ''); random.unit.unmount()
})
