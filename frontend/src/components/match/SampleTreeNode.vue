<template>
  <div v-if="visible" class="tree-node" :style="{ paddingLeft: level * 14 + 'px' }">
    <!-- 对象/数组节点 -->
    <div v-if="node.kind !== 'leaf'" class="branch">
      <span class="caret" @click.stop="$emit('toggle', node.path)">
        {{ collapsed[node.path] ? '▶' : '▼' }}
      </span>
      <span class="key">{{ label }}</span>
      <span class="meta">{{ node.kind === 'array' ? '[' : '{' }}{{ node.size }}{{ node.kind === 'array' ? ']' : '}' }}</span>
    </div>

    <!-- 叶子节点 -->
    <div
      v-else
      class="leaf"
      :class="{ used: usedPaths.has(node.path) }"
      :title="fullLeafValue"
      @click="$emit('pick', node)"
    >
      <span class="key">{{ label }}</span>
      <span class="colon">:</span>
      <span class="value" :class="'t-' + node.valueType.toLowerCase()">{{ truncatedValue }}</span>
      <span class="type-tag">{{ node.valueType.toLowerCase() }}</span>
      <span v-if="usedPaths.has(node.path)" class="used-mark">✓</span>
    </div>

    <!-- 子节点 -->
    <template v-if="node.kind !== 'leaf' && !collapsed[node.path]">
      <SampleTreeNode
        v-for="child in node.children"
        :key="child.path + '|' + child.key"
        :node="child"
        :level="level + 1"
        :search="search"
        :used-paths="usedPaths"
        :collapsed="collapsed"
        @toggle="$emit('toggle', $event)"
        @pick="$emit('pick', $event)"
      />
    </template>
  </div>
</template>

<script setup>
/**
 * SampleTree 的递归节点组件。单独文件是为了 Vue 3 递归组件需要 name（或使用 script setup 中自引用）。
 *
 * 负责：
 * - 分支节点显示 ▶/▼ 折叠按钮
 * - 叶子节点点击触发 pick 事件
 * - 搜索过滤：只要后代叶子路径匹配关键字就保持该节点可见
 */
import { computed } from 'vue'

const props = defineProps({
  node: { type: Object, required: true },
  level: { type: Number, default: 0 },
  search: { type: String, default: '' },
  usedPaths: { type: Set, required: true },
  collapsed: { type: Object, required: true }
})

defineEmits(['toggle', 'pick'])

const LONG_VALUE_LIMIT = 40

const label = computed(() => {
  const k = props.node.key || ''
  // 根节点 key 为空，不显示
  if (!k) return ''
  // 数组索引形式 [i] 原样显示；对象键加引号
  return k.startsWith('[') ? k : '"' + k + '"'
})

const fullLeafValue = computed(() => {
  if (props.node.kind !== 'leaf') return ''
  const v = props.node.value
  if (v == null) return 'null'
  return props.node.valueType === 'STRING' ? '"' + v + '"' : String(v)
})

const truncatedValue = computed(() => {
  const s = fullLeafValue.value
  if (s.length <= LONG_VALUE_LIMIT) return s
  return s.substring(0, LONG_VALUE_LIMIT - 3) + '...'
})

// 搜索命中判断：节点可见当且仅当（无搜索）或（自身 path 命中）或（任何后代叶子 path 命中）
const visible = computed(() => {
  const kw = props.search
  if (!kw) return true
  if ((props.node.path || '').toLowerCase().includes(kw)) return true
  return hasMatchingDescendant(props.node, kw)
})

function hasMatchingDescendant(n, kw) {
  if (n.kind === 'leaf') {
    return (n.path || '').toLowerCase().includes(kw)
  }
  if (!n.children) return false
  for (const c of n.children) {
    if (hasMatchingDescendant(c, kw)) return true
  }
  return false
}
</script>

<style scoped>
.tree-node {
  position: relative;
}
.branch {
  display: flex;
  align-items: center;
  gap: 4px;
  user-select: none;
}
.caret {
  display: inline-block;
  width: 14px;
  cursor: pointer;
  color: #6b7280;
  text-align: center;
  font-size: 10px;
}
.caret:hover { color: #4f46e5; }
.leaf {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding-left: 14px;
  border-radius: 3px;
  transition: background 0.15s;
}
.leaf:hover {
  background: #eef2ff;
}
.leaf.used {
  color: #9ca3af;
  text-decoration: line-through;
  cursor: default;
}
.leaf.used:hover {
  background: transparent;
}
.key {
  color: #059669;
}
.colon {
  color: #6b7280;
}
.value {
  flex: 0 1 auto;
  word-break: break-all;
}
.value.t-string { color: #7c3aed; }
.value.t-number { color: #b45309; }
.value.t-boolean { color: #0369a1; }
.type-tag {
  margin-left: 4px;
  font-size: 10px;
  color: #9ca3af;
}
.used-mark {
  margin-left: 4px;
  color: #10b981;
  font-weight: bold;
}
.meta {
  color: #9ca3af;
  font-size: 11px;
  margin-left: 2px;
}
</style>
