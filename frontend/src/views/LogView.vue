<!--
  LogView.vue — 日志查看页面
  功能：操作日志和请求日志的查看，通过路由 path 判断显示哪种日志类型
  支持团队筛选、分页、请求日志详情查看
-->
<template>
  <div class="page-log-view">
    <!-- 页面标题 -->
    <h2 class="page-title">日志查看</h2>

    <!-- 工具栏：Tab 切换 + 团队筛选 -->
    <div class="log-toolbar">
      <div class="log-tabs">
        <div
          class="log-tab"
          :class="{ active: logType === 'operation' }"
          @click="switchTab('operation')"
        >
          操作日志
        </div>
        <div
          class="log-tab"
          :class="{ active: logType === 'request' }"
          @click="switchTab('request')"
        >
          请求日志
        </div>
      </div>

      <el-select
        v-model="selectedTeamId"
        placeholder="选择团队"
        clearable
        class="team-select"
        @change="handleTeamChange"
      >
        <el-option
          v-for="team in appStore.teams"
          :key="team.id"
          :label="team.name"
          :value="team.id"
        />
      </el-select>
    </div>

    <!-- 日志卡片 -->
    <div class="log-card">
      <!-- 操作日志表格 -->
      <el-table
        v-if="logType === 'operation'"
        v-loading="loading"
        :data="operationLogs"
        class="soft-table"
        :header-cell-style="headerCellStyle"
      >
        <el-table-column label="时间" prop="createdAt" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作人" prop="username" width="120" />
        <el-table-column label="操作类型" prop="action" width="120">
          <template #default="{ row }">
            <span class="action-tag" :style="actionTagStyle(row.action)">
              {{ actionLabel(row.action) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="目标类型" prop="targetType" width="100" />
        <el-table-column label="目标名称" prop="targetName" min-width="160" show-overflow-tooltip />
        <el-table-column label="详细信息" prop="detail" min-width="200" show-overflow-tooltip />
        <!-- 空状态 -->
        <template #empty>
          <div class="empty-state">
            <p>暂无操作日志</p>
          </div>
        </template>
      </el-table>

      <!-- 请求日志表格 -->
      <el-table
        v-if="logType === 'request'"
        v-loading="loading"
        :data="requestLogs"
        class="soft-table"
        :header-cell-style="headerCellStyle"
      >
        <el-table-column label="时间" prop="createdAt" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="方法" prop="method" width="100">
          <template #default="{ row }">
            <HttpMethodTag :method="row.method" />
          </template>
        </el-table-column>
        <el-table-column label="请求路径" prop="apiPath" min-width="240" show-overflow-tooltip />
        <el-table-column label="响应码" prop="responseCode" width="100">
          <template #default="{ row }">
            <span class="status-code-tag" :style="statusCodeStyle(row.responseCode)">
              {{ row.responseCode }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" prop="durationMs" width="100">
          <template #default="{ row }">
            <span class="duration-text">{{ row.durationMs }} ms</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
        <!-- 空状态 -->
        <template #empty>
          <div class="empty-state">
            <p>暂无请求日志</p>
          </div>
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 请求日志详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="请求详情"
      width="640px"
      class="log-detail-dialog"
      destroy-on-close
    >
      <div v-if="detailRow" class="detail-content">
        <!-- 基本信息 -->
        <div class="detail-section">
          <div class="detail-label">请求</div>
          <div class="detail-value">
            <HttpMethodTag :method="detailRow.method" />
            <span class="detail-path">{{ detailRow.apiPath }}</span>
            <span class="status-code-tag" :style="statusCodeStyle(detailRow.responseCode)">
              {{ detailRow.responseCode }}
            </span>
            <span class="duration-text">{{ detailRow.durationMs }} ms</span>
          </div>
        </div>

        <!-- 请求头 -->
        <div class="detail-section" v-if="detailRow.requestHeaders && Object.keys(detailRow.requestHeaders).length > 0">
          <div class="detail-label">请求头</div>
          <pre class="detail-pre">{{ formatJson(detailRow.requestHeaders) }}</pre>
        </div>

        <!-- 请求参数 -->
        <div class="detail-section" v-if="detailRow.requestParams && Object.keys(detailRow.requestParams).length > 0">
          <div class="detail-label">请求参数</div>
          <pre class="detail-pre">{{ formatJson(detailRow.requestParams) }}</pre>
        </div>

        <!-- 请求体 -->
        <div class="detail-section" v-if="detailRow.requestBody">
          <div class="detail-label">请求体</div>
          <pre class="detail-pre">{{ formatBody(detailRow.requestBody) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { getOperationLogs, getRequestLogs } from '@/api/logs'
import HttpMethodTag from '@/components/HttpMethodTag.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

// ========== 状态 ==========
const loading = ref(false)
const operationLogs = ref([])
const requestLogs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(50)
const selectedTeamId = ref(null)

// 当前日志类型，根据路由 path 判断
const logType = computed(() => {
  return route.path.includes('request') ? 'request' : 'operation'
})

// ========== 请求详情对话框 ==========
const detailVisible = ref(false)
const detailRow = ref(null)

// ========== 表格样式 ==========
const headerCellStyle = {
  background: 'transparent',
  color: '#A3AED0',
  fontWeight: '500',
  fontSize: '13px',
  borderBottom: '1px solid #F1F5F9'
}

// ========== 数据加载 ==========
async function loadLogs() {
  // 需要选择团队才加载
  if (!selectedTeamId.value) {
    operationLogs.value = []
    requestLogs.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const params = {
      teamId: selectedTeamId.value,
      page: page.value,
      size: pageSize.value
    }

    if (logType.value === 'operation') {
      const result = await getOperationLogs(params)
      operationLogs.value = result.items || []
      total.value = result.total || 0
    } else {
      const result = await getRequestLogs(params)
      requestLogs.value = result.items || []
      total.value = result.total || 0
    }
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

// ========== 事件处理 ==========
function switchTab(type) {
  const path = type === 'operation' ? '/logs/operation' : '/logs/request'
  router.push(path)
}

function handleTeamChange() {
  page.value = 1
  loadLogs()
}

function handlePageChange() {
  loadLogs()
}

function handleSizeChange() {
  page.value = 1
  loadLogs()
}

function showDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

// ========== 格式化函数 ==========
function formatTime(timeStr) {
  if (!timeStr) return '-'
  // 兼容 ISO 格式和普通格式
  const d = new Date(timeStr)
  if (isNaN(d.getTime())) return timeStr
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatJson(obj) {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

function formatBody(body) {
  if (!body) return ''
  // 尝试格式化为 JSON
  try {
    const parsed = JSON.parse(body)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return body
  }
}

// 操作类型标签配色
const actionColorMap = {
  CREATE: { bg: 'rgba(16, 185, 129, 0.12)', color: '#059669' },
  UPDATE: { bg: 'rgba(99, 102, 241, 0.12)', color: '#4F46E5' },
  DELETE: { bg: 'rgba(239, 68, 68, 0.12)', color: '#DC2626' },
  TOGGLE: { bg: 'rgba(245, 158, 11, 0.12)', color: '#D97706' },
  IMPORT: { bg: 'rgba(139, 92, 246, 0.12)', color: '#7C3AED' }
}

const actionLabelMap = {
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
  TOGGLE: '切换',
  IMPORT: '导入'
}

function actionTagStyle(action) {
  const scheme = actionColorMap[action] || { bg: 'rgba(107, 114, 128, 0.12)', color: '#6B7280' }
  return { backgroundColor: scheme.bg, color: scheme.color }
}

function actionLabel(action) {
  return actionLabelMap[action] || action
}

// 响应码标签配色
function statusCodeStyle(code) {
  if (code >= 200 && code < 300) {
    return { backgroundColor: 'rgba(16, 185, 129, 0.12)', color: '#059669' }
  } else if (code >= 400 && code < 500) {
    return { backgroundColor: 'rgba(245, 158, 11, 0.12)', color: '#D97706' }
  } else if (code >= 500) {
    return { backgroundColor: 'rgba(239, 68, 68, 0.12)', color: '#DC2626' }
  }
  return { backgroundColor: 'rgba(107, 114, 128, 0.12)', color: '#6B7280' }
}

// ========== 路由变化时重新加载 ==========
watch(() => route.path, () => {
  page.value = 1
  loadLogs()
})

// ========== 初始化 ==========
onMounted(async () => {
  // 确保团队列表已加载
  if (appStore.teams.length === 0) {
    await appStore.loadTeams()
  }
  // 默认选中第一个团队
  if (appStore.teams.length > 0 && !selectedTeamId.value) {
    selectedTeamId.value = appStore.teams[0].id
  }
  loadLogs()
})
</script>

<style lang="scss" scoped>
.page-log-view {
  padding: 0;
}

// ========== 页面标题 ==========
.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1B2559;
  margin: 0 0 24px;
}

// ========== 工具栏 ==========
.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.log-tabs {
  display: flex;
  gap: 4px;
  background: #F7F8FA;
  border-radius: 12px;
  padding: 4px;
}

.log-tab {
  padding: 8px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #A3AED0;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    color: #4A5568;
  }

  &.active {
    background: #ffffff;
    color: #6366F1;
    box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
  }
}

.team-select {
  width: 200px;
}

// ========== 日志卡片 ==========
.log-card {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  padding: 20px;
}

// ========== Soft UI 无边框表格 ==========
.soft-table {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: #F7F8FF;

  :deep(th.el-table__cell) {
    border-bottom: 1px solid #F1F5F9 !important;
  }

  :deep(td.el-table__cell) {
    border-bottom: 1px solid #F8FAFC !important;
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-table__row) {
    transition: background-color 0.15s ease;
  }
}

// ========== 标签样式 ==========
.action-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}

.status-code-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}

.duration-text {
  color: #A3AED0;
  font-size: 13px;
}

// ========== 分页 ==========
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #F1F5F9;
}

// ========== 空状态 ==========
.empty-state {
  padding: 40px 0;
  text-align: center;
  color: #A3AED0;
  font-size: 14px;

  p {
    margin: 0;
  }
}

// ========== 详情对话框 ==========
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  .detail-label {
    font-size: 13px;
    font-weight: 500;
    color: #A3AED0;
    margin-bottom: 8px;
  }

  .detail-value {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .detail-path {
    font-size: 14px;
    color: #1B2559;
    font-weight: 500;
    word-break: break-all;
  }
}

.detail-pre {
  background: #F7F8FA;
  border-radius: 10px;
  padding: 14px 16px;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #4A5568;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

<style lang="scss">
// 详情对话框全局样式覆盖
.log-detail-dialog {
  .el-dialog {
    border-radius: 16px;
    box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
  }

  .el-dialog__header {
    padding: 20px 24px 0;
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 600;
    color: #1B2559;
  }

  .el-dialog__body {
    padding: 16px 24px 24px;
  }
}
</style>
