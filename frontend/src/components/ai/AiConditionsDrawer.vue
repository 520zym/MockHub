<template>
  <el-drawer
    v-model="visible"
    :with-header="false"
    size="600px"
    append-to-body
    destroy-on-close
    :modal="false"
    class="ai-rules-drawer"
  >
    <div class="ai-rules">
      <header class="ai-rules__head">
        <div class="ai-rules__icon">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div class="ai-rules__title-wrap">
          <div class="ai-rules__title">
            AI 生成响应规则
            <span class="ai-beta">Beta</span>
          </div>
          <div class="ai-rules__desc">把自然语言描述转成现有 conditions 结构，应用前可逐条确认</div>
        </div>
        <el-button text circle class="ai-rules__close" @click="visible = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </header>

      <div class="ai-rules__ctx">
        <span class="ctx-chip">{{ responseName }}</span>
        <span class="ctx-chip">{{ bodySample ? 'Body 示例已提供' : '未提供 Body 示例' }}</span>
        <span class="ctx-chip">仅支持 Body / Query</span>
      </div>

      <div class="ai-rules__body">
        <div class="ai-field">
          <div class="ai-field__label">用自然语言描述命中条件</div>
          <el-input
            v-model="instruction"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            resize="none"
            placeholder="例如：当 body 的 userType 是 vip，并且 query 的 env 是 test 时命中"
            @keydown.ctrl.enter.prevent="generate"
            @keydown.meta.enter.prevent="generate"
          />
        </div>

        <div class="ai-rules__hint">
          <el-icon><InfoFilled /></el-icon>
          当前匹配引擎只支持 BODY / QUERY 两类来源；Header 条件暂不支持。
        </div>

        <div class="ai-preview-head">
          <span>规则预览</span>
          <span v-if="result" class="ai-preview-meta">
            {{ result.rules.length }} 条 · 已选 {{ selectedCount }} 条
          </span>
          <span class="ai-preview-spacer"></span>
          <el-button v-if="result && !loading" text size="small" @click="generate">
            <el-icon><Refresh /></el-icon>
            重新生成
          </el-button>
        </div>

        <div class="ai-rule-box">
          <div v-if="loading" class="ai-rule-loading">
            <span class="ai-spinner"></span>
            正在生成规则…
          </div>

          <div v-else-if="error" class="ai-rule-error">
            <el-alert type="error" :closable="false" show-icon :title="error" />
            <el-button size="small" @click="generate">重试</el-button>
          </div>

          <template v-else-if="result">
            <div class="ai-rule-table">
              <div class="ai-rule-head">
                <span></span>
                <span>来源</span>
                <span>字段路径</span>
                <span>运算符</span>
                <span>值</span>
              </div>
              <div
                v-for="(rule, idx) in result.rules"
                :key="idx"
                class="ai-rule-row"
                :class="{ 'ai-rule-row--invalid': !rule.valid }"
              >
                <el-checkbox
                  v-model="checked[idx]"
                  :disabled="!rule.valid"
                  size="small"
                />
                <span class="ai-rule-src">{{ rule.source }}</span>
                <span class="ai-rule-path" :title="rule.path">{{ rule.path }}</span>
                <span class="ai-rule-op">{{ operatorLabel(rule.operator) }}</span>
                <span class="ai-rule-value" :title="rule.value">{{ displayValue(rule) }}</span>
              </div>
            </div>
            <div v-if="invalidRules.length" class="ai-rule-warn">
              <el-icon><Warning /></el-icon>
              <span>{{ invalidRules[0].warning }}</span>
            </div>
            <div v-if="result.warnings && result.warnings.length" class="ai-rule-warn ai-rule-warn--soft">
              <el-icon><InfoFilled /></el-icon>
              <span>{{ result.warnings[0] }}</span>
            </div>
          </template>

          <div v-else class="ai-rule-empty">
            <div class="ai-rule-empty__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-rule-empty__title">描述命中条件</div>
            <div class="ai-rule-empty__desc">生成后可逐条勾选，未通过校验的规则默认不应用</div>
          </div>
        </div>

        <template v-if="result">
          <div class="ai-field__label" style="margin-top: 4px">应用方式</div>
          <div class="ai-apply">
            <button
              type="button"
              class="ai-apply__item"
              :class="{ 'ai-apply__item--on': applyMode === 'append' }"
              @click="applyMode = 'append'"
            >
              <span class="ai-radio" :class="{ 'ai-radio--on': applyMode === 'append' }"></span>
              <span>
                <span class="ai-apply__title">追加到现有规则</span>
                <span class="ai-apply__desc">保留原有 {{ existingCount }} 条，把勾选的规则加在后面</span>
              </span>
            </button>
            <button
              type="button"
              class="ai-apply__item"
              :class="{ 'ai-apply__item--on': applyMode === 'replace' }"
              @click="applyMode = 'replace'"
            >
              <span class="ai-radio" :class="{ 'ai-radio--on': applyMode === 'replace' }"></span>
              <span>
                <span class="ai-apply__title">替换现有规则</span>
                <span class="ai-apply__desc">清空原有 {{ existingCount }} 条后再写入，需再次确认</span>
              </span>
            </button>
          </div>
        </template>
      </div>

      <footer class="ai-rules__foot">
        <span class="ai-rules__note">
          <el-icon><InfoFilled /></el-icon>
          {{ footerNote }}
        </span>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="!result" type="primary" :loading="loading" @click="generate">
          生成规则
        </el-button>
        <el-button v-else type="primary" :disabled="selectedCount === 0" @click="apply">
          应用 {{ selectedCount }} 条规则
        </el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Close, Refresh, InfoFilled, Warning
} from '@element-plus/icons-vue'
import { generateAiConditions } from '@/api/ai'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  teamId: { type: String, default: null },
  apiContext: { type: Object, default: () => ({}) },
  responseName: { type: String, default: '默认返回体' },
  /** 现有 conditions JSON 字符串 */
  conditions: { type: String, default: '' },
  /** Body 示例文本 */
  bodySample: { type: String, default: '' },
  /** BODY 示例类型：json / xml */
  sampleKind: { type: String, default: 'json' }
})

const emit = defineEmits(['update:modelValue', 'apply'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const instruction = ref('')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const checked = ref([])
const applyMode = ref('append')

const selectedCount = computed(() => checked.value.filter(Boolean).length)
const invalidRules = computed(() => (result.value?.rules || []).filter((rule) => !rule.valid))
const existingCount = computed(() => parseExisting().length)

const footerNote = computed(() => {
  if (!result.value) return '生成后可逐条确认'
  const action = applyMode.value === 'append'
    ? '追加到现有 ' + existingCount.value + ' 条之后'
    : '将替换现有 ' + existingCount.value + ' 条'
  return '已选 ' + selectedCount.value + ' 条，' + action
})

watch(() => props.modelValue, (open) => {
  if (open) {
    instruction.value = ''
    loading.value = false
    error.value = ''
    result.value = null
    checked.value = []
    applyMode.value = 'append'
  }
})

function parseExisting() {
  if (!props.conditions) return []
  try {
    const obj = JSON.parse(props.conditions)
    return Array.isArray(obj?.conditions) ? obj.conditions : []
  } catch (e) {
    return []
  }
}

async function generate() {
  if (!props.teamId) {
    ElMessage.warning('请先选择团队')
    return
  }
  if (!instruction.value.trim()) {
    ElMessage.warning('请先描述命中条件')
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await generateAiConditions({
      teamId: props.teamId,
      apiType: props.apiContext?.type || 'REST',
      apiName: props.apiContext?.name || '',
      method: props.apiContext?.method || '',
      path: props.apiContext?.path || '',
      responseName: props.responseName,
      instruction: instruction.value,
      bodySample: props.bodySample || '',
      currentConditions: props.conditions || ''
    })
    checked.value = (result.value.rules || []).map((rule) => !!rule.valid)
  } catch (e) {
    error.value = e?.msg || '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function apply() {
  const rules = (result.value?.rules || [])
    .filter((rule, idx) => checked.value[idx])
    .map((rule) => ({
      source: rule.source,
      path: rule.path,
      operator: rule.operator,
      value: rule.value,
      valueType: rule.valueType
    }))
  if (rules.length === 0) {
    ElMessage.warning('请至少勾选一条规则')
    return
  }
  if (applyMode.value === 'replace' && existingCount.value > 0) {
    try {
      await ElMessageBox.confirm(
        '将清空现有 ' + existingCount.value + ' 条规则后再写入，是否继续？',
        '替换现有规则？',
        { type: 'warning', confirmButtonText: '替换', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
  }
  const existing = applyMode.value === 'append' ? parseExisting() : []
  const merged = existing.concat(rules)
  emit('apply', {
    json: JSON.stringify({ conditions: merged }),
    mode: applyMode.value,
    count: rules.length
  })
  visible.value = false
  ElMessage.success('已应用 ' + rules.length + ' 条规则，保存接口后生效')
}

function operatorLabel(operator) {
  const map = {
    EQ: '等于',
    NE: '不等于',
    CONTAINS: '包含',
    IS_EMPTY: '为空',
    IN: '属于',
    GT: '大于',
    GTE: '大于等于',
    LT: '小于',
    LTE: '小于等于',
    REGEX: '匹配'
  }
  return map[operator] || operator
}

function displayValue(rule) {
  if (rule.operator === 'IS_EMPTY') return '—'
  if (rule.operator === 'IN') return rule.value
  return '"' + rule.value + '"'
}
</script>

<style scoped>
.ai-rules {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.ai-rules__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
}

.ai-rules__icon {
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

.ai-rules__title-wrap {
  flex: 1;
  min-width: 0;
}

.ai-rules__title {
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

.ai-rules__desc {
  font-size: 12.5px;
  color: #A3AED0;
  margin-top: 2px;
}

.ai-rules__close {
  color: #A3AED0;
}

.ai-rules__ctx {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #FBFBFE;
  border-bottom: 1px solid #F1F5F9;
  flex-wrap: wrap;
}

.ctx-chip {
  font-size: 11.5px;
  color: #4A5568;
  background: #F7F8FA;
  border-radius: 6px;
  padding: 2px 8px;
}

.ai-rules__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
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
}

.ai-rules__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: #8A93A8;
  background: #F8FAFC;
  border-radius: 8px;
  padding: 7px 10px;
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

.ai-rule-box {
  border: 1px solid #EDF0F5;
  border-radius: 12px;
  min-height: 190px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.ai-rule-table {
  display: flex;
  flex-direction: column;
}

.ai-rule-head,
.ai-rule-row {
  display: grid;
  grid-template-columns: 30px 62px 1fr 76px 1fr;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
}

.ai-rule-head {
  height: 34px;
  background: #FBFBFD;
  border-bottom: 1px solid #F1F5F9;
  font-size: 11.5px;
  color: #A3AED0;
}

.ai-rule-row {
  min-height: 44px;
  border-bottom: 1px solid #F8FAFC;
  font-size: 12px;
}

.ai-rule-row:last-child {
  border-bottom: none;
}

.ai-rule-row--invalid {
  background: #FFFBF2;
}

.ai-rule-row--invalid .ai-rule-path,
.ai-rule-row--invalid .ai-rule-value {
  color: #A3AED0;
  text-decoration: line-through;
}

.ai-rule-src {
  font-size: 10.5px;
  font-weight: 600;
  color: #4A5568;
  background: #F7F8FA;
  border-radius: 5px;
  text-align: center;
  padding: 1px 0;
}

.ai-rule-path {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  color: #1B2559;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-rule-op {
  font-size: 11px;
  color: #4F46E5;
  background: #EEF0FF;
  border-radius: 5px;
  text-align: center;
  padding: 1px 0;
}

.ai-rule-value {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  color: #4A5568;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-rule-warn {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 11.5px;
  color: #B45309;
  background: #FFFBEB;
  border-top: 1px solid #FDE8B9;
  padding: 8px 12px;
  line-height: 1.6;
}

.ai-rule-warn--soft {
  color: #4A5568;
  background: #F7F8FA;
  border-top-color: #F1F5F9;
}

.ai-rule-loading {
  margin: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
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

@keyframes ai-spin {
  to { transform: rotate(360deg); }
}

.ai-rule-error {
  margin: auto;
  padding: 16px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-rule-empty {
  margin: auto;
  text-align: center;
  padding: 20px;
}

.ai-rule-empty__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  margin: 0 auto 8px;
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  color: var(--ai-accent, #8B5CF6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.ai-rule-empty__title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1B2559;
}

.ai-rule-empty__desc {
  font-size: 11px;
  color: #A3AED0;
  margin-top: 3px;
}

.ai-apply {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.ai-apply__item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  text-align: left;
  border: 1px solid #EDF0F5;
  background: #fff;
  border-radius: 10px;
  padding: 9px 10px;
  cursor: pointer;
  font-family: inherit;
}

.ai-apply__item--on {
  border-color: #C7D2FE;
  background: #FBFCFF;
}

.ai-radio {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid #CBD5E1;
  background: #fff;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-radio--on {
  border: 4px solid #6366F1;
}

.ai-apply__title {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #1B2559;
}

.ai-apply__desc {
  display: block;
  font-size: 11px;
  color: #A3AED0;
  line-height: 1.55;
  margin-top: 1px;
}

.ai-rules__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #F1F5F9;
  background: #FDFDFF;
  flex-shrink: 0;
}

.ai-rules__note {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: #A3AED0;
}

:deep(.el-drawer.ai-rules-drawer) {
  border-radius: 16px 0 0 16px;
}

:deep(.ai-rules-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
</style>