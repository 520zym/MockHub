<template>
  <div ref="conditionRoot" class="condition-table">
    <div v-if="conditions.length" class="table-scroll" role="group" aria-label="所有条件均需满足">
      <div class="condition-grid table-head" aria-hidden="true"><span>请求位置</span><span>字段</span><span>关系</span><span>比较值</span><span></span></div>
      <div v-for="(c, idx) in conditions" :key="idx" class="condition-grid condition-row" role="group" :aria-label="'第 ' + (idx + 1) + ' 条条件'">
        <div class="condition-control source-control"><span class="mobile-label">请求位置</span>
          <el-select :model-value="c.source" :disabled="readOnly" aria-label="条件参数来源" @update:model-value="patchAt(idx, { source: $event })">
            <el-option label="Body" value="BODY" /><el-option label="Query" value="QUERY" /><el-option label="Header" value="HEADER" />
          </el-select>
        </div>
        <div class="condition-control path-control"><span class="mobile-label">字段</span>
          <el-input :model-value="c.path" :readonly="readOnly" :placeholder="pathPlaceholder(c)" aria-label="条件字段路径" spellcheck="false" @update:model-value="patchAt(idx, { path: $event })" />
        </div>
        <div class="condition-control operator-control"><span class="mobile-label">关系</span>
          <el-select :model-value="c.operator" :disabled="readOnly" aria-label="条件运算符" @update:model-value="changeOperator(idx, $event)">
            <el-option v-for="opt in availableOperators(c)" :key="opt.value" :label="opt.label" :value="opt.value" :title="opt.hint || opt.label" />
          </el-select>
        </div>
        <div class="condition-control value-control"><span class="mobile-label">比较值</span>
          <div v-if="c.operator !== 'IS_EMPTY'" class="value-group">
            <el-input :model-value="c.value" :readonly="readOnly" :placeholder="valuePlaceholder(c)" aria-label="条件预期值" spellcheck="false" @update:model-value="patchAt(idx, { value: $event })" />
            <el-select class="value-type" :model-value="(c.valueType || 'STRING').toUpperCase()" :disabled="readOnly" aria-label="条件值类型" title="比较类型；选数字后可使用大于、小于" @update:model-value="changeValueType(idx, $event)">
              <el-option v-for="type in VALUE_TYPES" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
          </div>
          <span v-else class="value-na">不需要填值</span>
        </div>
        <button v-if="!readOnly" type="button" class="delete-row" :aria-label="'删除第 ' + (idx + 1) + ' 条条件'" @click="removeAt(idx)">×</button>
      </div>
    </div>
    <div v-if="!readOnly" class="condition-actions">
      <button type="button" class="add-row" :disabled="conditions.length >= 100" @click="addEmpty"><span aria-hidden="true">＋</span> 添加条件</button>
      <slot name="actions" />
    </div>
    <slot v-else name="actions" />
  </div>
</template>

<script setup>
import { computed, ref, nextTick } from 'vue'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, readOnly: { type: Boolean, default: false }, defaultSource: { type: String, default: 'BODY' } })
const emit = defineEmits(['update:modelValue'])
const conditions = computed(() => props.modelValue), conditionRoot = ref(null)
const VALUE_TYPES = [
  { value: 'STRING', label: '文本' }, { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '布尔' }
]
const ALL_OPERATORS = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'CONTAINS', label: '包含' },
  { value: 'IS_EMPTY', label: '为空', hint: '不存在、null、空字符串、空数组或空对象' },
  { value: 'IN', label: '在列表中', hint: '比较值填 JSON 数组' },
  { value: 'GT', label: '大于', numberOnly: true }, { value: 'GTE', label: '大于等于', numberOnly: true },
  { value: 'LT', label: '小于', numberOnly: true }, { value: 'LTE', label: '小于等于', numberOnly: true },
  { value: 'REGEX', label: '匹配正则', hint: 'Java 正则，部分匹配（find）' }
]
function availableOperators(c) { return ALL_OPERATORS.filter(op => !op.numberOnly || String(c.valueType || 'STRING').toUpperCase() === 'NUMBER') }
function pathPlaceholder(c) { return c.source === 'HEADER' ? '如 X-Token' : c.source === 'QUERY' ? '如 status' : '如 data.status' }
function valuePlaceholder(c) { return c.operator === 'IN' ? '["paid","done"]' : c.operator === 'REGEX' ? 'Java 正则' : c.valueType === 'NUMBER' ? '如 100' : c.valueType === 'BOOLEAN' ? 'true / false' : '如 paid' }
async function addEmpty() {
  if (props.readOnly || conditions.value.length >= 100) return
  emit('update:modelValue', [...conditions.value, { source: ['QUERY', 'HEADER'].includes(props.defaultSource) ? props.defaultSource : 'BODY', path: '', operator: 'EQ', value: '', valueType: 'STRING' }])
  await nextTick()
  const inputs = conditionRoot.value?.querySelectorAll('.path-control input')
  inputs?.[inputs.length - 1]?.focus()
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
function changeValueType(idx, valueType) {
  if (props.readOnly || !conditions.value[idx] || !VALUE_TYPES.some(t => t.value === valueType)) return
  const c = conditions.value[idx], numericOperator = ALL_OPERATORS.find(op => op.value === c.operator)?.numberOnly
  patchAt(idx, { valueType, ...(numericOperator && valueType !== 'NUMBER' ? { operator: 'EQ' } : {}) })
}
// Retained handler names for readonly regression coverage; never mutate prop objects.
function emitChange() { if (!props.readOnly) emit('update:modelValue', conditions.value.map(row => ({ ...row }))) }
function onOperatorChange(c) { if (!props.readOnly) changeOperator(conditions.value.indexOf(c), c.operator) }
</script>

<style scoped>
.condition-table { min-width: 0; container-type: inline-size; }
.table-scroll { max-width: 100%; }
.condition-grid { display: grid; grid-template-columns: 88px minmax(110px, 1fr) 108px minmax(188px, 1.35fr) 28px; gap: 8px; align-items: center; }
.table-head { color: var(--app-text-secondary); font-size: 12px; padding: 0 0 7px; }
.condition-row { margin-bottom: 10px; font-size: 13px; }
.condition-control, .value-group { min-width: 0; }
.mobile-label { display: none; }
.condition-control :deep(.el-input__wrapper), .condition-control :deep(.el-select__wrapper) { min-height: 36px; background: var(--app-bg-control); border: 1px solid transparent; border-radius: 7px; box-shadow: none; padding: 0 9px; }
.condition-control :deep(.el-input__wrapper:focus-within), .condition-control :deep(.el-select__wrapper.is-focused) { border-color: var(--app-accent); box-shadow: none; }
.condition-control :deep(.el-input__inner), .condition-control :deep(.el-select__selected-item) { color: var(--app-text-primary); font-size: 13px; }
.path-control :deep(input), .value-group :deep(input) { font-family: var(--app-font-mono); }
.value-group { display: flex; background: var(--app-bg-control); border-radius: 7px; }
.value-group > .el-input { flex: 1; min-width: 0; }
.value-type { flex: 0 0 68px; width: 68px; }
.value-type :deep(.el-select__wrapper) { padding: 0 6px; background: transparent; border-left: 1px solid var(--app-divider); border-radius: 0 7px 7px 0; }
.value-type :deep(.el-select__selected-item) { color: var(--app-text-secondary); font-size: 12px; }
.delete-row { border: 0; border-radius: 6px; background: transparent; color: var(--app-text-secondary); cursor: pointer; font-size: 21px; padding: 0; width: 28px; height: 36px; }
.delete-row:hover { color: var(--app-text-danger); background: var(--app-bg-control); }
.condition-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.add-row { min-height: 34px; border: 0; border-radius: 7px; background: transparent; color: var(--app-text-accent); padding: 0 10px; font: inherit; font-size: 13px; cursor: pointer; }
.add-row:hover { background: var(--app-bg-control); }
.add-row:disabled { cursor: not-allowed; color: var(--app-text-disabled); }
.value-na { display: flex; align-items: center; min-height: 36px; color: var(--app-text-secondary); font-size: 12px; }
@container (max-width: 610px) {
  .table-head { display: none; }
  .condition-row { grid-template-columns: 88px minmax(0, 1fr) 28px; gap: 9px 7px; padding: 12px; border-radius: 9px; background: var(--app-bg-control); }
  .mobile-label { display: block; font-size: 12px; color: var(--app-text-secondary); margin-bottom: 5px; }
  .operator-control { grid-column: 1; grid-row: 2; }
  .value-control { grid-column: 2 / 4; grid-row: 2; }
  .delete-row { grid-column: 3; grid-row: 1; align-self: end; }
  .condition-control :deep(.el-input__wrapper), .condition-control :deep(.el-select__wrapper), .value-group { background: var(--app-bg-surface); }
}
</style>
