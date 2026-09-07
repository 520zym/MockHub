<template>
  <div class="file-server-page">
    <div class="toolbar soft-card">
      <div class="toolbar__filters">
        <el-input v-model="keyword" class="toolbar__search" clearable placeholder="搜索 fileId、文件名或别名"
          prefix-icon="Search" @keyup.enter="loadFiles" @clear="loadFiles" />
        <el-select v-model="teamId" class="toolbar__select" clearable placeholder="全部可访问团队" @change="handleFilterChange">
          <el-option v-for="team in appStore.teams" :key="team.id" :label="team.name" :value="team.id" />
        </el-select>
        <el-input v-model="tag" class="toolbar__tag" clearable placeholder="按标签筛选" @keyup.enter="loadFiles" @clear="loadFiles" />
      </div>
      <div class="toolbar__actions">
        <el-button @click="openUploadDialog"><el-icon><Upload /></el-icon>上传文件</el-button>
        <el-button type="primary" @click="loadFiles"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <div v-if="selectedRows.length" class="batch-bar soft-card">
      已选 <strong>{{ selectedRows.length }}</strong> 个文件
      <el-button size="small" type="danger" :loading="batchDeleting" @click="handleBatchDelete"><el-icon><Delete /></el-icon>批量删除</el-button>
      <el-button size="small" text @click="clearSelection">取消选择</el-button>
    </div>

    <div class="table-card soft-card">
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
            <el-button v-if="canManage(row)" text size="small" type="danger" @click="handleDelete(row)"><el-icon><Delete /></el-icon></el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无上传文件" /></template>
      </el-table>
      <div v-if="total" class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadFiles" @size-change="handleSizeChange" /></div>
    </div>

    <el-dialog v-model="uploadDialogVisible" title="上传模拟文件" width="560px" destroy-on-close @closed="resetUpload">
      <el-form label-width="100px">
        <el-form-item label="所属团队" required>
          <el-select v-model="uploadTeamId" placeholder="选择团队" style="width: 100%">
            <el-option v-for="team in appStore.teams" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
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
      <template #footer><el-button @click="uploadDialogVisible = false">取消</el-button><el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="文件详情" size="680px" destroy-on-close @closed="resetDetail">
      <template v-if="detail">
        <div class="detail-actions"><el-button type="primary" @click="copy(detail.downloadUrl)"><el-icon><DocumentCopy /></el-icon>复制下载链接</el-button><el-button :disabled="!canManage(detail)" @click="saveDetail" :loading="saving">保存信息</el-button></div>
        <el-alert title="公开下载链接最后一段为 fileId。删除后该链接将返回 404。" type="info" :closable="false" show-icon />
        <el-descriptions :column="2" border class="detail-meta">
          <el-descriptions-item label="fileId"><code>{{ detail.fileId }}</code></el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatSize(detail.size) }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatFull(detail.uploadedAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近下载">{{ detail.lastDownloadedAt ? formatFull(detail.lastDownloadedAt) : '从未下载' }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ detail.downloadCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="累计传输量">{{ formatSize(detail.transferredBytes) }}</el-descriptions-item>
          <el-descriptions-item label="下载地址" :span="2"><div class="url-row"><code>{{ detail.downloadUrl }}</code><CopyButton :text="detail.downloadUrl" /></div></el-descriptions-item>
        </el-descriptions>
        <el-form label-width="80px" class="detail-form">
          <el-form-item label="别名"><el-input v-model="editForm.alias" :disabled="!canManage(detail)" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="标签"><el-select v-model="editForm.tags" :disabled="!canManage(detail)" multiple filterable allow-create default-first-option placeholder="输入后回车添加标签" style="width: 100%"><el-option v-for="item in editForm.tags" :key="item" :label="item" :value="item" /></el-select></el-form-item>
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
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { batchDeleteFiles, deleteFile, getFileDetail, getFilePreview, getFiles, updateFile, uploadFile } from '@/api/files'
import { getServerAddress } from '@/api/settings'
import { formatFull } from '@/utils/time'
import CopyButton from '@/components/CopyButton.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const tableRef = ref()
const files = ref([])
const loading = ref(false)
const selectedRows = ref([])
const keyword = ref('')
const tag = ref('')
const teamId = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const batchDeleting = ref(false)
const uploadDialogVisible = ref(false)
const uploadTeamId = ref('')
const uploadRawFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const serverAddress = ref('')
const detailVisible = ref(false)
const detail = ref(null)
const detailFileId = ref('')
const preview = ref(null)
const previewLoading = ref(false)
const saving = ref(false)
const editForm = ref({ alias: '', tags: [] })
let listRequestSequence = 0
let detailRequestSequence = 0
let previewRequestSequence = 0

const uploadTeam = computed(() => appStore.teams.find(item => item.id === uploadTeamId.value))
const publicUploadUrl = computed(() => uploadTeam.value ? `${publicBase()}/file-server/${uploadTeam.value.identifier}/upload` : '')

onMounted(async () => {
  if (!appStore.teams.length) await appStore.loadTeams()
  teamId.value = appStore.currentTeamId || ''
  await resolveServerAddress()
  loadFiles()
})

watch(() => appStore.currentTeamId, value => {
  if (value !== teamId.value) { teamId.value = value || ''; handleFilterChange() }
})

function serverBase() { return (serverAddress.value || window.location.origin).replace(/\/$/, '') }
function publicBase() {
  const downloadUrl = detail.value?.downloadUrl || files.value.find(item => item.downloadUrl)?.downloadUrl
  if (downloadUrl) {
    const marker = downloadUrl.lastIndexOf('/files/')
    if (marker >= 0) return downloadUrl.substring(0, marker).replace(/\/$/, '')
  }
  return serverBase()
}
async function resolveServerAddress() { try { const result = await getServerAddress(); serverAddress.value = result.address || '' } catch (e) { serverAddress.value = '' } }
function handleFilterChange() { page.value = 1; loadFiles() }
function handleSizeChange() { page.value = 1; loadFiles() }
async function loadFiles() {
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const data = await getFiles({ teamId: teamId.value || undefined, keyword: keyword.value || undefined, tag: tag.value || undefined, page: page.value, size: size.value })
    if (requestSequence !== listRequestSequence) return
    files.value = data.items || []
    total.value = data.total || 0
  } finally { if (requestSequence === listRequestSequence) loading.value = false }
}
function canManage(file) { return !!file && userStore.isTeamAdmin(file.teamId) }
function canSelect(file) { return !batchDeleting.value && canManage(file) }
function formatSize(value) { const number = Number(value || 0); if (number < 1024) return `${number} B`; if (number < 1024 ** 2) return `${(number / 1024).toFixed(1)} KB`; if (number < 1024 ** 3) return `${(number / 1024 ** 2).toFixed(1)} MB`; return `${(number / 1024 ** 3).toFixed(1)} GB` }
async function copy(value) { if (!value) return; try { await navigator.clipboard.writeText(value); ElMessage.success('已复制到剪贴板') } catch (e) { ElMessage.error('复制失败，请手动复制') } }
function clearSelection() { tableRef.value?.clearSelection() }
async function handleDelete(row) {
  await ElMessageBox.confirm(`删除「${row.alias || row.fileName}」后，原下载链接将返回 404。`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  await deleteFile(row.fileId); ElMessage.success('文件已删除'); detailVisible.value = false; loadFiles()
}
async function handleBatchDelete() {
  const fileIds = selectedRows.value.map(item => item.fileId)
  await ElMessageBox.confirm(`删除选中的 ${fileIds.length} 个文件后，其下载链接将返回 404。`, '确认批量删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  batchDeleting.value = true
  try { await batchDeleteFiles(fileIds); ElMessage.success('已批量删除'); clearSelection(); loadFiles() } finally { batchDeleting.value = false }
}
function handleUploadFileChange(file) { uploadRawFile.value = file.raw }
function resetUploadFile() { uploadRawFile.value = null }
function resetUpload() { uploadRawFile.value = null; uploadProgress.value = 0; uploading.value = false }
function openUploadDialog() { uploadTeamId.value = teamId.value || appStore.currentTeamId || ''; uploadDialogVisible.value = true }
async function submitUpload() {
  if (!uploadTeamId.value) return ElMessage.warning('请选择所属团队')
  if (!uploadRawFile.value) return ElMessage.warning('请选择要上传的文件')
  uploading.value = true; uploadProgress.value = 0
  try {
    const uploaded = await uploadFile(uploadTeamId.value, uploadRawFile.value, event => { if (event.total) uploadProgress.value = Math.round(event.loaded * 100 / event.total) })
    ElMessage.success('文件上传成功'); uploadDialogVisible.value = false; await loadFiles(); await copy(uploaded.downloadUrl); openDetail(uploaded)
  } finally { uploading.value = false }
}
async function openDetail(row) { detailFileId.value = row.fileId; detail.value = row; detailVisible.value = true; loadDetail(row.fileId) }
function resetDetail() { detailRequestSequence++; previewRequestSequence++; detail.value = null; preview.value = null; detailFileId.value = '' }
async function loadDetail(fileId) {
  const requestSequence = ++detailRequestSequence
  preview.value = null
  try {
    const data = await getFileDetail(fileId)
    if (requestSequence !== detailRequestSequence || fileId !== detailFileId.value) return
    detail.value = data
    editForm.value = { alias: detail.value.alias || '', tags: [...(detail.value.tags || [])] }
    loadPreview(fileId, requestSequence)
  } catch (e) { if (requestSequence === detailRequestSequence && fileId === detailFileId.value) detailVisible.value = false }
}
async function loadPreview(fileId, detailSequence) {
  const requestSequence = ++previewRequestSequence
  previewLoading.value = true
  try {
    const data = await getFilePreview(fileId)
    if (requestSequence === previewRequestSequence && detailSequence === detailRequestSequence && fileId === detailFileId.value) preview.value = data
  } catch (e) {
    if (requestSequence === previewRequestSequence && detailSequence === detailRequestSequence && fileId === detailFileId.value) preview.value = { kind: 'unsupported', message: '预览内容暂不可用。' }
  } finally { if (requestSequence === previewRequestSequence) previewLoading.value = false }
}
async function saveDetail() {
  const fileId = detail.value.fileId
  saving.value = true
  try {
    const data = await updateFile(fileId, editForm.value)
    if (fileId === detailFileId.value) {
      detail.value = data
      editForm.value = { alias: detail.value.alias || '', tags: [...(detail.value.tags || [])] }
      ElMessage.success('文件信息已保存')
    }
    loadFiles()
  } finally { saving.value = false }
}
function inlineUrl(url) { if (!url) return ''; return `${url}${url.includes('?') ? '&' : '?'}inline=true` }
function safeHtmlSrcdoc(value) {
  const policy = "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; form-action 'none'; base-uri 'none'"
  return `<meta http-equiv="Content-Security-Policy" content="${policy}">${value || ''}`
}
function maxColumns(rows) { const count = Math.max(0, ...(rows || []).map(item => item.length)); return Array.from({ length: count }) }
function rowsToObjects(rows) { return (rows || []).map(row => Object.fromEntries(row.map((value, index) => [String(index), value]))) }
function excelColumnName(index) { let value = ''; let number = index + 1; while (number) { const rem = (number - 1) % 26; value = String.fromCharCode(65 + rem) + value; number = Math.floor((number - 1) / 26) } return value }
</script>

<style lang="scss" scoped>
.file-server-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; justify-content: space-between; gap: 12px; padding: 16px; }
.toolbar__filters, .toolbar__actions, .tag-list, .url-row, .detail-actions { display: flex; align-items: center; gap: 10px; }
.toolbar__search { width: 280px; }.toolbar__select { width: 180px; }.toolbar__tag { width: 160px; }
.table-card { overflow: hidden; }.batch-bar { padding: 10px 16px; }.pagination { display: flex; justify-content: flex-end; padding: 16px; }
.file-name-cell { display: flex; align-items: center; gap: 9px; }.file-name-cell__name { font-weight: 500; }.file-name-cell__original, .muted, .preview-note { color: #909399; font-size: 12px; }.file-id { color: #6366F1; font-size: 12px; }
.tag-list { flex-wrap: wrap; }.url-row { width: 100%; }.url-row code { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; }.upload-example { width: 100%; margin-top: 6px; padding: 8px; border-radius: 4px; background: #f5f7fa; color: #606266; font-size: 12px; overflow-wrap: anywhere; }
.detail-actions { justify-content: flex-end; margin-bottom: 14px; }.detail-meta { margin: 16px 0; }.detail-form { margin-top: 18px; }.preview-area { min-height: 120px; }.preview-notice { margin-bottom: 12px; }.text-preview { max-height: 410px; margin: 0; padding: 12px; white-space: pre-wrap; overflow: auto; background: #f5f7fa; border-radius: 4px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }.html-preview, .pdf-preview { width: 100%; height: 430px; border: 1px solid #dcdfe6; }.image-preview { max-width: 100%; max-height: 430px; display: block; margin: auto; }.media-preview { width: 100%; }.excel-preview section + section { margin-top: 18px; }.excel-preview h4 { margin: 0 0 8px; }
@media (max-width: 900px) { .toolbar { align-items: flex-start; flex-direction: column; }.toolbar__filters { flex-wrap: wrap; }.toolbar__search { width: min(100%, 280px); } }
</style>
