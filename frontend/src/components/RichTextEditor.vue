<template>
  <div class="rich-text-editor">
    <!-- 工具栏 -->
    <Toolbar
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
import { ref, shallowRef, onBeforeUnmount } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'

const props = defineProps({
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
  // 图片以 base64 内嵌，无需上传服务
  MENU_CONF: {
    insertImage: {
      // 允许 base64 图片
    },
    uploadImage: {
      // 自定义上传：转为 base64
      customUpload(file, insertFn) {
        const reader = new FileReader()
        reader.onload = () => {
          insertFn(reader.result, file.name, '')
        }
        reader.readAsDataURL(file)
      }
    }
  }
}

/** 编辑器创建完成回调 */
function handleCreated(editor) {
  editorRef.value = editor
}

/** 内容变化回调 */
function handleChange(editor) {
  const html = editor.getHtml()
  // wangeditor 空内容时返回 '<p><br></p>'，统一为空字符串
  if (html === '<p><br></p>') {
    emit('update:modelValue', '')
  } else {
    emit('update:modelValue', html)
  }
}

// 组件销毁时释放编辑器
onBeforeUnmount(() => {
  if (editorRef.value) {
    editorRef.value.destroy()
  }
})
</script>

<style scoped>
.rich-text-editor {
  border: 2px solid #F1F5F9;
  border-radius: 10px;
  overflow: hidden;
}

.rich-text-toolbar {
  border-bottom: 1px solid #F1F5F9;
}

/* 覆盖 wangeditor 默认样式以匹配 Soft UI 风格 */
.rich-text-content {
  overflow-y: auto;
}

:deep(.w-e-text-container) {
  font-size: 14px;
  line-height: 1.6;
  color: #1B2559;
}

:deep(.w-e-text-placeholder) {
  color: #A3AED0;
  font-style: normal;
}
</style>
