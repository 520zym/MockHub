<template>
  <el-drawer
    v-model="visible"
    :with-header="false"
    size="600px"
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
          <div class="ai-panel__desc">根据接口信息和你的描述生成内容，确认后才会写入编辑器</div>
        </div>
        <el-button text circle class="ai-panel__close" @click="visible = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </header>

      <div class="ai-panel__ctx">
        <span v-if="apiContext.method" class="ctx-method">{{ apiContext.method }}</span>
        <span class="ctx-chip">{{ apiContext.path || '未填写路径' }}</span>
        <span class="ctx-chip">{{ responseName }}</span>
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
            resize="none"
            @keydown.ctrl.enter.prevent="generate"
            @keydown.meta.enter.prevent="generate"
          />
        </div>

        <div class="ai-options">
          <div class="ai-option">
            <div class="ai-field__label">生成方式</div>
            <el-radio-group v-model="mode" size="small">
              <el-radio-button value="NEW">新建内容</el-radio-button>
              <el-radio-button value="COMPLETE">补全现有</el-radio-button>
            </el-radio-group>
          </div>
          <div class="ai-option">
            <div class="ai-field__label">字段规模</div>
            <el-radio-group v-model="size" size="small">
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
              <span class="ai-spinner"></span>
              正在生成{{ formatLabel }}…
            </div>
          </div>

          <div v-else-if="error" class="ai-error">
            <el-alert type="error" :closable="false" show-icon :title="error" />
            <el-button size="small" @click="generate">重试</el-button>
          </div>

          <div v-else-if="result" class="ai-code">
            <div class="ai-code__toolbar">
              <span class="ai-code__hint">已生成，确认后插入</span>
              <el-button text size="small" @click="copyContent">
                <el-icon><DocumentCopy /></el-icon>
                复制
              </el-button>
            </div>
            <div class="ai-code__body">
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
            <div class="ai-empty__desc">例如：返回用户详情，包含昵称、头像、等级和注册时间</div>
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
          确认前不会写入编辑器
        </span>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="!result" type="primary" :loading="loading" @click="generate">
          生成
        </el-button>
        <el-button v-else type="primary" @click="insert">
          插入到编辑器
        </el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Close, Refresh, DocumentCopy, CircleCheck, Lock
} from '@element-plus/icons-vue'
import { generateAiResponse } from '@/api/ai'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  teamId: { type: String, default: null },
  /** { type, name, method, path } */
  apiContext: { type: Object, default: () => ({}) },
  /** 当前返回体 { name, contentType, responseBody } */
  response: { type: Object, default: null },
  /** SOAP operation 名称 */
  operationName: { type: String, default: null }
})

const emit = defineEmits(['update:modelValue', 'insert'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const instruction = ref('')
const mode = ref('NEW')
const size = ref('STANDARD')
const loading = ref(false)
const error = ref('')
const result = ref(null)

const responseName = computed(() => props.response?.name || '默认返回体')
const isXml = computed(() => {
  if (props.apiContext?.type === 'SOAP') return true
  const contentType = props.response?.contentType || ''
  return contentType.toLowerCase().includes('xml')
})
const formatLabel = computed(() => (isXml.value ? 'XML' : 'JSON'))
const lines = computed(() => (result.value?.content || '').split('\n'))
const placeholder = computed(() => (
  isXml.value
    ? '例如：返回操作结果、订单号与处理时间，使用 SOAP Envelope'
    : '例如：返回用户详情，包含昵称、头像、等级和注册时间；等级从普通 / VIP / SVIP 中取一个'
))

watch(() => props.modelValue, (open) => {
  if (open) {
    instruction.value = ''
    mode.value = 'NEW'
    size.value = 'STANDARD'
    loading.value = false
    error.value = ''
    result.value = null
  }
})

async function generate() {
  if (!props.teamId) {
    ElMessage.warning('请先选择团队')
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await generateAiResponse({
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
    })
    if (!result.value?.content) {
      error.value = '模型没有返回内容，请重试'
    }
  } catch (e) {
    error.value = e?.msg || '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function insert() {
  if (!result.value?.content) return
  const existing = (props.response?.responseBody || '').trim()
  if (existing && mode.value === 'NEW') {
    try {
      await ElMessageBox.confirm(
        '插入后将覆盖当前返回体的内容，保存接口后才会真正生效。',
        '覆盖现有内容？',
        { type: 'warning', confirmButtonText: '覆盖并插入', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
  }
  emit('insert', result.value.content)
  visible.value = false
  ElMessage.success('已插入到编辑器，保存接口后生效')
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(result.value.content)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择内容')
  }
}
</script>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.ai-panel__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
}

.ai-panel__icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ai-accent, #8B5CF6);
  font-size: 19px;
  flex-shrink: 0;
}

.ai-panel__title-wrap {
  flex: 1;
  min-width: 0;
}

.ai-panel__title {
  font-size: 16px;
  font-weight: 700;
  color: #1B2559;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-beta {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--ai-accent, #8B5CF6);
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  border-radius: 6px;
  padding: 0 6px;
  line-height: 18px;
}

.ai-panel__desc {
  font-size: 12.5px;
  color: #A3AED0;
  margin-top: 2px;
}

.ai-panel__close {
  color: #A3AED0;
}

.ai-panel__ctx {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #FBFBFE;
  border-bottom: 1px solid #F1F5F9;
  flex-wrap: wrap;
}

.ctx-method {
  font-size: 11.5px;
  font-weight: 600;
  color: #4F46E5;
  background: rgba(99, 102, 241, 0.12);
  border-radius: 6px;
  padding: 1px 8px;
}

.ctx-chip {
  font-size: 11.5px;
  color: #4A5568;
  background: #F7F8FA;
  border-radius: 6px;
  padding: 2px 8px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ai-field__label {
  font-size: 12.5px;
  font-weight: 600;
  color: #4A5568;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-field__optional {
  font-size: 11px;
  font-weight: 400;
  color: #A3AED0;
}

.ai-options {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.ai-option {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ai-preview-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #4A5568;
  margin-top: 2px;
}

.ai-preview-meta {
  font-size: 11.5px;
  font-weight: 400;
  color: #A3AED0;
}

.ai-preview-spacer {
  flex: 1;
}

.ai-preview {
  border: 1px solid #EDF0F5;
  border-radius: 12px;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.ai-preview--empty {
  background: #FCFCFE;
}

.ai-empty {
  margin: auto;
  text-align: center;
  padding: 24px 16px;
}

.ai-empty__icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  margin: 0 auto 10px;
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  color: var(--ai-accent, #8B5CF6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.ai-empty__title {
  font-size: 13px;
  font-weight: 600;
  color: #1B2559;
}

.ai-empty__desc {
  font-size: 11.5px;
  color: #A3AED0;
  margin-top: 4px;
}

.ai-code {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.ai-code__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 6px 12px;
  border-bottom: 1px solid #F1F5F9;
  background: #FCFCFE;
}

.ai-code__hint {
  font-size: 11px;
  color: #A3AED0;
}

.ai-code__body {
  padding: 8px 0;
  overflow: auto;
  max-height: 46vh;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.8;
}

.ai-code__line {
  display: flex;
  padding-right: 12px;
}

.ai-code__line:hover {
  background: #FAFBFF;
}

.ai-code__ln {
  width: 32px;
  text-align: right;
  padding-right: 10px;
  color: #C7CDD8;
  flex-shrink: 0;
  user-select: none;
  font-size: 10.5px;
}

.ai-code__text {
  white-space: pre;
  color: #1B2559;
}

.ai-skeleton {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-skeleton__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ai-skeleton__dot {
  width: 20px;
  height: 20px;
  border-radius: 7px;
  background: #EEF0F6;
}

.ai-skeleton__bar {
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(90deg, #F1F3F9 25%, #F8FAFC 37%, #F1F3F9 63%);
  background-size: 400% 100%;
  animation: ai-shimmer 1.4s ease infinite;
}

.ai-skeleton__hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 11.5px;
  color: #A3AED0;
}

.ai-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #E0E7FF;
  border-top-color: #6366F1;
  animation: ai-spin 0.8s linear infinite;
}

@keyframes ai-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@keyframes ai-spin {
  to { transform: rotate(360deg); }
}

.ai-error {
  margin: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.ai-warnings {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-result-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: #A3AED0;
}

.ai-panel__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #F1F5F9;
  background: #FDFDFF;
  flex-shrink: 0;
}

.ai-panel__note {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: #A3AED0;
}

:deep(.ai-generate-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

:deep(.el-drawer.ai-generate-drawer) {
  border-radius: 16px 0 0 16px;
}
</style>