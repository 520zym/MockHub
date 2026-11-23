<template>
  <div class="rich-text-editor" :class="{ 'rich-text-editor--readonly': readOnly }" @keydown.capture="markUserEditing" @pointerdown.capture="markUserEditing" @drop.capture="markUserEditing">
    <!-- 工具栏 -->
    <Toolbar v-if="!readOnly"
      :editor="editorRef"
      :defaultConfig="toolbarConfig"
      class="rich-text-toolbar"
      mode="simple"
    />
    <!-- 编辑区 -->
    <Editor
      :defaultConfig="editorConfig"
      :modelValue="modelValue"
      :style="{ height: height + 'px' }"
      class="rich-text-content"
      mode="simple"
      @onCreated="handleCreated"
      @onChange="handleChange"
    />
  </div>
</template>

<script setup>
/**
 * 富文本编辑器组件
 *
 * 基于 wangeditor 5 封装，支持标题、加粗、斜体、下划线、链接、图片等。
 * 图片以 base64 内嵌（离线环境无需图片上传服务）。
 * 存储格式为 HTML。
 */
import { ref, shallowRef, watch, onBeforeUnmount } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
  /** HTML 内容 (v-model) */
  modelValue: {
    type: String,
    default: ''
  },
  /** 占位提示文字 */
  placeholder: {
    type: String,
    default: '输入接口描述...'
  },
  /** 编辑区高度 (px) */
  height: {
    type: Number,
    default: 250
  }
})

const emit = defineEmits(['update:modelValue'])

// 编辑器实例（shallowRef 避免深层响应式代理导致问题）
const editorRef = shallowRef(null)
let disposed = false, userEditing = false, lastEmitted = null
function markUserEditing() { if (!props.readOnly) userEditing = true }
watch(() => props.readOnly, value => { const editor = editorRef.value; if (editor) value ? editor.disable() : editor.enable() })
watch(() => props.modelValue, value => { if (value !== lastEmitted) userEditing = false })

// 工具栏配置：只保留常用功能
const toolbarConfig = {
  toolbarKeys: [
    'headerSelect',
    'bold',
    'italic',
    'underline',
    'through',
    '|',
    'bulletedList',
    'numberedList',
    '|',
    'insertLink',
    'insertImage',
    '|',
    'blockquote',
    'codeBlock',
    '|',
    'undo',
    'redo'
  ]
}

// 编辑器配置
const editorConfig = {
  placeholder: props.placeholder,
  readOnly: props.readOnly,
  autoFocus: false,
  // 图片以 base64 内嵌，无需上传服务
  MENU_CONF: {
    insertImage: {
      // 允许 base64 图片
    },
    uploadImage: {
      // 自定义上传：转为 base64
      customUpload(file, insertFn) {
        if (props.readOnly || disposed) return
        const reader = new FileReader()
        reader.onload = () => {
          if (!props.readOnly && !disposed) insertFn(reader.result, file.name, '')
        }
        reader.readAsDataURL(file)
      }
    }
  }
}

/** 编辑器创建完成回调 */
function handleCreated(editor) {
  editorRef.value = editor
  if (props.readOnly) editor.disable()
}

/** 内容变化回调 */
function handleChange(editor) {
  if (props.readOnly || disposed || !userEditing) return
  const value = editor.getHtml()
  const html = value === '<p><br></p>' ? '' : value
  lastEmitted = html
  if (html !== props.modelValue) emit('update:modelValue', html)
}

// 组件销毁时释放编辑器
onBeforeUnmount(() => {
  disposed = true
  if (editorRef.value) {
    editorRef.value.destroy()
  }
})
</script>

<style scoped>
.rich-text-editor { min-width: 0; border: 0; border-radius: 10px; overflow: hidden; background: var(--app-bg-control); --w-e-textarea-bgcolor: var(--app-bg-control); --w-e-toolbar-bgcolor: var(--app-bg-control); --w-e-textarea-color: var(--app-text-regular); --w-e-toolbar-active-color: var(--app-accent); --w-e-toolbar-border-color: transparent; }
.rich-text-toolbar { background: var(--app-bg-control); border: 0; padding: 2px; }
.rich-text-content { overflow-y: auto; min-width: 0; font-size: 12px; line-height: 1.8; }
.rich-text-editor :deep(.w-e-bar) { background: transparent; flex-wrap: wrap; }.rich-text-editor :deep(.w-e-text-container) { background: var(--app-bg-control); border: 0; }.rich-text-editor :deep(.w-e-bar-item) { min-width: 25px; }.rich-text-editor :deep(.w-e-bar-item button) { height: 28px; padding: 4px; }.rich-text-editor :deep(.w-e-modal),.rich-text-editor :deep(.w-e-select-list) { box-shadow: none; border: 0; border-radius: 10px; background: white; }.rich-text-editor--readonly :deep([contenteditable]) { cursor: text; }
</style>
