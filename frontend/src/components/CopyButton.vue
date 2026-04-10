<template>
  <el-button text size="small" class="copy-button" @click="handleCopy">
    <el-icon><DocumentCopy /></el-icon>
    <span v-if="showLabel">{{ copied ? '已复制' : '复制' }}</span>
  </el-button>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  text: {
    type: String,
    required: true
  },
  showLabel: {
    type: Boolean,
    default: true
  }
})

const copied = ref(false)

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.text)
    copied.value = true
    ElMessage.success('已复制到剪贴板')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (err) {
    // 降级方案：使用 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = props.text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copied.value = true
    ElMessage.success('已复制到剪贴板')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}
</script>

<style scoped>
.copy-button {
  color: #A3AED0;
  transition: color 0.2s ease;

  &:hover {
    color: #6366F1;
  }
}
</style>
