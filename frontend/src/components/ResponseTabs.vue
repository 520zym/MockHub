<template>
  <div class="response-tabs">
    <!-- Tab 栏 -->
    <div class="tabs-header">
      <div
        v-for="(resp, idx) in responses"
        :key="idx"
        class="tab-item"
        :class="{ active: activeIndex === idx }"
        @click="activeIndex = idx"
      >
        <span v-if="resp.isActive" class="active-dot"></span>
        <span v-if="!editingTabIndex !== idx" class="tab-name" @dblclick="startEditTabName(idx)">
          {{ resp.name || 'Response ' + (idx + 1) }}
        </span>
        <el-input
          v-if="editingTabIndex === idx"
          v-model="resp.name"
          size="small"
          class="tab-name-input"
          @blur="editingTabIndex = -1"
          @keyup.enter="editingTabIndex = -1"
          autofocus
        />
      </div>
      <div class="tab-add" @click="addResponse">
        <el-icon><Plus /></el-icon>
      </div>
    </div>

    <!-- 当前 Tab 内容 -->
    <div v-if="currentResponse" class="tab-content">
      <el-form label-position="top">
        <el-row :gutter="16">
          <!-- 名称 -->
          <el-col :span="6">
            <el-form-item label="返回体名称">
              <el-input v-model="currentResponse.name" placeholder="如：成功响应" />
            </el-form-item>
          </el-col>
          <!-- 状态码 -->
          <el-col :span="4">
            <el-form-item label="响应状态码">
              <el-input-number
                v-model="currentResponse.responseCode"
                :min="100"
                :max="599"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <!-- Content-Type（REST 显示，SOAP 隐藏） -->
          <el-col :span="8" v-if="!operationName">
            <el-form-item label="Content-Type">
              <div class="content-type-bar">
                <span class="content-type-auto">
                  自动识别：{{ detectedContentType }}
                  <el-icon v-if="detectedContentType !== '未知'" class="check-icon"><Check /></el-icon>
                </span>
                <el-radio-group v-model="manualContentType" size="small">
                  <el-radio-button value="json">JSON</el-radio-button>
                  <el-radio-button value="xml">XML</el-radio-button>
                  <el-radio-button value="text">Text</el-radio-button>
                </el-radio-group>
              </div>
            </el-form-item>
          </el-col>
          <!-- 延迟 -->
          <el-col :span="4">
            <el-form-item label="延迟 (ms)">
              <el-input-number
                v-model="currentResponse.delayMs"
                :min="0"
                :max="60000"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 编辑器工具栏 -->
        <div class="editor-toolbar">
          <el-button size="small" @click="formatBody">
            <el-icon><MagicStick /></el-icon>
            格式化
          </el-button>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleUploadFile"
          >
            <el-button size="small">
              <el-icon><Upload /></el-icon>
              上传文件
            </el-button>
          </el-upload>
        </div>

        <!-- Monaco 编辑器 -->
        <MonacoEditor
          v-model="currentResponse.responseBody"
          :language="currentEditorLanguage"
          class="response-editor"
        />
      </el-form>

      <!-- 底部操作栏 -->
      <div class="tab-actions">
        <el-button
          v-if="!currentResponse.isActive"
          type="success"
          size="small"
          plain
          @click="setActive(activeIndex)"
        >
          <el-icon><Select /></el-icon>
          设为生效
        </el-button>
        <el-tag v-else type="success" size="small" effect="dark" class="active-tag">
          <el-icon><Select /></el-icon>
          当前生效
        </el-tag>
        <el-button
          type="danger"
          size="small"
          plain
          :disabled="responses.length <= 1"
          @click="removeResponse(activeIndex)"
        >
          <el-icon><Delete /></el-icon>
          删除此返回体
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 多返回体标签页组件
 *
 * 通用组件，同时服务于 REST 和 SOAP 模式。
 * REST 模式下 operationName 为 null，SOAP 模式下为对应的 operation 名称。
 * 支持添加、删除、切换、设为生效等操作。
 */
import { ref, computed, watch } from 'vue'
import { Plus, Check, MagicStick, Upload, Select, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MonacoEditor from './MonacoEditor.vue'

const props = defineProps({
  /** 返回体数组 (v-model) */
  modelValue: {
    type: Array,
    default: () => []
  },
  /** SOAP operation 名称，null 表示 REST 模式 */
  operationName: {
    type: String,
    default: null
  },
  /** 默认 Content-Type */
  defaultContentType: {
    type: String,
    default: 'application/json'
  },
  /** 编辑器语言（SOAP 固定 xml，REST 动态切换） */
  editorLanguage: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

// 当前选中的 Tab 索引
const activeIndex = ref(0)

// 正在编辑名称的 Tab 索引
const editingTabIndex = ref(-1)

// REST 模式手动选择的 Content-Type
const manualContentType = ref('json')

// 响应数组的本地引用
const responses = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 当前选中的返回体
const currentResponse = computed(() => {
  if (activeIndex.value >= 0 && activeIndex.value < responses.value.length) {
    return responses.value[activeIndex.value]
  }
  return null
})

/** 自动识别内容类型 */
const detectedContentType = computed(() => {
  if (!currentResponse.value) return '未知'
  const body = (currentResponse.value.responseBody || '').trim()
  if (!body) return '未知'
  if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
    return 'JSON'
  }
  if (body.startsWith('<') && body.endsWith('>')) {
    return 'XML'
  }
  return 'Text'
})

/** Monaco 编辑器语言 */
const currentEditorLanguage = computed(() => {
  if (props.editorLanguage) return props.editorLanguage
  const typeMap = { json: 'json', xml: 'xml', text: 'plaintext' }
  return typeMap[manualContentType.value] || 'json'
})

// 自动识别结果变化时，同步更新手动选中的格式标签
// 场景：用户贴入/改写响应体后，detectedContentType 实时变化，需要让格式标签跟随识别结果
// 用户仍可手动点击格式标签进行覆盖（后续的 manualContentType watch 会处理）
watch(detectedContentType, (val) => {
  if (props.operationName) return
  if (val === '未知') return
  const next = val.toLowerCase() // 'json' | 'xml' | 'text'
  if (manualContentType.value !== next) {
    manualContentType.value = next
  }
})

// 同步 manualContentType → currentResponse.contentType
watch(manualContentType, (val) => {
  if (!currentResponse.value || props.operationName) return
  const typeMap = { json: 'application/json', xml: 'application/xml', text: 'text/plain' }
  currentResponse.value.contentType = typeMap[val] || 'application/json'
})

// 切换 Tab 时同步 manualContentType
watch(activeIndex, () => {
  if (!currentResponse.value || props.operationName) return
  const ct = currentResponse.value.contentType || 'application/json'
  if (ct.includes('json')) manualContentType.value = 'json'
  else if (ct.includes('xml')) manualContentType.value = 'xml'
  else manualContentType.value = 'text'
})

/** 双击 Tab 名称开始编辑 */
function startEditTabName(idx) {
  editingTabIndex.value = idx
}

/** 添加新返回体 */
function addResponse() {
  const newResp = {
    id: null,
    soapOperationName: props.operationName,
    name: 'Response ' + (responses.value.length + 1),
    responseCode: 200,
    contentType: props.defaultContentType,
    responseBody: '',
    delayMs: 0,
    isActive: false,
    sortOrder: responses.value.length
  }
  responses.value.push(newResp)
  activeIndex.value = responses.value.length - 1
  emit('update:modelValue', [...responses.value])
}

/** 设置指定返回体为活跃 */
function setActive(idx) {
  responses.value.forEach((r, i) => {
    r.isActive = (i === idx)
  })
  emit('update:modelValue', [...responses.value])
}

/** 删除返回体 */
function removeResponse(idx) {
  if (responses.value.length <= 1) return
  const wasActive = responses.value[idx].isActive
  responses.value.splice(idx, 1)
  // 如果删除的是活跃项，自动激活第一个
  if (wasActive && responses.value.length > 0) {
    responses.value[0].isActive = true
  }
  // 调整 activeIndex
  if (activeIndex.value >= responses.value.length) {
    activeIndex.value = responses.value.length - 1
  }
  emit('update:modelValue', [...responses.value])
}

/**
 * 简易 XML 缩进格式化
 * 纯字符串处理，不依赖 DOMParser（浏览器对畸形 XML 的解析容错不一）。
 * 支持自闭合标签、嵌套层级缩进；不解析 CDATA / 注释等高级结构，满足 Mock 场景足够。
 */
function formatXml(xml) {
  const PADDING = '  '
  const reg = /(>)(<)(\/*)/g
  const compact = xml
    .replace(/\r?\n/g, '')
    .replace(/>\s+</g, '><')
    .replace(reg, '$1\n$2$3')
  let pad = 0
  return compact.split('\n').map(function (line) {
    let indent = 0
    if (/^<\/\w/.test(line)) {
      pad = Math.max(pad - 1, 0)
    } else if (/^<\w[^>]*[^\/]>.*$/.test(line) && !/<.+<\/.+>/.test(line)) {
      indent = 1
    }
    const result = PADDING.repeat(pad) + line
    pad += indent
    return result
  }).join('\n')
}

/**
 * 格式化响应体
 * 根据当前手动选中的格式标签（manualContentType）走对应分支：
 * - json: JSON.parse + stringify(2 空格缩进)
 * - xml:  formatXml 纯字符串缩进
 * - text: 纯文本无需格式化
 * 之所以依赖 manualContentType 而非 detectedContentType，是因为用户可能手动覆盖识别结果。
 */
function formatBody() {
  if (!currentResponse.value) return
  const body = (currentResponse.value.responseBody || '').trim()
  if (!body) return

  const type = manualContentType.value
  if (type === 'json') {
    try {
      const parsed = JSON.parse(body)
      currentResponse.value.responseBody = JSON.stringify(parsed, null, 2)
    } catch (e) {
      ElMessage.warning('当前内容不是有效 JSON，无法格式化')
    }
  } else if (type === 'xml') {
    // 简单校验：至少包含一个闭合尖括号对
    if (!/<[^>]+>/.test(body)) {
      ElMessage.warning('当前内容不是有效 XML，无法格式化')
      return
    }
    try {
      currentResponse.value.responseBody = formatXml(body)
    } catch (e) {
      ElMessage.warning('当前内容不是有效 XML，无法格式化')
    }
  } else {
    ElMessage.info('纯文本无需格式化')
  }
}

/** 从文件上传响应体内容 */
function handleUploadFile(uploadFile) {
  if (!uploadFile || !uploadFile.raw) return
  const reader = new FileReader()
  reader.onload = (e) => {
    if (currentResponse.value) {
      currentResponse.value.responseBody = e.target.result
    }
  }
  reader.readAsText(uploadFile.raw)
}
</script>

<style scoped>
.response-tabs {
  width: 100%;
}

/* Tab 栏 */
.tabs-header {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 2px solid #F1F5F9;
  padding-bottom: 0;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  cursor: pointer;
  border-radius: 10px 10px 0 0;
  font-size: 14px;
  color: #A3AED0;
  transition: all 0.2s;
  border: 2px solid transparent;
  border-bottom: none;
  position: relative;
  bottom: -2px;
}

.tab-item:hover {
  color: #1B2559;
  background: #F8FAFC;
}

.tab-item.active {
  color: #1B2559;
  background: #fff;
  border-color: #F1F5F9;
  font-weight: 500;
}

.active-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22C55E;
  flex-shrink: 0;
}

.tab-name {
  user-select: none;
}

.tab-name-input {
  width: 120px;
}

.tab-add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  cursor: pointer;
  border-radius: 8px;
  color: #A3AED0;
  transition: all 0.2s;
}

.tab-add:hover {
  background: #F1F5F9;
  color: #6366F1;
}

/* Tab 内容区 */
.tab-content {
  padding: 0;
}

/* Content-Type 栏 */
.content-type-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content-type-auto {
  font-size: 13px;
  color: #A3AED0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.check-icon {
  color: #22C55E;
}

/* 编辑器工具栏 */
.editor-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

/* Monaco 编辑器 */
.response-editor {
  height: 350px;
}

/* 底部操作栏 */
.tab-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #F1F5F9;
}

.active-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
