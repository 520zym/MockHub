<template>
  <!-- 接口编辑/新建页面：分区式卡片布局 -->
  <div class="page-api-edit" v-loading="pageLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <el-button class="back-button" text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="page-title">{{ isEdit ? '编辑接口' : '新建接口' }}</span>
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
          <el-col :span="16">
            <el-form-item label="接口名称" required>
              <el-input v-model="form.name" placeholder="如：获取用户信息" />
            </el-form-item>
          </el-col>

          <!-- 所属团队 -->
          <el-col :span="8">
            <el-form-item label="所属团队" required>
              <el-select
                v-model="form.teamId"
                placeholder="选择团队"
                style="width: 100%"
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

    <!-- 卡片：接口描述（REST 和 SOAP 都显示） -->
    <div class="soft-card section-card">
      <h3 class="section-title">接口描述</h3>
      <RichTextEditor v-model="form.description" placeholder="输入接口描述..." />
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

      <!-- Operation 列表（每个 operation 使用 ResponseTabs 管理多返回体） -->
      <!-- v1.4.4 性能：默认折叠。展开才挂载 ResponseTabs（含 Monaco 编辑器）。 -->
      <div v-if="form.soapConfig && form.soapConfig.operations && form.soapConfig.operations.length" class="operations-list">
        <div class="operations-toolbar">
          <h4 class="operations-title">Operations（{{ form.soapConfig.operations.length }} 个）</h4>
          <div class="operations-actions">
            <el-button text size="small" @click="expandAllOps">全部展开</el-button>
            <el-button text size="small" @click="collapseAllOps">全部折叠</el-button>
          </div>
        </div>
        <div
          v-for="(op, idx) in form.soapConfig.operations"
          :key="idx"
          class="operation-card soft-card"
          :class="{ 'operation-card--collapsed': isOpCollapsed(op) }"
        >
          <!-- 标题行可点击整行切换折叠（编辑响应体前必须先展开） -->
          <div class="operation-header" @click="toggleOpCollapsed(op)">
            <el-icon class="operation-chevron">
              <ArrowDown v-if="isOpCollapsed(op)" />
              <ArrowUp v-else />
            </el-icon>
            <span class="operation-name">{{ op.operationName }}</span>
            <span class="operation-action">SOAPAction: {{ op.soapAction }}</span>
            <!-- 折叠态展示描述首行预览，便于不展开就知道该 operation 做什么 -->
            <span
              v-if="isOpCollapsed(op) && op.description"
              class="operation-desc-preview"
              :title="op.description"
            >{{ getDescriptionPreview(op.description) }}</span>
          </div>

          <!-- 展开后才渲染编辑区：描述输入 + 返回体 tabs（含 Monaco） -->
          <template v-if="!isOpCollapsed(op)">
            <el-input
              v-model="op.description"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="接口描述（选填，建议 200 字内说明用途 / 参数 / 注意事项）"
              class="operation-description"
            />
            <ResponseTabs
              v-model="op.responses"
              :operation-name="op.operationName"
              :team-id="form.teamId"
              default-content-type="text/xml"
              editor-language="xml"
            />
          </template>
        </div>
      </div>

      <div v-else-if="form.soapConfig && form.soapConfig.wsdlFileName" class="empty-operations">
        <p>未解析到 Operation，请检查 WSDL 文件</p>
      </div>
    </div>

    <!-- 卡片：返回体配置（REST 模式，使用 ResponseTabs 组件） -->
    <div class="soft-card section-card" v-if="form.type === 'REST'">
      <h3 class="section-title">返回体配置</h3>
      <ResponseTabs v-model="form.responses" :team-id="form.teamId" />
    </div>

    <!-- 卡片：标签 -->
    <div class="soft-card section-card">
      <h3 class="section-title">标签</h3>
      <TagInput v-model="form.tagIds" :team-id="form.teamId" />
    </div>

    <!-- 卡片：高级配置 -->
    <div class="soft-card section-card">
      <h3 class="section-title">高级配置</h3>

      <el-form label-position="top">
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
 *
 * 分区式卡片布局：基本信息 / 接口描述 / SOAP配置 / 返回体配置 / 标签 / 高级配置
 * 路由 /apis/new → 新建模式；/apis/:id/edit → 编辑模式
 *
 * 多返回体支持：REST 和 SOAP 都使用 ResponseTabs 组件管理多个返回体，
 * 每个返回体有独立的名称、状态码、Content-Type、延迟和响应体。
 */
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { getApiDetail, createApi, updateApi } from '@/api/apis'
import { uploadWsdl } from '@/api/soap'
import { getServerAddress } from '@/api/settings'
import RichTextEditor from '@/components/RichTextEditor.vue'
import ResponseTabs from '@/components/ResponseTabs.vue'
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

/**
 * v1.4.4 性能：SOAP operation 折叠态管理。
 *
 * 默认全部折叠——大响应体（含 base64）场景下，一个 SOAP 接口 N 个 operation
 * 全量挂 Monaco 是整页滑动卡顿的关键来源。折叠时用 v-if 销毁 ResponseTabs
 * 及内部 Monaco 实例，只为当前在看的 operation 付出渲染成本。
 *
 * 用 reactive(Set) 追踪折叠的 operation（按 operationName 索引）。
 */
const collapsedOps = reactive(new Set())

function isOpCollapsed(op) {
  return collapsedOps.has(op.operationName)
}

function toggleOpCollapsed(op) {
  if (collapsedOps.has(op.operationName)) {
    collapsedOps.delete(op.operationName)
  } else {
    collapsedOps.add(op.operationName)
  }
}

function collapseAllOps() {
  if (!form.soapConfig || !form.soapConfig.operations) return
  form.soapConfig.operations.forEach(op => collapsedOps.add(op.operationName))
}

function expandAllOps() {
  collapsedOps.clear()
}

/** 获取描述的首行预览（供折叠态标题旁展示，超 80 字符 … 截断） */
function getDescriptionPreview(desc) {
  if (!desc) return ''
  const firstLine = desc.split('\n')[0]
  return firstLine.length > 80 ? firstLine.slice(0, 80) + '…' : firstLine
}

// --- 表单数据 ---
const form = reactive({
  type: 'REST',
  name: '',
  description: '',
  teamId: '',
  method: 'GET',
  path: '',
  // 旧字段保留用于后端兼容
  responseCode: 200,
  contentType: 'application/json',
  responseBody: '',
  delayMs: 0,
  enabled: true,
  tagIds: [],
  globalHeaderOverrides: {},
  soapConfig: null,
  // 多返回体（REST 模式）
  responses: [{
    id: null,
    soapOperationName: null,
    name: 'Default',
    responseCode: 200,
    contentType: 'application/json',
    responseBody: '',
    delayMs: 0,
    isActive: true,
    sortOrder: 0
  }]
})

// WSDL 上传相关
const wsdlFile = ref(null)
const wsdlUploading = ref(false)

// 服务器局域网地址
const serverAddress = ref('')

// 全局响应头覆盖的可编辑列表（[{key, value}]）
const headerOverrideList = ref([])

// --- 计算属性 ---

/** 当前选中团队的 identifier */
const currentTeamIdentifier = computed(() => {
  const team = appStore.teams.find(t => t.id === form.teamId)
  return team ? team.identifier : '{team}'
})

/** 完整 Mock 地址（优先使用服务器局域网地址） */
const mockUrl = computed(() => {
  const base = serverAddress.value || window.location.origin
  return `${base}/mock/${currentTeamIdentifier.value}${form.path || '/'}`
})

/**
 * WSDL 托管地址（方案 A：ASMX 风格，和 mockUrl 同源）
 *
 * 行为：
 *   GET  {mockUrl}?wsdl  → 返回 WSDL 文件（后端动态替换 location）
 *   POST {mockUrl}       → SOAP 调用
 *
 * 上传 WSDL 前返回空串，避免显示无效链接。
 */
const wsdlHostUrl = computed(() => {
  if (!form.soapConfig || !form.soapConfig.wsdlFileName) return ''
  return `${mockUrl.value}?wsdl`
})

// --- 数据加载（编辑模式） ---

async function loadApiDetail() {
  if (!route.params.id) return
  pageLoading.value = true
  try {
    const data = await getApiDetail(route.params.id)

    // 填充基本信息
    Object.assign(form, {
      type: data.type || 'REST',
      name: data.name || '',
      description: data.description || '',
      teamId: data.teamId || '',
      method: data.method || 'GET',
      path: data.path || '',
      responseCode: data.responseCode || 200,
      contentType: data.contentType || 'application/json',
      responseBody: data.responseBody || '',
      delayMs: data.delayMs || 0,
      enabled: data.enabled !== undefined ? data.enabled : true,
      tagIds: data.tags ? data.tags.map(t => t.id) : [],
      globalHeaderOverrides: data.globalHeaderOverrides || {},
      soapConfig: null
    })

    // 填充返回体
    if (data.responses && data.responses.length > 0) {
      if (data.type === 'REST') {
        // REST 模式：直接使用 responses（soapOperationName 为 null 的）
        form.responses = data.responses
          .filter(r => !r.soapOperationName)
          .map(r => ({
            id: r.id,
            soapOperationName: null,
            name: r.name || 'Default',
            responseCode: r.responseCode || 200,
            contentType: r.contentType || 'application/json',
            responseBody: r.responseBody || '',
            delayMs: r.delayMs || 0,
            isActive: r.active !== undefined ? r.active : false,
            sortOrder: r.sortOrder || 0
          }))
        // 确保至少有一个
        if (form.responses.length === 0) {
          form.responses = [createDefaultResponse()]
        }
      } else {
        // SOAP 模式：按 soapOperationName 分组，塞入各 operation
        const soapResponses = data.responses.filter(r => r.soapOperationName)
        const responsesByOp = {}
        soapResponses.forEach(r => {
          if (!responsesByOp[r.soapOperationName]) {
            responsesByOp[r.soapOperationName] = []
          }
          responsesByOp[r.soapOperationName].push({
            id: r.id,
            soapOperationName: r.soapOperationName,
            name: r.name || 'Default',
            responseCode: r.responseCode || 200,
            contentType: r.contentType || 'text/xml',
            responseBody: r.responseBody || '',
            delayMs: r.delayMs || 0,
            isActive: r.active !== undefined ? r.active : false,
            sortOrder: r.sortOrder || 0
          })
        })

        // 解析 soapConfig 并填充 responses
        if (data.soapConfig) {
          const soapConfig = typeof data.soapConfig === 'string' ? JSON.parse(data.soapConfig) : data.soapConfig
          form.soapConfig = {
            wsdlFileName: soapConfig.wsdlFileName,
            operations: (soapConfig.operations || []).map(op => ({
              operationName: op.operationName,
              soapAction: op.soapAction,
              // v1.4.4：operation 级描述，老数据为 undefined/null 时回退空串便于 v-model 绑定
              description: op.description || '',
              responses: responsesByOp[op.operationName] || [createDefaultSoapResponse(op.operationName)]
            }))
          }
          // v1.4.4 性能：加载已有接口时默认全部折叠，避免 N 个 Monaco 一次性挂载
          collapseAllOps()
        }
      }
    } else {
      // 无返回体数据（兼容旧数据），创建默认
      if (data.type === 'REST') {
        form.responses = [{
          id: null,
          soapOperationName: null,
          name: 'Default',
          responseCode: data.responseCode || 200,
          contentType: data.contentType || 'application/json',
          responseBody: data.responseBody || '',
          delayMs: data.delayMs || 0,
          isActive: true,
          sortOrder: 0
        }]
      }
    }

    // SOAP soapConfig（非返回体部分）
    if (data.type === 'SOAP' && data.soapConfig && !form.soapConfig) {
      const soapConfig = typeof data.soapConfig === 'string' ? JSON.parse(data.soapConfig) : data.soapConfig
      form.soapConfig = {
        wsdlFileName: soapConfig.wsdlFileName,
        operations: (soapConfig.operations || []).map(op => ({
          operationName: op.operationName,
          soapAction: op.soapAction,
          description: op.description || '',
          responses: [createDefaultSoapResponse(op.operationName)]
        }))
      }
      collapseAllOps()
    }

    // 将 globalHeaderOverrides 对象转为可编辑列表
    const overridesObj = typeof form.globalHeaderOverrides === 'string'
      ? JSON.parse(form.globalHeaderOverrides || '{}')
      : (form.globalHeaderOverrides || {})
    headerOverrideList.value = Object.entries(overridesObj).map(
      ([key, value]) => ({ key, value })
    )
  } catch (e) {
    ElMessage.error('加载接口详情失败')
  } finally {
    pageLoading.value = false
  }
}

/** 创建默认 REST 返回体 */
function createDefaultResponse() {
  return {
    id: null,
    soapOperationName: null,
    name: 'Default',
    responseCode: 200,
    contentType: 'application/json',
    responseBody: '',
    delayMs: 0,
    isActive: true,
    sortOrder: 0
  }
}

/**
 * 创建默认 SOAP operation 返回体。
 *
 * 若传入 suggestedResponseBody（非空）则使用它（WSDL XSD 递归生成的骨架）；
 * 否则回退到简易 Envelope 占位（含注释）。
 *
 * @param {string} operationName       operation 名称
 * @param {string=} suggestedResponseBody  后端返回的骨架（可选）
 */
function createDefaultSoapResponse(operationName, suggestedResponseBody) {
  const fallback = `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">\n  <soap:Body>\n    <!-- ${operationName} response -->\n  </soap:Body>\n</soap:Envelope>`
  return {
    id: null,
    soapOperationName: operationName,
    name: 'Default',
    responseCode: 200,
    contentType: 'text/xml',
    responseBody: suggestedResponseBody && suggestedResponseBody.trim() ? suggestedResponseBody : fallback,
    delayMs: 0,
    isActive: true,
    sortOrder: 0
  }
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

    // 合并策略：
    // - 已存在且有 responses（用户编辑过）→ 保留原 responses，只更新 soapAction
    // - 不存在 → 用 suggestedResponseBody 骨架，无骨架则 fallback
    const existingOpMap = {}
    if (form.soapConfig && form.soapConfig.operations) {
      for (const old of form.soapConfig.operations) {
        existingOpMap[old.operationName] = old
      }
    }

    form.soapConfig = {
      wsdlFileName: res.fileName,
      operations: (res.operations || []).map(op => {
        const existing = existingOpMap[op.operationName]
        // v1.4.4：description 合并策略与 responses 一致——已有非空值不覆盖
        const mergedDescription = existing && existing.description && existing.description.trim()
          ? existing.description
          : (op.description || '')
        if (existing && existing.responses && existing.responses.length > 0) {
          return {
            operationName: op.operationName,
            soapAction: op.soapAction,
            description: mergedDescription,
            responses: existing.responses
          }
        }
        return {
          operationName: op.operationName,
          soapAction: op.soapAction,
          description: mergedDescription,
          responses: [createDefaultSoapResponse(op.operationName, op.suggestedResponseBody)]
        }
      })
    }
    form.method = 'POST'
    // v1.4.4 性能：WSDL 上传后默认全部折叠；用户点击标题展开时才挂 Monaco
    collapseAllOps()
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

/**
 * v1.4.3 新增：前端提前校验多启用 + 条件匹配约束，避免来回网络。
 * 返回错误文案（字符串），null 表示通过。
 * 后端 ResponseValidator 仍做最终校验，这里只是 UX 优化。
 */
function validateResponsesLocal() {
  // REST：全部 responses 放一组；SOAP：按 operation 分组
  let groups = []
  if (form.type === 'REST') {
    groups.push({ label: 'REST', list: form.responses || [] })
  } else if (form.type === 'SOAP' && form.soapConfig && form.soapConfig.operations) {
    form.soapConfig.operations.forEach(op => {
      groups.push({
        label: 'SOAP · ' + op.operationName,
        list: op.responses || []
      })
    })
  }

  for (const g of groups) {
    const enabled = g.list.filter(r => r.isActive)
    if (enabled.length === 0) {
      return `[${g.label}] 至少需要一个启用的返回体`
    }
    if (enabled.length >= 2) {
      const noRule = enabled.filter(r => isConditionsEmptyLocal(r.conditions))
      if (noRule.length === 0) {
        return `[${g.label}] 多启用返回体时必须有一个无规则的作为兜底`
      }
      if (noRule.length > 1) {
        return `[${g.label}] 只允许一个无规则的启用返回体作为兜底，当前有 ${noRule.length} 个`
      }
    }
  }
  return null
}

function isConditionsEmptyLocal(json) {
  if (!json) return true
  try {
    const obj = JSON.parse(json)
    return !Array.isArray(obj?.conditions) || obj.conditions.length === 0
  } catch {
    return true
  }
}

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

  // v1.4.3 新增：多启用 + 条件匹配前置校验（前端提前拦截，后端仍做最终校验）
  const err = validateResponsesLocal()
  if (err) {
    ElMessage.error(err)
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

    // 收集所有返回体
    let allResponses = []
    if (form.type === 'REST') {
      allResponses = form.responses
    } else if (form.type === 'SOAP' && form.soapConfig && form.soapConfig.operations) {
      // 从各 operation 中收集返回体
      form.soapConfig.operations.forEach(op => {
        if (op.responses) {
          allResponses.push(...op.responses)
        }
      })
    }

    // 从活跃返回体取值填充旧字段（后端兼容）
    const activeResp = allResponses.find(r => r.isActive) || allResponses[0]
    const responseCode = activeResp ? activeResp.responseCode : 200
    const contentType = activeResp ? activeResp.contentType : 'application/json'
    const responseBody = activeResp ? activeResp.responseBody : ''
    const delayMs = activeResp ? activeResp.delayMs : 0

    // 构建 soapConfig（只保存元数据，不含 responses）
    let soapConfigPayload = null
    if (form.type === 'SOAP' && form.soapConfig) {
      soapConfigPayload = {
        wsdlFileName: form.soapConfig.wsdlFileName,
        operations: (form.soapConfig.operations || []).map(op => ({
          operationName: op.operationName,
          soapAction: op.soapAction,
          // v1.4.4：operation 级描述持久化；空字符串转 null 避免落库冗余
          description: (op.description && op.description.trim()) ? op.description.trim() : null
        }))
      }
    }

    const payload = {
      type: form.type,
      name: form.name,
      description: form.description,
      teamId: form.teamId,
      method: form.type === 'SOAP' ? 'POST' : form.method,
      path: form.path,
      responseCode: responseCode,
      contentType: contentType,
      responseBody: responseBody,
      delayMs: delayMs,
      enabled: form.enabled,
      tagIds: form.tagIds,
      globalHeaderOverrides: overrides,
      soapConfig: soapConfigPayload,
      responses: allResponses
    }

    if (isEdit.value) {
      await updateApi(route.params.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createApi(payload)
      ElMessage.success('创建成功')
    }
    // 刷新团队数据（apiCount 可能变化）
    appStore.loadTeams()
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
onMounted(async () => {
  // 加载服务器局域网地址
  try {
    const data = await getServerAddress()
    serverAddress.value = data.address || ''
  } catch (e) {
    // 失败时使用浏览器当前地址
  }
  // 确保团队数据已加载，否则 el-select 无法匹配显示团队名称
  if (appStore.teams.length === 0) {
    await appStore.loadTeams()
  }
  // 编辑模式：加载接口详情
  if (isEdit.value) {
    loadApiDetail()
  } else {
    // 新建模式：如果侧边栏已选中团队，默认选中
    if (appStore.currentTeamId) {
      form.teamId = appStore.currentTeamId
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
  gap: 4px;
  margin-bottom: 24px;
}

.back-button {
  flex-shrink: 0;
  font-size: 20px;
  color: #4A5568;
  padding: 0;
  height: auto;
  line-height: 1;
  vertical-align: middle;

  &:hover {
    color: #6366F1;
  }
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1B2559;
  margin: 0;
  padding: 0;
  line-height: 1;
}

// 分区卡片
.section-card {
  margin-bottom: 24px;
  padding: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1B2559;
  margin: 0 0 20px 0;
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
  margin-bottom: 0;
}

/* v1.4.4：Operations 区头部工具条（标题 + 展开/折叠全部） */
.operations-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.operations-actions {
  display: flex;
  gap: 4px;
}

.operation-card {
  margin-bottom: 16px;
  padding: 16px;
  transition: padding 0.2s ease;
}

/* v1.4.4：折叠态的卡片内边距减小，视觉更紧凑 */
.operation-card--collapsed {
  padding: 12px 16px;
}

.operation-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  cursor: pointer;
  user-select: none;
}

/* 折叠态下 header 本身就是唯一可见区，去掉 margin-bottom 避免额外空白 */
.operation-card--collapsed .operation-header {
  margin-bottom: 0;
}

/* 展开/折叠指示箭头 */
.operation-chevron {
  color: #8392AB;
  font-size: 14px;
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
  flex-shrink: 0;
}

/* v1.4.4：折叠态下标题行右侧的描述首行预览 */
.operation-desc-preview {
  color: #8392AB;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

/* v1.4.4：operation 级描述输入区 */
.operation-description {
  margin-bottom: 12px;
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

// 底部操作栏
.bottom-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 0 40px;
}
</style>
