<template>
  <div v-if="visible" class="tree-node" :style="{ paddingLeft: Math.min(level, 6) * 10 + 'px' }">
    <button v-if="node.kind !== 'leaf'" type="button" class="branch" :aria-expanded="!collapsed[node.path]" @click.stop="emit('toggle', node.path)"><span class="caret">{{ collapsed[node.path] ? '›' : '⌄' }}</span><span class="key">{{ label || (node.kind === 'array' ? 'Array' : 'Object') }}</span><span class="meta">{{ node.size }}</span></button>
    <button v-else type="button" class="leaf" :class="{ used: usedPaths.has(node.path) }" :disabled="readOnly || usedPaths.has(node.path) || !node.path" :title="node.path + ': ' + fullLeafValue" :aria-label="'添加条件 ' + node.path" @click="pick">
      <span class="key">{{ node.key || node.path }}</span><span class="value">{{ truncatedValue }}</span><span class="type-tag">{{ usedPaths.has(node.path) ? '已添加 ✓' : node.valueType.toLowerCase() }}</span>
    </button>
    <template v-if="node.kind !== 'leaf' && !collapsed[node.path]"><SampleTreeNode v-for="child in node.children" :key="child.path + '|' + child.key" :node="child" :level="level + 1" :search="search" :used-paths="usedPaths" :collapsed="collapsed" :read-only="readOnly" @toggle="emit('toggle', $event)" @pick="emit('pick', $event)" /></template>
  </div>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ node: { type: Object, required: true }, level: { type: Number, default: 0 }, search: { type: String, default: '' }, usedPaths: { type: Set, required: true }, collapsed: { type: Object, required: true }, readOnly: { type: Boolean, default: false } })
const emit = defineEmits(['toggle', 'pick'])
const label = computed(() => props.node.key || '')
const fullLeafValue = computed(() => props.node.kind !== 'leaf' ? '' : props.node.value == null ? 'null' : String(props.node.value))
const truncatedValue = computed(() => fullLeafValue.value.length > 40 ? fullLeafValue.value.slice(0, 37) + '…' : fullLeafValue.value)
const visible = computed(() => !props.search || hasMatchingDescendant(props.node, props.search))
function hasMatchingDescendant(node, keyword) { return (node.path || '').toLowerCase().includes(keyword) || (node.children || []).some(child => hasMatchingDescendant(child, keyword)) }
function pick() { if (!props.readOnly && !props.usedPaths.has(props.node.path) && props.node.path) emit('pick', props.node) }
</script>
<style scoped>
.tree-node { min-width: 0; }
.branch, .leaf { display: flex; align-items: center; gap: 7px; width: 100%; border: 0; color: #333a47; background: transparent; font: inherit; text-align: left; padding: 6px; border-radius: 6px; cursor: pointer; }
.branch { color: #8c939e; }
.leaf:hover:not(:disabled), .leaf.used { background: #f0f4fc; }
.leaf:disabled { cursor: default; }
.leaf.used .key { color: #3568D4; }
.key { flex: 0 0 auto; max-width: 55%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.value { min-width: 0; color: #8a919c; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.type-tag { margin-left: auto; flex-shrink: 0; color: #9aa1ab; font: 9px system-ui, sans-serif; }
.caret, .meta { color: #969da8; }
</style>
