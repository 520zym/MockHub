<template>
  <!-- 接口编辑/新建页面：分区式卡片布局 -->
  <div class="page-api-edit" v-loading="pageLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <el-button class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h1 class="page-title">{{ isEdit ? '编辑接口' : '新建接口' }}</h1>
    </div>

    <!-- 卡片 1：基本信息 -->
    <div class="soft-card section-card">
      <h3 class="section-title">基本信息</h3>

      <el-form :model="form" label-width="100px" label-position="top">
        <!-- 接口类型切换 -->
        <el-form-item label="接口类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="REST">REST</el-radio-button>
            <el-radio-button value="SOAP">SOAP</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-row :gutter="20">
          <!-- 接口名称 -->
          <el-col :span="12">
            <el-form-item label="接口名称" required>
              <el-input v-model="form.name" placeholder="如：获取用户信息" />
            </el-form-item>
          </el-col>

          <!-- 所属团队 -->
          <el-col :span="6">
            <el-form-item label="所属团队" required>
              <el-select
                v-model="form.teamId"
                placeholder="选择团队"
                style="width: 100%"
                @change="handleTeamChange"
              >
                <el-option
                  v-for="team in appStore.teams"
                  :key="team.id"
                  :label="team.name"
                  :value="team.id"
                >
                  <span style="display: flex; align-items: center; gap: 8px">
                    <TeamTag :identifier="team.identifier" :color="team.color" />
                    {{ team.name }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <!-- 分组 -->
          <el-col :span="6">
            <el-form-item label="分组">
              <el-select
                v-model="form.groupId"
                placeholder="选择分组（可选）"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="group in groups"
                  :key="group.id"
                  :label="group.name"
                  :value="group.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- REST 模式下的方法 + 路径 -->
        <el-row :gutter="20" v-if="form.type === 'REST'">
          <el-col :span="4">
            <el-form-item label="HTTP 方法" required>
              <el-select v-model="form.method" style="width: 100%">
                <el-option v-for="m in httpMethods" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="20">
            <el-form-item label="路径" required>
              <el-input v-model="form.path" placeholder="/api/user/{id}">
                <template #prepend>/mock/{{ currentTeamIdentifier }}</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- SOAP 模式下的路径 -->
        <el-row :gutter="20" v-if="form.type === 'SOAP'">
          <el-col :span="24">
            <el-form-item label="路径" required>
              <el-input v-model="form.path" placeholder="/ws/notice">
                <template #prepend>/mock/{{ currentTeamIdentifier }}</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- Mock 地址展示 -->
        <el-form-item label="Mock 地址" v-if="form.path">
          <div class="mock-url-display">
            <code class="mock-url">{{ mockUrl }}</code>
            <CopyButton :text="mockUrl" />
          </div>
        </el-form-item>
      </el-form>
    </div>

    <!-- SOAP 模式：WSDL 上传及 Operation 配置 -->
    <div class="soft-card section-card" v-if="form.type === 'SOAP'">
      <h3 class="section-title">SOAP / WSDL 配置</h3>

      <!-- WSDL 上传 -->
      <el-form label-position="top">
        <el-form-item label="WSDL 文件">
          <div class="wsdl-upload-row">
            <el-upload
              :auto-upload="false"
              :limit="1"
              accept=".wsdl,.xml"
              :on-change="handleWsdlFileChange"
              :show-file-list="false"
            >
              <el-button>
                <el-icon><Upload /></el-icon>
                选择 WSDL 文件
              </el-button>
            </el-upload>
            <el-button type="primary" :loading="wsdlUploading" @click="handleUploadWsdl" :disabled="!wsdlFile">
              上传并解析
            </el-button>
            <span v-if="form.soapConfig && form.soapConfig.wsdlFileName" class="wsdl-filename">
              已加载：{{ form.soapConfig.wsdlFileName }}
            </span>
          </div>
        </el-form-item>

        <!-- WSDL 托管地址 -->
        <el-form-item
          label="WSDL 托管地址"
          v-if="form.soapConfig && form.soapConfig.wsdlFileName"
        >
          <div class="mock-url-display">
            <code class="mock-url">{{ wsdlHostUrl }}</code>
            <CopyButton :text="wsdlHostUrl" />
          </div>
        </el-form-item>
      </el-form>

      <!-- Operation 列表 -->
      <div v-if="form.soapConfig && form.soapConfig.operations && form.soapConfig.operations.length" class="operations-list">
        <h4 class="operations-title">Operations（{{ form.soapConfig.operations.length }} 个）</h4>
        <div
          v-for="(op, idx) in form.soapConfig.operations"
          :key="idx"
          class="operation-card soft-card"
        >
          <div class="operation-header">
            <span class="operation-name">{{ op.operationName }}</span>
            <span class="operation-action">SOAPAction: {{ op.soapAction }}</span>
          </div>
          <el-row :gutter="16" style="margin-bottom: 12px">
            <el-col :span="6">
              <el-form-item label="响应状态码" label-position="top">
                <el-input-number v-model="op.responseCode" :min="100" :max="599" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="延迟 (ms)" label-position="top">
                <el-input-number v-model="op.delayMs" :min="0" :max="60000" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="operation-editor-label">响应 XML</div>
          <MonacoEditor
            v-model="op.responseBody"
            language="xml"
            class="operation-editor"
          />
        </div>
      </div>

      <div v-else-if="form.soapConfig && form.soapConfig.wsdlFileName" class="empty-operations">
        <p>未解析到 Operation，请检查 WSDL 文件</p>
      </div>
    </div>

    <!-- 卡片 2：返回体配置（REST 模式） -->
    <div class="soft-card section-card" v-if="form.type === 'REST'">
      <h3 class="section-title">返回体配置</h3>

      <el-form label-position="top">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="响应状态码">
              <el-input-number
                v-model="form.responseCode"
                :min="100"
                :max="599"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="18">
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
        </el-row>

        <!-- 编辑器工具栏 -->
        <div class="editor-toolbar">
          <el-button size="small" @click="formatResponseBody">
            <el-icon><MagicStick /></el-icon>
            格式化
          </el-button>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleUploadResponseFile"
          >
            <el-button size="small">
              <el-icon><Upload /></el-icon>
              上传文件
            </el-button>
          </el-upload>
        </div>

        <!-- Monaco 编辑器 -->
        <MonacoEditor
          v-model="form.responseBody"
          :language="editorLanguage"
          class="response-editor"
        />
      </el-form>
    </div>

    <!-- 卡片 3：标签 -->
    <div class="soft-card section-card">
      <h3 class="section-title">标签</h3>
      <TagInput v-model="form.tagIds" :team-id="form.teamId" />
    </div>

    <!-- 卡片 4：高级配置 -->
    <div class="soft-card section-card">
      <h3 class="section-title">高级配置</h3>

      <el-form label-position="top">
        <!-- 响应延迟（REST 模式下显示，SOAP 模式延迟在各 Operation 中配置） -->
        <el-form-item label="响应延迟 (ms)" v-if="form.type === 'REST'">
          <el-input-number
            v-model="form.delayMs"
            :min="0"
            :max="60000"
            :step="100"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-hint">设为 0 表示无延迟</span>
        </el-form-item>

        <!-- 全局响应头覆盖 -->
        <el-form-item label="全局响应头覆盖">
          <div class="header-overrides">
            <div
              v-for="(item, idx) in headerOverrideList"
              :key="idx"
              class="header-row"
            >
              <el-input v-model="item.key" placeholder="Header Name" class="header-key" />
              <el-input v-model="item.value" placeholder="Header Value" class="header-value" />
              <el-button text type="danger" @click="removeHeaderOverride(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addHeaderOverride">
              <el-icon><Plus /></el-icon>
              添加响应头覆盖
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <!-- 底部操作栏 -->
    <div class="bottom-actions">
      <el-button @click="goBack">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
/**
 * 接口编辑/新建页面
 * 分区式卡片布局：基本信息 / SOAP配置 / 返回体配置 / 标签 / 高级配置
 * 路由 /apis/new → 新建模式；/apis/:id/edit → 编辑模式
 */
import { ref, computed, watch, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { getApiDetail, createApi, updateApi } from '@/api/apis'
import { getGroups } from '@/api/groups'
import { uploadWsdl } from '@/api/soap'
import MonacoEditor from '@/components/MonacoEditor.vue'
import TeamTag from '@/components/TeamTag.vue'
import CopyButton from '@/components/CopyButton.vue'
import TagInput from '@/components/TagInput.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

// 是否为编辑模式
const isEdit = computed(() => !!route.params.id)
const pageLoading = ref(false)
const saving = ref(false)

// --- 表单数据 ---
const form = reactive({
  type: 'REST',
  name: '',
  teamId: '',
  groupId: null,
  method: 'GET',
  path: '',
  responseCode: 200,
  contentType: 'application/json',
  responseBody: '',
  delayMs: 0,
  enabled: true,
  tagIds: [],
  globalHeaderOverrides: {},
  soapConfig: null
})

// 当前团队的分组列表
const groups = ref([])

// 手动选择的 Content-Type（json/xml/text），用于覆盖自动识别
const manualContentType = ref('json')

// WSDL 上传相关
const wsdlFile = ref(null)
const wsdlUploading = ref(false)

// 全局响应头覆盖的可编辑列表（[{key, value}]）
const headerOverrideList = ref([])

// --- 计算属性 ---

/** 当前选中团队的 identifier */
const currentTeamIdentifier = computed(() => {
  const team = appStore.teams.find(t => t.id === form.teamId)
  return team ? team.identifier : '{team}'
})

/** 完整 Mock 地址 */
const mockUrl = computed(() => {
  const base = window.location.origin
  return `${base}/mock/${currentTeamIdentifier.value}${form.path || '/'}`
})

/** WSDL 托管地址 */
const wsdlHostUrl = computed(() => {
  if (!form.soapConfig || !form.soapConfig.wsdlFileName) return ''
  return `${window.location.origin}/wsdl/${form.soapConfig.wsdlFileName}`
})

/** 自动识别内容类型 */
const detectedContentType = computed(() => {
  const body = (form.responseBody || '').trim()
  if (!body) return '未知'
  // JSON 检测
  if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
    return 'JSON'
  }
  // XML 检测
  if (body.startsWith('<') && body.endsWith('>')) {
    return 'XML'
  }
  return 'Text'
})

/** Monaco 编辑器语言（根据手动选择的 Content-Type） */
const editorLanguage = computed(() => {
  const map = { json: 'json', xml: 'xml', text: 'plaintext' }
  return map[manualContentType.value] || 'plaintext'
})

// --- 内容类型自动识别联动 ---
watch(() => form.responseBody, (val) => {
  const body = (val || '').trim()
  if (!body) return
  if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
    manualContentType.value = 'json'
  } else if (body.startsWith('<') && body.endsWith('>')) {
    manualContentType.value = 'xml'
  }
})

// 手动切换 Content-Type 时同步到 form.contentType
watch(manualContentType, (val) => {
  const map = { json: 'application/json', xml: 'application/xml', text: 'text/plain' }
  form.contentType = map[val] || 'text/plain'
})

// --- 团队变化时加载分组 ---
async function handleTeamChange(teamId) {
  form.groupId = null
  if (teamId) {
    try {
      groups.value = await getGroups(teamId)
    } catch (e) {
      groups.value = []
    }
  } else {
    groups.value = []
  }
}

// 初始化时也加载分组
watch(() => form.teamId, async (teamId) => {
  if (teamId && groups.value.length === 0) {
    try {
      groups.value = await getGroups(teamId)
    } catch (e) {
      groups.value = []
    }
  }
})

// --- 数据加载（编辑模式） ---

async function loadApiDetail() {
  if (!route.params.id) return
  pageLoading.value = true
  try {
    const data = await getApiDetail(route.params.id)
    // 填充表单
    Object.assign(form, {
      type: data.type || 'REST',
      name: data.name || '',
      teamId: data.teamId || '',
      groupId: data.groupId || null,
      method: data.method || 'GET',
      path: data.path || '',
      responseCode: data.responseCode || 200,
      contentType: data.contentType || 'application/json',
      responseBody: data.responseBody || '',
      delayMs: data.delayMs || 0,
      enabled: data.enabled !== undefined ? data.enabled : true,
      tagIds: data.tags ? data.tags.map(t => t.id) : [],
      globalHeaderOverrides: data.globalHeaderOverrides || {},
      soapConfig: data.soapConfig || null
    })

    // 根据 contentType 设置手动选择的类型
    if (form.contentType.includes('json')) {
      manualContentType.value = 'json'
    } else if (form.contentType.includes('xml')) {
      manualContentType.value = 'xml'
    } else {
      manualContentType.value = 'text'
    }

    // 加载分组
    if (form.teamId) {
      try {
        groups.value = await getGroups(form.teamId)
      } catch (e) {
        groups.value = []
      }
    }

    // 将 globalHeaderOverrides 对象转为可编辑列表
    headerOverrideList.value = Object.entries(form.globalHeaderOverrides || {}).map(
      ([key, value]) => ({ key, value })
    )
  } catch (e) {
    ElMessage.error('加载接口详情失败')
  } finally {
    pageLoading.value = false
  }
}

// --- 格式化返回体 ---
function formatResponseBody() {
  const body = (form.responseBody || '').trim()
  if (!body) return

  if (manualContentType.value === 'json') {
    try {
      const obj = JSON.parse(body)
      form.responseBody = JSON.stringify(obj, null, 2)
      ElMessage.success('格式化成功')
    } catch (e) {
      ElMessage.warning('JSON 格式不正确，无法格式化')
    }
  } else if (manualContentType.value === 'xml') {
    // 简单的 XML 格式化（缩进处理）
    ElMessage.info('XML 格式化暂不支持，请手动调整')
  }
}

// --- 上传返回体文件 ---
function handleUploadResponseFile(uploadFile) {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.responseBody = e.target.result
    ElMessage.success('文件内容已加载到编辑器')
  }
  reader.readAsText(uploadFile.raw)
}

// --- WSDL 上传 ---
function handleWsdlFileChange(uploadFile) {
  wsdlFile.value = uploadFile.raw
}

async function handleUploadWsdl() {
  if (!wsdlFile.value) return

  wsdlUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', wsdlFile.value)
    const res = await uploadWsdl(formData)

    // 初始化 soapConfig
    form.soapConfig = {
      wsdlFileName: res.fileName,
      operations: (res.operations || []).map(op => ({
        operationName: op.operationName,
        soapAction: op.soapAction,
        responseCode: 200,
        delayMs: 0,
        responseBody: `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">\n  <soap:Body>\n    <!-- ${op.operationName} response -->\n  </soap:Body>\n</soap:Envelope>`
      }))
    }
    // SOAP 模式使用 POST 方法
    form.method = 'POST'
    ElMessage.success(`WSDL 解析成功，共 ${res.operations.length} 个 Operation`)
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    wsdlUploading.value = false
  }
}

// --- 全局响应头覆盖 ---

function addHeaderOverride() {
  headerOverrideList.value.push({ key: '', value: '' })
}

function removeHeaderOverride(idx) {
  headerOverrideList.value.splice(idx, 1)
}

// --- 保存 ---

async function handleSave() {
  // 基本校验
  if (!form.name.trim()) {
    ElMessage.warning('请输入接口名称')
    return
  }
  if (!form.teamId) {
    ElMessage.warning('请选择所属团队')
    return
  }
  if (!form.path.trim()) {
    ElMessage.warning('请输入接口路径')
    return
  }

  saving.value = true
  try {
    // 将 headerOverrideList 转回对象
    const overrides = {}
    headerOverrideList.value.forEach(item => {
      if (item.key.trim()) {
        overrides[item.key.trim()] = item.value
      }
    })

    const payload = {
      type: form.type,
      name: form.name,
      teamId: form.teamId,
      groupId: form.groupId || null,
      method: form.type === 'SOAP' ? 'POST' : form.method,
      path: form.path,
      responseCode: form.responseCode,
      contentType: form.contentType,
      responseBody: form.responseBody,
      delayMs: form.delayMs,
      enabled: form.enabled,
      tagIds: form.tagIds,
      globalHeaderOverrides: overrides,
      soapConfig: form.type === 'SOAP' ? form.soapConfig : null
    }

    if (isEdit.value) {
      await updateApi(route.params.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createApi(payload)
      ElMessage.success('创建成功')
    }
    router.push('/apis')
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/apis')
}

// --- 初始化 ---
onMounted(() => {
  // 编辑模式：加载接口详情
  if (isEdit.value) {
    loadApiDetail()
  } else {
    // 新建模式：如果侧边栏已选中团队，默认选中
    if (appStore.currentTeamId) {
      form.teamId = appStore.currentTeamId
      handleTeamChange(appStore.currentTeamId)
    }
    if (appStore.currentGroupId && appStore.currentGroupId !== '') {
      form.groupId = appStore.currentGroupId
    }
  }
})
</script>

<style lang="scss" scoped>
.page-api-edit {
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.back-button {
  flex-shrink: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1B2559;
}

// 分区卡片
.section-card {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1B2559;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #F1F5F9;
}

// Mock 地址展示
.mock-url-display {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #F7F8FA;
  padding: 8px 12px;
  border-radius: 10px;
}

.mock-url {
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  color: #6366F1;
  word-break: break-all;
}

// Content-Type 栏
.content-type-bar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.content-type-auto {
  font-size: 13px;
  color: #A3AED0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.check-icon {
  color: #10B981;
}

// 编辑器工具栏
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

// Monaco 编辑器高度
.response-editor {
  :deep(.monaco-editor-container) {
    height: 400px;
  }
}

// SOAP Operation 相关
.wsdl-upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wsdl-filename {
  font-size: 13px;
  color: #10B981;
}

.operations-list {
  margin-top: 16px;
}

.operations-title {
  font-size: 14px;
  font-weight: 600;
  color: #4A5568;
  margin-bottom: 12px;
}

.operation-card {
  margin-bottom: 16px;
  padding: 16px;
}

.operation-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.operation-name {
  font-weight: 600;
  color: #1B2559;
  font-size: 14px;
}

.operation-action {
  font-size: 12px;
  color: #A3AED0;
  font-family: monospace;
}

.operation-editor-label {
  font-size: 13px;
  color: #4A5568;
  margin-bottom: 6px;
}

.operation-editor {
  :deep(.monaco-editor-container) {
    height: 250px;
  }
}

.empty-operations {
  padding: 20px;
  text-align: center;
  color: #A3AED0;
  font-size: 13px;
}

// 全局响应头覆盖
.header-overrides {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-key {
  width: 200px;
}

.header-value {
  flex: 1;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #A3AED0;
}

// 底部操作栏
.bottom-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 0 40px;
}
</style>
