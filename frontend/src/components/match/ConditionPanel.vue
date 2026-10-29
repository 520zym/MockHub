<template>
  <div class="condition-panel">
    <section class="rules-card">
      <div class="eyebrow">RESPONSE RULES</div>
      <header class="rules-heading"><h3>{{ responseName }}</h3><span v-if="selectedResponse" class="state-badge">{{ selectedResponse.isActive ?? selectedResponse.active ? '已启用' : '未启用' }}</span></header>
      <p class="card-description">{{ readOnly ? '当前规则只读；可使用请求样例试配已保存配置。' : '只编辑当前返回体的条件，不改动其他返回体。' }}</p>
      <div class="rules-toolbar">
        <button v-if="!readOnly && !isRandom && aiStore.capabilities.conditions" type="button" class="soft-button" @click="openAi"><span aria-hidden="true">⌘</span> AI 生成规则</button>
        <button type="button" class="soft-button unavailable" disabled title="从请求日志导入暂未开放">从日志导入 · 暂未开放</button>
        <span class="rule-count">{{ conditions.length }} 条条件</span>
      </div>
      <p v-if="parseError" class="inline-error" role="alert">{{ parseError }}</p>
      <div v-if="isRandom" class="mode-notice"><h4>等概率随机</h4><p>忽略条件与顺序，从启用返回体中等概率选择一个。</p><p>已配置条件会保留，切回条件匹配后继续使用。</p></div>
      <template v-else>
        <div v-if="conditions.length" class="and-heading"><span>AND</span> 以下条件必须全部满足</div>
        <div v-else-if="!parseError" class="mode-notice fallback-notice"><h4>无条件 · 兜底返回体</h4><p>其他带规则的返回体都未命中时，再使用此返回体。</p><p>多个启用返回体时，条件模式只能保留一个无规则兜底。</p></div>
        <ConditionTable v-if="!parseError" :model-value="conditions" :default-source="source" :read-only="readOnly" @update:model-value="onConditionsUpdate" />
        <p class="type-note">ⓘ 类型由示例字段推断，为只读标签。数字字段才出现大小比较；Header 名称不区分大小写。</p>
      </template>
      <p class="strategy-note">{{ isRandom ? '随机模式不支持权重，也不以条件或返回体顺序决定结果。' : '按返回体顺序选择首个命中项；都未命中时，最后使用无规则兜底。不是按条件数量排序。' }}</p>
      <section class="return-preview"><header><strong>命中后返回</strong><button type="button" class="text-button" @click="emit('edit-response')">{{ readOnly ? '查看返回体' : '编辑返回体' }} →</button></header><slot name="return-preview"><p class="preview-placeholder">响应模板由返回体编辑区提供；试配只返回选择元信息。</p></slot></section>
      <p class="local-note">● {{ readOnly ? '规则只读 · 请求样例不保存' : '未提交草稿仅供本次试配，保存接口后才生效' }}</p>
    </section>

    <aside class="sample-card" aria-label="请求样例">
      <header class="sample-heading"><h3>请求样例</h3><span>仅此页本地使用</span></header>
      <div class="source-tabs" role="tablist" aria-label="请求样例来源"><button v-for="item in sources" :key="item" type="button" role="tab" :aria-selected="source === item" :class="{ active: source === item }" @click="source = item">{{ sourceLabels[item] }}</button></div>
      <label class="sample-label" :for="sampleInputId">{{ sampleLabel }}</label>
      <el-input :id="sampleInputId" v-model="sampleInputs[source]" type="textarea" :rows="7" resize="vertical" :placeholder="samplePlaceholder" aria-label="请求样例文本" spellcheck="false" />
      <div class="sample-actions"><span>Body / Query / Header 同时参与</span><button type="button" class="soft-button" @click="applyPasted">解析示例</button></div>
      <p v-if="sampleError" class="inline-error" role="alert">{{ sampleError }}</p>
      <div class="fields-heading"><span>{{ readOnly || isRandom ? '示例字段（仅查看）' : '点击字段添加条件' }}</span><span>{{ sourceLabels[source] }}</span></div>
      <SampleTree :model-value="sampleText" :used-paths="usedPaths" :read-only="readOnly || isRandom" :sample-kind="source === 'BODY' ? bodySampleKind : 'json'" :flat-fields="source !== 'BODY'" @select="onTreePick" />
      <section class="sample-preview">
        <header><strong>样例试配</strong><span class="server-label">服务端真实选择</span></header>
        <p v-if="readOnly" class="saved-notice">按已保存配置试配，未提交草稿不参与</p>
        <p v-if="!teamId || (readOnly && !apiId)" class="inline-error">{{ !teamId ? '请选择团队后试配' : '只读试配需要已保存的接口' }}</p>
        <button type="button" class="run-preview" :disabled="previewLoading || !teamId || (readOnly && !apiId)" @click="runPreview">{{ previewLoading ? '正在试配…' : isRandom ? '查看随机候选' : '▷ 试匹配' }}</button>
        <p v-if="previewError" class="inline-error" role="alert">{{ previewError }}</p>
        <div v-if="previewResult" class="preview-result" :class="{ neutral: !previewResult.matchedIndex && previewResult.matchedIndex !== 0 }" role="status">
          <strong>{{ resultTitle }}</strong><p>{{ resultExplanation }}</p>
          <p v-if="previewResult.responseCode != null" class="result-meta">返回 {{ previewResult.responseCode }} · {{ previewResult.bodyType || 'TEXT' }}</p>
          <ul v-if="previewResult.mode === 'RANDOM' && previewResult.candidates?.length" class="random-candidates"><li v-for="candidate in previewResult.candidates" :key="candidate.index"><span>{{ candidate.responseName || candidate.responseId || '返回体 ' + (candidate.index + 1) }}</span><span>1 / {{ previewResult.enabledCount }}</span></li></ul>
          <p v-if="previewResult.usingSavedConfiguration" class="saved-notice">按已保存配置试配，未提交草稿不参与</p>
        </div>
        <p class="privacy-note">仅选择返回体，不执行 Mock 请求，不写调用记录。不返回响应正文或文件内容；不提供逐条件执行轨迹。</p>
      </section>
    </aside>
    <AiConditionsDrawer v-if="!readOnly" v-model="aiDrawerVisible" :read-only="readOnly" :team-id="teamId" :api-context="apiContext" :response-name="responseName" :conditions="modelValue" :body-sample="sampleInputs.BODY" :sample-kind="bodySampleKind" @apply="applyAiRules" />
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { ElMessage } from 'element-plus'
import SampleTree from './SampleTree.vue'
import ConditionTable from './ConditionTable.vue'
import AiConditionsDrawer from '@/components/ai/AiConditionsDrawer.vue'
import { useAiStore } from '@/stores/ai'
import { previewApiMatch } from '@/api/matchPreview'
import { SAMPLE_SOURCES, sampleTreeText, buildPreviewSample, previewResponses } from '@/utils/request-samples'

const props = defineProps({
  modelValue: { type: String, default: '' }, readOnly: { type: Boolean, default: false },
  bodySampleKind: { type: String, default: 'json' }, teamId: { type: String, default: null },
  apiContext: { type: Object, default: () => ({}) }, responseName: { type: String, default: '默认返回体' },
  apiId: { type: String, default: null }, responses: { type: Array, default: () => [] },
  selectedResponseIndex: { type: Number, default: 0 }, selectionMode: { type: String, default: 'CONDITION' },
  operationName: { type: String, default: null }
})
const emit = defineEmits(['update:modelValue', 'edit-response'])
const aiStore = useAiStore(), aiDrawerVisible = ref(false)
const sampleInputId = 'match-sample-' + (getCurrentInstance()?.uid ?? 'isolated')
const sources = SAMPLE_SOURCES, sourceLabels = { BODY: 'Body', QUERY: 'Query', HEADER: 'Header' }
const source = ref('BODY'), sampleInputs = reactive({ BODY: '', QUERY: '', HEADER: '' })
const parsedSamples = reactive({ BODY: '', QUERY: '', HEADER: '' }), sampleError = ref('')
const parseError = ref(''), conditions = ref(parseConditions(props.modelValue))
const previewResult = ref(null), previewError = ref(''), previewLoading = ref(false)
let requestVersion = 0, controller = null, disposed = false
const isRandom = computed(() => props.selectionMode === 'RANDOM')
const selectedResponse = computed(() => props.responses?.[props.selectedResponseIndex])
const sampleText = computed(() => parsedSamples[source.value])
const usedPaths = computed(() => conditions.value.filter(c => c.source === source.value).map(c => c.path).filter(Boolean))
const sampleLabel = computed(() => source.value === 'QUERY' ? '粘贴 URL 查询串' : source.value === 'HEADER' ? '粘贴 Header JSON 或 Name: value' : props.bodySampleKind === 'xml' ? '粘贴 SOAP / XML Body' : '粘贴 JSON Body')
const samplePlaceholder = computed(() => source.value === 'QUERY' ? '?channel=alipay&orderId=202610280001' : source.value === 'HEADER' ? 'X-Mock-Sandbox: 0' : props.bodySampleKind === 'xml' ? '<Envelope><Body>...</Body></Envelope>' : '{ "channel": "alipay", "amount": 100 }')
const reasonCopy = {
  CONDITION_MATCH: ['条件命中', '由服务端按返回体顺序选择首个命中项。'],
  FALLBACK: ['使用兜底', '带规则的返回体均未命中，使用无规则兜底。'],
  SINGLE_ENABLED: ['唯一启用返回体', '运行时唯一启用直接返回，不执行条件判断。'],
  NO_MATCH: ['未命中', '没有条件命中，也没有可用的无规则兜底；不会伪造默认返回体。'],
  NO_ENABLED_RESPONSE: ['无启用返回体', '没有可参与选择的返回体，请检查启用状态。'],
  RANDOM_EQUAL_PROBABILITY: ['等概率随机候选', '每个启用返回体等概率参与，条件不执行；此处不固定命中任何一项。']
}
const resultTitle = computed(() => { const r = previewResult.value; if (!r) return ''; const name = r.matchedResponseName || r.matchedResponseId; return (reasonCopy[r.reason]?.[0] || '服务端选择结果') + (name ? ' · ' + name : '') })
const resultExplanation = computed(() => reasonCopy[previewResult.value?.reason]?.[1] || '仅展示服务端返回的选择元信息。')

function parseConditions(json) {
  if (!json) { parseError.value = ''; return [] }
  try {
    const obj = JSON.parse(json)
    if (!obj || Array.isArray(obj) || (obj.conditions != null && !Array.isArray(obj.conditions))) throw new Error()
    parseError.value = ''
    return (obj.conditions || []).filter(Boolean).map(c => ({ ...c }))
  } catch { parseError.value = '当前条件 JSON 无效，请修复后再试配；不会将它视为兜底。'; return [] }
}
function serializeConditions(list) { return list?.length ? JSON.stringify({ conditions: list }) : '' }
function onConditionsUpdate(next) {
  if (props.readOnly || isRandom.value) return
  if (next.length > 100) { ElMessage.warning('每条返回体最多 100 个条件'); return }
  conditions.value = next.map(c => ({ ...c })); parseError.value = ''; invalidatePreview()
  emit('update:modelValue', serializeConditions(conditions.value))
}
function openAi() { if (!props.readOnly && !isRandom.value && aiStore.capabilities.conditions) aiDrawerVisible.value = true }
function applyAiRules(payload) {
  if (props.readOnly || isRandom.value) return
  try { const parsed = JSON.parse(payload.json); if (!Array.isArray(parsed?.conditions)) throw new Error(); onConditionsUpdate(parsed.conditions) }
  catch { ElMessage.error('规则内容解析失败，请重试') }
}
function onTreePick(path, value, valueType) {
  if (props.readOnly || isRandom.value || !path) return
  const duplicate = conditions.value.some(c => c.source === source.value && (source.value === 'HEADER' ? c.path?.toLowerCase() === path.toLowerCase() : c.path === path))
  if (duplicate) { ElMessage.info('该字段已在当前来源的条件中'); return }
  onConditionsUpdate([...conditions.value, { source: source.value, path, operator: 'EQ', value: value == null ? '' : String(value), valueType: valueType || 'STRING' }])
}
function applyPasted() {
  sampleError.value = ''
  try { parsedSamples[source.value] = sampleTreeText(source.value, sampleInputs[source.value], props.bodySampleKind) }
  catch (error) { parsedSamples[source.value] = ''; sampleError.value = error.message || '样例解析失败' }
}
function identity() { return JSON.stringify([props.teamId, props.apiId, props.operationName, props.readOnly]) }
function fingerprint() { return JSON.stringify([identity(), props.selectionMode, props.modelValue, props.responses, sampleInputs.BODY, sampleInputs.QUERY, sampleInputs.HEADER, props.bodySampleKind, props.selectedResponseIndex]) }
function invalidatePreview() { requestVersion++; controller?.abort(); controller = null; previewLoading.value = false; previewResult.value = null; previewError.value = '' }
async function runPreview() {
  invalidatePreview()
  if (!props.teamId || (props.readOnly && !props.apiId)) return
  let payload
  try {
    payload = { teamId: props.teamId, apiId: props.apiId || undefined, operationName: props.operationName || undefined,
      responseMode: props.selectionMode || 'CONDITION',
      responses: props.readOnly ? [] : previewResponses(props.responses || [], props.operationName, props.selectedResponseIndex, props.modelValue || ''),
      sample: buildPreviewSample(sampleInputs, props.bodySampleKind) }
  } catch (error) { previewError.value = error.message || '请求样例无效'; return }
  const version = ++requestVersion, scope = fingerprint(), abort = new AbortController(); controller = abort; previewLoading.value = true
  try {
    const result = await previewApiMatch(payload, { signal: abort.signal })
    if (disposed || version !== requestVersion || scope !== fingerprint()) return
    previewResult.value = result
  } catch (error) {
    if (disposed || version !== requestVersion || scope !== fingerprint() || error.code === 'ERR_CANCELED') return
    previewError.value = error?.msg || (error?.isTimeout ? '试配超时，请重试' : '试配失败，请检查网络或配置后重试')
  } finally { if (version === requestVersion) { previewLoading.value = false; controller = null } }
}
watch(() => props.modelValue, value => { conditions.value = parseConditions(value); invalidatePreview() })
watch(() => [props.teamId, props.apiId, props.operationName], () => {
  invalidatePreview(); aiDrawerVisible.value = false; sampleError.value = ''
  for (const key of sources) { sampleInputs[key] = ''; parsedSamples[key] = '' }
}, { flush: 'sync' })
watch(() => props.readOnly, () => { aiDrawerVisible.value = false; invalidatePreview() }, { flush: 'sync' })
watch(() => [props.selectionMode, props.selectedResponseIndex, props.bodySampleKind], () => { aiDrawerVisible.value = false; invalidatePreview() })
watch(() => props.responses, invalidatePreview, { deep: true })
for (const key of sources) watch(() => sampleInputs[key], () => { invalidatePreview(); parsedSamples[key] = ''; sampleError.value = '' }, { flush: 'sync' })
watch(source, () => { sampleError.value = '' })
onMounted(() => { aiStore.load() })
onBeforeUnmount(() => { disposed = true; invalidatePreview() })
</script>

<style scoped>
.condition-panel { --match-bg: #F4F6F9; --match-text: #17191D; --match-blue: #3568D4; display: grid; grid-template-columns: minmax(0, 1fr) minmax(260px, 300px); gap: 16px; min-width: 0; color: var(--match-text); font-size: 12px; }
.rules-card, .sample-card { min-width: 0; background: #fff; border-radius: 18px; padding: 20px; }
.rules-card { display: flex; flex-direction: column; }
.eyebrow { color: #969ba3; font-size: 10px; letter-spacing: 1.5px; margin-bottom: 13px; }
.rules-heading, .sample-heading, .rules-toolbar, .return-preview > header, .sample-preview > header { display: flex; align-items: center; gap: 10px; min-width: 0; }
h3 { margin: 0; font-size: 17px; font-weight: 650; overflow-wrap: anywhere; }
.state-badge { margin-left: auto; flex-shrink: 0; color: #4c7d6e; background: #edf6f2; border-radius: 6px; padding: 3px 7px; font-size: 10px; }
.card-description { color: #7a7f87; line-height: 1.8; margin: 10px 0 20px; }
.rules-toolbar { flex-wrap: wrap; margin-bottom: 20px; }
.soft-button { border: 0; border-radius: 8px; background: var(--match-bg); padding: 8px 10px; color: #424852; font: inherit; cursor: pointer; white-space: nowrap; }
.soft-button:hover:not(:disabled) { background: #eaf0fa; color: var(--match-blue); }
.soft-button:disabled { color: #9ca2ad; cursor: not-allowed; background: #f8f9fb; }
.rule-count { margin-left: auto; border-radius: 6px; background: var(--match-bg); color: #858b95; padding: 3px 8px; white-space: nowrap; }
.and-heading { display: flex; align-items: center; gap: 8px; color: #7a8089; margin-bottom: 12px; }
.and-heading > span { color: var(--match-blue); background: #edf2fc; padding: 4px 8px; border-radius: 6px; font-size: 11px; }
.mode-notice { background: var(--match-bg); padding: 24px 18px; border-radius: 12px; line-height: 1.8; margin-bottom: 14px; }
.mode-notice h4 { margin: 0 0 10px; font-size: 16px; }
.mode-notice p { margin: 3px 0; color: #7b818b; }
.type-note, .privacy-note { color: #969ba4; line-height: 1.9; font-size: 10px; }
.type-note { margin: 14px 0 18px; }
.strategy-note { margin: 8px 0 18px; color: #7f858f; padding: 12px; background: var(--match-bg); border-radius: 10px; line-height: 1.8; }
.return-preview { margin-top: auto; padding: 14px; background: #F7F8FA; border-radius: 12px; min-width: 0; overflow: hidden; }
.return-preview header { justify-content: space-between; flex-wrap: wrap; margin-bottom: 10px; }
.text-button { border: 0; background: transparent; color: var(--match-blue); cursor: pointer; font: inherit; padding: 0; }
.preview-placeholder { color: #9298a2; line-height: 1.8; }
.local-note { color: #9399a3; font-size: 10px; margin: 16px 0 0; line-height: 1.8; }
.sample-card { display: flex; flex-direction: column; }
.sample-heading { justify-content: space-between; margin-bottom: 20px; }
.sample-heading h3 { font-size: 13px; }
.sample-heading > span { color: #9a9fa8; font-size: 10px; white-space: nowrap; }
.source-tabs { display: flex; border-radius: 10px; background: var(--match-bg); padding: 3px; gap: 3px; margin-bottom: 18px; }
.source-tabs button { flex: 1; border: 0; background: transparent; color: #7c828c; border-radius: 8px; padding: 8px 4px; font: inherit; cursor: pointer; }
.source-tabs button.active { background: #fff; color: var(--match-text); }
.sample-label { color: #9298a1; font-size: 10px; margin-bottom: 8px; line-height: 1.8; }
.sample-card :deep(.el-textarea__inner) { background: var(--match-bg); border: 1px solid transparent; border-radius: 10px; box-shadow: none; min-height: 154px; color: #353a43; font: 11px/1.8 'SF Mono', Menlo, Consolas, monospace; padding: 12px; overflow-wrap: normal; white-space: pre; overflow-x: auto; }
.sample-card :deep(.el-textarea__inner:focus) { border-color: var(--match-blue); }
.sample-actions { display: flex; align-items: center; justify-content: space-between; gap: 5px; margin: 12px 0 18px; }
.sample-actions > span { font-size: 9px; color: #a0a5ae; line-height: 1.5; }
.sample-actions button { font-size: 10px; flex-shrink: 0; }
.fields-heading { display: flex; justify-content: space-between; color: #838993; margin: 0 0 9px; font-size: 11px; }
.sample-preview { margin-top: auto; padding-top: 32px; }
.sample-preview > header { justify-content: space-between; flex-wrap: wrap; margin-bottom: 10px; }
.server-label { font-size: 9px; background: #edf2fb; border-radius: 5px; padding: 3px 5px; color: var(--match-blue); }
.run-preview { width: 100%; border: 0; border-radius: 10px; background: var(--match-text); color: #fff; min-height: 42px; font: inherit; cursor: pointer; }
.run-preview:disabled { background: #9499a3; cursor: not-allowed; }
.inline-error { color: #ad5149; line-height: 1.8; font-size: 11px; overflow-wrap: anywhere; }
.preview-result { background: #edf6f2; color: #487967; border-radius: 10px; padding: 12px; margin-top: 12px; font-size: 11px; overflow-wrap: anywhere; }
.preview-result.neutral { background: var(--match-bg); color: #596477; }
.preview-result p { line-height: 1.8; margin: 5px 0 0; }
.saved-notice { color: #836d45; font-size: 10px; line-height: 1.8; }
.random-candidates { margin: 10px 0 0; padding: 0; list-style: none; }
.random-candidates li { display: flex; justify-content: space-between; gap: 10px; padding: 5px 0; }
.privacy-note { margin: 10px 0 0; }
@media (max-width: 1000px) { .condition-panel { grid-template-columns: minmax(0, 1fr); } .sample-preview { margin-top: 0; padding-top: 22px; } }
@media (max-width: 768px) { .rules-card, .sample-card { padding: 16px; border-radius: 14px; } .condition-panel { gap: 12px; } h3 { font-size: 16px; } }
</style>
