<!--
  LogView.vue — 日志查看页面
  功能：操作日志和请求日志的查看，通过路由 path 判断显示哪种日志类型
  支持团队筛选、分页、请求日志详情查看
-->
<template>
  <div class="page-log-view">
    <header class="resource-header"><div><h1>团队日志</h1><p>{{ appStore.currentTeam?.name || '未选择团队' }} · 查看操作记录与请求快照</p></div></header>
    <div class="log-toolbar">
      <div class="log-tabs" role="tablist" aria-label="日志类型">
        <button type="button" class="log-tab" role="tab" :aria-selected="logType === 'operation'" :class="{ active: logType === 'operation' }" @click="switchTab('operation')">操作日志</button>
        <button type="button" class="log-tab" role="tab" :aria-selected="logType === 'request'" :class="{ active: logType === 'request' }" @click="switchTab('request')">请求日志</button>
      </div>
      <div class="toolbar-actions"><span class="team-context">{{ appStore.currentTeam?.name || '未选择团队' }}</span><el-button :disabled="!canRead" :loading="loading" @click="loadLogs">刷新</el-button></div>
    </div>
    <div v-if="!canRead" class="resource-state" role="status">请选择有访问权限的团队。日志不会自动扩展到全部团队。</div>
    <div v-if="loadError" class="resource-state" role="alert"><strong>日志加载失败</strong><p>{{ loadError }}</p><el-button @click="loadLogs">重试</el-button></div>

    <!-- 日志卡片 -->
    <div v-if="canRead && !loadError" class="log-card">
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
        <el-table-column label="操作人" prop="username" width="120">
          <template #default="{ row }">
            <span class="cell-title" :title="row.username">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" prop="action" width="120">
          <template #default="{ row }">
            <span class="action-tag" :style="actionTagStyle(row.action)">
              {{ actionLabel(row.action) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="目标类型" prop="targetType" width="100" />
        <el-table-column label="目标名称" prop="targetName" min-width="300">
          <template #default="{ row }">
            <span class="cell-title" :title="row.targetName">{{ row.targetName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="详细信息" prop="detail" min-width="200" show-overflow-tooltip />
        <!-- 空状态 -->
        <template #empty>
          <div class="empty-state">
            <p>{{ loading ? '正在读取操作日志…' : '当前团队暂无操作日志' }}</p>
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
        <el-table-column label="请求路径" prop="apiPath" min-width="280">
          <template #default="{ row }">
            <el-tag v-if="row.targetType === 'FILE'" size="small" class="target-tag">文件</el-tag>
            <span class="cell-title" :title="row.apiPath">{{ row.apiPath }}</span>
          </template>
        </el-table-column>
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
            <p>{{ loading ? '正在读取请求日志…' : '当前团队暂无请求日志' }}</p>
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
      <div v-if="detailRow && canRead" class="detail-content">
        <!-- 基本信息 -->
        <div class="detail-section">
          <div class="detail-label">请求</div>
          <div class="detail-value">
            <HttpMethodTag :method="detailRow.method" />
            <el-tag v-if="detailRow.targetType === 'FILE'" size="small" class="target-tag">文件下载</el-tag>
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

        <!-- AI 匹配诊断 -->
        <div v-if="canExplain" class="detail-section ai-diagnosis">
          <div class="ai-diagnosis__head">
            <div class="detail-label" style="margin: 0">AI 匹配诊断</div>
            <el-button
              v-if="!aiExplain"
              size="small"
              :loading="aiExplainLoading" :disabled="!canExplain"
              @click="runAiExplain"
            >
              <el-icon><MagicStick /></el-icon>
              解释这次匹配
            </el-button>
            <el-button v-else size="small" text :loading="aiExplainLoading" :disabled="!canExplain" @click="runAiExplain">
              <el-icon><Refresh /></el-icon>
              重新诊断
            </el-button>
          </div>

          <el-alert
            v-if="aiExplainError"
            type="error"
            :closable="false"
            show-icon
            :title="aiExplainError"
            style="margin-top: 10px"
          />

          <div v-if="aiExplain" class="ai-diagnosis__body">
            <div class="ai-verdict">
              <div class="ai-verdict__title">{{ aiExplain.conclusion }}</div>
              <div class="ai-verdict__desc">{{ aiExplain.summary }}</div>
            </div>
            <div class="ai-chain">
              <div v-for="item in aiExplain.chain" :key="item.index" class="ai-chain__item">
                <span class="ai-chain__dot" :class="'ai-chain__dot--' + String(item.status).toLowerCase()">
                  {{ item.index }}
                </span>
                <div class="ai-chain__content">
                  <div class="ai-chain__title">
                    {{ item.name }}
                    <span class="ai-chain__badge">{{ statusLabel(item.status) }}</span>
                  </div>
                  <div class="ai-chain__desc">{{ item.detail }}</div>
                </div>
              </div>
            </div>
            <div v-if="aiExplain.suggestion" class="ai-suggestion">
              <div class="ai-suggestion__title">
                <el-icon><MagicStick /></el-icon>
                建议
              </div>
              <div class="ai-suggestion__text">{{ aiExplain.suggestion }}</div>
            </div>
            <div class="ai-diagnosis__note">
              诊断基于本次请求快照，不会修改任何配置；如需调整请到接口编辑页。
            </div>
          </div>

          <div v-else-if="!aiExplainLoading" class="ai-diagnosis__empty">
            点击「解释这次匹配」，按当前条件重放本次请求并给出原因。
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getOperationLogs, getRequestLogs } from '@/api/logs'
import { explainAiMatch } from '@/api/ai'
import { useAiStore } from '@/stores/ai'
import { MagicStick, Refresh } from '@element-plus/icons-vue'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import { formatFull } from '@/utils/time'
const route = useRoute(), router = useRouter(), appStore = useAppStore(), userStore = useUserStore(), aiStore = useAiStore()
const selectedTeamId = computed(() => appStore.currentTeamId || null)
const canRead = computed(() => !!selectedTeamId.value && userStore.identityReady && userStore.hasCapability(selectedTeamId.value, 'read'))
// 文件下载日志没有对应的接口定义，AI 匹配诊断只对 Mock 接口调用开放。
const canExplain = computed(() => canRead.value && aiStore.available && aiStore.capabilities.explain && !!detailRow.value?.apiId)
const loading = ref(false), loadError = ref(''), operationLogs = ref([]), requestLogs = ref([]), total = ref(0), page = ref(1), pageSize = ref(50)
const logType = computed(() => route.path.includes('request') ? 'request' : 'operation')
const detailVisible = ref(false), detailRow = ref(null), aiExplain = ref(null), aiExplainLoading = ref(false), aiExplainError = ref('')
let contextVersion = 0, listSequence = 0, detailVersion = 0, explainSequence = 0, disposed = false
const headerCellStyle = { background: 'var(--app-bg-control)', color: 'var(--app-text-secondary)', fontWeight: '500', fontSize: '12px' }
function snapshot() { return { teamId: selectedTeamId.value, version: contextVersion, type: logType.value } }
function current(ctx) { return !disposed && canRead.value && ctx.teamId === selectedTeamId.value && ctx.version === contextVersion && ctx.type === logType.value }
function clearDetail() { detailVersion++; explainSequence++; detailRow.value = null; aiExplain.value = null; aiExplainError.value = ''; aiExplainLoading.value = false }
function resetContext() { contextVersion++; listSequence++; operationLogs.value = []; requestLogs.value = []; total.value = 0; page.value = 1; loading.value = false; loadError.value = ''; detailVisible.value = false; clearDetail(); if (canRead.value) loadLogs() }
async function loadLogs() {
  const ctx = snapshot(), sequence = ++listSequence
  if (!canRead.value) { operationLogs.value = []; requestLogs.value = []; total.value = 0; return }
  loading.value = true; loadError.value = ''
  try {
    const params = { teamId: ctx.teamId, page: page.value, size: pageSize.value }
    const result = await (ctx.type === 'operation' ? getOperationLogs(params) : getRequestLogs(params))
    if (!current(ctx) || sequence !== listSequence) return
    if (ctx.type === 'operation') operationLogs.value = result.items || []
    else requestLogs.value = result.items || []
    total.value = result.total || 0
  } catch (error) { if (current(ctx) && sequence === listSequence) { operationLogs.value = []; requestLogs.value = []; total.value = 0; loadError.value = error?.response?.data?.msg || error?.msg || error?.message || '日志请求失败。' } }
  finally { if (current(ctx) && sequence === listSequence) loading.value = false }
}
function switchTab(type) { router.push(type === 'operation' ? '/logs/operation' : '/logs/request') }
function handlePageChange() { loadLogs() }
function handleSizeChange() { page.value = 1; loadLogs() }
function showDetail(row) { if (!canRead.value || !requestLogs.value.some(item => item.id === row.id)) return; clearDetail(); detailRow.value = row; detailVisible.value = true }
async function runAiExplain() {
  if (!canExplain.value || !detailRow.value?.id || aiExplainLoading.value) return
  const ctx = snapshot(), id = detailRow.value.id, detail = detailVersion, sequence = ++explainSequence
  aiExplainLoading.value = true; aiExplainError.value = ''
  try {
    const result = await explainAiMatch({ requestLogId: id })
    if (current(ctx) && canExplain.value && sequence === explainSequence && detail === detailVersion && id === detailRow.value?.id) aiExplain.value = result
  } catch (error) { if (current(ctx) && sequence === explainSequence && detail === detailVersion) aiExplainError.value = error?.msg || error?.message || '诊断失败，请重试。' }
  finally { if (current(ctx) && sequence === explainSequence && detail === detailVersion) aiExplainLoading.value = false }
}
function statusLabel(status) { return ({ HIT: '命中', MISS: '未命中', SKIP: '未参与', FALLBACK: '兜底候选', FALLBACK_HIT: '兜底命中' })[status] || status }
function formatTime(value) { return formatFull(value) }
function formatJson(value) { try { return JSON.stringify(value, null, 2) } catch { return String(value) } }
function formatBody(value) { if (!value) return ''; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }
const actionColors = { CREATE: ['var(--el-color-success-light-9)', 'var(--app-text-success)'], UPDATE: ['var(--app-bg-selected)', 'var(--app-text-accent)'], DELETE: ['var(--el-color-danger-light-9)', 'var(--app-text-danger)'], TOGGLE: ['var(--el-color-warning-light-9)', 'var(--app-text-warning)'], IMPORT: ['var(--app-bg-selected)', 'var(--app-text-accent)'] }
function actionTagStyle(action) { const [backgroundColor, color] = actionColors[action] || ['var(--app-bg-control)', 'var(--app-text-secondary)']; return { backgroundColor, color } }
function actionLabel(action) { return ({ CREATE: '创建', UPDATE: '更新', DELETE: '删除', TOGGLE: '切换', IMPORT: '导入' })[action] || action }
function statusCodeStyle(code) { const scheme = code >= 200 && code < 300 ? ['var(--el-color-success-light-9)', 'var(--app-text-success)'] : code >= 400 && code < 500 ? ['var(--el-color-warning-light-9)', 'var(--app-text-warning)'] : code >= 500 ? ['var(--el-color-danger-light-9)', 'var(--app-text-danger)'] : ['var(--app-bg-control)', 'var(--app-text-secondary)']; return { backgroundColor: scheme[0], color: scheme[1] } }
watch([selectedTeamId, canRead, logType, () => userStore.user?.id], resetContext, { immediate: true, flush: 'sync' })
watch(detailVisible, visible => { if (!visible) clearDetail() }, { flush: 'sync' })
watch(canExplain, allowed => { if (!allowed) { explainSequence++; aiExplain.value = null; aiExplainError.value = ''; aiExplainLoading.value = false } }, { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; contextVersion++; listSequence++; detailVisible.value = false; clearDetail() })
</script>

<style lang="scss" scoped>
.page-log-view { min-width: 0; color: var(--app-text-primary); }.resource-header { margin-bottom: 20px; }.resource-header h1 { margin: 0 0 6px; font-size: 26px; font-weight: 600; letter-spacing: -.025em; }.resource-header p { margin: 0; font-size: 13px; color: var(--app-text-secondary); }
.log-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px; margin-bottom: 16px; }.log-tabs { display: flex; gap: 4px; background: var(--app-bg-control); border-radius: 10px; padding: 4px; }.log-tab { padding: 8px 18px; font: inherit; border: 0; border-radius: 8px; color: var(--app-text-secondary); cursor: pointer; background: var(--app-bg-control); }.log-tab:hover { color: var(--app-text-primary); }.log-tab.active { color: var(--app-accent); background: var(--app-bg-surface); }.toolbar-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }.team-context { font-size: 12px; color: var(--app-text-secondary); padding: 8px 12px; background: var(--app-bg-surface); border-radius: 8px; }
.log-card { min-width: 0; background: var(--app-bg-surface); border-radius: 16px; padding: 20px; }.target-tag { margin-right: 6px; border-color: transparent; background: var(--app-bg-selected); color: var(--app-text-accent); }
.cell-title { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.action-tag, .status-code-tag { display: inline-block; padding: 3px 8px; border-radius: 8px; font-size: var(--app-font-caption); font-weight: 500; line-height: 1.6; white-space: nowrap; }.duration-text { color: var(--app-text-secondary); font-size: 12px; }.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; padding-top: 12px; min-width: 0; }.empty-state { padding: 36px 16px; text-align: center; color: var(--app-text-secondary); font-size: 13px; }.empty-state p { margin: 0; }.resource-state { padding: 32px 20px; border-radius: 16px; background: var(--app-bg-surface); color: var(--app-text-secondary); text-align: center; font-size: 13px; }.resource-state p { margin: 12px 0; }
.detail-content { display: flex; flex-direction: column; gap: 18px; min-width: 0; }.detail-label { font-size: 13px; font-weight: 500; color: var(--app-text-secondary); margin-bottom: 8px; }.detail-value { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }.detail-path { font: 13px var(--app-font-mono); color: var(--app-text-primary); overflow-wrap: anywhere; min-width: 0; }.detail-pre { margin: 0; padding: 14px; background: var(--app-bg-control); border-radius: 10px; color: var(--app-text-regular); font: 12px/1.8 var(--app-font-mono); max-height: 300px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
.ai-diagnosis { padding-top: 4px; min-width: 0; }.ai-diagnosis__head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }.ai-diagnosis__body { margin-top: 12px; display: flex; flex-direction: column; gap: 12px; }.ai-verdict, .ai-suggestion { padding: 14px; border-radius: 10px; background: var(--app-bg-control); }.ai-verdict__title { font-size: 14px; font-weight: 600; color: var(--app-text-primary); }.ai-verdict__desc, .ai-suggestion__text { font-size: 13px; line-height: 1.8; margin-top: 5px; color: var(--app-text-regular); overflow-wrap: anywhere; }.ai-chain { display: flex; flex-direction: column; gap: 14px; }.ai-chain__item { display: flex; align-items: flex-start; gap: 10px; }.ai-chain__dot { width: 24px; height: 24px; border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-regular); font-size: var(--app-font-caption); font-weight: 500; display: grid; place-items: center; flex-shrink: 0; }.ai-chain__dot--hit, .ai-chain__dot--fallback_hit { color: var(--app-text-success); background: var(--el-color-success-light-9); }.ai-chain__dot--fallback { color: var(--app-text-warning); background: var(--el-color-warning-light-9); }.ai-chain__content { min-width: 0; flex: 1; }.ai-chain__title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 13px; font-weight: 500; }.ai-chain__badge { padding: 3px 8px; border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-secondary); font-size: var(--app-font-caption); line-height: 1.6; }.ai-chain__desc { color: var(--app-text-secondary); margin-top: 4px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }.ai-suggestion__title { display: flex; align-items: center; gap: 6px; color: var(--app-accent); font-size: 13px; font-weight: 500; }.ai-diagnosis__note, .ai-diagnosis__empty { color: var(--app-text-secondary); font-size: 12px; line-height: 1.8; }.ai-diagnosis__empty { padding-top: 12px; }
@media(max-width:600px){.resource-header h1{font-size:24px}.log-card{padding:12px}.log-tabs{width:100%}.log-tab{flex:1}.pagination-wrapper{justify-content:flex-start}.toolbar-actions{width:100%;justify-content:space-between}}
</style>