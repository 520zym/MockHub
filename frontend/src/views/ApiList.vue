<template>
  <!-- 接口列表页：工具栏筛选 + 数据表格 + 分页 + 导入导出 -->
  <div class="page-api-list">
    <!-- 工具栏 -->
    <div class="toolbar soft-card">
      <div class="toolbar__filters">
        <!-- 搜索框 -->
        <el-input
          v-model="keyword"
          placeholder="搜索接口名称或路径"
          clearable
          prefix-icon="Search"
          class="toolbar__search"
          @clear="handleKeywordChange"
          @keyup.enter="handleKeywordChange"
        />

        <!-- HTTP 方法筛选 -->
        <el-select
          v-model="methodFilter"
          placeholder="HTTP 方法"
          clearable
          class="toolbar__select"
          @change="handleMethodChange"
        >
          <el-option
            v-for="m in httpMethods"
            :key="m"
            :label="m"
            :value="m"
          />
        </el-select>

        <!-- 启用状态筛选 -->
        <el-select
          v-model="enabledFilter"
          placeholder="启用状态"
          clearable
          class="toolbar__select"
          @change="handleEnabledChange"
        >
          <el-option label="已启用" :value="true" />
          <el-option label="已禁用" :value="false" />
        </el-select>

        <!-- 标签筛选 -->
        <el-select
          v-model="tagFilter"
          placeholder="标签筛选"
          clearable
          class="toolbar__select"
          @change="handleTagChange"
        >
          <el-option
            v-for="tag in availableTags"
            :key="tag.id"
            :label="tag.name"
            :value="tag.id"
          >
            <span class="tag-option">
              <span class="tag-option__dot" :style="{ backgroundColor: tag.color }" />
              {{ tag.name }}
            </span>
          </el-option>
        </el-select>
      </div>

      <div class="toolbar__actions">
        <!-- 导入按钮 -->
        <el-button @click="importDialogVisible = true">
          <el-icon><Upload /></el-icon>
          导入
        </el-button>
        <!-- 导出按钮 -->
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
        <!-- 新建接口按钮 -->
        <el-button type="primary" @click="$router.push('/apis/new')">
          <el-icon><Plus /></el-icon>
          新建接口
        </el-button>
      </div>
    </div>

    <!-- 接口表格 -->
    <div class="table-card soft-card">
      <el-table
        :data="apiList"
        v-loading="loading"
        style="width: 100%"
        row-class-name="api-row"
        @row-click="handleRowClick"
      >
        <!-- 空状态 -->
        <template #empty>
          <div class="empty-state">
            <el-icon class="empty-state__icon"><Connection /></el-icon>
            <p class="empty-state__text">暂无接口数据</p>
            <el-button type="primary" size="small" @click="$router.push('/apis/new')">
              新建接口
            </el-button>
          </div>
        </template>

        <!-- HTTP 方法列 -->
        <el-table-column label="方法" width="80" align="center">
          <template #default="{ row }">
            <HttpMethodTag :method="row.method" />
          </template>
        </el-table-column>

        <!-- 路径列 -->
        <el-table-column label="路径" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="api-path">{{ row.path }}</span>
          </template>
        </el-table-column>

        <!-- 接口名称列 -->
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />

        <!-- 团队列 -->
        <el-table-column label="团队" width="80" align="center">
          <template #default="{ row }">
            <TeamTag :identifier="row.teamIdentifier" :color="row.teamColor" />
          </template>
        </el-table-column>

        <!-- 状态码列 -->
        <el-table-column prop="responseCode" label="状态码" width="70" align="center">
          <template #default="{ row }">
            <span class="response-code" :class="responseCodeClass(row.responseCode)">
              {{ row.responseCode }}
            </span>
          </template>
        </el-table-column>

        <!-- 启用开关列 -->
        <el-table-column label="启用" width="65" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              size="small"
              @click.stop
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column label="操作" width="190" align="center">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button text size="small" @click="handleCopyUrl(row)" title="复制 Mock 地址">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
              <el-button text size="small" type="primary" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button text size="small" @click="handleCopy(row)">
                复制
              </el-button>
              <el-button text size="small" type="danger" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入接口"
      width="500"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="目标团队">
          <el-select v-model="importForm.teamId" placeholder="选择导入到哪个团队" style="width: 100%">
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
        <el-form-item label="导入模式">
          <el-radio-group v-model="importForm.mode">
            <el-radio value="merge">合并（已存在的跳过）</el-radio>
            <el-radio value="override">覆盖（已存在的替换）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".json"
            :on-change="handleImportFileChange"
          >
            <el-button>选择 JSON 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 接口列表页
 * 功能：接口的搜索/筛选、分页展示、启用/禁用切换、复制、删除、导入/导出
 * 数据流：筛选条件来自 appStore（团队/分组）和 apiStore（关键词/方法/状态/标签/分页）
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useApiStore } from '@/stores/api'
import { getApis, deleteApi, copyApi, toggleApi, importApis, exportApis } from '@/api/apis'
import { getTags } from '@/api/tags'
import { getServerAddress } from '@/api/settings'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import TeamTag from '@/components/TeamTag.vue'

const router = useRouter()
const appStore = useAppStore()
const apiStore = useApiStore()

// --- 列表数据 ---
const apiList = ref([])
const total = ref(0)
const loading = ref(false)

// --- 工具栏筛选状态（双向绑定到输入控件，变化后同步到 store） ---
const keyword = ref(apiStore.listParams.keyword)
const methodFilter = ref(apiStore.listParams.method)
const enabledFilter = ref(apiStore.listParams.enabled)
const tagFilter = ref(apiStore.listParams.tagId)
const currentPage = ref(apiStore.listParams.page)
const pageSize = ref(apiStore.listParams.size)

const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

// 当前可选标签（基于当前团队筛选加载）
const availableTags = ref([])

// --- 服务器地址（用于拼接 Mock URL） ---
const serverAddress = ref('')

// --- 导入相关 ---
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importUploadRef = ref(null)
const importForm = ref({
  teamId: '',
  mode: 'merge'
})
const importFile = ref(null)

// --- 数据加载 ---

/** 加载接口列表，组合侧边栏筛选 + 工具栏筛选参数 */
async function loadApis() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    // 侧边栏团队/分组筛选
    if (appStore.currentTeamId) {
      params.teamId = appStore.currentTeamId
    }
    if (appStore.currentGroupId !== null && appStore.currentGroupId !== undefined) {
      // 空字符串表示查询未分组接口
      params.groupId = appStore.currentGroupId
    }
    // 工具栏筛选
    if (keyword.value) params.keyword = keyword.value
    if (methodFilter.value) params.method = methodFilter.value
    if (enabledFilter.value !== null && enabledFilter.value !== undefined && enabledFilter.value !== '') {
      params.enabled = enabledFilter.value
    }
    if (tagFilter.value) params.tagId = tagFilter.value

    const res = await getApis(params)
    apiList.value = res.items || []
    total.value = res.total || 0
  } catch (e) {
    apiList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 加载可选标签（按当前团队加载，未选团队时加载所有团队标签） */
async function loadTags() {
  try {
    if (appStore.currentTeamId) {
      availableTags.value = await getTags(appStore.currentTeamId)
    } else {
      // 没有选中团队时，加载所有团队标签合集
      const allTags = []
      for (const team of appStore.teams) {
        try {
          const tags = await getTags(team.id)
          allTags.push(...tags)
        } catch (e) {
          // 单个团队失败不影响其他
        }
      }
      availableTags.value = allTags
    }
  } catch (e) {
    availableTags.value = []
  }
}

// --- 筛选条件变化处理 ---

function handleKeywordChange() {
  apiStore.setParam('keyword', keyword.value)
  currentPage.value = 1
  loadApis()
}

function handleMethodChange(val) {
  apiStore.setParam('method', val)
  currentPage.value = 1
  loadApis()
}

function handleEnabledChange(val) {
  apiStore.setParam('enabled', val)
  currentPage.value = 1
  loadApis()
}

function handleTagChange(val) {
  apiStore.setParam('tagId', val)
  currentPage.value = 1
  loadApis()
}

function handlePageChange(page) {
  apiStore.setParam('page', page)
  loadApis()
}

function handleSizeChange(size) {
  apiStore.setParam('size', size)
  pageSize.value = size
  currentPage.value = 1
  apiStore.setParam('page', 1)
  loadApis()
}

// 监听侧边栏团队/分组筛选变化，自动刷新列表和标签
watch(
  () => [appStore.currentTeamId, appStore.currentGroupId],
  () => {
    currentPage.value = 1
    apiStore.setParam('page', 1)
    loadApis()
    loadTags()
  }
)

// 搜索框防抖：输入时延迟触发
let keywordTimer = null
watch(keyword, (val) => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    apiStore.setParam('keyword', val)
    currentPage.value = 1
    loadApis()
  }, 300)
})

// --- 行操作 ---

/** 点击行跳转编辑 */
function handleRowClick(row) {
  router.push(`/apis/${row.id}/edit`)
}

/** 编辑 */
function handleEdit(row) {
  router.push(`/apis/${row.id}/edit`)
}

/** 复制 Mock 地址到剪贴板 */
async function handleCopyUrl(row) {
  const base = serverAddress.value || window.location.origin
  const url = `${base}/mock/${row.teamIdentifier || ''}${row.path || '/'}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('Mock 地址已复制')
  } catch (err) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('Mock 地址已复制')
  }
}

/** 复制接口 */
async function handleCopy(row) {
  try {
    await copyApi(row.id)
    ElMessage.success('复制成功')
    loadApis()
    appStore.loadTeams()
  } catch (e) {
    // 错误已由拦截器处理
  }
}

/** 删除接口 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除接口「${row.name}」(${row.method} ${row.path})？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteApi(row.id)
    ElMessage.success('删除成功')
    loadApis()
    appStore.loadTeams()
  } catch (e) {
    // 取消或错误，不处理
  }
}

/** 启用/禁用切换 */
async function handleToggle(row) {
  try {
    const res = await toggleApi(row.id)
    row.enabled = res.enabled
    ElMessage.success(res.enabled ? '已启用' : '已禁用')
  } catch (e) {
    // 错误已由拦截器处理
  }
}

// --- 导入/导出 ---

function handleImportFileChange(uploadFile) {
  importFile.value = uploadFile.raw
}

async function handleImport() {
  if (!importForm.value.teamId) {
    ElMessage.warning('请选择目标团队')
    return
  }
  if (!importFile.value) {
    ElMessage.warning('请选择导入文件')
    return
  }

  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    formData.append('teamId', importForm.value.teamId)
    formData.append('mode', importForm.value.mode)

    const res = await importApis(formData)
    ElMessage.success(`导入完成：新增 ${res.imported} 个，跳过 ${res.skipped} 个，覆盖 ${res.overridden} 个`)
    importDialogVisible.value = false
    importFile.value = null
    loadApis()
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    importLoading.value = false
  }
}

async function handleExport() {
  // 如果侧边栏选中了团队，直接导出该团队；否则让用户选择
  const teamId = appStore.currentTeamId
  if (!teamId) {
    ElMessage.warning('请先在左侧选择一个团队再导出')
    return
  }

  try {
    const blob = await exportApis(teamId)
    const team = appStore.teams.find(t => t.id === teamId)
    const fileName = `mockhub-export-${team ? team.identifier : 'all'}-${new Date().toISOString().slice(0, 10)}.json`

    // 触发浏览器下载
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    // 错误已由拦截器处理
  }
}

// --- 工具函数 ---

function hexToRgba(hex, alpha) {
  const h = (hex || '#6366F1').replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** 根据状态码返回颜色 class */
function responseCodeClass(code) {
  if (code >= 200 && code < 300) return 'response-code--success'
  if (code >= 300 && code < 400) return 'response-code--redirect'
  if (code >= 400 && code < 500) return 'response-code--client-error'
  if (code >= 500) return 'response-code--server-error'
  return ''
}

// --- 初始化 ---
onMounted(async () => {
  loadApis()
  loadTags()
  // 加载服务器地址用于拼接 Mock URL
  try {
    const data = await getServerAddress()
    serverAddress.value = data.address || ''
  } catch (e) {
    // 获取失败时降级使用 window.location.origin
  }
})
</script>

<style lang="scss" scoped>
.page-api-list {
}

// 工具栏
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  &__filters {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__search {
    width: 240px;
  }

  &__select {
    width: 130px;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

// 标签选项（下拉中带色点）
.tag-option {
  display: flex;
  align-items: center;
  gap: 8px;

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }
}

// 表格卡片
.table-card {
  padding: 0;

  :deep(.el-table) {
    border-radius: 16px;
  }
}

// 表格行样式
.api-row {
  cursor: pointer;
  height: 52px;
}

.api-path {
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  color: #1B2559;
}

.group-name {
  font-size: 13px;
  color: #4A5568;
}

// 状态码颜色
.response-code {
  font-weight: 600;
  font-size: 13px;

  &--success { color: #10B981; }
  &--redirect { color: #F59E0B; }
  &--client-error { color: #EF4444; }
  &--server-error { color: #DC2626; }
}

.delay-text {
  font-size: 13px;
  color: #A3AED0;
}

// 标签列
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.no-tags {
  color: #A3AED0;
}

// 操作按钮
.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}

// 分页
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
}

// 空状态
.empty-state {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  &__icon {
    font-size: 48px;
    color: #A3AED0;
  }

  &__text {
    font-size: 14px;
    color: #A3AED0;
  }
}
</style>
