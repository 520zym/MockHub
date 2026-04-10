<template>
  <div class="tag-input">
    <!-- 已选标签展示：彩色胶囊样式，点击 x 可删除 -->
    <div class="tag-input__tags">
      <el-tag
        v-for="tag in selectedTags"
        :key="tag.id"
        closable
        :style="{ backgroundColor: hexToRgba(tag.color, 0.15), color: tag.color, borderColor: 'transparent' }"
        @close="removeTag(tag.id)"
      >
        {{ tag.name }}
      </el-tag>
    </div>

    <!-- 搜索输入 + 下拉列表 -->
    <el-popover
      :visible="popoverVisible"
      placement="bottom-start"
      :width="240"
      trigger="click"
    >
      <template #reference>
        <el-input
          ref="inputRef"
          v-model="searchText"
          placeholder="搜索或添加标签"
          size="small"
          class="tag-input__search"
          @focus="popoverVisible = true"
          @blur="handleBlur"
          @keydown.enter.prevent="handleEnter"
        />
      </template>

      <div class="tag-input__dropdown">
        <!-- 匹配到的已有标签列表（带颜色圆点 + 选中勾） -->
        <div
          v-for="tag in filteredTags"
          :key="tag.id"
          class="tag-input__option"
          @mousedown.prevent="toggleTag(tag)"
        >
          <span class="tag-input__dot" :style="{ backgroundColor: tag.color }" />
          <span>{{ tag.name }}</span>
          <el-icon v-if="isSelected(tag.id)" class="tag-input__check"><Check /></el-icon>
        </div>

        <!--
          创建新标签提示：
          - 有输入文字但无精确匹配时显示
          - 点击或按回车触发创建
        -->
        <div
          v-if="showCreateOption"
          class="tag-input__option tag-input__option--create"
          @mousedown.prevent="handleCreateTag"
        >
          <el-icon class="tag-input__plus"><Plus /></el-icon>
          <span>按回车创建「<strong>{{ searchText.trim() }}</strong>」</span>
        </div>

        <!-- 无输入、无标签时的空状态 -->
        <div v-if="filteredTags.length === 0 && !showCreateOption" class="tag-input__empty">
          无匹配标签
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Check, Plus } from '@element-plus/icons-vue'
import { getTags, createTag } from '@/api/tags'
import { ElMessage } from 'element-plus'

// 新标签随机颜色预设色板
const PRESET_COLORS = [
  '#6366F1', '#8B5CF6', '#EC4899', '#EF4444', '#F59E0B',
  '#10B981', '#06B6D4', '#3B82F6', '#6B7280', '#D97706'
]

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  teamId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const searchText = ref('')
const popoverVisible = ref(false)
const allTags = ref([])
const creating = ref(false) // 防止重复创建
const inputRef = ref(null)

// 团队切换时重新加载标签列表
watch(() => props.teamId, async (newTeamId) => {
  if (newTeamId) {
    try {
      allTags.value = await getTags(newTeamId)
    } catch (e) {
      allTags.value = []
    }
  } else {
    allTags.value = []
  }
}, { immediate: true })

// 已选中的标签对象列表（用于展示彩色胶囊）
const selectedTags = computed(() => {
  return allTags.value.filter(t => props.modelValue.includes(t.id))
})

// 根据搜索文字模糊过滤标签列表
const filteredTags = computed(() => {
  if (!searchText.value) return allTags.value
  const keyword = searchText.value.trim().toLowerCase()
  return allTags.value.filter(t => t.name.toLowerCase().includes(keyword))
})

// 是否显示"创建新标签"选项：有输入文字 && 没有名称完全匹配的已有标签
const showCreateOption = computed(() => {
  const text = searchText.value.trim()
  if (!text) return false
  // 已有标签中没有精确匹配（不区分大小写）才显示创建选项
  const exactMatch = allTags.value.some(
    t => t.name.toLowerCase() === text.toLowerCase()
  )
  return !exactMatch
})

function isSelected(tagId) {
  return props.modelValue.includes(tagId)
}

// 切换标签选中/取消选中
function toggleTag(tag) {
  const newValue = [...props.modelValue]
  const idx = newValue.indexOf(tag.id)
  if (idx >= 0) {
    newValue.splice(idx, 1)
  } else {
    newValue.push(tag.id)
  }
  emit('update:modelValue', newValue)
}

function removeTag(tagId) {
  const newValue = props.modelValue.filter(id => id !== tagId)
  emit('update:modelValue', newValue)
}

/**
 * 回车键处理逻辑：
 * 1. 如果输入文字精确匹配某个已有标签 → 切换该标签的选中状态
 * 2. 如果输入文字不匹配 → 创建新标签
 * 3. 如果输入框为空 → 不做任何操作
 */
function handleEnter() {
  const text = searchText.value.trim()
  if (!text) return

  // 检查是否精确匹配已有标签
  const exactMatch = allTags.value.find(
    t => t.name.toLowerCase() === text.toLowerCase()
  )
  if (exactMatch) {
    toggleTag(exactMatch)
    searchText.value = ''
    return
  }

  // 没有精确匹配 → 创建新标签
  handleCreateTag()
}

/**
 * 创建新标签流程：
 * 1. 从预设色板随机选一个颜色
 * 2. 调用 createTag API
 * 3. 成功后添加到 allTags 列表并自动选中
 */
async function handleCreateTag() {
  const name = searchText.value.trim()
  if (!name || creating.value) return

  creating.value = true
  try {
    const color = PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)]
    const newTag = await createTag({
      teamId: props.teamId,
      name,
      color
    })
    // 将新标签加入本地列表
    allTags.value.push(newTag)
    // 自动选中新创建的标签
    emit('update:modelValue', [...props.modelValue, newTag.id])
    searchText.value = ''
    ElMessage.success(`标签「${name}」创建成功`)
  } catch (e) {
    ElMessage.error('标签创建失败')
  } finally {
    creating.value = false
  }
}

function handleBlur() {
  // 延迟关闭，以便点击下拉选项时不被提前关闭
  setTimeout(() => {
    popoverVisible.value = false
  }, 200)
}

function hexToRgba(hex, alpha) {
  const h = (hex || '#6366F1').replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<style lang="scss" scoped>
.tag-input {
  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;

    &:empty {
      margin-bottom: 0;
    }
  }

  &__search {
    width: 180px;
  }

  &__dropdown {
    max-height: 200px;
    overflow-y: auto;
  }

  &__option {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    cursor: pointer;
    border-radius: 6px;
    transition: background 0.15s;
    font-size: 13px;

    &:hover {
      background: #F7F8FA;
    }

    // 创建新标签选项的特殊样式
    &--create {
      color: #6366F1;
      border-top: 1px solid #F0F0F5;
      margin-top: 4px;
      padding-top: 10px;
    }
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__plus {
    font-size: 14px;
    flex-shrink: 0;
  }

  &__check {
    margin-left: auto;
    color: #6366F1;
  }

  &__empty {
    padding: 12px;
    text-align: center;
    color: #A3AED0;
    font-size: 13px;
  }
}
</style>
