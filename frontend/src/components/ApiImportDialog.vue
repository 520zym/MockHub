<template>
  <el-dialog :model-value="modelValue" title="导入当前团队接口" width="min(820px, 95vw)" class="api-import-dialog" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" @update:model-value="emit('update:modelValue', $event)">
    <p class="import-scope">目标团队：<b>{{ teamName }}</b></p>
    <template v-if="!result">
      <div class="import-file" @dragover.prevent @drop.prevent="handleDrop"><FilledIcon name="files" :size="30" /><div><b>{{ file?.name || '选择或拖入 JSON / ZIP 导入包' }}</b><span>{{ file ? formatSize(file.size) : '支持 JSON 与完整 ZIP；响应文件或 WSDL 迁移请使用完整 ZIP，最多 100 MB' }}</span></div><label class="import-choose">选择文件<input ref="fileInput" type="file" accept=".json,.zip,application/json,application/zip" :disabled="submitting || previewing" aria-label="选择导入文件" @change="selectFile($event.target.files?.[0])" /></label></div>
      <fieldset class="import-modes"><legend>导入模式</legend><el-radio-group v-model="mode" :disabled="submitting || previewing" @change="invalidatePreview"><el-radio value="merge">合并：同路径与方法跳过</el-radio><el-radio value="override" :disabled="!canOverride">覆盖：仅覆盖同路由接口</el-radio></el-radio-group><p>覆盖不会清空整个团队。仅团队管理员或系统维护身份可以覆盖导入。</p></fieldset>
      <div v-if="error" class="import-feedback import-feedback--error" role="alert">{{ error }}</div>
      <div v-if="versionConflict" class="import-feedback import-feedback--warning" role="alert">目标版本已变化，导入文件仍保留。请重新预览并确认。</div>
      <div v-if="preview" class="import-preview">
        <div class="import-preview__head"><h3>目标团队预览</h3><span>{{ preview.format }}</span></div>
        <div v-if="preview.plan.counts" class="import-counts"><span><b>{{ preview.plan.counts.create }}</b> 预计新增</span><span><b>{{ preview.plan.counts.skip }}</b> 同路由跳过</span><span><b>{{ preview.plan.counts.override }}</b> 预计覆盖</span></div>
        <div v-if="preview.warning" class="import-feedback import-feedback--warning">{{ preview.warning }}<el-checkbox v-model="opaqueAcknowledged">我已知晓无法预览数量，仍继续导入</el-checkbox></div>
        <p v-if="preview.hasTeamHeaders" class="import-feedback">包内团队全局响应头不会导入。</p>
        <div v-if="preview.plan.items.length" class="import-routes" aria-label="导入路由预览"><div v-for="item in preview.plan.items.slice(0, 100)" :key="item.sourceId" class="import-route"><span class="import-status" :class="item.status">{{ statusLabels[item.status] }}</span><div><code>{{ item.method }} {{ item.path }}</code><span>{{ item.name }}</span></div><small v-if="item.targetRevision">目标 v{{ item.targetRevision }}</small><small v-else>新接口</small></div></div>
        <p v-if="preview.plan.items.length > 100" class="import-note">仅展示前 100 项路由，共 {{ preview.plan.items.length }} 项。</p>
        <p v-if="!preview.plan.opaque && !preview.plan.items.length" class="import-note">文件没有接口可导入。</p>
        <p v-if="preview.plan.counts && preview.plan.counts.create + preview.plan.counts.override === 0 && preview.plan.items.length" class="import-note">全部为已存在路由，无需重复提交。</p>
      </div>
    </template>
    <div v-else class="import-result" role="status"><h3>导入完成</h3><div class="import-counts"><span><b>{{ result.imported }}</b> 实际新增</span><span><b>{{ result.skipped }}</b> 实际跳过</span><span><b>{{ result.overridden }}</b> 实际覆盖</span></div><section v-if="result.warnings?.length" class="import-feedback import-feedback--warning"><b>导入提醒</b><ul><li v-for="(warning, index) in result.warnings" :key="index">{{ warning }}</li></ul></section></div>
    <template #footer><div class="import-footer"><div><el-button :disabled="submitting" @click="emit('update:modelValue', false)">{{ result ? '关闭并返回列表' : '取消' }}</el-button><el-button v-if="!result" :loading="previewing" :disabled="!file || !canWrite || submitting" @click="preparePreview">{{ preview || versionConflict ? '重新预览目标版本' : '预览目标版本' }}</el-button><el-button v-if="!result" type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">确认导入{{ mode === 'override' ? '并覆盖' : '' }}</el-button></div></div></template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import FilledIcon from '@/components/FilledIcon.vue'
import { getApis, importApis } from '@/api/apis'
import { fetchTargetVersions, planImport, readImportManifest } from '@/utils/api-import'
import { isVersionConflict, readableError } from '@/utils/api-lifecycle'
const props = defineProps({ modelValue: Boolean, teamId: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'imported'])
const user = useUserStore(), app = useAppStore()
const file = ref(null), fileInput = ref(null), mode = ref('merge'), preview = ref(null), result = ref(null), error = ref(''), versionConflict = ref(false), opaqueAcknowledged = ref(false), previewing = ref(false), submitting = ref(false)
let generation = 0, requestId = 0
const teamName = computed(() => app.teams.find(team => team.id === props.teamId)?.name || '当前团队')
const canWrite = computed(() => !!props.teamId && props.teamId === app.currentTeamId && app.currentTeam?.status !== 'ARCHIVED' && user.hasCapability(props.teamId, 'write'))
const canOverride = computed(() => canWrite.value && user.hasCapability(props.teamId, 'overwriteImport'))
const key = computed(() => [props.teamId, app.currentTeamId, app.currentTeam?.status, user.user?.id, user.token, user.identityReady, canWrite.value, canOverride.value].join('|'))
const statusLabels = { create: '新增', skip: '跳过', override: '覆盖' }
const canSubmit = computed(() => canWrite.value && !submitting.value && !previewing.value && !versionConflict.value && preview.value && preview.value.file === file.value && preview.value.mode === mode.value && (mode.value !== 'override' || canOverride.value) && (preview.value.plan.opaque ? opaqueAcknowledged.value : preview.value.plan.counts.create + preview.value.plan.counts.override > 0))
function scope() { return { generation, key: key.value, teamId: props.teamId } }
function live(captured) { return props.modelValue && captured.generation === generation && captured.key === key.value && canWrite.value }
function reset() { generation++; requestId++; file.value = null; mode.value = 'merge'; preview.value = null; result.value = null; error.value = ''; versionConflict.value = false; opaqueAcknowledged.value = false; previewing.value = false; submitting.value = false; if (fileInput.value) fileInput.value.value = '' }
watch([() => props.modelValue, key], () => { reset(); if (props.modelValue && !canWrite.value) emit('update:modelValue', false) }, { immediate: true, flush: 'sync' })
onBeforeUnmount(reset)
function invalidatePreview() { requestId++; preview.value = null; error.value = ''; versionConflict.value = false; opaqueAcknowledged.value = false }
function selectFile(value) { if (!canWrite.value || submitting.value || previewing.value) return; file.value = value || null; invalidatePreview() }
function handleDrop(event) { if (event.dataTransfer?.files?.length) selectFile(event.dataTransfer.files[0]) }
function formatSize(size) { return size > 1048576 ? (size / 1048576).toFixed(1) + ' MB' : Math.ceil(size / 1024) + ' KB' }
async function preparePreview() {
  if (!file.value || !canWrite.value || (mode.value === 'override' && !canOverride.value)) return
  const captured = scope(), request = ++requestId, selectedFile = file.value, selectedMode = mode.value
  previewing.value = true; error.value = ''; preview.value = null; versionConflict.value = false; opaqueAcknowledged.value = false
  try {
    const manifest = await readImportManifest(selectedFile)
    if (!live(captured) || request !== requestId) return
    const targets = await fetchTargetVersions(getApis, captured.teamId, () => live(captured) && request === requestId)
    if (!live(captured) || request !== requestId) return
    const plan = planImport(manifest.data, targets, selectedMode)
    preview.value = Object.freeze({ ...captured, file: selectedFile, mode: selectedMode, format: manifest.format, warning: manifest.warning, hasTeamHeaders: !!manifest.data?.globalHeaders?.length, plan: Object.freeze({ ...plan, expectedRevisions: Object.freeze({ ...plan.expectedRevisions }) }) })
  } catch (failure) { if (live(captured) && request === requestId) error.value = readableError(failure, '导入包预览失败') }
  finally { if (live(captured) && request === requestId) previewing.value = false }
}
async function submit() {
  if (!canSubmit.value) return
  const captured = preview.value
  if (!live(captured) || captured.file !== file.value || captured.mode !== mode.value) return
  submitting.value = true; error.value = ''
  const form = new FormData(); form.append('file', captured.file); form.append('teamId', captured.teamId); form.append('mode', captured.mode); form.append('expectedRevisions', JSON.stringify(captured.plan.expectedRevisions))
  try {
    const data = await importApis(form)
    if (!live(captured)) return
    result.value = data; preview.value = null; file.value = null
    emit('imported', { teamId: captured.teamId, result: data })
  } catch (failure) { if (live(captured)) { versionConflict.value = isVersionConflict(failure); error.value = readableError(failure) } }
  finally { if (live(captured)) submitting.value = false }
}
</script>
<style scoped>
.import-scope{font-size:13px;color:var(--app-text-secondary);line-height:1.8;margin:0 0 18px}.import-scope b{color:var(--app-text-primary)}.import-file{display:flex;align-items:center;gap:14px;padding:22px;background:var(--app-bg-control);border-radius:12px;color:var(--app-text-secondary)}.import-file>div{display:flex;flex-direction:column;gap:7px;flex:1;min-width:0}.import-file b{font-size:14px;color:var(--app-text-primary);overflow-wrap:anywhere}.import-file span{font-size:12px}.import-choose{cursor:pointer;flex:none;position:relative;border-radius:8px;padding:8px 12px;background:var(--app-bg-surface);color:var(--app-accent);font-size:13px}.import-choose input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%}.import-modes{border:0;padding:0;margin:20px 0}.import-modes legend{font-weight:600;font-size:13px;color:var(--app-text-primary);margin-bottom:6px}.import-modes p,.import-note{font-size:12px;line-height:1.8;color:var(--app-text-secondary);margin:6px 0}.import-modes :deep(.el-radio-group){display:flex;flex-wrap:wrap;gap:6px 20px}.import-modes :deep(.el-radio){margin:0;white-space:normal;line-height:1.6;height:auto;min-height:32px}.import-feedback{background:var(--app-bg-control);border-radius:10px;padding:12px 14px;font-size:12px;line-height:1.9;margin:14px 0;color:var(--app-text-regular)}.import-feedback--error{background:var(--el-color-danger-light-9);color:var(--app-text-danger)}.import-feedback--warning{background:var(--el-color-warning-light-9);color:var(--app-text-warning)}.import-feedback :deep(.el-checkbox){height:auto;white-space:normal;align-items:flex-start;margin-top:8px}.import-feedback :deep(.el-checkbox__label){white-space:normal;line-height:1.8}.import-preview__head{display:flex;align-items:center;justify-content:space-between;gap:14px}.import-preview__head h3,.import-result h3{font-size:15px;margin:0;color:var(--app-text-primary)}.import-preview__head>span{font-size:12px;color:var(--app-text-secondary)}.import-counts{display:flex;gap:12px;margin:14px 0}.import-counts>span{flex:1;padding:14px;background:var(--app-bg-control);border-radius:10px;font-size:12px;color:var(--app-text-secondary)}.import-counts b{display:block;color:var(--app-text-primary);font:600 22px/1.5 var(--app-font-sans)}.import-routes{max-height:300px;overflow:auto}.import-route{display:flex;align-items:center;gap:12px;padding:12px 4px;border-bottom:1px solid var(--app-divider)}.import-route>div{min-width:0;flex:1;display:flex;flex-direction:column;gap:6px}.import-route code{font:12px/1.7 var(--app-font-mono);overflow-wrap:anywhere;color:var(--app-text-primary)}.import-route>div>span,.import-route small{font-size:12px;color:var(--app-text-secondary)}.import-status{font-size:11px;border-radius:6px;padding:4px 7px;flex:none;background:var(--app-bg-control);color:var(--app-text-secondary)}.import-status.create{color:var(--app-text-success);background:var(--el-color-success-light-9)}.import-status.override{color:var(--app-text-warning);background:var(--el-color-warning-light-9)}.import-footer{display:flex;align-items:center;justify-content:flex-end;gap:16px;text-align:left}.import-footer>div{display:flex;gap:8px;flex:none}.import-footer :deep(.el-button+.el-button){margin:0}.import-result ul{padding-left:18px;margin-bottom:0}
@media(max-width:650px){.import-file{flex-wrap:wrap;padding:16px}.import-file>div{min-width:170px}.import-choose{width:100%;text-align:center}.import-preview__head{align-items:flex-start;flex-direction:column;gap:6px}.import-counts{gap:7px}.import-counts>span{padding:10px;font-size:12px}.import-footer{flex-direction:column;align-items:stretch}.import-footer>div{flex-wrap:wrap;justify-content:flex-end}.import-footer :deep(.el-button){font-size:12px;padding:9px 12px}.import-route{gap:7px}.import-route small{font-size:12px}}
</style>
