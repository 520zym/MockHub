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
          <!-- 动态变量插入入口：点击后弹出 popover，选择变量后插入到编辑器光标位置 -->
          <el-popover
            placement="bottom-start"
            :width="320"
            trigger="click"
            popper-class="dynamic-var-popover"
          >
            <template #reference>
              <el-button size="small">
                <el-icon><MagicStick /></el-icon>
                插入变量
                <el-icon style="margin-left: 2px"><ArrowDown /></el-icon>
              </el-button>
            </template>
            <div class="dynamic-var-header">动态变量（点击插入）</div>
            <el-input
              v-model="variableSearchKeyword"
              size="small"
              placeholder="搜索变量名或描述"
              clearable
              class="dynamic-var-search"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <!-- 内置变量段 -->
            <div v-if="filteredBuiltin.length > 0" class="dynamic-var-section">内置</div>
            <div
              v-for="v in filteredBuiltin"
              :key="'builtin:' + v.name"
              class="dynamic-var-item"
              @click="insertVariable(v.name)"
            >
              <code class="dynamic-var-name">{{ '{{' + v.name + '}' + '}' }}</code>
              <span class="dynamic-var-desc">{{ v.desc }}</span>
            </div>

            <!-- 自定义变量段 -->
            <div v-if="filteredCustom.length > 0" class="dynamic-var-section">
              自定义（本团队）
            </div>
            <div
              v-for="cv in filteredCustom"
              :key="'custom:' + cv.id"
              class="dynamic-var-item"
              @click="handleCustomVariableClick(cv)"
            >
              <code class="dynamic-var-name">{{ '{{' + cv.name + '}' + '}' }}</code>
              <span class="dynamic-var-desc">
                {{ cv.description || (cv.groups.length + ' 个分组 / ' + cv.values.length + ' 个值') }}
              </span>
            </div>

            <div
              v-if="filteredBuiltin.length === 0 && filteredCustom.length === 0"
              class="dynamic-var-empty"
            >
              无匹配变量
            </div>
            <div class="dynamic-var-tip">
              提示：在编辑器中输入 <code>{{ '{{' }}</code> 也会弹出补全建议
            </div>
          </el-popover>
        </div>

        <!-- Monaco 编辑器 -->
        <MonacoEditor
          ref="editorRef"
          v-model="currentResponse.responseBody"
          :language="currentEditorLanguage"
          :dynamic-variables="combinedVariablesForEditor"
          class="response-editor"
        />
      </el-form>

      <!-- 选择自定义变量分组的小对话框 -->
      <el-dialog
        v-model="showGroupPickDialog"
        :title="'选择分组 - ' + (pickingVariable?.name || '')"
        width="440px"
      >
        <div class="group-pick-list">
          <div
            class="group-pick-item"
            @click="insertCustomPick(pickingVariable, null)"
          >
            <code>{{ '{{' + (pickingVariable?.name || '') + '}' + '}' }}</code>
            <span>全部值（{{ pickingVariable?.values?.length || 0 }} 个）</span>
          </div>
          <div
            v-for="g in (pickingVariable?.groups || [])"
            :key="g.id"
            class="group-pick-item"
            @click="insertCustomPick(pickingVariable, g.name)"
          >
            <code>{{ '{{' + (pickingVariable?.name || '') + '.' + g.name + '}' + '}' }}</code>
            <span>{{ g.valueIds.length }} 项{{ g.description ? ' · ' + g.description : '' }}</span>
          </div>
        </div>
      </el-dialog>

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
import { ref, computed, watch, onMounted } from 'vue'
import { Plus, Check, MagicStick, Upload, Select, Delete, ArrowDown, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MonacoEditor from './MonacoEditor.vue'
import { DYNAMIC_VARIABLES } from '@/constants/dynamicVariables'
import { getTeamVariables } from '@/api/customVariable'

// 动态变量元数据（用于 popover 列表渲染）
const dynamicVariables = DYNAMIC_VARIABLES

// 动态变量搜索关键字（支持按变量名或描述过滤，不区分大小写）
const variableSearchKeyword = ref('')

// 团队自定义变量列表（从后端按 teamId 拉取，打开接口编辑页时加载一次）
const customVariables = ref([])

// 过滤后的内置变量
const filteredBuiltin = computed(() => {
  const kw = variableSearchKeyword.value.trim().toLowerCase()
  if (!kw) return dynamicVariables
  return dynamicVariables.filter((v) =>
    v.name.toLowerCase().includes(kw) || v.desc.toLowerCase().includes(kw)
  )
})

// 过滤后的自定义变量（按变量名、描述、分组名匹配）
const filteredCustom = computed(() => {
  const kw = variableSearchKeyword.value.trim().toLowerCase()
  if (!kw) return customVariables.value
  return customVariables.value.filter((cv) => {
    if (cv.name.toLowerCase().includes(kw)) return true
    if (cv.description && cv.description.toLowerCase().includes(kw)) return true
    if (cv.groups && cv.groups.some(g => g.name.toLowerCase().includes(kw))) return true
    return false
  })
})

// 合并后的变量列表，用于 MonacoEditor 的补全 provider
// 包含内置（带 desc）+ 自定义（展开成 {{xxx}} 和每个分组的 {{xxx.yyy}}）
const combinedVariablesForEditor = computed(() => {
  const list = dynamicVariables.map((v) => ({ name: v.name, desc: v.desc }))
  for (const cv of customVariables.value) {
    list.push({
      name: cv.name,
      desc: cv.description || `自定义变量（${cv.values.length} 个值）`
    })
    for (const g of cv.groups || []) {
      list.push({
        name: cv.name + '.' + g.name,
        desc: `自定义分组（${g.valueIds.length} 项）${g.description ? ' · ' + g.description : ''}`
      })
    }
  }
  return list
})

// 选择分组的小对话框状态
const showGroupPickDialog = ref(false)
const pickingVariable = ref(null)

// Monaco 编辑器引用，用于调用 insertAtCursor
const editorRef = ref(null)

/**
 * 在 Monaco 编辑器光标位置插入动态变量占位符
 * @param {string} name 变量名，如 'timestamp'
 */
function insertVariable(name) {
  if (!editorRef.value) return
  editorRef.value.insertAtCursor(`{{${name}}}`)
  // 插入后清空搜索关键字，下次打开 popover 重新看到完整列表
  variableSearchKeyword.value = ''
}

/**
 * 点击自定义变量：若有分组则弹对话框让用户选，否则直接插入 {{name}}
 */
function handleCustomVariableClick(cv) {
  if (cv.groups && cv.groups.length > 0) {
    pickingVariable.value = cv
    showGroupPickDialog.value = true
  } else {
    insertVariable(cv.name)
  }
}

/**
 * 对话框中点某一项：插入对应占位符
 * @param cv        自定义变量对象
 * @param groupName 分组名；null 表示插入 {{cv.name}}（全部值）
 */
function insertCustomPick(cv, groupName) {
  if (!cv) return
  const placeholder = groupName ? `${cv.name}.${groupName}` : cv.name
  insertVariable(placeholder)
  showGroupPickDialog.value = false
  pickingVariable.value = null
}

/**
 * 拉取团队自定义变量列表
 */
async function loadCustomVariables() {
  if (!props.teamId) {
    customVariables.value = []
    return
  }
  try {
    customVariables.value = await getTeamVariables(props.teamId)
  } catch (e) {
    // 权限不足或其他错误时静默，响应拦截器已弹错
    customVariables.value = []
  }
}

onMounted(() => {
  loadCustomVariables()
})

// teamId 变化时重新拉取（接口编辑页切换 team 的场景）
watch(() => props.teamId, () => {
  loadCustomVariables()
})

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
  },
  /** 所属团队 ID，用于拉取该团队的自定义动态变量 */
  teamId: {
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

<!-- 动态变量 popover 样式（非 scoped，因 popover 渲染在 body 下） -->
<style>
.dynamic-var-popover .dynamic-var-header {
  font-size: 12px;
  color: #A3AED0;
  padding: 4px 8px 8px;
  border-bottom: 1px solid #F1F5F9;
  margin-bottom: 4px;
}

.dynamic-var-popover .dynamic-var-search {
  margin: 0 4px 6px;
  width: calc(100% - 8px);
}

.dynamic-var-popover .dynamic-var-empty {
  font-size: 12px;
  color: #A3AED0;
  text-align: center;
  padding: 12px 0;
}

.dynamic-var-popover .dynamic-var-section {
  font-size: 11px;
  color: #A3AED0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 6px 10px 2px;
  margin-top: 2px;
}

.group-pick-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-pick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.group-pick-item:hover {
  background: #F1F5F9;
}

.group-pick-item code {
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
  font-size: 13px;
  color: #6366F1;
}

.group-pick-item span {
  font-size: 12px;
  color: #64748B;
}

.dynamic-var-popover .dynamic-var-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.dynamic-var-popover .dynamic-var-item:hover {
  background: #F1F5F9;
}

.dynamic-var-popover .dynamic-var-name {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  color: #6366F1;
  background: transparent;
  padding: 0;
}

.dynamic-var-popover .dynamic-var-desc {
  font-size: 12px;
  color: #64748B;
  text-align: right;
  flex: 1;
}

.dynamic-var-popover .dynamic-var-tip {
  font-size: 11px;
  color: #A3AED0;
  padding: 8px 10px 2px;
  margin-top: 4px;
  border-top: 1px solid #F1F5F9;
}

.dynamic-var-popover .dynamic-var-tip code {
  background: #F1F5F9;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: #6366F1;
}
</style>
