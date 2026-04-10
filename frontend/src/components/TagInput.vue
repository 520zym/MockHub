<template>
  <div class="tag-input">
    <!-- 已选标签展示 -->
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

    <!-- 搜索输入 -->
    <el-popover
      :visible="popoverVisible"
      placement="bottom-start"
      :width="240"
      trigger="click"
    >
      <template #reference>
        <el-input
          v-model="searchText"
          placeholder="搜索或添加标签"
          size="small"
          class="tag-input__search"
          @focus="popoverVisible = true"
          @blur="handleBlur"
        />
      </template>

      <div class="tag-input__dropdown">
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
        <div v-if="filteredTags.length === 0" class="tag-input__empty">
          无匹配标签
        </div>
        <!-- Wave 2: 创建新标签选项 -->
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getTags } from '@/api/tags'

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

// 加载团队标签
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

const selectedTags = computed(() => {
  return allTags.value.filter(t => props.modelValue.includes(t.id))
})

const filteredTags = computed(() => {
  if (!searchText.value) return allTags.value
  const keyword = searchText.value.toLowerCase()
  return allTags.value.filter(t => t.name.toLowerCase().includes(keyword))
})

function isSelected(tagId) {
  return props.modelValue.includes(tagId)
}

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

function handleBlur() {
  // 延迟关闭，以便点击选项时不被提前关闭
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
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
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
