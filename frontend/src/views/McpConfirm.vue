<template>
  <section class="mcp-confirm-page" aria-labelledby="mcp-page-title" :aria-busy="loading">
    <header class="page-header">
      <el-button text class="back-button" aria-label="返回" @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <h1 id="mcp-page-title">AI 草稿确认</h1>
    </header>

    <div v-if="loading" class="mcp-card mcp-state" role="status">正在读取当前账号的草稿…</div>
    <div v-else-if="loadError" class="mcp-card mcp-state" role="alert">
      <h2>无法读取草稿</h2>
      <p>{{ loadError }}</p>
      <el-button :disabled="busy" @click="load">重试读取</el-button>
    </div>
    <article v-else-if="canViewAction" class="mcp-card">
      <header class="mcp-card__head">
        <div class="mcp-card__icon" :class="statusClass"><el-icon><Check v-if="action.status === 'CONFIRMED'" /><Clock v-else-if="expired || action.status === 'EXPIRED'" /><EditPen v-else /></el-icon></div>
        <div class="mcp-card__title-wrap">
          <h2>{{ title }} <span class="mcp-status" :class="statusClass">{{ statusLabel }}</span></h2>
          <p>{{ isUpdate ? '确认后修改已保存的接口' : (action.actionType === 'CREATE_API' ? '确认后创建接口' : '服务端草稿操作：' + action.actionType) }}</p>
          <p>有效至 {{ formatTime(action.expiresAt) }} · 生成时版本 {{ action.baseRevision ?? '未提供' }}</p>
        </div>
        <el-button text :disabled="busy" aria-label="刷新草稿状态" @click="load"><el-icon><Refresh /></el-icon></el-button>
      </header>

      <div class="mcp-card__body">
        <div class="mcp-grid">
          <div><div class="mcp-k">方法 / 路径</div><div class="mcp-v"><code>{{ action.summary?.method || '未提供方法' }}</code><code>{{ action.summary?.path || '未提供路径' }}</code></div></div>
          <div><div class="mcp-k">名称 / 目标团队</div><div class="mcp-v">{{ action.summary?.name || '未提供名称' }} · {{ teamLabel(targetTeamId) }}</div></div>
          <div v-if="action.summary?.responseName"><div class="mcp-k">目标返回体</div><div class="mcp-v">{{ action.summary.responseName }}</div></div>
          <div v-if="action.apiType || action.summary?.apiType || action.summary?.type"><div class="mcp-k">接口类型</div><div class="mcp-v">{{ action.apiType || action.summary.apiType || action.summary.type }}</div></div>
          <div v-if="action.soapOperationName || action.summary?.soapOperationName"><div class="mcp-k">SOAP 操作</div><div class="mcp-v">{{ action.soapOperationName || action.summary.soapOperationName }}</div></div>
        </div>

        <aside v-if="!teamMatches" class="mcp-notice" role="status">
          <p>此草稿的目标是「{{ teamLabel(targetTeamId) }}」，当前侧栏是「{{ teamLabel(appStore.currentTeamId) }}」。不会自动切换或在其他团队确认。</p>
          <el-button :disabled="busy || !canViewAction" @click="switchToTargetTeam">切换到目标团队</el-button>
        </aside>
        <p class="mcp-notice">这里只展示服务端返回的摘要，可能已截断，不代表完整草稿。当前详情接口未完整提供 SOAP 操作列表、响应头或文件信息；未展示不代表草稿中不存在。确认会应用服务端保存的草稿，而非此摘要文本。</p>

        <div v-if="responses.length" class="mcp-responses">
          <div class="mcp-responses-head"><h3>返回体摘要（{{ responses.length }} 个）</h3><span class="mcp-mode">{{ responseModeLabel }}</span></div>
          <p v-if="action.responseMode === 'RANDOM'" class="mcp-muted">随机模式下等概率选择已启用返回体，不按下方规则决定命中。</p>
          <section v-for="(res, index) in responses" :key="index" class="mcp-response">
            <div class="mcp-response__head">
              <span class="mcp-response__index">{{ index + 1 }}</span><h4>{{ res.name || '返回体 ' + (index + 1) }}</h4>
              <span class="mcp-response__state">{{ res.active === true ? '启用' : res.active === false ? '未启用' : '未提供启用状态' }}</span>
              <code>{{ res.responseCode ?? '未提供状态码' }}</code><span v-if="res.bodyType">{{ res.bodyType }}</span>
            </div>
            <p v-if="res.soapOperationName" class="mcp-muted">SOAP 操作：{{ res.soapOperationName }}</p>
            <p class="mcp-response__conditions">{{ formatConditions(res.conditions) }}</p>
            <details v-if="res.conditions"><summary>规则原文</summary><pre class="mcp-preview">{{ res.conditions }}</pre></details>
            <div class="mcp-k">响应体摘要（服务端可能截断）</div>
            <pre class="mcp-preview" tabindex="0">{{ res.body === undefined ? '服务端未提供响应体摘要' : res.body || '（空摘要）' }}</pre>
          </section>
        </div>
        <section v-if="action.preview !== undefined" class="mcp-preview-section">
          <h3>{{ isUpdate ? '修改后摘要（可能截断）' : '默认响应摘要（可能截断）' }}</h3><pre class="mcp-preview" tabindex="0">{{ action.preview || '（空摘要）' }}</pre>
        </section>
        <section v-if="action.oldPreview !== undefined" class="mcp-preview-section">
          <h3>修改前摘要（可能截断）</h3><pre class="mcp-preview" tabindex="0">{{ action.oldPreview || '（空摘要）' }}</pre>
        </section>
        <p v-if="!responses.length && action.preview === undefined" class="mcp-notice">服务端未返回内容摘要，不能据此认定草稿为空。</p>

        <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" role="alert" />
        <p v-if="confirmDisabledReason && action.status === 'PENDING'" class="mcp-notice" role="status">{{ confirmDisabledReason }}</p>
        <p class="mcp-muted">人工确认仍由服务端校验生成时版本、来源凭证与持有人当前权限，不会自动改用最新版本或重试。取消草稿不修改接口；中止本地请求不等于回滚服务端操作。</p>
        <footer class="mcp-actions">
          <template v-if="action.status === 'PENDING'">
            <el-button :loading="cancelling" :disabled="!canCancel" @click="handleCancel">取消我的草稿</el-button>
            <el-button type="primary" :loading="confirming" :disabled="!canConfirm" @click="handleConfirm">{{ isUpdate ? '确认修改' : '确认创建' }}</el-button>
          </template>
          <el-button v-else-if="action.status === 'CONFIRMED' && action.resultApiId" type="primary" :disabled="busy || !teamMatches" @click="openApi">查看接口</el-button>
          <span v-else class="mcp-muted">该草稿当前不可操作。</span>
        </footer>
      </div>
    </article>
    <div v-else class="mcp-card mcp-state"><h2>未返回草稿</h2><p>服务端未返回可读取的草稿详情；不会将读取失败当作空草稿。</p><el-button :disabled="busy" @click="load">重新读取</el-button></div>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Clock, EditPen, Refresh } from '@element-plus/icons-vue'
import request from '@/api/request'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute(), router = useRouter(), appStore = useAppStore(), userStore = useUserStore()
const loading = ref(false), confirming = ref(false), cancelling = ref(false), action = ref(null)
const loadError = ref(''), error = ref(''), failureCode = ref(null), now = ref(Date.now())
let epoch = 0, disposed = false, clock = null, loadedContext = null
const pendingRequests = new Set()
const actionId = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const identityReady = computed(() => userStore.identityReady && !!userStore.token && !!userStore.user?.id && userStore.user?.status !== 'DISABLED')
const scopeKey = computed(() => JSON.stringify([
  actionId.value, appStore.currentTeamId, userStore.token, userStore.identityReady, userStore.user,
  appStore.teams.map(team => [team.id, team.status, userStore.hasCapability(team.id, 'read'), userStore.canWriteTeam(team.id)])
]))
const targetTeamId = computed(() => action.value?.teamId || null)
const targetTeam = computed(() => appStore.teams.find(team => team.id === targetTeamId.value) || null)
const teamMatches = computed(() => !!targetTeamId.value && targetTeamId.value === appStore.currentTeamId)
const busy = computed(() => loading.value || confirming.value || cancelling.value)
const isUpdate = computed(() => ['UPDATE_API', 'UPDATE_API_FULL'].includes(action.value?.actionType))
const supported = computed(() => ['CREATE_API', 'UPDATE_API', 'UPDATE_API_FULL'].includes(action.value?.actionType))
const responses = computed(() => Array.isArray(action.value?.responses) ? action.value.responses : [])
const expiryTime = computed(() => Date.parse(action.value?.expiresAt || ''))
const expired = computed(() => !Number.isFinite(expiryTime.value) || now.value >= expiryTime.value)
const needsRegeneration = computed(() => action.value?.requiresRegeneration === true || action.value?.baseRevision == null)
const sourceOrVersionRejected = computed(() => [40901, 40920, 40921, 40926, 40102, 40305].includes(failureCode.value))
// GET is strict owner + actual-team read on the server. Its authorization proof belongs
// only to this user/session/context; the VO does not expose owner or source-token metadata.
const canViewAction = computed(() => !!action.value && !!loadedContext && current(loadedContext)
  && action.value.id === actionId.value && canReadTeam(targetTeamId.value)
  && (!action.value.summary?.teamId || action.value.summary.teamId === targetTeamId.value)
  && (!action.value.userId || action.value.userId === userStore.user?.id))
const canConfirmAction = computed(() => canViewAction.value && teamMatches.value && targetTeam.value?.status === 'ACTIVE'
  && userStore.canWriteTeam(targetTeamId.value) && action.value.status === 'PENDING' && supported.value
  && !expired.value && !needsRegeneration.value && !action.value.payloadInvalid && !sourceOrVersionRejected.value)
const canCancelAction = computed(() => canViewAction.value && action.value.status === 'PENDING' && !expired.value
  && failureCode.value !== 40901)
const canConfirm = computed(() => canConfirmAction.value && !busy.value)
const canCancel = computed(() => canCancelAction.value && !busy.value)
const title = computed(() => action.value?.summary?.path || '待确认操作')
const responseModeLabel = computed(() => action.value?.responseMode === 'RANDOM' ? '随机返回'
  : action.value?.responseMode === 'CONDITION' ? '按顺序匹配条件' : action.value?.responseMode || '服务端未提供选择策略')
const statusLabel = computed(() => {
  const status = action.value?.status
  if (status === 'CONFIRMED') return isUpdate.value ? '已更新' : '已创建'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'EXPIRED' || (status === 'PENDING' && expired.value)) return '已过期'
  return status === 'PENDING' ? '待确认' : status === 'CONFIRMING' ? '服务端处理中' : status || '未知状态'
})
const statusClass = computed(() => action.value?.status === 'CONFIRMED' ? 'is-done' : action.value?.status === 'PENDING' && !expired.value ? 'is-pending' : 'is-muted')
const confirmDisabledReason = computed(() => {
  if (!canViewAction.value) return '当前账号不能读取此草稿。'
  if (needsRegeneration.value) return '旧草稿缺少生成时版本，请重新生成；不会补取最新版本。'
  if (action.value.payloadInvalid) return '服务端报告草稿内容损坏，不能确认。'
  if (!supported.value) return '此页面不支持确认该操作类型。'
  if (expired.value) return '草稿已过期或未提供有效期限，请重新生成。'
  if (sourceOrVersionRejected.value) return '服务端已拒绝此草稿，请处理上述真实错误并重新生成；不会自动重试。'
  if (!teamMatches.value) return '请先主动切换到上述目标团队，再确认写入。'
  if (targetTeam.value?.status !== 'ACTIVE') return '目标团队不是 ACTIVE 状态，不能确认写入；仍可取消自己的有效草稿。'
  if (!userStore.canWriteTeam(targetTeamId.value)) return '当前账号对目标团队只读，不能确认写入；仍可取消自己的有效草稿。'
  return ''
})

function snapshot() { return { epoch, key: scopeKey.value, id: actionId.value, userId: userStore.user?.id } }
function current(ctx) { return !disposed && identityReady.value && ctx.epoch === epoch && ctx.key === scopeKey.value && ctx.id === actionId.value && ctx.userId === userStore.user?.id }
function canReadTeam(id) { return !!id && identityReady.value && appStore.teams.some(team => team.id === id && team.status !== 'DISABLED') && userStore.hasCapability(id, 'read') }
function invalidate() {
  epoch++
  // Abort only local transport. A server mutation may already have completed.
  for (const controller of pendingRequests) controller.abort()
  pendingRequests.clear()
  loadedContext = null; action.value = null
  loading.value = false; confirming.value = false; cancelling.value = false
}
function actionKey(value) { return JSON.stringify([value.id, value.teamId, value.summary?.teamId, value.actionType, value.status, value.baseRevision, value.expiresAt]) }
function mutationSnapshot() { return { ...snapshot(), action: action.value, actionKey: actionKey(action.value) } }
function currentAction(ctx) { return current(ctx) && action.value === ctx.action && actionKey(action.value) === ctx.actionKey && canViewAction.value }
function errorText(e, fallback) { return e?.response?.data?.msg || e?.msg || e?.message || fallback }
function recordFailure(e, fallback) {
  const message = errorText(e, fallback), code = Number(e?.response?.data?.code ?? e?.code) || null
  if (sourceOrVersionRejected.value) {
    // A failed owner cancellation must not unlock a draft already rejected for its
    // stored version/source. Keep both real errors, without replacing its baseline.
    if (!error.value.includes(message)) error.value = [error.value, message].filter(Boolean).join('；')
    if ([40901, 40920, 40921, 40926, 40102, 40305].includes(code)) failureCode.value = code
  } else { error.value = message; failureCode.value = code }
}
async function call(method, suffix, ctx) {
  if (!current(ctx)) throw new Error('草稿上下文已变化')
  const controller = new AbortController(); pendingRequests.add(controller)
  const url = '/ai/actions/' + encodeURIComponent(ctx.id) + suffix
  try {
    return method === 'get'
      ? await request.get(url, { signal: controller.signal, skipGlobalNetworkError: true })
      : await request.post(url, null, { timeout: 120000, skipGlobalNetworkError: true, signal: controller.signal })
  } finally { pendingRequests.delete(controller) }
}
function validateDetail(detail, ctx) {
  if (detail.id !== ctx.id) throw new Error('服务端返回的草稿标识不一致，已停止展示。')
  if (typeof detail.teamId !== 'string' || !detail.teamId || (detail.summary?.teamId && detail.summary.teamId !== detail.teamId)) throw new Error('草稿的实际团队与摘要不一致或缺失，已停止展示。')
  if (detail.userId && detail.userId !== ctx.userId) throw new Error('不能展示其他账号的草稿。')
  if (!canReadTeam(detail.teamId)) throw new Error('当前账号没有草稿实际目标团队的读取权限。')
}
async function load() {
  if (disposed || confirming.value || cancelling.value) return
  const keepFailure = action.value?.id === actionId.value
  invalidate(); loadError.value = ''
  if (!keepFailure) { error.value = ''; failureCode.value = null }
  if (!identityReady.value) { loadError.value = '请先确认当前登录账号与团队权限。'; return }
  if (!actionId.value) { loadError.value = '缺少有效的草稿标识。'; return }
  const ctx = snapshot(); loading.value = true
  try {
    const detail = await call('get', '', ctx)
    if (!current(ctx)) return
    if (detail == null) return
    validateDetail(detail, ctx)
    loadedContext = ctx; action.value = detail; now.value = Date.now()
  } catch (e) { if (current(ctx)) loadError.value = errorText(e, '加载草稿失败，请重试') }
  finally { if (current(ctx)) loading.value = false }
}
async function handleConfirm() {
  now.value = Date.now()
  if (disposed || !canConfirm.value) return
  const ctx = mutationSnapshot(), update = isUpdate.value
  confirming.value = true; error.value = ''; failureCode.value = null
  try {
    try {
      await ElMessageBox.confirm(
        '将向「' + teamLabel(ctx.action.teamId) + '」' + (update ? '写入接口修改' : '创建接口') + '，服务端会校验草稿生成时保存的版本与来源授权。摘要可能截断，是否确认？',
        update ? '确认修改接口？' : '确认创建接口？',
        { type: 'warning', confirmButtonText: update ? '确认修改' : '确认创建', cancelButtonText: '返回检查', customClass: 'mcp-action-confirm' }
      )
    } catch { return }
    now.value = Date.now()
    if (!currentAction(ctx) || !canConfirmAction.value) return
    const result = await call('post', '/confirm', ctx)
    now.value = Date.now()
    if (!currentAction(ctx) || !canConfirmAction.value) return
    if (result?.status !== 'CONFIRMED' || typeof result.apiId !== 'string' || !result.apiId || (result.teamId && result.teamId !== ctx.action.teamId)) throw new Error('服务端未返回有效的已确认结果，请手动刷新状态核实。')
    action.value = { ...ctx.action, status: result.status, resultApiId: result.apiId,
      summary: { ...ctx.action.summary, name: result.name ?? ctx.action.summary?.name, method: result.method ?? ctx.action.summary?.method, path: result.path ?? ctx.action.summary?.path } }
    appStore.bumpApiRevision()
    ElMessage.success(update ? '接口已更新' : '接口已创建')
  } catch (e) { if (currentAction(ctx)) recordFailure(e, '确认失败，请稍后重试') }
  finally { if (current(ctx)) confirming.value = false }
}
async function handleCancel() {
  now.value = Date.now()
  if (disposed || !canCancel.value) return
  const ctx = mutationSnapshot()
  cancelling.value = true
  if (!sourceOrVersionRejected.value) { error.value = ''; failureCode.value = null }
  try {
    const result = await call('post', '/cancel', ctx)
    now.value = Date.now()
    if (!currentAction(ctx) || !canCancelAction.value) return
    if (result?.status !== 'CANCELLED') throw new Error('服务端未返回已取消状态，请手动刷新草稿状态核实。')
    action.value = { ...ctx.action, status: result.status }
    ElMessage.success('草稿已取消，接口未修改')
  } catch (e) { if (currentAction(ctx)) recordFailure(e, '取消失败，请稍后重试') }
  finally { if (current(ctx)) cancelling.value = false }
}
function switchToTargetTeam() {
  if (disposed || busy.value || !canViewAction.value || teamMatches.value) return
  if (appStore.setFilter(targetTeamId.value) === false) error.value = '无法切换到目标团队，请检查当前团队权限。'
}
function openApi() {
  if (!canViewAction.value || busy.value || !teamMatches.value || action.value.status !== 'CONFIRMED' || !action.value.resultApiId) return
  router.push('/apis/' + encodeURIComponent(action.value.resultApiId) + '/edit')
}
function teamLabel(id) { return appStore.teams.find(team => team.id === id)?.name || (id ? '团队 ' + id : '未选择团队') }
function formatConditions(raw) {
  if (!raw) return '无规则（兜底候选，实际选择由服务端决定）'
  try {
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(data?.conditions)) return typeof raw === 'string' ? raw : JSON.stringify(raw)
    if (!data.conditions.length) return '无规则（兜底候选，实际选择由服务端决定）'
    return data.conditions.map(rule => [rule.source || '未提供来源', rule.path || '', rule.operator || '未提供运算符', rule.value === undefined ? '' : typeof rule.value === 'string' ? rule.value : JSON.stringify(rule.value)].join(' ')).join(' 且 ')
  } catch { return '规则原文（未解析）：' + String(raw) }
}
function formatTime(value) { const date = new Date(value); return Number.isFinite(date.getTime()) ? date.toLocaleString() : '未提供有效期限' }
watch(scopeKey, () => {
  invalidate(); error.value = ''; failureCode.value = null; loadError.value = ''
  void load()
}, { flush: 'sync' })
onMounted(() => { clock = window.setInterval(() => { now.value = Date.now() }, 1000); return load() })
onBeforeUnmount(() => { disposed = true; invalidate(); window.clearInterval(clock) })
</script>

<style scoped>
.mcp-confirm-page { width: 100%; max-width: 860px; min-width: 0; margin: 0 auto; padding-bottom: 20px; color: var(--app-text-primary, #17191D); background: var(--app-bg-page, #EEF1F4); }
.mcp-confirm-page, .mcp-confirm-page * { box-sizing: border-box; }
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
h1, h2, h3, h4, p { margin: 0; }
h1 { font-size: 22px; line-height: 1.5; font-weight: 600; }
.back-button { flex-shrink: 0; font-size: 20px; }
.mcp-card { min-width: 0; padding: 0; border: 0; border-radius: 16px; background: var(--app-bg-surface, #FFFFFF); box-shadow: none; overflow: hidden; }
.mcp-card__head { display: flex; align-items: flex-start; gap: 12px; padding: 22px; background: var(--app-bg-surface, #FFFFFF); }
.mcp-card__icon { display: grid; place-items: center; flex-shrink: 0; width: 38px; height: 38px; border-radius: 10px; font-size: 20px; color: var(--app-accent, #3568D4); background: var(--app-bg-control, #F4F6F9); }
.mcp-card__title-wrap { flex: 1; min-width: 0; }
h2 { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 16px; line-height: 1.7; font-weight: 600; overflow-wrap: anywhere; }
.mcp-card__title-wrap p { margin-top: 4px; font-size: 12px; line-height: 1.7; color: var(--app-text-secondary, #6F757E); overflow-wrap: anywhere; }
.mcp-status, .mcp-mode, .mcp-response__state { display: inline-block; padding: 4px 8px; border-radius: 6px; color: var(--app-text-accent, #2A53AD); background: var(--app-bg-control, #F4F6F9); font-size: 12px; line-height: 1.5; font-weight: 500; }
.is-muted { color: var(--app-text-secondary, #6F757E); }
.is-done { color: var(--app-text-success, #256F55); }
.mcp-card__body { display: flex; flex-direction: column; gap: 16px; min-width: 0; padding: 0 22px 22px; }
.mcp-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.mcp-k { font-size: 12px; line-height: 1.7; color: var(--app-text-secondary, #6F757E); }
.mcp-v { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 5px 8px; margin-top: 4px; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; }
code { max-width: 100%; font: 12px/1.8 var(--app-font-mono, Menlo, monospace); overflow-wrap: anywhere; }
.mcp-notice { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; padding: 12px 14px; border-radius: 10px; color: var(--app-text-regular, #3B4048); background: var(--app-bg-control, #F4F6F9); font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }
h3 { font-size: 14px; font-weight: 500; line-height: 1.8; }
.mcp-responses, .mcp-preview-section { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.mcp-responses-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.mcp-response { display: flex; flex-direction: column; gap: 8px; min-width: 0; padding: 14px; border: 0; border-radius: 12px; background: var(--app-bg-control, #F4F6F9); }
.mcp-response__head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; min-width: 0; font-size: 12px; overflow-wrap: anywhere; }
h4 { max-width: 100%; font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.mcp-response__index { display: grid; place-items: center; flex-shrink: 0; min-width: 24px; height: 24px; border-radius: 6px; color: var(--app-text-accent, #2A53AD); background: var(--app-bg-surface, #FFFFFF); font-size: 12px; }
.mcp-response__conditions, details { font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; color: var(--app-text-regular, #3B4048); }
summary { cursor: pointer; color: var(--app-text-accent, #2A53AD); }
.mcp-preview { min-width: 0; max-width: 100%; max-height: 320px; margin: 0; padding: 12px; border: 0; border-radius: 10px; overflow: auto; overscroll-behavior: contain; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--app-text-regular, #3B4048); background: var(--app-bg-control, #F4F6F9); font: 12px/1.8 var(--app-font-mono, Menlo, monospace); }
.mcp-response .mcp-preview { background: var(--app-bg-surface, #FFFFFF); }
.mcp-muted { font-size: 12px; line-height: 1.8; color: var(--app-text-secondary, #6F757E); overflow-wrap: anywhere; }
.mcp-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 10px; padding-top: 4px; }
.mcp-state { display: flex; flex-direction: column; align-items: flex-start; gap: 16px; padding: 28px; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; }
.mcp-confirm-page :deep(.el-button) { max-width: 100%; height: auto; min-height: 36px; padding: 8px 14px; border: 0; background: var(--app-bg-control, #F4F6F9); box-shadow: none; white-space: normal; }
.mcp-confirm-page :deep(.el-button + .el-button) { margin-left: 0; }
.mcp-confirm-page :deep(.el-button--primary) { color: var(--app-text-inverse, #FFFFFF); background: var(--app-primary, #17191D); }
.mcp-confirm-page :deep(.el-button--primary:not(.is-disabled):hover) { background: var(--app-primary-hover, #30343B); }
.mcp-confirm-page :deep(.el-button.is-disabled) { color: var(--app-text-disabled, #626B76); background: var(--app-bg-disabled, #E9EDF2); }
.mcp-confirm-page :deep(.el-button.is-text) { color: var(--app-text-accent, #2A53AD); background: var(--app-bg-surface, #FFFFFF); }
.mcp-confirm-page :deep(.el-alert) { border: 0; box-shadow: none; }
.mcp-confirm-page :deep(.el-button:focus-visible), .mcp-preview:focus-visible, summary:focus-visible { outline: 2px solid var(--app-focus-color, #3568D4); outline-offset: 2px; }
:global(.el-message-box.mcp-action-confirm) { max-width: calc(100vw - 32px); border: 0; box-shadow: none; background: var(--app-bg-surface, #FFFFFF); }
@media (max-width: 600px) { h1 { font-size: 20px; }.page-header { margin-bottom: 14px; }.mcp-card__head { padding: 16px; gap: 8px; }.mcp-card__body { padding: 0 16px 16px; }.mcp-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }.mcp-state { padding: 20px; }.mcp-response { padding: 12px; } }
</style>
