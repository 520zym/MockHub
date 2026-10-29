<template>
  <el-drawer :model-value="modelValue" class="api-history-drawer" size="min(1120px, 100vw)" :destroy-on-close="true" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <template #header>
      <div class="history-heading"><span class="history-heading__icon"><FilledIcon name="history" :size="22" /></span><div><h2>变更记录</h2><p>{{ teamName }} · {{ currentSnapshot?.config?.method || '接口' }} {{ currentSnapshot?.config?.path || '' }} · 发布快照不可原位编辑</p></div><span class="history-count">{{ versions.length }} 个版本</span></div>
    </template>
    <div v-if="error" class="history-notice history-notice--error" role="alert">{{ error }} <el-button text @click="loadHistory">重新加载记录</el-button></div>
    <div v-if="conflict || outOfDate" class="history-notice history-notice--warning" role="alert">{{ conflict || '服务器已有新版本。请关闭历史并刷新接口，核对后重新确认；不会自动使用新版本重试。' }}</div>
    <div v-if="!canWrite" class="history-notice">{{ deleted ? '接口在回收站中，仅可查看历史；有编辑权限的成员须先恢复，再从活跃接口回滚。' : '当前为只读或归档上下文，可以查看和比较历史，不能回滚。' }}</div>
    <div class="history-layout" v-loading="loading">
      <aside class="history-versions" aria-label="接口版本列表">
        <div class="history-section-title"><h3>版本历史</h3><span>最新在前</span></div>
        <button v-for="version in versions" :key="version.id" type="button" class="version-button" :class="{ 'is-selected': selectedId === version.id }" :aria-current="selectedId === version.id ? 'true' : undefined" @click="selectVersion(version.id)">
          <span class="version-number">v{{ version.revision }}</span><span class="version-copy"><b>{{ historyAction(version.action) }}</b><span>{{ version.actorNameSnapshot || '系统' }} · {{ sourceLabel(version.source) }}</span><time>{{ formatDate(version.createdAt) }}</time></span>
        </button>
        <p v-if="!loading && !versions.length" class="history-empty">没有可读取的版本记录。</p>
        <p class="history-retention">只展示留存窗口内的版本。升级基线不是伪造的旧修改历史。</p>
      </aside>
      <main class="history-comparison" v-loading="detailLoading">
        <div class="history-mobile-selector"><label for="history-version-select">查看版本</label><el-select id="history-version-select" :model-value="selectedId" aria-label="查看版本" @update:model-value="selectVersion"><el-option v-for="version in versions" :key="version.id" :label="'v' + version.revision + ' · ' + historyAction(version.action)" :value="version.id" /></el-select></div>
        <template v-if="selectedVersion && selectedSnapshot && currentSnapshot">
          <div class="comparison-heading"><div><h3>v{{ selectedVersion.revision }} 与当前发布 v{{ latestVersion?.revision }} 对照</h3><p>{{ selectedVersion.actorNameSnapshot || '系统' }} · {{ sourceLabel(selectedVersion.source) }} · {{ formatDate(selectedVersion.createdAt) }}</p></div><el-checkbox v-model="onlyChanged" :disabled="!comparisonSupported">仅看差异</el-checkbox></div>
          <div v-if="!comparisonSupported" class="history-notice history-notice--warning" role="alert">当前快照结构版本尚不支持比较，不能推断其差异或回滚。可下载完整原始快照核对。</div>
          <div class="comparison-legend"><span>所选版本 v{{ selectedVersion.revision }}</span><span>当前发布 v{{ latestVersion?.revision }}</span></div>
          <section v-for="group in visibleGroups" :key="group.key" class="comparison-group">
            <div class="comparison-group__heading"><h4>{{ group.title }}</h4><span v-if="group.added" class="change-badge">当前新增</span><span v-else-if="group.removed" class="change-badge change-badge--removed">当前已移除</span><span v-else class="change-badge" :class="{ 'is-equal': !group.changed }">{{ group.changed ? '配置有差异' : '相同' }}</span></div>
            <div v-for="field in group.rows" :key="field.key" class="comparison-field" :class="{ 'has-change': field.changed }"><h5>{{ field.label }}</h5><div class="comparison-values"><div><small>所选版本</small><pre>{{ field.beforePreview.text }}</pre></div><div><small>当前发布</small><pre>{{ field.afterPreview.text }}</pre></div></div></div>
          </section>
          <div v-if="comparisonSupported && !visibleGroups.length" class="history-empty"><FilledIcon name="shield" :size="28" /><h3>两个版本没有配置差异</h3><p>版本仍保留实际操作人、来源和时间；不会人为生成差异。</p></div>
        </template>
        <div v-else-if="!loading && !detailLoading && versions.length" class="history-empty">选择一个版本读取真实快照。</div>
      </main>
    </div>
    <template #footer>
      <div class="history-footer"><p>回滚会生成新版本，不还原团队、创建人、调用计数或删除状态。</p><div><el-button :disabled="!selectedSnapshot || detailLoading" @click="downloadSnapshot">下载所选快照</el-button><el-button type="primary" :disabled="!canRollback" @click="prepareRollback">回滚到 v{{ selectedVersion?.revision || '—' }}</el-button></div></div>
    </template>
  </el-drawer>
  <el-dialog v-model="confirmOpen" title="确认回滚配置" width="min(460px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!rollingBack" :show-close="!rollingBack" append-to-body>
    <p class="rollback-confirm-copy">{{ frozenRollback?.teamName }} · {{ frozenRollback?.path }}</p><p class="rollback-confirm-copy">将所选快照 <b>v{{ frozenRollback?.targetRevision }}</b> 写为一个新版本。当前发布版本为 <b>v{{ frozenRollback?.expectedRevision }}</b>，确认期间若发生并发修改，本次回滚会被拒绝。</p><p class="rollback-confirm-copy">不会删除历史，也不会改变资源归属、创建人、运行统计或回收站状态。</p>
    <template #footer><el-button :disabled="rollingBack" @click="confirmOpen = false">取消</el-button><el-button type="primary" :loading="rollingBack" :disabled="!canWrite || !!conflict" @click="confirmRollback">确认回滚</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getApiRevisions, getApiRevision, rollbackApiRevision } from '@/api/apis'
import { requireRevision } from '@/utils/revisions'
import { formatDate, historyAction, historyComparisons, isVersionConflict, readableError } from '@/utils/api-lifecycle'

const props = defineProps({ modelValue: Boolean, apiId: { type: String, default: '' }, teamId: { type: String, default: '' }, currentRevision: { type: Number, default: 0 }, readOnly: Boolean, deleted: Boolean, capabilities: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'changed'])
const userStore = useUserStore(), appStore = useAppStore()
const versions = ref([]), selectedId = ref(''), selectedSnapshot = ref(null), currentSnapshot = ref(null)
const loading = ref(false), detailLoading = ref(false), error = ref(''), conflict = ref(''), onlyChanged = ref(true)
const confirmOpen = ref(false), frozenRollback = ref(null), rollingBack = ref(false)
let generation = 0, detailRequest = 0
const cache = new Map()
const canRead = computed(() => !!props.teamId && userStore.hasCapability(props.teamId, 'read') && props.capabilities?.read !== false)
const canWrite = computed(() => canRead.value && !props.readOnly && !props.deleted && props.capabilities?.write !== false && userStore.hasCapability(props.teamId, 'write'))
const teamName = computed(() => appStore.teams.find(team => team.id === props.teamId)?.name || '当前团队')
const contextKey = computed(() => [props.apiId, props.teamId, props.currentRevision, props.readOnly, props.deleted, userStore.token, userStore.user?.id, userStore.identityReady, canRead.value, canWrite.value].join('|'))
const latestVersion = computed(() => versions.value[0] || null)
const selectedVersion = computed(() => versions.value.find(version => version.id === selectedId.value) || null)
const outOfDate = computed(() => !!latestVersion.value && latestVersion.value.revision !== props.currentRevision)
const canRollback = computed(() => comparisonSupported.value && canWrite.value && !!selectedSnapshot.value && selectedVersion.value?.snapshotSchemaVersion === 1 && Number.isSafeInteger(props.currentRevision) && props.currentRevision > 0 && selectedVersion.value.id !== latestVersion.value?.id && !loading.value && !detailLoading.value && !outOfDate.value && !conflict.value)
const comparisonSupported = computed(() => selectedVersion.value?.snapshotSchemaVersion === 1 && latestVersion.value?.snapshotSchemaVersion === 1 && selectedSnapshot.value?.schemaVersion === 1 && currentSnapshot.value?.schemaVersion === 1)
const visibleGroups = computed(() => !comparisonSupported.value ? [] : historyComparisons(selectedSnapshot.value, currentSnapshot.value).map(group => ({ ...group, rows: onlyChanged.value ? group.rows.filter(field => field.changed) : group.rows })).filter(group => !onlyChanged.value || group.rows.length))
const sourceLabel = source => ({ WEB: '网页', AI: 'AI 确认', MCP: 'MCP', IMPORT: '导入', MIGRATION: '系统迁移', SYSTEM: '系统维护' }[source] || source || '未知来源')
function ticket() { return { generation, key: contextKey.value, apiId: props.apiId, teamId: props.teamId } }
function live(scope) { return props.modelValue && scope.generation === generation && scope.key === contextKey.value && canRead.value }
function clear() { generation++; detailRequest++; versions.value = []; selectedId.value = ''; selectedSnapshot.value = null; currentSnapshot.value = null; cache.clear(); error.value = ''; conflict.value = ''; loading.value = false; detailLoading.value = false; confirmOpen.value = false; frozenRollback.value = null; rollingBack.value = false }
watch([() => props.modelValue, contextKey], () => {
  clear()
  if (props.modelValue && props.apiId && props.teamId && canRead.value) loadHistory()
  else if (props.modelValue && !canRead.value) emit('update:modelValue', false)
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
async function readSnapshot(id, scope) {
  if (cache.has(id)) return cache.get(id)
  const result = await getApiRevision(scope.apiId, id)
  if (!live(scope)) return null
  if (result.apiId !== scope.apiId || result.teamId !== scope.teamId) throw new Error('历史记录不属于当前资源，已拒绝显示')
  if (!result.snapshot || typeof result.snapshot !== 'object') throw new Error('该版本没有有效快照')
  if (cache.size >= 3) for (const key of cache.keys()) if (key !== latestVersion.value?.id) { cache.delete(key); break }
  cache.set(id, result.snapshot)
  return result.snapshot
}
async function loadHistory() {
  if (!props.modelValue || !props.apiId || !props.teamId || !canRead.value) return
  const scope = ticket(); loading.value = true; error.value = ''
  try {
    const list = await getApiRevisions(scope.apiId)
    if (!live(scope)) return
    if (!Array.isArray(list) || list.some(version => version.apiId !== scope.apiId || version.teamId !== scope.teamId)) throw new Error('历史记录团队范围不匹配，已拒绝显示')
    versions.value = [...list].sort((a, b) => b.revision - a.revision)
    if (!versions.value.length) return
    currentSnapshot.value = await readSnapshot(versions.value[0].id, scope)
    if (!live(scope)) return
    await selectVersion(versions.value[1]?.id || versions.value[0].id)
  } catch (failure) { if (live(scope)) { error.value = readableError(failure, '历史记录加载失败'); selectedSnapshot.value = null; currentSnapshot.value = null } }
  finally { if (live(scope)) loading.value = false }
}
async function selectVersion(id) {
  if (!versions.value.some(version => version.id === id)) return
  const scope = ticket(), request = ++detailRequest
  selectedId.value = id; selectedSnapshot.value = null; detailLoading.value = true; error.value = ''
  try { const snapshot = await readSnapshot(id, scope); if (live(scope) && request === detailRequest) selectedSnapshot.value = snapshot }
  catch (failure) { if (live(scope) && request === detailRequest) error.value = readableError(failure) }
  finally { if (live(scope) && request === detailRequest) detailLoading.value = false }
}
function prepareRollback() {
  if (!canRollback.value) return
  frozenRollback.value = Object.freeze({ ...ticket(), revisionId: selectedVersion.value.id, targetRevision: selectedVersion.value.revision, expectedRevision: requireRevision(props.currentRevision), teamName: teamName.value, path: (currentSnapshot.value?.config?.method || '') + ' ' + (currentSnapshot.value?.config?.path || '') })
  confirmOpen.value = true
}
async function confirmRollback() {
  const scope = frozenRollback.value
  if (!scope || !live(scope) || !canWrite.value || scope.expectedRevision !== props.currentRevision) { confirmOpen.value = false; return }
  rollingBack.value = true
  try {
    const result = await rollbackApiRevision(scope.apiId, scope.revisionId, scope.expectedRevision)
    if (!live(scope)) return
    confirmOpen.value = false; ElMessage.success('已生成回滚新版本 v' + result.revision)
    emit('changed', { ...result, apiId: scope.apiId, teamId: scope.teamId, reason: 'rollback' }); emit('update:modelValue', false)
  } catch (failure) {
    if (live(scope)) { if (isVersionConflict(failure)) conflict.value = '接口版本已变化。当前编辑未被替换；请关闭历史并刷新，核对后重新确认。'; else error.value = readableError(failure); confirmOpen.value = false }
  } finally { if (live(scope)) rollingBack.value = false }
}
function downloadSnapshot() {
  if (!selectedSnapshot.value || !canRead.value) return
  const blob = new Blob([JSON.stringify({ apiId: props.apiId, revision: selectedVersion.value?.revision, snapshot: selectedSnapshot.value }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = 'mockhub-revision-' + selectedVersion.value.revision + '.json'; link.click(); URL.revokeObjectURL(url)
}
</script>

<style scoped>
.history-heading{display:flex;align-items:center;gap:12px;min-width:0}.history-heading__icon{display:grid;place-items:center;width:44px;height:44px;border-radius:12px;background:var(--app-bg-control);flex:none}.history-heading h2{font-size:22px;line-height:1.3;margin:0;color:var(--app-text-primary)}.history-heading>div{min-width:0}.history-heading p{overflow-wrap:anywhere;line-height:1.7}.history-heading p,.comparison-heading p{margin:6px 0 0;font-size:12px;color:var(--app-text-secondary)}.history-count{margin-left:auto;border-radius:8px;background:var(--app-bg-control);padding:6px 9px;white-space:nowrap;font-size:12px;color:var(--app-text-secondary)}.history-notice{background:var(--app-bg-control);padding:12px 14px;border-radius:10px;font-size:13px;color:var(--app-text-regular);margin-bottom:12px;line-height:1.65}.history-notice--warning{background:var(--el-color-warning-light-9);color:var(--el-color-warning-dark-2)}.history-notice--error{background:var(--el-color-danger-light-9);color:var(--el-color-danger-dark-2)}.history-layout{display:grid;grid-template-columns:220px minmax(0,1fr);gap:22px;flex:1;min-height:0}.history-versions,.history-comparison{min-width:0;overflow:auto}.history-section-title{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}.history-section-title h3,.comparison-heading h3{font-size:15px;margin:0;color:var(--app-text-primary)}.history-section-title span{font-size:11px;color:var(--app-text-secondary)}.version-button{border:0;text-align:left;background:transparent;border-radius:11px;padding:13px 10px;width:100%;display:flex;gap:10px;color:var(--app-text-regular);font:inherit;cursor:pointer}.version-button:hover{background:var(--app-bg-control)}.version-button.is-selected{background:var(--app-bg-selected)}.version-number{font-family:var(--app-font-mono);font-size:13px;color:var(--app-accent);padding-top:2px;min-width:28px}.version-copy{display:flex;min-width:0;flex-direction:column;gap:5px}.version-copy b{font-size:13px;font-weight:600;overflow-wrap:anywhere}.version-copy span,.version-copy time{font-size:11px;color:var(--app-text-secondary)}.history-retention{color:var(--app-text-secondary);font-size:11px;line-height:1.7;padding:8px 10px}.comparison-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin:0 0 16px}.comparison-heading :deep(.el-checkbox){height:24px;flex:none}.comparison-legend{display:grid;grid-template-columns:1fr 1fr;gap:12px;font-size:12px;color:var(--app-text-secondary);margin-bottom:14px}.comparison-legend span{padding:10px 12px;background:var(--app-bg-control);border-radius:8px}.comparison-group{padding:0 0 18px;margin-bottom:16px;border-bottom:1px solid var(--app-divider)}.comparison-group__heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.comparison-group h4{font-size:14px;margin:0;overflow-wrap:anywhere;color:var(--app-text-primary)}.change-badge{flex:none;background:var(--el-color-success-light-9);color:var(--el-color-success-dark-2);border-radius:6px;padding:4px 7px;font-size:10px}.change-badge--removed{background:var(--el-color-danger-light-9);color:var(--el-color-danger-dark-2)}.change-badge.is-equal{background:var(--app-bg-control);color:var(--app-text-secondary)}.comparison-field{margin-top:14px}.comparison-field h5{font-size:12px;font-weight:500;color:var(--app-text-secondary);margin:0 0 6px}.comparison-values{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}.comparison-values>div{min-width:0;border-radius:9px;background:var(--app-bg-control);padding:10px 12px}.comparison-field.has-change .comparison-values>div:first-child{background:var(--el-color-danger-light-9)}.comparison-field.has-change .comparison-values>div:last-child{background:var(--el-color-success-light-9)}.comparison-values pre{font:12px/1.65 var(--app-font-mono);white-space:pre-wrap;overflow-wrap:anywhere;margin:0;max-height:290px;overflow:auto;color:var(--app-text-regular)}.comparison-values small{display:none}.history-empty{display:flex;align-items:center;justify-content:center;flex-direction:column;padding:40px 14px;gap:10px;color:var(--app-text-secondary);font-size:13px;text-align:center}.history-empty h3{font-size:15px;margin:0;color:var(--app-text-primary)}.history-empty p{margin:0;line-height:1.8}.history-footer{display:flex;justify-content:space-between;align-items:center;gap:16px;text-align:left}.history-footer p{font-size:12px;color:var(--app-text-secondary);margin:0;line-height:1.7}.history-footer>div{display:flex;gap:8px;flex:none}.history-footer :deep(.el-button+.el-button){margin-left:0}.history-mobile-selector{display:none}.rollback-confirm-copy{font-size:14px;line-height:1.9;color:var(--app-text-regular);overflow-wrap:anywhere}
@media(min-width:701px) and (max-width:900px){.history-layout{grid-template-columns:minmax(0,1fr)}.history-versions{display:none}.history-mobile-selector{display:flex;align-items:center;gap:12px;font-size:12px;color:var(--app-text-secondary);margin-bottom:18px}.history-mobile-selector :deep(.el-select){flex:1;max-width:380px}}
@media(max-width:700px){.history-layout{grid-template-columns:minmax(0,1fr);gap:0}.history-versions{display:none}.history-mobile-selector{display:flex;align-items:center;gap:12px;margin-bottom:18px;font-size:12px;color:var(--app-text-secondary)}.history-mobile-selector :deep(.el-select){flex:1;min-width:0}.history-heading h2{font-size:20px}.history-heading p{font-size:11px}.history-count{display:none}.history-footer{flex-direction:column;align-items:stretch;gap:10px}.history-footer>div{display:grid;grid-template-columns:1fr 1fr}.comparison-heading{flex-direction:column;gap:8px}.comparison-values{grid-template-columns:1fr;gap:7px}.comparison-values small{display:block;font-size:10px;color:var(--app-text-secondary);margin-bottom:6px}.comparison-legend{display:none}.comparison-values pre{font-size:11px;max-height:220px}}
</style>
<style>
.api-history-drawer{transition-property:transform!important}.api-history-drawer .el-drawer__header{margin:0;padding:22px 22px 18px}.api-history-drawer .el-drawer__body{display:flex;flex-direction:column;min-height:0;overflow:hidden;padding:0 22px 12px}.api-history-drawer .el-drawer__footer{padding:14px 22px 20px;background:var(--app-bg-surface)}
@media(max-width:700px){.api-history-drawer{transition-property:transform!important}.api-history-drawer .el-drawer__header{padding:18px 16px}.api-history-drawer .el-drawer__body{padding:0 16px 10px}.api-history-drawer .el-drawer__footer{padding:12px 16px 16px}}
</style>
