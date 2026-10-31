<template>
  <div class="condition-panel">
    <section class="rules-card">
      <header class="rules-heading">
        <button type="button" class="rules-toggle" :aria-expanded="rulesOpen" @click="rulesOpen = !rulesOpen"><strong>匹配条件</strong><span class="rules-summary" :class="{ 'fallback-notice': !conditions.length && !parseError }">{{ rulesSummary }}</span><span aria-hidden="true">{{ rulesOpen ? '⌃' : '⌄' }}</span></button>
        <div class="rules-tools"><button type="button" class="text-button" @click="openSamples('trial')">试匹配</button><button v-if="!hideHelp" type="button" class="help-button" @click="helpVisible = true">图解用法</button></div>
      </header>
      <div v-show="rulesOpen" class="rules-content">
        <p v-if="parseError" class="inline-error" role="alert">{{ parseError }}</p>
        <div v-if="isRandom" class="mode-notice"><strong>等概率随机</strong><span>条件保留，本模式不参与判断。</span></div>
        <template v-else>
          <p v-if="conditions.length && enabledCount === 1 && (selectedResponse?.isActive ?? selectedResponse?.active)" class="selection-hint" role="status">仅一份启用，条件暂不参与判断</p>
          <ConditionTable v-if="!parseError" :model-value="conditions" :default-source="source" :read-only="readOnly" @update:model-value="onConditionsUpdate">
            <template #actions><button v-if="!readOnly" type="button" class="soft-button from-request" @click="openSamples('build')">从请求生成条件</button><button v-if="!readOnly && aiStore.capabilities.conditions" type="button" class="text-button ai-rules-button" @click="openAi">AI 生成</button></template>
          </ConditionTable>
        </template>
        <section v-if="!hideReturnPreview" class="return-preview"><header><strong>命中后返回</strong><button type="button" class="text-button" @click="emit('edit-response')">{{ readOnly ? '查看返回体' : '编辑返回体' }} →</button></header><slot name="return-preview"><p class="preview-placeholder">在返回内容中填写这份响应。</p></slot></section>
      </div>
    </section>

    <el-dialog v-model="samplesOpen" :title="sampleIntent === 'build' ? '从请求生成条件' : '试匹配'" width="min(860px, calc(100vw - 28px))" top="6vh" append-to-body class="match-sample-dialog">
      <aside class="sample-card" aria-label="请求样例">
        <div class="source-tabs" role="tablist" aria-label="请求样例来源"><button v-for="item in sources" :key="item" type="button" role="tab" :aria-selected="source === item" :class="{ active: source === item }" @click="source = item">{{ sourceLabels[item] }}</button></div>
        <div class="sample-columns">
          <section class="sample-input-column"><div class="sample-label-row"><label :for="sampleInputId">{{ sampleLabel }}</label><button type="button" class="text-button" @click="fillExample">载入示例</button></div>
            <el-input :id="sampleInputId" v-model="sampleInputs[source]" type="textarea" :rows="6" resize="vertical" :placeholder="samplePlaceholder" aria-label="请求样例文本" spellcheck="false" />
            <div class="sample-actions"><button type="button" class="soft-button extract-fields" @click="applyPasted">提取字段 →</button></div>
            <p v-if="sampleError" class="inline-error" role="alert">{{ sampleError }}</p>
          </section>
          <section class="sample-fields-column"><div class="fields-heading"><strong>{{ readOnly || isRandom ? '请求字段' : '点一下，加入当前返回体' }}</strong><span>{{ conditions.length }} 条已选</span></div>
            <SampleTree :model-value="sampleText" :used-paths="usedPaths" :read-only="readOnly || isRandom" :sample-kind="source === 'BODY' ? bodySampleKind : 'json'" :flat-fields="source !== 'BODY'" @select="onTreePick" />
            <p v-if="latestAdded" class="sample-added" role="status">✓ 已添加 {{ latestAdded }}</p>
          </section>
        </div>
        <section class="sample-preview">
          <div class="preview-actions"><strong>试匹配结果</strong><button type="button" class="run-preview" :disabled="previewLoading || !teamId || (readOnly && !apiId)" @click="runPreview">{{ previewLoading ? '正在试配…' : isRandom ? '查看随机候选' : '试匹配' }}</button></div>
          <p v-if="readOnly" class="saved-notice">规则只读 · 按已保存配置试配，未提交草稿不参与</p>
          <p v-if="!teamId || (readOnly && !apiId)" class="inline-error">{{ !teamId ? '请选择团队后试配' : '只读试配需要已保存的接口' }}</p>
          <p v-if="previewError" class="inline-error" role="alert">{{ previewError }}</p>
          <div v-if="previewResult" class="preview-result" :class="{ neutral: !previewResult.matchedIndex && previewResult.matchedIndex !== 0 }" role="status">
            <strong>{{ resultTitle }}</strong><p>{{ resultExplanation }}</p>
            <p v-if="previewResult.responseCode != null" class="result-meta">返回 {{ previewResult.responseCode }} · {{ previewResult.bodyType || 'TEXT' }}</p>
            <ul v-if="previewResult.mode === 'RANDOM' && previewResult.candidates?.length" class="random-candidates"><li v-for="candidate in previewResult.candidates" :key="candidate.index"><span>{{ candidate.responseName || candidate.responseId || '返回体 ' + (candidate.index + 1) }}</span><span>1 / {{ previewResult.enabledCount }}</span></li></ul>
            <p v-if="previewResult.usingSavedConfiguration" class="saved-notice">按已保存配置试配，未提交草稿不参与</p>
          </div>
          <details class="privacy-note"><summary>试匹配范围</summary><p>只选择返回体，不执行 Mock、不写调用日志、不返回正文。可编辑时包含当前草稿，保存接口后才正式生效。</p></details>
        </section>
      </aside>
      <template #footer><el-button type="primary" @click="samplesOpen = false">{{ sampleIntent === 'build' ? '完成，返回编辑' : '返回编辑' }}</el-button></template>
    </el-dialog>
    <MatchGuide v-model="helpVisible" :mode="selectionMode" :body-sample-kind="bodySampleKind" />
    <AiConditionsDrawer v-if="!readOnly" v-model="aiDrawerVisible" :read-only="readOnly" :team-id="teamId" :api-context="apiContext" :response-name="responseName" :conditions="modelValue" :body-sample="sampleInputs.BODY" :sample-kind="bodySampleKind" @apply="applyAiRules" />
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { ElMessage } from 'element-plus'
import SampleTree from './SampleTree.vue'
import ConditionTable from './ConditionTable.vue'
import MatchGuide from './MatchGuide.vue'
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
  operationName: { type: String, default: null },
  // Inline usage (response tab) already shows the body right below, so the summary is noise.
  hideReturnPreview: { type: Boolean, default: false },
  hideHelp: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'edit-response'])
const aiStore = useAiStore(), aiDrawerVisible = ref(false)
// The request workspace is opt-in. Both generation and trial reuse these local samples.
const samplesOpen = ref(false), helpVisible = ref(false), rulesOpen = ref(true), sampleIntent = ref('build'), latestAdded = ref('')
const sampleInputId = 'match-sample-' + (getCurrentInstance()?.uid ?? 'isolated')
const sources = SAMPLE_SOURCES, sourceLabels = { BODY: 'Body', QUERY: 'Query', HEADER: 'Header' }
const source = ref('BODY'), sampleInputs = reactive({ BODY: '', QUERY: '', HEADER: '' })
const parsedSamples = reactive({ BODY: '', QUERY: '', HEADER: '' }), sampleError = ref('')
const parseError = ref(''), conditions = ref(parseConditions(props.modelValue))
const previewResult = ref(null), previewError = ref(''), previewLoading = ref(false)
let requestVersion = 0, controller = null, disposed = false
const isRandom = computed(() => props.selectionMode === 'RANDOM')
const selectedResponse = computed(() => props.responses?.[props.selectedResponseIndex])
const enabledCount = computed(() => props.responses.filter(r => r.isActive ?? r.active).length)
const rulesSummary = computed(() => {
  if (conditions.value.length) return conditions.value.length + ' 条条件'
  return enabledCount.value > 1 && (selectedResponse.value?.isActive ?? selectedResponse.value?.active) ? '兜底' : '无条件'
})
function openSamples(intent) { sampleIntent.value = intent; samplesOpen.value = true }
function fillExample() {
  sampleInputs[source.value] = source.value === 'QUERY' ? '?status=paid&amount=100' : source.value === 'HEADER' ? 'X-Mock-Mode: success' : props.bodySampleKind === 'xml' ? '<Envelope><Body><status>paid</status><amount>100</amount></Body></Envelope>' : '{ "status": "paid", "amount": 100 }'
  applyPasted()
}
const sampleText = computed(() => parsedSamples[source.value])
const usedPaths = computed(() => conditions.value.filter(c => c.source === source.value).map(c => c.path).filter(Boolean))
const sampleLabel = computed(() => source.value === 'QUERY' ? '粘贴 URL 查询串' : source.value === 'HEADER' ? '粘贴 Header JSON 或 Name: value' : props.bodySampleKind === 'xml' ? '粘贴 SOAP / XML Body' : '粘贴 JSON Body')
const samplePlaceholder = computed(() => source.value === 'QUERY' ? '?channel=alipay&orderId=202610280001' : source.value === 'HEADER' ? 'X-Mock-Sandbox: 0' : props.bodySampleKind === 'xml' ? '<Envelope><Body>...</Body></Envelope>' : '{ "channel": "alipay", "amount": 100 }')
const reasonCopy = {
  CONDITION_MATCH: ['条件命中', '由服务端按返回体顺序选择首个命中项。'],
  FALLBACK: ['使用兜底', '带规则的返回体均未命中，使用无规则兜底。'],
  SINGLE_ENABLED: ['唯一启用返回体', '运行时唯一启用直接返回，不执行条件判断。'],
  NO_MATCH: ['未命中', '没有条件命中，也没有可用兜底。'],
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
  } catch { parseError.value = '当前条件 JSON 无效，请修复后再试配。'; return [] }
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
  latestAdded.value = path
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
  invalidatePreview(); aiDrawerVisible.value = false; samplesOpen.value = false; helpVisible.value = false; latestAdded.value = ''; sampleError.value = ''
  for (const key of sources) { sampleInputs[key] = ''; parsedSamples[key] = '' }
}, { flush: 'sync' })
watch(() => props.readOnly, () => { aiDrawerVisible.value = false; invalidatePreview() }, { flush: 'sync' })
watch(() => [props.selectionMode, props.selectedResponseIndex, props.bodySampleKind], () => { aiDrawerVisible.value = false; latestAdded.value = ''; invalidatePreview() })
watch(() => props.responses, invalidatePreview, { deep: true })
for (const key of sources) watch(() => sampleInputs[key], () => { invalidatePreview(); parsedSamples[key] = ''; sampleError.value = '' }, { flush: 'sync' })
watch(source, () => { sampleError.value = '' })
defineExpose({ openSamples })
onMounted(() => { aiStore.load() })
onBeforeUnmount(() => { disposed = true; invalidatePreview() })
</script>

<style scoped>
.condition-panel { min-width: 0; font-size: 13px; color: var(--app-text-primary); }
.rules-card { min-width: 0; }
.rules-heading, .rules-tools, .rules-toggle { display: flex; align-items: center; gap: 9px; }
.rules-heading { justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.rules-toggle { border: 0; background: none; color: inherit; cursor: pointer; padding: 0; min-height: 34px; font: inherit; }
.rules-toggle strong { font-size: 14px; }
.rules-summary { color: var(--app-text-secondary); font-size: 12px; }.rules-content { padding-top: 8px; }
.selection-hint { margin: 0 0 12px; color: var(--app-text-secondary); line-height: 1.6; font-size: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
button { font: inherit; }.text-button,.help-button { border: 0; background: transparent; color: var(--app-text-accent); cursor: pointer; min-height: 34px; padding: 0 5px; font-size: 12px; }
.help-button { border-left: 1px solid var(--app-divider); padding-left: 12px; }.soft-button { min-height: 34px; border: 0; border-radius: 7px; padding: 0 12px; background: var(--app-bg-control); color: var(--app-text-regular); cursor: pointer; font-size: 13px; }.soft-button:hover { background: var(--app-bg-hover); }.ai-rules-button { margin-left: auto; }
.mode-notice { display: flex; flex-wrap: wrap; gap: 12px; padding: 10px; background: var(--app-bg-control); border-radius: 8px; }.mode-notice span { color: var(--app-text-secondary); }
.return-preview { margin-top: 14px; padding: 12px; background: var(--app-bg-control); border-radius: 9px; }.return-preview header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.preview-placeholder { color: var(--app-text-secondary); font-size: 12px; }
.sample-card { min-width: 0; color: var(--app-text-primary); font-size: 13px; }.sample-step-line { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 18px; color: var(--app-text-secondary); font-size: 13px; }.sample-step-line b { color: var(--app-text-accent); margin-right: 4px; }
.source-tabs { display: flex; width: fit-content; max-width: 100%; gap: 4px; border-radius: 8px; padding: 3px; margin-bottom: 16px; background: var(--app-bg-control); }.source-tabs button { border: 0; border-radius: 6px; background: transparent; color: var(--app-text-secondary); cursor: pointer; min-height: 34px; padding: 0 22px; font-size: 13px; }.source-tabs button.active { background: #fff; color: var(--app-text-accent); }
.sample-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; }.sample-input-column,.sample-fields-column { min-width: 0; }.sample-label-row,.fields-heading { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; font-size: 12px; }.fields-heading span { color: var(--app-text-secondary); }
.sample-card :deep(.el-textarea__inner) { min-height: 164px; background: var(--app-bg-control); border: 1px solid transparent; border-radius: 8px; box-shadow: none; color: var(--app-text-regular); font: 12px/1.7 var(--app-font-mono); padding: 10px; }.sample-card :deep(.el-textarea__inner:focus) { border-color: var(--app-accent); }
.sample-fields-column :deep(.tree-body) { max-height: 260px; overflow: auto; }.sample-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; }.sample-actions span { color: var(--app-text-secondary); font-size: 12px; }.sample-added { color: var(--app-text-success); font-size: 12px; overflow-wrap: anywhere; margin: 8px 0 0; }
.sample-preview { border-top: 1px solid var(--app-divider); margin-top: 18px; padding-top: 16px; }.preview-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.preview-actions > div { display: grid; gap: 5px; }.preview-actions span { font-size: 12px; color: var(--app-text-secondary); }.run-preview { border: 0; border-radius: 7px; min-height: 36px; min-width: 100px; padding: 0 14px; background: var(--app-accent); color: #fff; cursor: pointer; font-size: 13px; }.run-preview:disabled { background: var(--app-bg-control); color: var(--app-text-disabled); cursor: not-allowed; }
.inline-error { color: var(--app-text-danger); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }.privacy-note { margin-top: 12px; color: var(--app-text-secondary); font-size: 12px; line-height: 1.7; }.privacy-note summary { cursor: pointer; }.privacy-note p { margin: 6px 0 0; }.saved-notice { color: var(--app-text-warning); font-size: 12px; line-height: 1.6; }
.preview-result { border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-success); padding: 12px; margin-top: 12px; font-size: 13px; }.preview-result.neutral { color: var(--app-text-secondary); }.preview-result p { margin: 5px 0 0; line-height: 1.6; }.random-candidates { list-style: none; padding: 0; margin: 8px 0 0; }.random-candidates li { display: flex; justify-content: space-between; gap: 8px; padding: 4px 0; }
@media(max-width: 600px) { .sample-columns { grid-template-columns: minmax(0, 1fr); gap: 12px; }.sample-step-line { font-size: 12px; gap: 7px; justify-content: flex-start; }.sample-actions { flex-wrap: wrap; }.rules-tools { gap: 4px; }.source-tabs { width: 100%; }.source-tabs button { flex: 1; padding: 0 12px; }.sample-fields-column :deep(.tree-body) { max-height: 180px; } }
</style>
