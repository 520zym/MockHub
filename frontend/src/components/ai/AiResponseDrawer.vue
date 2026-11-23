<template>
  <el-drawer
    v-model="visible"
    :with-header="false"
    size="min(600px, 100vw)"
    title="AI 生成响应体"
    @close="onDrawerClose"
    append-to-body
    destroy-on-close
    :modal="false"
    class="ai-generate-drawer"
  >
    <div class="ai-panel">
      <header class="ai-panel__head">
        <div class="ai-panel__icon">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div class="ai-panel__title-wrap">
          <div class="ai-panel__title">
            AI 生成响应体
            <span class="ai-beta">Beta</span>
          </div>
        </div>
        <el-button text circle class="ai-panel__close" aria-label="关闭响应生成" @click="visible = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </header>

      <div class="ai-panel__ctx">
        <span v-if="apiContext.method" class="ctx-method">{{ apiContext.method }}</span>
        <span class="ctx-chip" :title="apiContext.path">{{ apiContext.path || '未填写路径' }}</span>
        <span class="ctx-chip" :title="responseName">{{ responseName }}</span>
        <span class="ctx-chip">{{ formatLabel }}</span>
      </div>

      <div class="ai-panel__body">
        <div class="ai-field">
          <div class="ai-field__label">
            描述你想要的响应
            <span class="ai-field__optional">可选</span>
          </div>
          <el-input
            v-model="instruction"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :placeholder="placeholder"
            :disabled="!canInteract || loading || confirming"
            aria-label="描述你想要的响应"
            resize="none"
            @keydown.ctrl.enter.prevent="generate"
            @keydown.meta.enter.prevent="generate"
          />
        </div>

        <div class="ai-options">
          <div class="ai-option">
            <div class="ai-field__label">生成方式</div>
            <el-radio-group v-model="mode" size="small" :disabled="!canInteract || loading || confirming" aria-label="生成方式">
              <el-radio-button value="NEW">新建内容</el-radio-button>
              <el-radio-button value="COMPLETE">补全现有</el-radio-button>
            </el-radio-group>
          </div>
          <div class="ai-option">
            <div class="ai-field__label">字段规模</div>
            <el-radio-group v-model="size" size="small" :disabled="!canInteract || loading || confirming" aria-label="字段规模">
              <el-radio-button value="BRIEF">精简</el-radio-button>
              <el-radio-button value="STANDARD">标准</el-radio-button>
              <el-radio-button value="RICH">丰富</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="ai-preview-head">
          <span>生成结果</span>
          <span v-if="result" class="ai-preview-meta">{{ formatLabel }} · {{ lines.length }} 行</span>
          <span class="ai-preview-spacer"></span>
          <el-button
            v-if="result && !loading"
            text
            size="small"
            :loading="loading"
            :disabled="!canGenerate"
            @click="generate"
          >
            <el-icon><Refresh /></el-icon>
            重新生成
          </el-button>
        </div>

        <div class="ai-preview" :class="{ 'ai-preview--empty': !result && !loading && !error }">
          <div v-if="loading" class="ai-skeleton">
            <div class="ai-skeleton__head">
              <span class="ai-skeleton__dot"></span>
              <span class="ai-skeleton__bar" style="width: 44%"></span>
            </div>
            <span v-for="n in 6" :key="n" class="ai-skeleton__bar" :style="{ width: (50 + (n * 7) % 42) + '%' }"></span>
            <div class="ai-skeleton__hint">
              <el-icon class="ai-spinner"><Loading /></el-icon>
              正在生成{{ formatLabel }}…
            </div>
          </div>

          <div v-else-if="error" class="ai-error">
            <el-alert type="error" :closable="false" show-icon :title="error" />
            <el-button size="small" :disabled="!canGenerate" @click="generate">重试</el-button>
          </div>

          <div v-else-if="result" class="ai-code">
            <div class="ai-code__toolbar">
              <span class="ai-code__hint">已生成，确认后插入</span>
              <el-button text size="small" @click="copyContent">
                <el-icon><DocumentCopy /></el-icon>
                复制
              </el-button>
            </div>
            <div class="ai-code__body" tabindex="0" role="region" aria-label="完整生成内容">
              <div v-for="(line, idx) in lines" :key="idx" class="ai-code__line">
                <span class="ai-code__ln">{{ idx + 1 }}</span>
                <span class="ai-code__text">{{ line }}</span>
              </div>
            </div>
          </div>

          <div v-else class="ai-empty">
            <div class="ai-empty__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-empty__title">描述你想要的响应</div>
          </div>
        </div>

        <div v-if="result && result.warnings && result.warnings.length" class="ai-warnings">
          <el-alert
            v-for="(warning, idx) in result.warnings"
            :key="idx"
            type="warning"
            :closable="false"
            show-icon
            :title="warning"
          />
        </div>

        <div v-if="result" class="ai-result-meta">
          <el-icon><CircleCheck /></el-icon>
          由 {{ result.model || 'AI' }} 生成 · 仅作为建议，插入前请检查内容
        </div>
      </div>

      <footer class="ai-panel__foot">
        <span class="ai-panel__note">
          <el-icon><Lock /></el-icon>
          {{ readOnly ? '只读状态，无法生成或插入' : '确认前不会写入编辑器' }}
        </span>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="!result" type="primary" :loading="loading" :disabled="!canGenerate" @click="generate">
          生成
        </el-button>
        <el-button v-else type="primary" :loading="confirming" :disabled="!canInsert" @click="insert">
          插入到编辑器
        </el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Close, Refresh, DocumentCopy, CircleCheck, Lock, Loading
} from '@element-plus/icons-vue'
import request from '@/api/request'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
  teamId: { type: String, default: null },
  /** { type, name, method, path } */
  apiContext: { type: Object, default: () => ({}) },
  /** 当前返回体 { name, contentType, responseBody } */
  response: { type: Object, default: null },
  /** SOAP operation 名称 */
  operationName: { type: String, default: null }
})

const emit = defineEmits(['update:modelValue', 'insert'])
const closedLocally = ref(false)
const visible = computed({
  get: () => props.modelValue && !closedLocally.value,
  set: (value) => {
    closedLocally.value = !value
    if (!value) invalidate()
    emit('update:modelValue', value)
  }
})

const instruction = ref('')
const mode = ref('NEW')
const size = ref('STANDARD')
const loading = ref(false)
const confirming = ref(false)
const error = ref('')
const result = ref(null)
let epoch = 0, disposed = false, activeRequest = null, resultContext = null

const responseName = computed(() => props.response?.name || '默认返回体')
const isXml = computed(() => props.apiContext?.type === 'SOAP' || (props.response?.contentType || '').toLowerCase().includes('xml'))
const formatLabel = computed(() => {
  if (isXml.value) return 'XML'
  const contentType = (props.response?.contentType || '').toLowerCase()
  return !contentType || contentType.includes('json') ? 'JSON' : '文本'
})
// No truncation, reformatting or parsing: preview, clipboard and insert share the entire body.
const lines = computed(() => (result.value?.content || '').split('\n'))
const placeholder = computed(() => {
  if (isXml.value) return props.apiContext?.type === 'SOAP'
    ? '例如：返回操作结果、订单号与处理时间，使用 SOAP Envelope'
    : '例如：返回订单号、状态与处理时间，使用 XML 结构'
  return formatLabel.value === '文本'
    ? '例如：返回一段简洁的订单处理结果说明'
    : '例如：返回用户详情，包含昵称、头像、等级和注册时间；等级从普通 / VIP / SVIP 中取一个'
})
const canInteract = computed(() => visible.value && !props.readOnly)
const canGenerate = computed(() => canInteract.value && !!props.teamId && !loading.value && !confirming.value)
const canInsert = computed(() => canGenerate.value && !!result.value?.content && !!resultContext && current(resultContext))
const contextKey = () => JSON.stringify([props.teamId, props.apiContext, props.response, props.operationName])
function snapshot() { return { epoch, context: contextKey() } }
function current(ctx) { return !disposed && canInteract.value && !!props.teamId && ctx.epoch === epoch && ctx.context === contextKey() }
function invalidate() {
  epoch++
  // Cancellation only stops local transport; it cannot roll back server-side work.
  activeRequest?.abort()
  activeRequest = null
  resultContext = null
  result.value = null
  error.value = ''
  loading.value = false
  confirming.value = false
}
function resetForm() {
  invalidate()
  instruction.value = ''
  mode.value = 'NEW'
  size.value = 'STANDARD'
}
function onDrawerClose() {
  // Element Plus also closes via Escape; fence requests at transition start, not after it.
  if (!closedLocally.value) visible.value = false
}
watch(() => props.modelValue, (open) => {
  closedLocally.value = !open
  resetForm()
}, { flush: 'sync' })
watch([() => props.readOnly, contextKey, () => props.apiContext, () => props.response], resetForm, { flush: 'sync' })
watch([instruction, mode, size], invalidate, { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; invalidate() })

async function generate() {
  if (disposed || !canInteract.value || loading.value || confirming.value) return
  if (!props.teamId) {
    ElMessage.warning('请先选择团队')
    return
  }
  invalidate()
  const ctx = snapshot(), controller = new AbortController()
  activeRequest = controller
  loading.value = true
  try {
    // Same authenticated client, endpoint, payload and AI timeout as generateAiResponse.
    // The shared wrapper has no signal argument, so cancellation stays local to this drawer.
    const generated = await request.post('/ai/generate-response', {
      teamId: props.teamId,
      apiType: props.apiContext?.type || 'REST',
      apiName: props.apiContext?.name || '',
      method: props.apiContext?.method || '',
      path: props.apiContext?.path || '',
      soapOperationName: props.operationName || null,
      responseName: responseName.value,
      contentType: props.response?.contentType || '',
      existingBody: props.response?.responseBody || '',
      instruction: instruction.value,
      mode: mode.value,
      size: size.value
    }, { timeout: 120000, skipGlobalNetworkError: true, signal: controller.signal })
    if (!current(ctx) || controller.signal.aborted) return
    if (typeof generated?.content !== 'string' || !generated.content) {
      error.value = '模型没有返回内容，请重试'
      return
    }
    resultContext = ctx
    result.value = generated
  } catch (e) {
    if (current(ctx) && !controller.signal.aborted) error.value = e?.response?.data?.msg || e?.msg || e?.message || '生成失败，请稍后重试'
  } finally {
    if (activeRequest === controller) activeRequest = null
    if (current(ctx)) loading.value = false
  }
}

async function insert() {
  if (disposed || !canInsert.value) return
  const ctx = resultContext, generated = result.value, content = generated.content
  const existing = (props.response?.responseBody || '').trim()
  confirming.value = true
  try {
    if (existing && mode.value === 'NEW') {
      try {
        await ElMessageBox.confirm(
          '插入后将覆盖当前返回体的内容，保存接口后才会真正生效。',
          '覆盖现有内容？',
          { type: 'warning', confirmButtonText: '覆盖并插入', cancelButtonText: '取消', customClass: 'ai-response-confirm' }
        )
      } catch {
        return
      }
    }
    if (!current(ctx) || result.value !== generated || result.value.content !== content) return
    // COMPLETE already contains the model-completed full body; never concatenate it again.
    emit('insert', content)
    visible.value = false
    ElMessage.success('已插入到编辑器，保存接口后生效')
  } finally {
    if (current(ctx)) confirming.value = false
  }
}

async function copyContent() {
  const ctx = resultContext, content = result.value?.content
  if (!ctx || !content || !current(ctx)) return
  try {
    await navigator.clipboard.writeText(content)
    if (current(ctx) && result.value?.content === content) ElMessage.success('已复制')
  } catch {
    if (current(ctx) && result.value?.content === content) ElMessage.warning('复制失败，请手动选择内容')
  }
}
</script>

<style scoped>
/* Teleported Element Plus roots need class-scoped global selectors. */
:global(.el-drawer.ai-generate-drawer) {
  max-width: 100vw;
  height: 100vh;
  height: 100dvh;
  border: 0;
  border-radius: 16px 0 0 16px;
  background: var(--app-bg-page, #EEF1F4);
  box-shadow: none;
  overflow: hidden;
}
:global(.el-drawer.ai-generate-drawer .el-drawer__body) {
  display: flex;
  min-height: 0;
  min-width: 0;
  padding: 0;
  overflow: hidden;
}
.ai-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  background: var(--app-bg-page, #EEF1F4);
  color: var(--app-text-primary, #17191D);
}
.ai-panel, .ai-panel * { box-sizing: border-box; }
.ai-panel__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px 20px 16px;
  flex-shrink: 0;
  background: var(--app-bg-surface, #FFFFFF);
}
.ai-panel__icon, .ai-empty__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--app-bg-control, #F4F6F9);
  color: var(--app-accent, #3568D4);
}
.ai-panel__icon { width: 36px; height: 36px; font-size: 19px; }
.ai-panel__title-wrap { flex: 1; min-width: 0; }
.ai-panel__title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 16px; font-weight: 600; }
.ai-beta { padding: 1px 6px; border-radius: 6px; line-height: 18px; font-size: 11px; font-weight: 500; color: var(--app-accent, #3568D4); background: var(--app-bg-control, #F4F6F9); }
.ai-panel__close { color: var(--app-text-secondary, #6F757E); }
.ai-panel__ctx { display: flex; align-items: center; flex-wrap: wrap; flex-shrink: 0; gap: 6px; padding: 8px 20px 14px; background: var(--app-bg-surface, #FFFFFF); }
.ctx-method, .ctx-chip { min-width: 0; max-width: 100%; padding: 3px 8px; border-radius: 6px; font-size: 12px; line-height: 1.5; background: var(--app-bg-control, #F4F6F9); }
.ctx-method { flex-shrink: 0; font-weight: 600; color: var(--app-accent, #3568D4); }
.ctx-chip { color: var(--app-text-regular, #3B4048); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-panel__body {
  flex: 1 1 0%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  overflow: auto;
  overscroll-behavior: contain;
}
.ai-panel__body > * { flex-shrink: 0; min-width: 0; }
.ai-field, .ai-options { padding: 14px; border-radius: 12px; background: var(--app-bg-surface, #FFFFFF); }
.ai-field, .ai-option { display: flex; flex-direction: column; gap: 8px; }
.ai-field__label { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 13px; font-weight: 500; color: var(--app-text-regular, #3B4048); }
.ai-field__optional { font-size: 12px; font-weight: 400; color: var(--app-text-secondary, #6F757E); }
.ai-options { display: flex; gap: 18px 24px; flex-wrap: wrap; }
.ai-option { min-width: 0; }
.ai-preview-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 2px; font-size: 13px; font-weight: 500; color: var(--app-text-regular, #3B4048); }
.ai-preview-meta { font-size: 12px; font-weight: 400; color: var(--app-text-secondary, #6F757E); }
.ai-preview-spacer { flex: 1; }
.ai-preview { display: flex; flex-direction: column; min-height: 240px; border-radius: 12px; background: var(--app-bg-surface, #FFFFFF); overflow: hidden; }
.ai-empty { margin: auto; padding: 28px 16px; text-align: center; }
.ai-empty__icon { width: 44px; height: 44px; margin: 0 auto 12px; border-radius: 12px; font-size: 22px; }
.ai-empty__title { font-size: 14px; font-weight: 500; }
.ai-code { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.ai-code__toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px; background: var(--app-bg-control, #F4F6F9); }
.ai-code__hint { font-size: 12px; color: var(--app-text-secondary, #6F757E); }
.ai-code__body {
  min-width: 0;
  padding: 10px 0;
  max-height: 46vh;
  max-height: 46dvh;
  overflow: auto;
  overscroll-behavior: contain;
  font-family: var(--app-font-mono, 'SF Mono', Menlo, Monaco, Consolas, monospace);
  font-size: 12px;
  line-height: 1.8;
}
.ai-code__line { display: flex; width: max-content; min-width: 100%; padding-right: 14px; }
.ai-code__line:hover { background: var(--app-bg-control, #F4F6F9); }
.ai-code__ln { flex-shrink: 0; min-width: 40px; padding: 0 10px; text-align: right; color: var(--app-text-secondary, #6F757E); user-select: none; font-size: 11px; }
.ai-code__text { white-space: pre; color: var(--app-text-primary, #17191D); }
.ai-skeleton { display: flex; flex-direction: column; gap: 10px; padding: 18px; }
.ai-skeleton__head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.ai-skeleton__dot { width: 20px; height: 20px; border-radius: 6px; background: var(--app-bg-page, #EEF1F4); }
.ai-skeleton__bar { height: 10px; border-radius: 5px; background: var(--app-bg-control, #F4F6F9); }
.ai-skeleton__hint { display: flex; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: var(--app-text-secondary, #6F757E); }
.ai-spinner { color: var(--app-accent, #3568D4); animation: ai-spin .8s linear infinite; }
@keyframes ai-spin { to { transform: rotate(360deg); } }
.ai-error { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; width: 100%; margin: auto; padding: 18px; }
.ai-warnings { display: flex; flex-direction: column; gap: 8px; }
.ai-result-meta { display: flex; align-items: flex-start; gap: 6px; font-size: 12px; line-height: 1.7; color: var(--app-text-secondary, #6F757E); overflow-wrap: anywhere; }
.ai-result-meta .el-icon { flex-shrink: 0; margin-top: 4px; }
.ai-panel__foot { display: flex; align-items: center; justify-content: flex-end; flex-shrink: 0; flex-wrap: wrap; gap: 10px; padding: 14px 20px; background: var(--app-bg-surface, #FFFFFF); }
.ai-panel__note { display: flex; align-items: center; flex: 1; gap: 5px; min-width: 0; font-size: 12px; color: var(--app-text-secondary, #6F757E); }
.ai-panel :deep(.el-button) { border: 0; box-shadow: none; }
.ai-panel :deep(.el-button + .el-button) { margin-left: 0; }
.ai-panel :deep(.el-button:not(.is-text):not(.el-button--primary)) { background: var(--app-bg-control, #F4F6F9); }
.ai-panel :deep(.el-button--primary) { background: var(--app-primary, #17191D); color: #FFFFFF; }
.ai-panel :deep(.el-button--primary:not(.is-disabled):hover) { background: var(--app-primary-hover, #30343B); }
.ai-panel :deep(.el-button.is-disabled) { background: var(--app-bg-disabled, #E9EDF2); color: var(--app-text-disabled, #626B76); }
.ai-panel :deep(.el-button.is-text:not(.is-disabled)) { color: var(--app-accent, #3568D4); }
.ai-panel :deep(.el-textarea__inner) { border: 0; box-shadow: none; background: var(--app-bg-control, #F4F6F9); font-size: 13px; line-height: 1.8; }
.ai-panel :deep(.el-input__count) { background: var(--app-bg-control, #F4F6F9); }
.ai-panel :deep(.el-radio-button__inner) { border: 0; box-shadow: none; background: var(--app-bg-control, #F4F6F9); color: var(--app-text-regular, #3B4048); }
.ai-panel :deep(.el-radio-button.is-active .el-radio-button__inner) { background: var(--app-primary, #17191D); color: #FFFFFF; }
.ai-panel :deep(.el-radio-button.is-disabled .el-radio-button__inner) { background: var(--app-bg-disabled, #E9EDF2); color: var(--app-text-disabled, #626B76); }
.ai-panel :deep(.el-alert) { border: 0; box-shadow: none; }
.ai-panel :deep(.el-button:focus-visible), .ai-panel :deep(.el-textarea__inner:focus-visible), .ai-panel :deep(.el-radio-button__original-radio:focus-visible + .el-radio-button__inner), .ai-code__body:focus-visible { outline: 2px solid var(--app-focus-color, #3568D4); outline-offset: 2px; }
:global(.el-message-box.ai-response-confirm) { max-width: calc(100vw - 32px); border: 0; background: var(--app-bg-surface, #FFFFFF); box-shadow: none; }
@media (max-width: 600px) {
  :global(.el-drawer.ai-generate-drawer) { border-radius: 0; }
  .ai-panel__head { padding: 16px 16px 12px; gap: 10px; }
  .ai-panel__ctx { padding: 6px 16px 12px; }
  .ai-panel__body { padding: 16px; }
  .ai-panel__foot { padding: 12px 16px; }
  .ai-panel__note { flex-basis: 100%; }
}
@media (prefers-reduced-motion: reduce) { .ai-spinner { animation: none; } }
</style>