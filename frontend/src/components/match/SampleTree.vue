<template>
  <div class="sample-tree">
    <!-- 顶部：搜索 + 字段总数 -->
    <div class="tree-toolbar">
      <el-input
        v-model="searchKeyword"
        size="small"
        clearable
        placeholder="搜索字段路径…"
        class="tree-search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span class="field-count">{{ leafCount }} 个字段</span>
    </div>

    <!-- 主体树 -->
    <div v-if="rootNode" class="tree-body">
      <TreeNode
        :node="rootNode"
        :level="0"
        :search="normalizedSearch"
        :used-paths="usedPathsSet"
        :collapsed="collapsedMap"
        @toggle="onToggle"
        @pick="onPick"
      />
    </div>
    <div v-else class="tree-empty">
      <p>粘贴一段示例请求，点击字段即可快速生成条件</p>
    </div>
  </div>
</template>

<script setup>
/**
 * JSON 示例树组件
 *
 * 将用户粘贴的 JSON 字符串解析成可交互树：
 * - 对象/数组可折叠（▶ / ▼）
 * - 顶部搜索按字段路径模糊过滤（只显示命中路径及其祖先）
 * - 超过 260px 独立滚动
 * - 已出现在条件中的叶子用灰色 + ✓ 标记
 * - 叶子值超长（>40 字符）截断展示，title 提示完整值
 *
 * Emits：
 * - select(path, valueText, valueType)：点击叶子，父组件据此生成一条条件
 */
import { ref, computed, reactive, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import TreeNode from './SampleTreeNode.vue'

const props = defineProps({
  /** 粘贴的原始 JSON 字符串 */
  modelValue: { type: String, default: '' },
  /** 已被加入条件的路径数组，用于灰显 + ✓ 标记 */
  usedPaths: { type: Array, default: () => [] }
})

const emit = defineEmits(['select'])

// 折叠状态：path → true 表示折叠；默认全部展开
const collapsedMap = reactive({})
const searchKeyword = ref('')

const normalizedSearch = computed(() => searchKeyword.value.trim().toLowerCase())

const usedPathsSet = computed(() => new Set(props.usedPaths || []))

// 解析 JSON，解析失败返回 null（组件显示空态）
const rootNode = computed(() => {
  const raw = (props.modelValue || '').trim()
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return buildNode('', '', parsed)
  } catch {
    return null
  }
})

/** 递归构建统一的 TreeNode 结构 */
function buildNode(parentPath, key, value) {
  const path = composePath(parentPath, key)
  if (Array.isArray(value)) {
    return {
      path,
      key,
      kind: 'array',
      size: value.length,
      children: value.map((v, i) => buildNode(path, '[' + i + ']', v))
    }
  }
  if (value !== null && typeof value === 'object') {
    return {
      path,
      key,
      kind: 'object',
      size: Object.keys(value).length,
      children: Object.keys(value).map((k) => buildNode(path, k, value[k]))
    }
  }
  const valueType = typeof value === 'number'
    ? 'NUMBER'
    : typeof value === 'boolean'
      ? 'BOOLEAN'
      : 'STRING'
  return {
    path,
    key,
    kind: 'leaf',
    value: value === null ? null : String(value),
    valueType
  }
}

/** 组合父路径+当前键：对象用 `.`，数组用 `[i]`（不加点） */
function composePath(parent, key) {
  if (!key) return parent
  if (key.startsWith('[')) return parent + key
  if (!parent) return key
  return parent + '.' + key
}

// 统计叶子数量（搜索后不变，用于总览）
const leafCount = computed(() => countLeaves(rootNode.value))
function countLeaves(node) {
  if (!node) return 0
  if (node.kind === 'leaf') return 1
  return (node.children || []).reduce((s, c) => s + countLeaves(c), 0)
}

function onToggle(path) {
  collapsedMap[path] = !collapsedMap[path]
}

function onPick(node) {
  if (node.kind !== 'leaf') return
  emit('select', node.path, node.value == null ? '' : node.value, node.valueType)
}

// 搜索时自动展开所有被命中路径的祖先（UX：搜索结果立即可见）
watch(normalizedSearch, (kw) => {
  if (!kw) return
  for (const k of Object.keys(collapsedMap)) {
    collapsedMap[k] = false
  }
})
</script>

<style scoped>
.sample-tree {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
}
.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.tree-search {
  flex: 1;
}
.field-count {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
}
.tree-body {
  max-height: 260px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.7;
  border-top: 1px solid #f3f4f6;
  padding-top: 6px;
}
.tree-empty {
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
  padding: 28px 12px;
  border-top: 1px dashed #e5e7eb;
}
</style>
