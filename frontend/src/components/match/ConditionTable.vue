<template>
  <div class="condition-table">
    <div class="table-header">
      <span class="hint">条件（AND · 共 {{ conditions.length }} 条）</span>
    </div>

    <div class="table-body">
      <div v-if="conditions.length === 0" class="empty">
        点击左侧字段或下方「添加条件」来新增
      </div>

      <div
        v-for="(c, idx) in conditions"
        :key="idx"
        class="condition-row"
      >
        <el-input
          v-model="c.path"
          size="small"
          placeholder="字段路径"
          class="col-path"
          @input="emitChange"
        />
        <el-select
          v-model="c.operator"
          size="small"
          class="col-op"
          @change="onOperatorChange(c)"
        >
          <el-option
            v-for="opt in availableOperators(c)"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          >
            <span>{{ opt.label }}</span>
            <el-tooltip
              v-if="opt.hint"
              effect="dark"
              :content="opt.hint"
              placement="right"
            >
              <el-icon class="op-hint"><InfoFilled /></el-icon>
            </el-tooltip>
          </el-option>
        </el-select>
        <el-input
          v-if="c.operator !== 'IS_EMPTY'"
          v-model="c.value"
          size="small"
          :placeholder="valuePlaceholder(c)"
          class="col-value"
          @input="emitChange"
        />
        <span v-else class="value-na">—</span>

        <span class="col-type-tag" :class="'t-' + (c.valueType || 'STRING').toLowerCase()">
          {{ (c.valueType || 'STRING').toLowerCase() }}
        </span>

        <el-button
          size="small"
          text
          type="danger"
          class="col-delete"
          @click="removeAt(idx)"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="add-row" @click="addEmpty">
      <el-icon><Plus /></el-icon>
      <span>手动添加条件</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 条件表格组件
 *
 * 展示并编辑一组 MatchCondition（AND 关系）。列：字段路径 / 操作符 / 值 / 类型标签 / 删除。
 *
 * 操作符下拉根据每条条件的 valueType 动态筛选：
 * - NUMBER 类型才展示 GT / GTE / LT / LTE
 * - STRING / BOOLEAN 不展示纯数字操作符
 *
 * v-model 绑定的是 conditions 数组本身；内部修改会 emit update:modelValue。
 */
import { computed } from 'vue'
import { Close, Plus, InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** MatchCondition 数组 */
  modelValue: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const conditions = computed(() => props.modelValue)

// 操作符元数据（中文标签 + 说明）
const ALL_OPERATORS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'CONTAINS', label: '包含', hint: '仅对字符串生效，判断 actual 是否包含 value 子串' },
  { value: 'IS_EMPTY', label: '为空', hint: '当请求中该字段不存在、为 null、空字符串或空数组时命中' },
  { value: 'IN', label: '在列表中', hint: 'value 写成 JSON 数组：["a","b","c"]' },
  { value: 'GT', label: '大于', numberOnly: true },
  { value: 'GTE', label: '大于等于', numberOnly: true },
  { value: 'LT', label: '小于', numberOnly: true },
  { value: 'LTE', label: '小于等于', numberOnly: true },
  { value: 'REGEX', label: '匹配正则', hint: '标准 Java 正则，使用 find() 语义（部分匹配即命中）' }
]

function availableOperators(c) {
  const isNumber = c.valueType === 'NUMBER'
  return ALL_OPERATORS.filter((op) => !op.numberOnly || isNumber)
}

function valuePlaceholder(c) {
  if (c.operator === 'IN') return 'JSON 数组，如 ["a","b"]'
  if (c.operator === 'REGEX') return '正则，如 ^\\d+$'
  return '预期值'
}

function addEmpty() {
  const next = [...conditions.value, {
    source: 'BODY',
    path: '',
    operator: 'EQ',
    value: '',
    valueType: 'STRING'
  }]
  emit('update:modelValue', next)
}

function removeAt(idx) {
  const next = [...conditions.value]
  next.splice(idx, 1)
  emit('update:modelValue', next)
}

function emitChange() {
  // 内部直接改 modelValue 元素；emit 让父组件感知并触发持久化
  emit('update:modelValue', [...conditions.value])
}

/** 切换操作符时联动 valueType：数字操作符强制 NUMBER */
function onOperatorChange(c) {
  const op = ALL_OPERATORS.find((o) => o.value === c.operator)
  if (op && op.numberOnly) {
    c.valueType = 'NUMBER'
  }
  emitChange()
}
</script>

<style scoped>
.condition-table {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
}
.table-header {
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 8px;
}
.table-body {
  max-height: 260px;
  overflow-y: auto;
}
.empty {
  padding: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
}
.condition-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
}
.col-path { flex: 1.2; }
.col-op { flex: 0.9; }
.col-value { flex: 1; }
.col-type-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: #f3f4f6;
  color: #6b7280;
  white-space: nowrap;
}
.col-type-tag.t-number { background: #fef3c7; color: #b45309; }
.col-type-tag.t-boolean { background: #dbeafe; color: #0369a1; }
.col-type-tag.t-string { background: #ede9fe; color: #7c3aed; }
.col-delete { padding: 4px; }
.value-na {
  flex: 1;
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}
.add-row {
  margin-top: 4px;
  padding: 8px;
  text-align: center;
  color: #6366f1;
  font-size: 12px;
  border: 1px dashed #c7d2fe;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.add-row:hover {
  background: #eef2ff;
}
.op-hint {
  margin-left: 4px;
  color: #9ca3af;
  font-size: 12px;
}
</style>
