<template>
  <div class="mcp-confirm-page" v-loading="loading">
    <div class="page-header">
      <el-button class="back-button" text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="page-title">AI 草稿确认</span>
    </div>

    <div v-if="action" class="soft-card mcp-card">
      <header class="mcp-card__head">
        <div class="mcp-card__icon" :class="statusClass">
          <el-icon>
            <Check v-if="action.status === 'CONFIRMED'" />
            <Clock v-else-if="action.status === 'EXPIRED' || expired" />
            <EditPen v-else />
          </el-icon>
        </div>
        <div class="mcp-card__title-wrap">
          <div class="mcp-card__title">
            {{ title }}
            <span class="mcp-status" :class="statusClass">{{ statusLabel }}</span>
          </div>
          <div class="mcp-card__desc">
            {{ isUpdate ? 'AI 生成的接口修改草稿，确认后才会更新 MockHub' : (action.actionType === 'CREATE_API' ? 'AI 生成的接口草稿，确认后才会写入 MockHub' : action.actionType) }}
            <template v-if="action.expiresAt"> · 有效至 {{ formatTime(action.expiresAt) }}</template>
          </div>
        </div>
        <el-button text @click="load">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </header>

      <div class="mcp-card__body">
        <div class="mcp-grid">
          <div>
            <div class="mcp-k">方法 / 路径</div>
            <div class="mcp-v">
              <code>{{ action.summary?.method }}</code>
              <code>{{ action.summary?.path }}</code>
            </div>
          </div>
          <div>
            <div class="mcp-k">名称 / 团队</div>
            <div class="mcp-v">{{ action.summary?.name }} · {{ teamLabel(action.summary?.teamId) }}</div>
          </div>
        </div>

        <template v-if="action.responses && action.responses.length > 1">
          <div class="mcp-responses-head">
            <div class="mcp-k">返回体规则（{{ action.responses.length }} 个）</div>
            <span class="mcp-mode">{{ action.responseMode === 'RANDOM' ? '随机返回' : '条件匹配' }}</span>
          </div>
          <div class="mcp-responses">
            <div v-for="(res, index) in action.responses" :key="index" class="mcp-response">
              <div class="mcp-response__head">
                <span class="mcp-response__index">{{ index + 1 }}</span>
                <span class="mcp-response__name">{{ res.name || '返回体 ' + (index + 1) }}</span>
                <span class="mcp-response__state" :class="{ 'is-off': res.active === false }">{{ res.active === false ? '未启用' : '启用' }}</span>
                <code class="mcp-response__code">{{ res.responseCode || 200 }}</code>
              </div>
              <div class="mcp-response__conditions" :class="{ 'is-fallback': !res.conditions }">
                {{ formatConditions(res.conditions) }}
              </div>
              <pre class="mcp-response__body">{{ res.body || '（空响应体）' }}</pre>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="mcp-k" style="margin-top: 16px">{{ isUpdate ? '修改后预览' : '响应体预览' }}</div>
          <pre class="mcp-preview">{{ action.preview || '（无预览）' }}</pre>
        </template>

        <el-alert
          v-if="error"
          type="error"
          :closable="false"
          show-icon
          :title="error"
          style="margin-top: 14px"
        />

        <div class="mcp-actions">
          <template v-if="action.status === 'PENDING' && !expired">
            <el-button :loading="cancelling" @click="handleCancel">取消草稿</el-button>
            <el-button type="primary" :loading="confirming" @click="handleConfirm">{{ isUpdate ? '确认修改' : '确认创建' }}</el-button>
          </template>
          <template v-else-if="action.status === 'CONFIRMED' && action.resultApiId">
            <el-button type="primary" plain @click="openApi">查看接口</el-button>
          </template>
          <span v-else class="mcp-muted">该草稿已不可操作，请让 AI 重新生成。</span>
        </div>
      </div>
    </div>

    <div v-else-if="!loading" class="soft-card mcp-empty">
      <el-empty description="草稿不存在、已过期或无权访问" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Clock, EditPen, Refresh } from '@element-plus/icons-vue'
import { getAiAction, confirmAiAction, cancelAiAction } from '@/api/ai'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const confirming = ref(false)
const cancelling = ref(false)
const action = ref(null)
const error = ref('')

const isUpdate = computed(() => action.value?.actionType === 'UPDATE_API' || action.value?.actionType === 'UPDATE_API_FULL')

const expired = computed(() => {
  if (!action.value?.expiresAt) return false
  return new Date(action.value.expiresAt).getTime() < Date.now()
})

const statusLabel = computed(() => {
  if (!action.value) return ''
  if (action.value.status === 'CONFIRMED') return isUpdate.value ? '已更新' : '已创建'
  if (action.value.status === 'CANCELLED') return '已取消'
  if (action.value.status === 'EXPIRED' || expired.value) return '已过期'
  return '待确认'
})

const statusClass = computed(() => {
  if (action.value?.status === 'CONFIRMED') return 'is-done'
  if (action.value?.status === 'CANCELLED' || action.value?.status === 'EXPIRED' || expired.value) return 'is-muted'
  return 'is-pending'
})

const title = computed(() => {
  if (!action.value) return ''
  return action.value.summary?.path || '待确认操作'
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    action.value = await getAiAction(route.params.id)
  } catch (e) {
    action.value = null
    error.value = e?.msg || '加载草稿失败'
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  confirming.value = true
  error.value = ''
  try {
    const result = await confirmAiAction(action.value.id)
    appStore.bumpApiRevision()
    ElMessage.success(isUpdate.value ? '接口已更新' : '接口已创建')
    action.value.status = 'CONFIRMED'
    action.value.resultApiId = result.apiId
    action.value.summary = { ...action.value.summary, ...result }
  } catch (e) {
    error.value = e?.msg || '确认失败，请稍后重试'
  } finally {
    confirming.value = false
  }
}

async function handleCancel() {
  cancelling.value = true
  error.value = ''
  try {
    await cancelAiAction(action.value.id)
    ElMessage.success('已取消')
    action.value.status = 'CANCELLED'
  } catch (e) {
    error.value = e?.msg || '取消失败，请稍后重试'
  } finally {
    cancelling.value = false
  }
}

function openApi() {
  if (action.value?.resultApiId) {
    router.push('/apis/' + action.value.resultApiId + '/edit')
  }
}

function teamLabel(teamId) {
  if (!teamId) return '当前团队'
  const team = appStore.teams.find((item) => item.id === teamId)
  return team ? team.name : '当前团队'
}

function formatConditions(raw) {
  if (!raw) return '兜底返回（无规则）'
  try {
    const data = JSON.parse(raw)
    const rules = Array.isArray(data.conditions) ? data.conditions : []
    if (!rules.length) return '兜底返回（无规则）'
    return rules.map(rule => `${rule.source || 'BODY'} ${rule.path || ''} ${rule.operator || 'EQ'} ${rule.value === undefined ? '' : rule.value}`.trim()).join(' 且 ')
  } catch (e) {
    return raw
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
    + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes())
}
</script>

<style scoped>
.mcp-confirm-page {
  max-width: 760px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 22px;
}

.back-button {
  font-size: 20px;
  color: #4A5568;
  padding: 0;
  height: auto;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1B2559;
  margin: 0;
  line-height: 1;
}

.mcp-card {
  padding: 0;
  overflow: hidden;
}

.mcp-card__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid #F1F5F9;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.05), rgba(139, 92, 246, 0.02));
}

.mcp-card__icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}

.mcp-card__icon.is-pending {
  color: #6D28D9;
  background: #F5F3FF;
  border: 1px solid #E7E3FD;
}

.mcp-card__icon.is-done {
  color: #0F9D6E;
  background: #ECFDF5;
  border: 1px solid #DCF5EC;
}

.mcp-card__icon.is-muted {
  color: #A3AED0;
  background: #F7F8FA;
  border: 1px solid #F1F5F9;
}

.mcp-card__title-wrap {
  flex: 1;
  min-width: 0;
}

.mcp-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #1B2559;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mcp-card__desc {
  font-size: 12px;
  color: #A3AED0;
  margin-top: 3px;
}

.mcp-status {
  font-size: 10.5px;
  font-weight: 600;
  border-radius: 6px;
  padding: 0 7px;
  line-height: 19px;
}

.mcp-status.is-pending {
  color: #6D28D9;
  background: #F5F3FF;
  border: 1px solid #E7E3FD;
}

.mcp-status.is-done {
  color: #0F9D6E;
  background: #ECFDF5;
  border: 1px solid #DCF5EC;
}

.mcp-status.is-muted {
  color: #A3AED0;
  background: #F7F8FA;
  border: 1px solid #F1F5F9;
}

.mcp-card__body {
  padding: 20px 22px;
}

.mcp-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.mcp-k {
  font-size: 11.5px;
  color: #A3AED0;
}

.mcp-v {
  font-size: 13px;
  color: #1B2559;
  font-weight: 500;
  margin-top: 3px;
}

.mcp-v code {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 400;
}

.mcp-responses-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.mcp-mode {
  font-size: 10.5px;
  color: #6366F1;
  background: #EEF0FF;
  border-radius: 4px;
  padding: 1px 6px;
}

.mcp-responses {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
}

.mcp-response {
  border: 1px solid #F1F5F9;
  border-radius: 9px;
  padding: 9px 10px;
  background: #FCFCFE;
}

.mcp-response__head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.mcp-response__index {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #EEF0FF;
  color: #4F46E5;
  font-size: 9px;
  display: grid;
  place-items: center;
}

.mcp-response__name {
  font-size: 12px;
  font-weight: 600;
  color: #1B2559;
}

.mcp-response__state {
  font-size: 9.5px;
  color: #0F9D6E;
  background: #ECFDF5;
  border-radius: 4px;
  padding: 1px 5px;
}

.mcp-response__state.is-off {
  color: #A3AED0;
  background: #F1F5F9;
}

.mcp-response__code {
  margin-left: auto;
  font-size: 10px;
  color: #A3AED0;
}

.mcp-response__conditions {
  margin-top: 5px;
  font-size: 10.5px;
  color: #4F46E5;
  background: #F5F6FF;
  border-radius: 5px;
  padding: 3px 6px;
}

.mcp-response__conditions.is-fallback {
  color: #A3AED0;
  background: #F7F8FA;
}

.mcp-response__body {
  margin: 5px 0 0;
  max-height: 76px;
  overflow: auto;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 10px;
  color: #4A5568;
  white-space: pre-wrap;
  word-break: break-word;
}

.mcp-preview {
  margin: 8px 0 0;
  padding: 12px 14px;
  background: #FAFBFD;
  border: 1px solid #F1F5F9;
  border-radius: 10px;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.8;
  color: #4A5568;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.mcp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #F1F5F9;
}

.mcp-muted {
  font-size: 12px;
  color: #A3AED0;
}

.mcp-empty {
  padding: 40px;
}
</style>