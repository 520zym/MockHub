<template>
  <div class="condition-table">
    <div class="table-scroll" role="region" aria-label="AND 条件表格，可横向滚动" tabindex="0">
      <div class="condition-grid table-head" aria-hidden="true"><span>来源</span><span>字段路径 / 名称</span><span>运算符</span><span>预期值</span><span>类型</span><span></span></div>
      <div v-for="(c, idx) in conditions" :key="idx" class="condition-grid condition-row">
        <el-select :model-value="c.source" :disabled="readOnly" aria-label="条件参数来源" @update:model-value="patchAt(idx, { source: $event })">
          <el-option label="Body" value="BODY" /><el-option label="Query" value="QUERY" /><el-option label="Header" value="HEADER" />
        </el-select>
        <el-input :model-value="c.path" :readonly="readOnly" :placeholder="c.source === 'HEADER' ? 'Header 名称' : '字段路径'" aria-label="条件字段路径" @update:model-value="patchAt(idx, { path: $event })" />
        <el-select :model-value="c.operator" :disabled="readOnly" aria-label="条件运算符" @update:model-value="changeOperator(idx, $event)">
          <el-option v-for="opt in availableOperators(c)" :key="opt.value" :label="opt.label" :value="opt.value" :title="opt.hint || opt.label" />
        </el-select>
        <el-input v-if="c.operator !== 'IS_EMPTY'" :model-value="c.value" :readonly="readOnly" :placeholder="valuePlaceholder(c)" aria-label="条件预期值" @update:model-value="patchAt(idx, { value: $event })" />
        <span v-else class="value-na" title="为空操作符不使用预期值">—</span>
        <span class="col-type-tag" :title="'示例推断类型：' + (c.valueType || 'STRING')">{{ (c.valueType || 'STRING').toLowerCase() }}</span>
        <button v-if="!readOnly" type="button" class="delete-row" :aria-label="'删除第 ' + (idx + 1) + ' 条条件'" @click="removeAt(idx)">×</button><span v-else></span>
      </div>
    </div>
    <button v-if="!readOnly" type="button" class="add-row" :disabled="conditions.length >= 100" @click="addEmpty"><span aria-hidden="true">＋</span> 手动添加条件</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, readOnly: { type: Boolean, default: false }, defaultSource: { type: String, default: 'BODY' } })
const emit = defineEmits(['update:modelValue'])
const conditions = computed(() => props.modelValue)
const ALL_OPERATORS = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'CONTAINS', label: '包含', hint: 'actual 包含 value 子串' },
  { value: 'IS_EMPTY', label: '为空', hint: '不存在、null、空字符串、空数组或空对象' },
  { value: 'IN', label: '在列表中', hint: '预期值为 JSON 数组' },
  { value: 'GT', label: '大于', numberOnly: true }, { value: 'GTE', label: '大于等于', numberOnly: true },
  { value: 'LT', label: '小于', numberOnly: true }, { value: 'LTE', label: '小于等于', numberOnly: true },
  { value: 'REGEX', label: '匹配正则', hint: '标准 Java 正则，find() 部分匹配语义' }
]
function availableOperators(c) { return ALL_OPERATORS.filter(op => !op.numberOnly || c.valueType === 'NUMBER') }
function valuePlaceholder(c) { return c.operator === 'IN' ? 'JSON 数组，如 ["a","b"]' : c.operator === 'REGEX' ? 'Java 正则表达式' : '预期值' }
function addEmpty() {
  if (props.readOnly || conditions.value.length >= 100) return
  emit('update:modelValue', [...conditions.value, { source: ['QUERY', 'HEADER'].includes(props.defaultSource) ? props.defaultSource : 'BODY', path: '', operator: 'EQ', value: '', valueType: 'STRING' }])
}
function removeAt(idx) { if (!props.readOnly) emit('update:modelValue', conditions.value.filter((_, index) => index !== idx)) }
function patchAt(idx, patch) {
  if (props.readOnly || !conditions.value[idx]) return
  emit('update:modelValue', conditions.value.map((row, index) => index === idx ? { ...row, ...patch } : { ...row }))
}
function changeOperator(idx, value) {
  if (props.readOnly || !conditions.value[idx]) return
  if (!availableOperators(conditions.value[idx]).some(op => op.value === value)) return
  patchAt(idx, { operator: value })
}
// Retained handler names for existing readonly regression coverage; never mutate prop objects.
function emitChange() { if (!props.readOnly) emit('update:modelValue', conditions.value.map(row => ({ ...row }))) }
function onOperatorChange(c) { if (!props.readOnly) changeOperator(conditions.value.indexOf(c), c.operator) }
</script>

<style scoped>
.condition-table { min-width: 0; }
.table-scroll { max-width: 100%; overflow: auto; max-height: 360px; scrollbar-width: thin; padding-bottom: 4px; }
.condition-grid { display: grid; grid-template-columns: 86px minmax(155px, 1.3fr) 112px minmax(125px, 1fr) 62px 24px; gap: 8px; align-items: center; min-width: 640px; }
.table-head { color: var(--app-text-secondary); font-size: 11px; padding: 6px 0 10px; }
.condition-row { margin-bottom: 12px; font-size: 12px; }
.condition-row :deep(.el-input__wrapper), .condition-row :deep(.el-select__wrapper) { min-height: 38px; background: #F4F6F9; border: 1px solid transparent; border-radius: 9px; box-shadow: none; padding: 0 10px; }
.condition-row :deep(.el-input__wrapper:focus-within), .condition-row :deep(.el-select__wrapper.is-focused) { border-color: #3568D4; box-shadow: none; }
.condition-row :deep(.el-input__inner), .condition-row :deep(.el-select__selected-item) { color: var(--app-text-primary); font-size: 12px; }
.condition-row :deep(.el-input__inner) { font-family: 'SF Mono', Menlo, Consolas, monospace; }
.col-type-tag { justify-self: start; font: 11px 'SF Mono', Menlo, monospace; color: var(--app-text-accent); background: #edf2fb; border-radius: 5px; padding: 3px 5px; white-space: nowrap; }
.delete-row { border: 0; background: transparent; color: var(--app-text-secondary); cursor: pointer; font-size: 20px; padding: 0; }
.delete-row:hover { color: var(--app-text-danger); }
.add-row { width: 100%; margin-top: 4px; min-height: 38px; border: 0; border-radius: 9px; background: #F4F6F9; color: var(--app-text-accent); font: inherit; font-size: 12px; cursor: pointer; }
.add-row:hover { background: #edf2fb; }
.add-row:disabled { cursor: not-allowed; color: var(--app-text-disabled); }
.value-na { color: var(--app-text-muted); text-align: center; }
</style>
