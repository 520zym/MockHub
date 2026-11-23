import { createApp, reactive, computed, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import ConditionPanel from '../../../src/components/match/ConditionPanel.vue'
import { fixtureState } from './request-stub.js'
import 'element-plus/dist/index.css'
import '../../../src/styles/variables.scss'
import '../../../src/styles/element-overrides.scss'
import './fixture.css'
const rule = (source, path, value, valueType = 'STRING') => ({ source, path, operator: 'EQ', value, valueType })
const state = reactive({ teamId: 'team-a', apiId: 'api-a', readOnly: false, mode: 'CONDITION', selected: 0, kind: 'json', operation: null,
  responses: [
    { id: 'alipay', name: '支付宝渠道', isActive: true, responseCode: 200, bodyType: 'TEXT', responseBody: '{"code":0,"message":"ok","data":{"channel":"alipay","status":"PAID"}}', conditions: JSON.stringify({ conditions: [rule('QUERY', 'channel', 'alipay'), rule('HEADER', 'X-Mock-Sandbox', '0')] }) },
    { id: 'sandbox', name: '沙箱', isActive: true, responseCode: 403, bodyType: 'TEXT', responseBody: '{"code":403}', conditions: JSON.stringify({ conditions: [rule('HEADER', 'X-Mock-Sandbox', '1')] }) },
    { id: 'default', name: '默认响应', isActive: true, responseCode: 200, bodyType: 'TEXT', responseBody: '{"code":0,"data":{"status":"PAID"}}', conditions: '' }
  ] })
const edits = ref(0)
window.fixture = { apiStub: true, state, calls: fixtureState.calls,
  setReason(reason) { fixtureState.reason = reason },
  setSaved(value) { fixtureState.saved = value },
  setReadonly(value) { state.readOnly = value },
  setTeam(value) { state.teamId = value; state.apiId = 'api-' + value },
  setMode(value) { state.mode = value },
  select(value) { state.selected = value },
  setKind(value) { state.kind = value; state.operation = value === 'xml' ? 'GetUser' : null },
  hold(value) { fixtureState.hold = value },
  release() { fixtureState.hold = false; fixtureState.deferred.splice(0).forEach(resolve => resolve()) }
}
const Root = {
  components: { ConditionPanel },
  setup() { return { state, edits, selected: computed(() => state.responses[state.selected]), update(value) { if (state.readOnly) throw new Error('Readonly model mutation'); state.responses[state.selected].conditions = value } } },
  template: '<main><header class="fixture-heading"><div><span class="fixture-label">真实 Vue / Element Plus · API STUB · 非后端联调</span><h1>订单详情 · 匹配规则</h1><p>GET /api/pay/order · 现有规则能力，不新增业务路由</p></div><span class="fixture-edit-count">查看返回体事件 <b id="edit-count">{{ edits }}</b></span></header><ConditionPanel :model-value="selected.conditions" :response-name="selected.name" :team-id="state.teamId" :api-id="state.apiId" :responses="state.responses" :selected-response-index="state.selected" :selection-mode="state.mode" :operation-name="state.operation" :body-sample-kind="state.kind" :read-only="state.readOnly" @update:model-value="update" @edit-response="edits++"><template #return-preview><p class="return-meta">{{ selected.responseCode }} · application/json · 延迟 0 ms</p><pre class="response-template">{{ selected.responseBody }}</pre></template></ConditionPanel></main>'
}
createApp(Root).use(createPinia()).use(ElementPlus, { locale: zhCn }).mount('#app')
