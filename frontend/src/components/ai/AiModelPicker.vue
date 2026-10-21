<template>
  <div ref="root" class="model-picker">
    <div ref="controlRef" class="model-control" :class="{ 'model-control--open': open }" @click="toggle">
      <span class="model-value" :class="{ 'model-value--empty': !modelValue }">
        {{ modelValue || '选择模型' }}
      </span>
      <span v-if="sourceLabel" class="model-source">{{ sourceLabel }}</span>
      <el-icon class="model-arrow"><ArrowDown /></el-icon>
    </div>

    <div v-if="open" ref="popRef" class="model-pop" :style="popStyle" @click.stop>
      <div class="model-search">
        <el-icon><Search /></el-icon>
        <input
          ref="searchInput"
          v-model="keyword"
          placeholder="搜索模型 ID"
          @keydown.enter="chooseFirst"
          @keydown.esc="open = false"
        />
      </div>

      <div class="model-list">
        <template v-if="!manualMode">
          <div v-if="filteredRecommended.length" class="model-group">
            <div class="model-group__head">
              <span>推荐模型</span>
              <span>{{ filteredRecommended.length }} 个</span>
            </div>
            <button
              v-for="id in filteredRecommended"
              :key="'rec-' + id"
              type="button"
              class="model-item"
              :class="{ 'model-item--on': id === modelValue }"
              @click="choose(id)"
            >
              <div class="model-item__main">
                <div class="model-item__id">{{ id }}</div>
                <div class="model-item__desc">{{ hintFor(id) }}</div>
              </div>
              <span class="model-tag model-tag--rec">{{ tagFor(id) }}</span>
              <el-icon v-if="id === modelValue" class="model-item__check"><Check /></el-icon>
            </button>
          </div>

          <div v-if="filteredLive.length" class="model-group">
            <div class="model-group__head">
              <span>来自 {{ vendorName || '服务商' }}</span>
              <span>{{ filteredLive.length }} 个</span>
            </div>
            <button
              v-for="item in filteredLive"
              :key="'live-' + item.id"
              type="button"
              class="model-item"
              :class="{ 'model-item--on': item.id === modelValue }"
              @click="choose(item.id)"
            >
              <div class="model-item__main">
                <div class="model-item__id">{{ item.id }}</div>
                <div class="model-item__desc">{{ liveHint(item) }}</div>
              </div>
              <span class="model-tag model-tag--live">服务商</span>
              <el-icon v-if="item.id === modelValue" class="model-item__check"><Check /></el-icon>
            </button>
          </div>

          <div v-if="liveModels.length && !filteredLive.length && !keyword" class="model-state">已从服务商返回 {{ liveModels.length }} 个模型，均已在推荐列表</div>
          <div v-if="loading" class="model-state">正在拉取模型列表…</div>
          <div v-else-if="error" class="model-error">
            <el-icon><Warning /></el-icon>
            <span>{{ error }}</span>
            <button type="button" class="model-retry" @click="fetchModels">重试</button>
          </div>
          <div v-else-if="!filteredRecommended.length && !filteredLive.length" class="model-state">
            没有匹配的模型，试试手动输入
          </div>
        </template>

        <div v-else class="model-manual">
          <div class="model-manual__label">手动输入模型 ID</div>
          <el-input
            v-model="manualModel"
            size="small"
            placeholder="如 my-finetune-v2"
            @keydown.enter="applyManual"
          />
          <div class="model-manual__hint">模型 ID 区分大小写，保存前建议测试连接。</div>
          <div class="model-manual__actions">
            <el-button size="small" type="primary" @click="applyManual">使用此 ID</el-button>
            <el-button size="small" text @click="manualMode = false">取消</el-button>
          </div>
        </div>
      </div>

      <div class="model-foot">
        <button type="button" class="model-foot__btn" :disabled="!canFetch || loading" @click="fetchModels">
          <el-icon><Refresh /></el-icon>
          {{ liveModels.length ? '刷新模型列表' : '从服务商拉取模型' }}
        </button>
        <button type="button" class="model-foot__btn model-foot__btn--muted" @click="startManual">
          手动输入
        </button>
      </div>
      <div v-if="!canFetch" class="model-foot-hint">先保存 API Key 与地址，再拉取服务商模型列表。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDown, Check, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { getAiModels } from '@/api/ai'

const props = defineProps({
  modelValue: { type: String, default: '' },
  recommendations: { type: Array, default: () => [] },
  vendorName: { type: String, default: '' },
  canFetch: { type: Boolean, default: false },
  dirty: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const root = ref(null)
const searchInput = ref(null)
const open = ref(false)
const keyword = ref('')
const liveModels = ref([])
const controlRef = ref(null)
const popRef = ref(null)
const popStyle = ref({})
const loading = ref(false)
const error = ref('')
const manualMode = ref(false)
const manualModel = ref('')

const hints = {
  'deepseek-chat': '通用对话 · 适合生成响应体 / 条件',
  'deepseek-reasoner': '深度推理 · 响应更慢，适合复杂诊断',
  'kimi-k3': '通用对话 · 长上下文',
  'kimi-k2-0905-preview': 'Kimi K2 预览版',
  'MiniMax-M3': '通用对话',
  'MiniMax-M3.1-Flash': '高速版本',
  'glm-4.6': '通用对话 · 编码能力',
  'glm-4.5': '通用对话',
  'glm-4-flash': '免费高速版本',
  'qwen-plus': '通用对话',
  'qwen-max': '高质量版本',
  'gpt-4o-mini': '低成本通用模型',
  'gpt-4o': '多模态通用模型',
  'llama3.1': '本地通用模型',
  'qwen2.5': '本地通义千问',
  'deepseek-r1': '本地推理模型'
}

const sourceLabel = computed(() => {
  if (!props.modelValue) return ''
  if (props.recommendations.includes(props.modelValue)) return '推荐'
  if (liveModels.value.some(item => item.id === props.modelValue)) return '服务商'
  return '自定义'
})

const filteredRecommended = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return props.recommendations.filter(id => !key || id.toLowerCase().includes(key))
})

const filteredLive = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return liveModels.value.filter(item => !props.recommendations.includes(item.id) && (!key || item.id.toLowerCase().includes(key)))
})

function hintFor(id) {
  return hints[id] || '推荐模型'
}

function tagFor(id) {
  const lower = id.toLowerCase()
  return lower.includes('reason') || lower.includes('r1') ? '推理' : '推荐'
}

function liveHint(item) {
  const bits = []
  if (item.contextLength) {
    const k = Math.round(item.contextLength / 1024)
    bits.push(k >= 1024 ? (k / 1024).toFixed(0) + 'M 上下文' : k + 'K 上下文')
  }
  if (item.supportsReasoning) bits.push('推理')
  if (item.supportsImage) bits.push('视觉')
  return bits.length ? bits.join(' · ') : '来自服务商'
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    keyword.value = ''
    manualMode.value = false
    error.value = ''
    await nextTick()
    updatePopPosition()
    if (searchInput.value) searchInput.value.focus()
  }
}

function updatePopPosition() {
  if (!open.value || !controlRef.value) return
  const rect = controlRef.value.getBoundingClientRect()
  const width = Math.max(360, Math.min(rect.width, window.innerWidth - 16))
  const height = popRef.value ? popRef.value.offsetHeight : 380
  let top = rect.bottom + 6
  if (top + height > window.innerHeight - 8) {
    top = Math.max(8, rect.top - height - 6)
  }
  const left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 12))
  popStyle.value = { top: top + 'px', left: left + 'px', width: width + 'px' }
}

function choose(id) {
  emit('update:modelValue', id)
  open.value = false
  keyword.value = ''
}

function chooseFirst() {
  const list = filteredRecommended.value.length ? filteredRecommended.value : filteredLive.value.map(item => item.id)
  if (list.length) choose(list[0])
}

function startManual() {
  manualMode.value = true
  manualModel.value = props.modelValue || ''
}

function applyManual() {
  const id = manualModel.value.trim()
  if (!id) return
  emit('update:modelValue', id)
  open.value = false
  manualMode.value = false
}

async function fetchModels() {
  if (!props.canFetch || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const data = await getAiModels()
    liveModels.value = Array.isArray(data) ? data : []
    if (!liveModels.value.length) error.value = '服务商没有返回可用模型，可手动输入。'
    nextTick(updatePopPosition)
  } catch (e) {
    error.value = e?.response?.data?.msg || e?.message || '获取模型列表失败'
  } finally {
    loading.value = false
  }
}

function onDocumentClick(event) {
  if (!open.value) return
  if (root.value && !root.value.contains(event.target)) {
    open.value = false
  }
}

watch(() => props.vendorName, () => {
  liveModels.value = []
  error.value = ''
  keyword.value = ''
})

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  window.addEventListener('scroll', updatePopPosition, true)
  window.addEventListener('resize', updatePopPosition)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('scroll', updatePopPosition, true)
  window.removeEventListener('resize', updatePopPosition)
})
</script>

<style lang="scss" scoped>
.model-picker {
  position: relative;
  width: 100%;
}

.model-control {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  background: #F7F8FA;
  border: 1px solid #EDF0F5;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color .15s ease, background .15s ease;
}

.model-control:hover,
.model-control--open {
  border-color: #C7D2FE;
  background: #fff;
}

.model-value {
  flex: 1;
  min-width: 0;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 12.5px;
  color: #1B2559;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-value--empty {
  font-family: inherit;
  color: #A3AED0;
}

.model-source {
  flex-shrink: 0;
  height: 18px;
  padding: 0 6px;
  border-radius: 5px;
  background: #EEF0FF;
  color: #4F46E5;
  font-size: 9.5px;
  font-weight: 600;
  line-height: 18px;
}

.model-arrow {
  flex-shrink: 0;
  color: #A3AED0;
  transition: transform .15s ease;
}

.model-control--open .model-arrow {
  transform: rotate(180deg);
}

.model-pop {
  position: fixed;
  z-index: 3000;
  max-height: calc(100vh - 16px);
  background: #fff;
  border: 1px solid #E7EAF3;
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(27, 37, 89, .14);
  z-index: 30;
  overflow: hidden;
}

.model-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #F1F5F9;
  color: #A3AED0;
}

.model-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 12.5px;
  color: #1B2559;
}

.model-search input::placeholder {
  color: #A3AED0;
}

.model-list {
  max-height: 300px;
  overflow: auto;
  padding: 6px 8px;
}

.model-group + .model-group {
  border-top: 1px solid #F8FAFC;
  margin-top: 4px;
  padding-top: 4px;
}

.model-group__head {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px 6px;
  font-size: 10.5px;
  color: #A3AED0;
}

.model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px;
  border: none;
  background: transparent;
  border-radius: 7px;
  text-align: left;
  cursor: pointer;
}

.model-item:hover {
  background: #F7F8FA;
}

.model-item--on {
  background: #F5F6FF;
}

.model-item__main {
  flex: 1;
  min-width: 0;
}

.model-item__id {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #1B2559;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-item__desc {
  margin-top: 1px;
  font-size: 10.5px;
  color: #A3AED0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-tag {
  flex-shrink: 0;
  height: 17px;
  padding: 0 5px;
  border-radius: 4px;
  background: #F1F5F9;
  color: #64748B;
  font-size: 9px;
  font-weight: 600;
  line-height: 17px;
}

.model-tag--rec {
  background: #EEF0FF;
  color: #4F46E5;
}

.model-tag--live {
  background: #ECFDF5;
  color: #0F9D6E;
}

.model-item__check {
  flex-shrink: 0;
  color: #6366F1;
}

.model-state {
  padding: 18px 10px;
  text-align: center;
  font-size: 11.5px;
  color: #A3AED0;
}

.model-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #FFF7ED;
  border: 1px solid #FDE9D7;
  color: #B45309;
  font-size: 11px;
  line-height: 1.6;
}

.model-error span {
  flex: 1;
}

.model-retry {
  border: none;
  background: transparent;
  color: #B45309;
  font-size: 11px;
  text-decoration: underline;
  cursor: pointer;
}

.model-manual {
  padding: 8px;
}

.model-manual__label {
  font-size: 12px;
  font-weight: 600;
  color: #4A5568;
  margin-bottom: 6px;
}

.model-manual__hint {
  margin: 6px 0 10px;
  font-size: 10.5px;
  color: #A3AED0;
}

.model-manual__actions {
  display: flex;
  gap: 8px;
}

.model-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid #F1F5F9;
  background: #FBFBFD;
}

.model-foot__btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #6366F1;
  font-size: 11px;
  cursor: pointer;
}

.model-foot__btn:disabled {
  color: #C0C6D4;
  cursor: not-allowed;
}

.model-foot__btn--muted {
  margin-left: auto;
  color: #4A5568;
}

.model-foot-hint {
  padding: 0 12px 8px;
  background: #FBFBFD;
  font-size: 10.5px;
  color: #A3AED0;
}
</style>