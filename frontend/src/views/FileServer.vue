<template>
  <div class="file-server-page">
    <header class="resource-header"><div><h1>文件服务器</h1><p>{{ appStore.currentTeam?.name || '未选择团队' }} · 管理模拟文件与公开下载链接</p></div><el-tag>{{ canWrite ? '可编辑' : '只读' }}</el-tag></header>
    <div v-if="!canRead" class="soft-card resource-state" role="status">请选择有访问权限的团队。不会加载全部团队文件。</div>
    <div class="toolbar soft-card">
      <div class="toolbar__filters">
        <el-input v-model="keyword" class="toolbar__search" clearable placeholder="搜索 fileId、文件名或别名"
          prefix-icon="Search" @keyup.enter="loadFiles" @clear="loadFiles" />
        <span class="team-context">{{ appStore.currentTeam?.name || '未选择团队' }}</span>
        <el-input v-model="tag" class="toolbar__tag" clearable placeholder="按标签筛选" @keyup.enter="loadFiles" @clear="loadFiles" />
      </div>
      <div class="toolbar__actions">
        <el-button type="primary" :disabled="!canWrite" @click="openUploadDialog"><el-icon><Upload /></el-icon>上传文件</el-button>
        <el-button :disabled="!canRead" :loading="loading" @click="loadFiles"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <div v-if="selectedRows.length && canPurge" class="batch-bar soft-card">
      已选 <strong>{{ selectedRows.length }}</strong> 个文件
      <el-button size="small" type="danger" :loading="batchDeleting" @click="handleBatchDelete"><el-icon><Delete /></el-icon>批量删除</el-button>
      <el-button size="small" text @click="clearSelection">取消选择</el-button>
    </div>

    <div v-if="listError" class="soft-card resource-state" role="alert"><strong>文件加载失败</strong><p>{{ listError }}</p><el-button @click="loadFiles">重试</el-button></div>
    <p v-if="actionError" class="resource-error" role="alert">{{ actionError }}</p>
    <div v-if="canRead && !listError" class="table-card soft-card">
      <el-table ref="tableRef" v-loading="loading" :data="files" row-key="fileId" @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="46" :selectable="canSelect" />
        <el-table-column label="文件" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="file-name-cell">
              <el-icon><Document /></el-icon>
              <div>
                <div class="file-name-cell__name">{{ row.alias || row.fileName }}</div>
                <div v-if="row.alias" class="file-name-cell__original">{{ row.fileName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="fileId" min-width="160" show-overflow-tooltip>
          <template #default="{ row }"><code class="file-id">{{ row.fileId }}</code></template>
        </el-table-column>
        <el-table-column label="标签" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.tags?.length" class="tag-list"><el-tag v-for="item in row.tags" :key="item" size="small" round>{{ item }}</el-tag></div>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="105" align="right"><template #default="{ row }">{{ formatSize(row.size) }}</template></el-table-column>
        <el-table-column label="上传时间" width="170" align="center"><template #default="{ row }">{{ formatFull(row.uploadedAt) }}</template></el-table-column>
        <el-table-column label="下载次数" width="100" align="right"><template #default="{ row }">{{ row.downloadCount || 0 }}</template></el-table-column>
        <el-table-column label="传输量" width="110" align="right"><template #default="{ row }">{{ formatSize(row.transferredBytes) }}</template></el-table-column>
        <el-table-column label="操作" width="185" fixed="right" align="center">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openDetail(row)">详情</el-button>
            <el-button text size="small" @click="copy(row.downloadUrl)"><el-icon><DocumentCopy /></el-icon></el-button>
            <el-button v-if="canDelete(row)" text size="small" type="danger" @click="handleDelete(row)"><el-icon><Delete /></el-icon></el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="loading ? '正在加载文件…' : '当前团队暂无符合筛选条件的文件'" /></template>
      </el-table>
      <div v-if="total" class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadFiles" @size-change="handleSizeChange" /></div>
    </div>

    <el-dialog v-model="uploadDialogVisible" title="上传模拟文件" width="560px" destroy-on-close @closed="resetUpload">
      <p v-if="uploadError" class="resource-error" role="alert">{{ uploadError }}</p>
      <el-form label-position="top" :disabled="uploading || !canWrite">
        <el-form-item label="所属团队" required>
          <span class="team-context">{{ uploadTeam?.name || '未选择团队' }}</span>
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload :auto-upload="false" :limit="1" :on-change="handleUploadFileChange" :on-remove="resetUploadFile">
            <el-button>选择文件</el-button><template #tip><div class="el-upload__tip">上传后生成可公开下载、支持 Range 请求的链接。</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="uploadProgress > 0" label="上传进度"><el-progress :percentage="uploadProgress" /></el-form-item>
        <el-form-item v-if="uploadTeam" label="模拟上传地址">
          <div class="url-row"><code>{{ publicUploadUrl }}</code><CopyButton :text="publicUploadUrl" /></div>
          <div class="upload-example">curl -F "file=@./example.pdf" {{ publicUploadUrl }}</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="uploadDialogVisible = false">取消</el-button><el-button type="primary" :loading="uploading" :disabled="!canWrite || !uploadRawFile" @click="submitUpload">上传</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="文件详情" class="file-detail" size="680px" destroy-on-close @closed="resetDetail">
      <div v-if="detailError" class="resource-error" role="alert">{{ detailError }}<el-button v-if="detailFileId" link @click="loadDetail(detailFileId)">重试</el-button></div>
      <template v-if="detail && canRead">
        <div class="detail-actions"><el-button type="primary" @click="copy(detail.downloadUrl)"><el-icon><DocumentCopy /></el-icon>复制下载链接</el-button><el-button :disabled="!canEdit(detail)" @click="saveDetail" :loading="saving">保存信息</el-button></div>
        <el-alert title="公开下载链接最后一段为 fileId。删除后该链接将返回 404。" type="info" :closable="false" show-icon />
        <el-descriptions :column="1" class="detail-meta">
          <el-descriptions-item label="fileId"><code>{{ detail.fileId }}</code></el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatSize(detail.size) }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatFull(detail.uploadedAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近下载">{{ detail.lastDownloadedAt ? formatFull(detail.lastDownloadedAt) : '从未下载' }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ detail.downloadCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="累计传输量">{{ formatSize(detail.transferredBytes) }}</el-descriptions-item>
          <el-descriptions-item label="下载地址" :span="2"><div class="url-row"><code>{{ detail.downloadUrl }}</code><CopyButton :text="detail.downloadUrl" /></div></el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" class="detail-form">
          <el-form-item label="别名"><el-input v-model="editForm.alias" :disabled="!canEdit(detail)" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="标签"><el-select v-model="editForm.tags" :disabled="!canEdit(detail)" multiple filterable allow-create default-first-option placeholder="输入后回车添加标签" style="width: 100%"><el-option v-for="item in editForm.tags" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        </el-form>
        <el-divider>内容预览</el-divider>
        <div v-loading="previewLoading" class="preview-area">
          <template v-if="preview">
            <el-alert v-if="preview.truncated" title="内容过大，当前仅展示前一部分。" type="warning" :closable="false" show-icon class="preview-notice" />
            <el-alert v-if="preview.message" :title="preview.message" type="info" :closable="false" show-icon class="preview-notice" />
            <pre v-if="preview.kind === 'text'" class="text-preview">{{ preview.text }}</pre>
            <iframe v-else-if="preview.kind === 'html'" :srcdoc="safeHtmlSrcdoc(preview.text)" sandbox class="html-preview" title="HTML 文件预览" />
            <div v-else-if="preview.kind === 'table'" class="excel-preview"><section v-for="sheet in preview.sheets" :key="sheet.name"><h4>{{ sheet.name }}</h4><el-table :data="rowsToObjects(sheet.rows)" border size="small"><el-table-column v-for="(_, index) in maxColumns(sheet.rows)" :key="index" :label="excelColumnName(index)" :prop="String(index)" min-width="100" /></el-table></section></div>
            <img v-else-if="detail.previewType === 'image'" :src="inlineUrl(detail.downloadUrl)" class="image-preview" :alt="detail.fileName" />
            <audio v-else-if="detail.previewType === 'audio'" :src="inlineUrl(detail.downloadUrl)" controls class="media-preview" />
            <video v-else-if="detail.previewType === 'video'" :src="inlineUrl(detail.downloadUrl)" controls class="media-preview" />
            <iframe v-else-if="detail.previewType === 'pdf'" :src="inlineUrl(detail.downloadUrl)" class="pdf-preview" title="PDF 文件预览" />
            <el-empty v-else description="此文件暂不支持在线预览，请下载查看。" />
          </template>
        </div>
        <p v-if="['pdf', 'audio', 'video', 'image'].includes(detail.previewType)" class="preview-note">该预览会通过公开下载链接加载，可能增加下载次数和传输量。</p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { batchDeleteFiles, deleteFile, getFileDetail, getFilePreview, getFiles, updateFile, uploadFile } from '@/api/files'
import { getServerAddress } from '@/api/settings'
import { formatFull } from '@/utils/time'
import CopyButton from '@/components/CopyButton.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const teamId = computed(() => appStore.currentTeamId || null)
const canRead = computed(() => !!teamId.value && userStore.identityReady && userStore.hasCapability(teamId.value, 'read'))
const canWrite = computed(() => canRead.value && appStore.currentTeam?.status !== 'ARCHIVED' && userStore.hasCapability(teamId.value, 'write'))
const canPurge = computed(() => canWrite.value && userStore.hasCapability(teamId.value, 'purge'))
const tableRef = ref()
const files = ref([])
const loading = ref(false)
const listError = ref('')
const actionError = ref('')
const selectedRows = ref([])
const keyword = ref('')
const tag = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const batchDeleting = ref(false)
const uploadDialogVisible = ref(false)
const uploadTeamId = ref('')
const uploadRawFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadError = ref('')
const serverAddress = ref('')
const detailVisible = ref(false)
const detail = ref(null)
const detailError = ref('')
const detailFileId = ref('')
const preview = ref(null)
const previewLoading = ref(false)
const saving = ref(false)
const editForm = ref({ alias: '', tags: [] })
let contextVersion = 0
let listRequestSequence = 0
let detailRequestSequence = 0
let previewRequestSequence = 0
let disposed = false
let confirmationOpen = false
const uploadTeam = computed(() => appStore.teams.find(item => item.id === uploadTeamId.value))
const publicUploadUrl = computed(() => uploadTeam.value ? publicBase() + '/file-server/' + uploadTeam.value.identifier + '/upload' : '')
function snapshot() { return { teamId: teamId.value, version: contextVersion } }
function current(ctx) { return !disposed && ctx.version === contextVersion && ctx.teamId === teamId.value && canRead.value }
function message(error, fallback) { return error?.response?.data?.msg || error?.msg || error?.message || fallback }
function canEdit(file) { return !!file && file.teamId === teamId.value && canWrite.value && !saving.value }
function canDelete(file) { return !!file && file.teamId === teamId.value && canPurge.value }
function canSelect(file) { return !batchDeleting.value && canDelete(file) }
async function confirm(text, title = '确认删除') {
  confirmationOpen = true
  try { await ElMessageBox.confirm(text, title, { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', closeOnClickModal: false }); return true } catch { return false } finally { confirmationOpen = false }
}
watch([teamId, canRead, canWrite, canPurge, () => userStore.user?.id], () => {
  if (confirmationOpen) ElMessageBox.close()
  contextVersion++; listRequestSequence++; resetDetail()
  files.value = []; total.value = 0; selectedRows.value = []; tableRef.value?.clearSelection()
  listError.value = ''; actionError.value = ''; detailVisible.value = false; uploadDialogVisible.value = false
  page.value = 1; keyword.value = ''; tag.value = ''; resetUpload(); loading.value = false; batchDeleting.value = false
  if (canRead.value) { loadFiles(); resolveServerAddress(snapshot()) }
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; contextVersion++; listRequestSequence++; resetDetail() })
function serverBase() { return (serverAddress.value || window.location.origin).replace(/\/$/, '') }
function publicBase() {
  const url = detail.value?.downloadUrl || files.value.find(item => item.downloadUrl)?.downloadUrl
  const marker = url?.lastIndexOf('/files/') ?? -1
  return marker >= 0 ? url.substring(0, marker).replace(/\/$/, '') : serverBase()
}
async function resolveServerAddress(ctx) { try { const result = await getServerAddress(); if (current(ctx)) serverAddress.value = result.address || '' } catch { if (current(ctx)) serverAddress.value = '' } }
function handleFilterChange() { page.value = 1; clearSelection(); loadFiles() }
function handleSizeChange() { handleFilterChange() }
async function loadFiles() {
  const ctx = snapshot(), sequence = ++listRequestSequence
  if (!canRead.value) { files.value = []; total.value = 0; return }
  loading.value = true; listError.value = ''
  try {
    const data = await getFiles({ teamId: ctx.teamId, keyword: keyword.value.trim() || undefined, tag: tag.value.trim() || undefined, page: page.value, size: size.value })
    if (!current(ctx) || sequence !== listRequestSequence) return
    files.value = data.items || []; total.value = data.total || 0; clearSelection()
  } catch (error) { if (current(ctx) && sequence === listRequestSequence) { files.value = []; total.value = 0; listError.value = message(error, '文件加载失败，请重试。') } }
  finally { if (current(ctx) && sequence === listRequestSequence) loading.value = false }
}
function formatSize(value) { const n = Number(value || 0); if (n < 1024) return n + ' B'; if (n < 1024 ** 2) return (n / 1024).toFixed(1) + ' KB'; if (n < 1024 ** 3) return (n / 1024 ** 2).toFixed(1) + ' MB'; return (n / 1024 ** 3).toFixed(1) + ' GB' }
async function copy(value) { if (!value || !canRead.value) return; try { await navigator.clipboard.writeText(value); ElMessage.success('已复制到剪贴板') } catch { ElMessage.error('复制失败，请手动复制') } }
function clearSelection() { selectedRows.value = []; tableRef.value?.clearSelection() }
async function handleDelete(row) {
  if (!canDelete(row) || batchDeleting.value) return
  const ctx = snapshot()
  if (!await confirm('删除「' + (row.alias || row.fileName) + '」后，原下载链接将返回 404。') || !current(ctx) || !canDelete(row)) return
  actionError.value = ''; batchDeleting.value = true
  try { await deleteFile(row.fileId); if (current(ctx)) { ElMessage.success('文件已删除'); detailVisible.value = false; resetDetail(); await loadFiles() } }
  catch (error) { if (current(ctx)) actionError.value = message(error, '文件删除失败。') }
  finally { if (current(ctx)) batchDeleting.value = false }
}
async function handleBatchDelete() {
  if (!canPurge.value || batchDeleting.value || !selectedRows.value.length || selectedRows.value.some(row => !canDelete(row))) return
  const ctx = snapshot(), ids = selectedRows.value.map(item => item.fileId)
  if (!await confirm('删除选中的 ' + ids.length + ' 个文件后，其下载链接将返回 404。', '确认批量删除') || !current(ctx) || !canPurge.value) return
  batchDeleting.value = true; actionError.value = ''
  try { await batchDeleteFiles(ids); if (current(ctx)) { ElMessage.success('已批量删除'); clearSelection(); await loadFiles() } }
  catch (error) { if (current(ctx)) actionError.value = message(error, '批量删除失败。') }
  finally { if (current(ctx)) batchDeleting.value = false }
}
function handleUploadFileChange(file) { uploadRawFile.value = file.raw }
function resetUploadFile() { uploadRawFile.value = null }
function resetUpload() { uploadRawFile.value = null; uploadProgress.value = 0; uploading.value = false; uploadError.value = '' }
function openUploadDialog() { if (!canWrite.value) return; resetUpload(); uploadTeamId.value = teamId.value; uploadDialogVisible.value = true }
async function submitUpload() {
  if (!canWrite.value || uploading.value || uploadTeamId.value !== teamId.value) return
  if (!uploadRawFile.value) return ElMessage.warning('请选择要上传的文件')
  const ctx = snapshot(), file = uploadRawFile.value
  uploading.value = true; uploadProgress.value = 0; uploadError.value = ''
  try {
    const uploaded = await uploadFile(ctx.teamId, file, event => { if (current(ctx) && event.total) uploadProgress.value = Math.round(event.loaded * 100 / event.total) })
    if (!current(ctx) || !canWrite.value) return
    ElMessage.success('文件上传成功'); uploadDialogVisible.value = false; await loadFiles()
    if (current(ctx)) { await copy(uploaded.downloadUrl); if (current(ctx)) openDetail(uploaded) }
  } catch (error) { if (current(ctx)) uploadError.value = message(error, '上传失败，请重试。') }
  finally { if (current(ctx)) uploading.value = false }
}
function openDetail(row) {
  if (!canRead.value || row.teamId !== teamId.value) return
  resetDetail(); detailFileId.value = row.fileId; detail.value = row; editForm.value = { alias: row.alias || '', tags: [...(row.tags || [])] }
  detailVisible.value = true; loadDetail(row.fileId)
}
function resetDetail() { detailRequestSequence++; previewRequestSequence++; detail.value = null; preview.value = null; detailFileId.value = ''; editForm.value = { alias: '', tags: [] }; detailError.value = ''; previewLoading.value = false; saving.value = false }
async function loadDetail(fileId) {
  if (!canRead.value) return
  const ctx = snapshot(), sequence = ++detailRequestSequence
  preview.value = null; detailError.value = ''
  try {
    const data = await getFileDetail(fileId)
    if (!current(ctx) || sequence !== detailRequestSequence || fileId !== detailFileId.value) return
    if (data.teamId !== ctx.teamId) { detail.value = null; detailError.value = '文件不属于当前团队。'; return }
    detail.value = data; editForm.value = { alias: data.alias || '', tags: [...(data.tags || [])] }; loadPreview(fileId, sequence, ctx)
  } catch (error) { if (current(ctx) && sequence === detailRequestSequence) { detail.value = null; detailError.value = message(error, '文件详情加载失败。') } }
}
async function loadPreview(fileId, detailSequence, ctx) {
  const sequence = ++previewRequestSequence
  previewLoading.value = true
  try { const data = await getFilePreview(fileId); if (current(ctx) && sequence === previewRequestSequence && detailSequence === detailRequestSequence && fileId === detailFileId.value) preview.value = data }
  catch (error) { if (current(ctx) && sequence === previewRequestSequence && detailSequence === detailRequestSequence) preview.value = { kind: 'unsupported', message: message(error, '预览内容暂不可用。') } }
  finally { if (current(ctx) && sequence === previewRequestSequence) previewLoading.value = false }
}
async function saveDetail() {
  if (!canEdit(detail.value)) return
  const ctx = snapshot(), fileId = detail.value.fileId, payload = { alias: editForm.value.alias, tags: [...editForm.value.tags] }
  saving.value = true; detailError.value = ''
  try {
    const data = await updateFile(fileId, payload)
    if (!current(ctx) || !canWrite.value || fileId !== detailFileId.value) return
    detail.value = data; editForm.value = { alias: data.alias || '', tags: [...(data.tags || [])] }; ElMessage.success('文件信息已保存'); await loadFiles()
  } catch (error) { if (current(ctx)) detailError.value = message(error, '文件信息保存失败。') }
  finally { if (current(ctx)) saving.value = false }
}
function inlineUrl(url) { if (!url) return ''; return url + (url.includes('?') ? '&' : '?') + 'inline=true' }
function safeHtmlSrcdoc(value) { return '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data: blob:; form-action \'none\'; base-uri \'none\'">' + (value || '') }
function maxColumns(rows) { return Array.from({ length: Math.max(0, ...(rows || []).map(item => item.length)) }) }
function rowsToObjects(rows) { return (rows || []).map(row => Object.fromEntries(row.map((value, index) => [String(index), value]))) }
function excelColumnName(index) { let value = '', n = index + 1; while (n) { const rem = (n - 1) % 26; value = String.fromCharCode(65 + rem) + value; n = Math.floor((n - 1) / 26) } return value }
</script>

<style lang="scss" scoped>
.file-server-page { display: flex; flex-direction: column; gap: 16px; min-width: 0; color: var(--app-text-primary); }
.resource-header, .toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.resource-header h1 { margin: 0 0 6px; font-size: 26px; font-weight: 600; letter-spacing: -.025em; }
.resource-header p { margin: 0; font-size: 13px; color: var(--app-text-secondary); }
.toolbar { padding: 16px; }
.toolbar__filters, .toolbar__actions, .tag-list, .url-row, .detail-actions { display: flex; align-items: center; gap: 10px; min-width: 0; flex-wrap: wrap; }
.toolbar__filters { flex: 1; }
.toolbar__search { width: min(100%, 280px); }.toolbar__tag { width: 160px; }
.team-context { color: var(--app-text-secondary); padding: 8px 12px; background: var(--app-bg-control); border-radius: 8px; font-size: 12px; overflow-wrap: anywhere; }
.table-card { min-width: 0; padding: 16px; }.batch-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px 16px; }
.pagination { display: flex; justify-content: flex-end; padding-top: 16px; min-width: 0; }
.file-name-cell { display: flex; align-items: center; gap: 9px; min-width: 0; }.file-name-cell > div { min-width: 0; }.file-name-cell__name { font-weight: 500; }
.file-name-cell__original, .muted, .preview-note { color: var(--app-text-secondary); font-size: 12px; }.file-id { color: var(--app-accent); font: 12px var(--app-font-mono); }
.tag-list { gap: 4px; }.url-row { width: 100%; flex-wrap: nowrap; }.url-row code { flex: 1; min-width: 0; color: var(--app-text-regular); white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; }
.upload-example { width: 100%; margin-top: 8px; padding: 10px 12px; border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-secondary); font: 12px/1.7 var(--app-font-mono); overflow-wrap: anywhere; }
.file-detail :deep(.copy-button), .file-detail :deep(.copy-button:hover) { color: var(--app-accent); }
.detail-actions { justify-content: flex-end; margin-bottom: 14px; }.detail-meta { margin: 16px 0; }.detail-meta :deep(.el-descriptions__content) { overflow-wrap: anywhere; }.detail-form { margin-top: 18px; }
.preview-area { min-height: 120px; min-width: 0; }.preview-notice { margin-bottom: 12px; }.text-preview { max-height: 410px; margin: 0; padding: 14px; white-space: pre-wrap; overflow: auto; background: var(--app-bg-control); border-radius: 10px; font: 12px/1.8 var(--app-font-mono); overflow-wrap: anywhere; }
.html-preview, .pdf-preview { width: 100%; height: min(430px, 65vh); border: 0; border-radius: 10px; }.image-preview { max-width: 100%; max-height: 430px; display: block; margin: auto; }.media-preview { width: 100%; }.excel-preview { min-width: 0; }.excel-preview section + section { margin-top: 18px; }.excel-preview h4 { margin: 0 0 8px; }
.resource-state { text-align: center; color: var(--app-text-secondary); padding: 32px 20px; }.resource-state strong { color: var(--app-text-primary); }.resource-state p { margin: 10px 0; }
.resource-error { color: var(--el-color-danger); background: var(--el-color-danger-light-9); border-radius: 10px; padding: 12px 16px; margin: 0; font-size: 13px; overflow-wrap: anywhere; }
@media (max-width: 600px) { .toolbar__filters, .toolbar__actions { width: 100%; }.toolbar__search, .toolbar__tag { width: 100%; }.table-card { padding: 12px; }.pagination { justify-content: flex-start; }.resource-header h1 { font-size: 24px; } }
</style>
