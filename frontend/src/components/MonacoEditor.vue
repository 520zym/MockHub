<template>
  <div ref="editorContainer" class="monaco-editor-container" />
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'json'
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const editorContainer = ref(null)
let editor = null

// 大文本阈值（超过 100KB 禁用 minimap）
const LARGE_TEXT_THRESHOLD = 100 * 1024

onMounted(() => {
  if (!editorContainer.value) return

  const isLargeText = (props.modelValue || '').length > LARGE_TEXT_THRESHOLD

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue || '',
    language: props.language,
    readOnly: props.readOnly,
    minimap: { enabled: !isLargeText },
    automaticLayout: true,
    fontSize: 14,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    tabSize: 2,
    theme: 'vs',
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    scrollbar: {
      verticalScrollbarSize: 6,
      horizontalScrollbarSize: 6
    }
  })

  // 监听编辑器内容变化
  editor.onDidChangeModelContent(() => {
    const value = editor.getValue()
    emit('update:modelValue', value)

    // 动态切换 minimap
    const shouldDisableMinimap = value.length > LARGE_TEXT_THRESHOLD
    editor.updateOptions({ minimap: { enabled: !shouldDisableMinimap } })
  })
})

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  if (editor && newVal !== editor.getValue()) {
    editor.setValue(newVal || '')
  }
})

// 监听语言切换
watch(() => props.language, (newLang) => {
  if (editor) {
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, newLang)
    }
  }
})

// 监听只读切换
watch(() => props.readOnly, (newVal) => {
  if (editor) {
    editor.updateOptions({ readOnly: newVal })
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 400px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid #F1F5F9;
}
</style>
