<template>
  <div class="condition-panel" :class="{ collapsed: !expanded }">
    <!-- 顶部条 -->
    <div class="panel-header" @click="expanded = !expanded">
      <span class="title">
        <el-icon class="caret">
          <ArrowDown v-if="expanded" />
          <ArrowRight v-else />
        </el-icon>
        响应规则
      </span>
      <span class="subtitle">所有条件都满足才命中（AND）</span>
      <span class="spacer"></span>
      <span v-if="ruleSummary" class="rule-summary">{{ ruleSummary }}</span>
    </div>

    <!-- 主体 -->
    <div v-if="expanded" class="panel-body">
      <!-- 工具条 -->
      <div class="toolbar">
        <span class="label">参数来源</span>
        <el-radio-group v-model="source" size="small">
          <el-radio-button value="BODY">Body</el-radio-button>
          <el-radio-button value="QUERY">Query</el-radio-button>
        </el-radio-group>

        <el-button size="small" class="from-log-btn" disabled>
          <el-icon><Document /></el-icon>
          从请求日志导入
          <span class="badge">v1.1</span>
        </el-button>

        <el-popover
          :width="460"
          trigger="click"
          placement="bottom"
          v-model:visible="showPasteDialog"
        >
          <template #reference>
            <el-button size="small" type="primary" plain>
              <el-icon><Edit /></el-icon>
              粘贴示例
            </el-button>
          </template>
          <div class="paste-dialog">
            <div class="paste-hint">
              粘贴一段{{ source === 'BODY' ? 'JSON Body' : 'URL 查询串（?a=1&b=2）' }}，解析后在下方树中点击字段即可快速生成条件。
            </div>
            <el-input
              v-model="pastedText"
              type="textarea"
              :rows="8"
              placeholder="在这里粘贴..."
            />
            <div class="paste-actions">
              <el-button size="small" @click="showPasteDialog = false">取消</el-button>
              <el-button size="small" type="primary" @click="applyPasted">
                解析并导入
              </el-button>
            </div>
          </div>
        </el-popover>

        <span class="hint-right">
          <el-icon><InfoFilled /></el-icon>
          规则空时，该返回体作为兜底
        </span>
      </div>

      <!-- 左右分栏 -->
      <div class="split">
        <div class="col-left">
          <SampleTree
            :model-value="sampleText"
            :used-paths="usedPaths"
            @select="onTreePick"
          />
        </div>
        <div class="col-right">
          <ConditionTable
            :model-value="conditions"
            @update:model-value="onConditionsUpdate"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 响应规则折叠面板
 *
 * 对外暴露 v-model="conditionsJson"（JSON 字符串），内部管理：
 * - 展开/折叠状态
 * - 示例文本（粘贴后在左树展示）
 * - 当前参数来源（BODY / QUERY）
 * - conditions 数组（内部持有解析后的数组，变化时 emit JSON 字符串）
 *
 * 点击左树叶子 → 追加一条条件（字段路径 / 值 / 类型预填）。
 */
import { ref, computed, watch } from 'vue'
import { ArrowDown, ArrowRight, Document, Edit, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SampleTree from './SampleTree.vue'
import ConditionTable from './ConditionTable.vue'

const props = defineProps({
  /** conditions JSON 字符串，对应 api_response.conditions 字段；空字符串 / null 表示无规则 */
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

// 本地状态
const expanded = ref(true)
const source = ref('BODY')
const sampleText = ref('')
const showPasteDialog = ref(false)
const pastedText = ref('')

// 从 modelValue 解析出初始条件数组
const conditions = ref(parseConditions(props.modelValue))

// 仅当 modelValue 从外部变化时同步（避免自己 emit 后无限循环）
watch(() => props.modelValue, (v) => {
  const next = parseConditions(v)
  if (JSON.stringify(next) !== JSON.stringify(conditions.value)) {
    conditions.value = next
  }
})

// 用于 SampleTree 的已用路径列表
const usedPaths = computed(() => conditions.value.map((c) => c.path).filter(Boolean))

// 显示在折叠态右侧的规则摘要（如"3 条规则"）
const ruleSummary = computed(() => {
  const n = conditions.value.length
  return n === 0 ? '无规则（兜底）' : `${n} 条规则`
})

function parseConditions(json) {
  if (!json) return []
  try {
    const obj = JSON.parse(json)
    return Array.isArray(obj?.conditions) ? obj.conditions : []
  } catch {
    return []
  }
}

function serializeConditions(list) {
  if (!list || list.length === 0) return ''
  return JSON.stringify({ conditions: list })
}

function onConditionsUpdate(next) {
  conditions.value = next
  emit('update:modelValue', serializeConditions(next))
}

function onTreePick(path, value, valueType) {
  // 避免重复添加：若已有相同 source+path 的条件，不再追加（但值可能需要更新）
  const dup = conditions.value.find((c) => c.source === source.value && c.path === path)
  if (dup) {
    ElMessage.info(`字段 ${path} 已在条件列表中`)
    return
  }
  const next = [...conditions.value, {
    source: source.value,
    path,
    operator: 'EQ',
    value: value == null ? '' : String(value),
    valueType: valueType || 'STRING'
  }]
  onConditionsUpdate(next)
}

function applyPasted() {
  const text = pastedText.value.trim()
  if (!text) {
    showPasteDialog.value = false
    return
  }
  if (source.value === 'BODY') {
    // 尝试解析 JSON；失败给提示
    try {
      JSON.parse(text)
      sampleText.value = text
      showPasteDialog.value = false
    } catch {
      ElMessage.error('无法解析为 JSON，请检查格式')
    }
  } else {
    // QUERY：把 URL 查询串转为一个扁平对象再当 JSON 用
    const obj = queryStringToObject(text)
    if (Object.keys(obj).length === 0) {
      ElMessage.error('未识别出任何查询参数')
      return
    }
    sampleText.value = JSON.stringify(obj, null, 2)
    showPasteDialog.value = false
  }
}

/** 解析查询串：支持 ?a=1&b=2 和 a=1&b=2 两种形式 */
function queryStringToObject(s) {
  let cleaned = s.trim()
  const q = cleaned.indexOf('?')
  if (q >= 0) cleaned = cleaned.substring(q + 1)
  const out = {}
  for (const pair of cleaned.split('&')) {
    if (!pair) continue
    const eq = pair.indexOf('=')
    const k = eq >= 0 ? pair.substring(0, eq) : pair
    const v = eq >= 0 ? pair.substring(eq + 1) : ''
    if (k) out[decodeURIComponent(k)] = decodeURIComponent(v)
  }
  return out
}
</script>

<style scoped>
.condition-panel {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 14px;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
}
.panel-header:hover {
  background: #f3f4f6;
}
.caret {
  color: #6366f1;
  font-size: 14px;
}
.title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #374151;
  font-size: 13px;
}
.subtitle {
  color: #9ca3af;
  font-size: 12px;
}
.spacer { flex: 1; }
.rule-summary {
  font-size: 12px;
  color: #4f46e5;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 10px;
}
.panel-body {
  padding: 0 12px 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.label {
  font-size: 12px;
  color: #6b7280;
}
.from-log-btn {
  position: relative;
}
.badge {
  margin-left: 4px;
  padding: 0 4px;
  font-size: 9px;
  background: #fde68a;
  color: #92400e;
  border-radius: 3px;
}
.hint-right {
  margin-left: auto;
  font-size: 11px;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.split {
  display: flex;
  gap: 12px;
}
.col-left { flex: 0 0 44%; }
.col-right { flex: 1; }
.paste-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.paste-hint {
  font-size: 12px;
  color: #6b7280;
}
.paste-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
