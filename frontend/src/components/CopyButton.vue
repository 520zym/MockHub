<template>
  <el-button text size="small" class="copy-button" @click="handleCopy">
    <el-icon><DocumentCopy /></el-icon>
    <span v-if="showLabel">{{ copied ? '已复制' : '复制' }}</span>
  </el-button>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
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
let timer = null
onBeforeUnmount(() => clearTimeout(timer))

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.text)
    copied.value = true
    ElMessage.success('已复制到剪贴板')
    clearTimeout(timer)
    timer = setTimeout(() => {
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
    let success = false
    try { success = document.execCommand('copy') === true } catch { success = false }
    finally { document.body.removeChild(textarea) }
    if (!success) { ElMessage.warning('浏览器未允许复制，请手动选择并复制内容'); return }
    copied.value = true
    ElMessage.success('已复制到剪贴板')
    clearTimeout(timer)
    timer = setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}
</script>

<style scoped>
.copy-button {
  color: var(--app-text-secondary);
  transition: color 0.2s ease;

  &:hover {
    color: var(--app-accent);
  }
}
</style>
