<template>
  <div class="team-variables">
    <!-- 顶部工具条：团队选择器 + 当前角色提示 -->
    <div class="page-header">
      <div class="page-header__left">
        <h2 class="page-title">动态变量</h2>
        <span class="team-context">{{ appStore.currentTeam?.name || '未选择团队' }}</span>
      </div>
      <div class="page-header__right">
        <el-tag v-if="canWrite" type="success" effect="light">可编辑</el-tag>
        <el-tag v-else type="info" effect="light">只读</el-tag>
      </div>
    </div>

    <div v-if="loadError" class="resource-state panel" role="alert"><strong>变量加载失败</strong><p>{{ loadError }}</p><el-button @click="loadVariables">重试</el-button></div>
    <p v-if="writeError" class="resource-error" role="alert">{{ writeError }}</p>
    <!-- 三栏主内容 -->
    <div v-if="canRead && !loadError" v-loading="loading" class="panels">
      <!-- 左栏：变量列表 -->
      <section class="panel panel--left">
        <div class="panel__header">
          <span class="panel__title">变量</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!canWrite || writing"
            @click="openVariableDialog(null)"
          >
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
        <div class="panel__body">
          <div v-if="variables.length === 0" class="empty-hint">{{ loading ? '正在读取变量…' : '当前团队暂无变量' }}</div>
          <div
            v-for="v in variables"
            :key="v.id"
            class="variable-item" role="button" tabindex="0"
            @keydown.enter.prevent="currentVariableId = v.id" @keydown.space.prevent="currentVariableId = v.id"
            :class="{ 'variable-item--active': currentVariableId === v.id }"
            @click="currentVariableId = v.id"
          >
            <div class="variable-item__name">{{ v.name }}</div>
            <div v-if="v.description" class="variable-item__desc">{{ v.description }}</div>
            <div class="variable-item__actions" @click.stop>
              <el-button
                text
                size="small"
                :disabled="!canWrite || writing"
                @click="openVariableDialog(v)"
              >编辑</el-button>
              <el-button
                text
                size="small"
                style="color: var(--app-text-danger)"
                :disabled="!canWrite || writing"
                @click="handleDeleteVariable(v)"
              >删除</el-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 中栏：候选值 -->
      <section class="panel panel--middle">
        <div class="panel__header">
          <span class="panel__title">
            候选值
            <span v-if="currentVariable" class="panel__subtitle">（{{ currentVariable.name }}）</span>
          </span>
          <div class="panel__header-actions">
            <el-input
              v-model="valueSearchKeyword"
              size="small"
              placeholder="搜索"
              clearable
              style="width: 160px; margin-right: 8px"
            />
            <el-button
              size="small"
              :disabled="!canWrite || writing || !currentVariable"
              @click="openBatchDialog"
            >批量粘贴</el-button>
            <el-button
              type="primary"
              size="small"
              :disabled="!canWrite || writing || !currentVariable"
              @click="openValueDialog(null)"
            >
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
        </div>
        <div class="panel__body panel__body--table">
          <el-table
            v-if="currentVariable"
            :data="filteredValues"
            row-class-name="value-row"
            style="width: 100%"
          >
            <template #empty>
              <div class="empty-hint">暂无候选值，点右上角「新增」或「批量粘贴」</div>
            </template>
            <el-table-column label="值" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="value-text" :title="row.value">{{ row.value }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.description" class="desc-text" :title="row.description">{{ row.description }}</span>
                <span v-else class="desc-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button
                    link
                    type="primary"
                    :disabled="!canWrite || writing"
                    @click="openValueDialog(row)"
                  >编辑</el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="!canWrite || writing"
                    @click="handleDeleteValue(row)"
                  >删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-hint">请先选择或创建一个变量</div>
        </div>
      </section>

      <!-- 右栏：分组 -->
      <section class="panel panel--right">
        <div class="panel__header">
          <span class="panel__title">分组</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!canWrite || writing || !currentVariable"
            @click="openGroupDialog(null)"
          >
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
        <div class="panel__body">
          <div v-if="!currentVariable" class="empty-hint">请先选择变量</div>
          <div v-else-if="currentVariable.groups.length === 0" class="empty-hint">暂无分组</div>
          <div
            v-for="g in currentVariable?.groups || []"
            :key="g.id"
            class="group-item"
          >
            <div class="group-item__main">
              <div class="group-item__name">{{ g.name }}</div>
              <div class="group-item__count">{{ g.valueIds.length }} 项</div>
            </div>
            <div v-if="g.description" class="group-item__desc">{{ g.description }}</div>
            <div class="group-item__actions">
              <el-button
                text
                size="small"
                :disabled="!canWrite || writing"
                @click="openGroupDialog(g)"
              >编辑</el-button>
              <el-button
                text
                size="small"
                style="color: var(--app-text-danger)"
                :disabled="!canWrite || writing"
                @click="handleDeleteGroup(g)"
              >删除</el-button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-else-if="!canRead" class="empty-hint" style="padding: 48px 0; text-align: center">
      请在侧栏选择有访问权限的团队。
    </div>

    <!-- 变量新建/编辑对话框 -->
    <el-dialog
      v-model="showVariableDialog"
      :title="editingVariable ? '编辑变量' : '新建变量'"
      width="480px"
    >
      <p v-if="writeError" class="resource-error" role="alert">{{ writeError }}</p>
      <el-form :model="variableForm" label-position="top" :disabled="!canWrite || writing">
        <el-form-item label="变量名" required>
          <el-input
            v-model="variableForm.name"
            placeholder="如 pet，只允许字母数字下划线 1~32 字符"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="variableForm.description"
            type="textarea"
            :rows="2"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showVariableDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!canWrite" :loading="writing" @click="handleSubmitVariable">保存</el-button>
      </template>
    </el-dialog>

    <!-- 单条值新建/编辑对话框 -->
    <el-dialog
      v-model="showValueDialog"
      :title="editingValue ? '编辑候选值' : '新增候选值'"
      width="480px"
    >
      <p v-if="writeError" class="resource-error" role="alert">{{ writeError }}</p>
      <el-form :model="valueForm" label-position="top" :disabled="!canWrite || writing">
        <el-form-item label="值" required>
          <el-input v-model="valueForm.value" placeholder="如 旺财" maxlength="256" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="valueForm.description"
            placeholder="如 拉布拉多（仅展示用，不参与替换输出）"
            maxlength="256"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showValueDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!canWrite" :loading="writing" @click="handleSubmitValue">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量粘贴对话框 -->
    <el-dialog v-model="showBatchDialog" title="批量粘贴候选值" width="640px">
      <p v-if="writeError" class="resource-error" role="alert">{{ writeError }}</p>
      <div class="batch-hint">
        每行一条，用<strong>英文逗号</strong>分隔 <code>值</code> 和 <code>描述</code>，
        描述可省略。重复值将被跳过。
      </div>
      <el-input
        v-model="batchText" :disabled="!canWrite || writing"
        type="textarea"
        :rows="10"
        placeholder="旺财,拉布拉多&#10;小黑,金毛&#10;咪咪,英短&#10;豆豆,柯基"
      />
      <template #footer>
        <el-button @click="showBatchDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!canWrite" :loading="writing" @click="handleBatchInsert">导入</el-button>
      </template>
    </el-dialog>

    <!-- 分组新建/编辑对话框 -->
    <el-dialog
      v-model="showGroupDialog"
      :title="editingGroup ? '编辑分组' : '新建分组'"
      width="720px"
    >
      <p v-if="writeError" class="resource-error" role="alert">{{ writeError }}</p>
      <el-form :model="groupForm" label-position="top" :disabled="!canWrite || writing">
        <el-form-item label="分组名" required>
          <el-input
            v-model="groupForm.name"
            placeholder="如 domestic，只允许字母数字下划线 1~32 字符"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="groupForm.description" placeholder="可选" />
        </el-form-item>
        <el-form-item label="成员">
          <div style="width: 100%">
            <el-input
              v-model="groupValueSearch"
              size="small"
              placeholder="搜索候选值"
              clearable
              style="margin-bottom: 8px"
            />
            <el-table
              ref="groupValueTableRef"
              :data="filteredGroupValues"
              size="small"
              max-height="300"
              @selection-change="handleGroupSelectionChange"
            >
              <el-table-column type="selection" width="40" />
              <el-table-column prop="value" label="值" min-width="120" />
              <el-table-column prop="description" label="描述" min-width="160" />
            </el-table>
            <div class="selected-count">已选 {{ groupForm.valueIds.length }} 项</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGroupDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!canWrite" :loading="writing" @click="handleSubmitGroup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getTeamVariables, createVariable, updateVariable, deleteVariable, addValue, updateValue, deleteValue, batchInsertValues, createGroup, updateGroup, deleteGroup } from '@/api/customVariable'

const appStore = useAppStore()
const userStore = useUserStore()
const selectedTeamId = computed(() => appStore.currentTeamId || null)
const canRead = computed(() => !!selectedTeamId.value && userStore.identityReady && userStore.hasCapability(selectedTeamId.value, 'read'))
const canWrite = computed(() => canRead.value && appStore.currentTeam?.status !== 'ARCHIVED' && userStore.hasCapability(selectedTeamId.value, 'write'))
const variables = ref([])
const currentVariableId = ref(null)
const currentVariable = computed(() => variables.value.find(v => v.id === currentVariableId.value) || null)
const loading = ref(false)
const writing = ref(false)
const loadError = ref('')
const writeError = ref('')
const valueSearchKeyword = ref('')
const showVariableDialog = ref(false)
const editingVariable = ref(null)
const variableForm = reactive({ name: '', description: '' })
const showValueDialog = ref(false)
const editingValue = ref(null)
const valueForm = reactive({ value: '', description: '' })
const showBatchDialog = ref(false)
const batchText = ref('')
const showGroupDialog = ref(false)
const editingGroup = ref(null)
const groupForm = reactive({ name: '', description: '', valueIds: [] })
const groupValueSearch = ref('')
const groupValueTableRef = ref(null)
let version = 0, listSequence = 0, disposed = false, syncingSelection = false, confirmationOpen = false
let dialogContext = null, dialogVariableId = null
function snapshot() { return { teamId: selectedTeamId.value, version } }
function current(ctx) { return !disposed && canRead.value && ctx?.teamId === selectedTeamId.value && ctx.version === version }
function errorMessage(error, fallback) { return error?.response?.data?.msg || error?.msg || error?.message || fallback }
function closeDialogs() { showVariableDialog.value = false; showValueDialog.value = false; showBatchDialog.value = false; showGroupDialog.value = false; dialogContext = null; dialogVariableId = null; batchText.value = ''; editingVariable.value = null; editingValue.value = null; editingGroup.value = null; variableForm.name = ''; variableForm.description = ''; valueForm.value = ''; valueForm.description = ''; groupForm.name = ''; groupForm.description = ''; groupForm.valueIds = [] }
function beginDialog(needsVariable = false) {
  if (!canWrite.value || writing.value || (needsVariable && !currentVariable.value)) return false
  dialogContext = snapshot(); dialogVariableId = currentVariableId.value; writeError.value = ''; return true
}
function matchesDialog() { return current(dialogContext) && canWrite.value && (!dialogVariableId || currentVariableId.value === dialogVariableId) }
watch([selectedTeamId, canRead, canWrite, () => userStore.user?.id], () => {
  if (confirmationOpen) ElMessageBox.close()
  version++; listSequence++; closeDialogs(); variables.value = []; currentVariableId.value = null; valueSearchKeyword.value = ''; loadError.value = ''; writeError.value = ''; loading.value = false; writing.value = false
  if (canRead.value) loadVariables()
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; version++; listSequence++; closeDialogs() })
async function loadVariables() {
  const ctx = snapshot(), sequence = ++listSequence
  if (!canRead.value) { variables.value = []; currentVariableId.value = null; return }
  loading.value = true; loadError.value = ''
  try {
    const data = await getTeamVariables(ctx.teamId)
    if (!current(ctx) || sequence !== listSequence) return
    variables.value = (data || []).map(v => ({ ...v, values: v.values || [], groups: v.groups || [] }))
    if (!variables.value.some(v => v.id === currentVariableId.value)) currentVariableId.value = variables.value[0]?.id || null
  } catch (error) { if (current(ctx) && sequence === listSequence) { variables.value = []; currentVariableId.value = null; loadError.value = errorMessage(error, '变量加载失败。') } }
  finally { if (current(ctx) && sequence === listSequence) loading.value = false }
}
async function executeWrite(ctx, work, success) {
  if (!current(ctx) || !canWrite.value || writing.value) return
  writing.value = true; writeError.value = ''
  try {
    const result = await work(ctx.teamId)
    if (!current(ctx) || !canWrite.value) return
    success?.(result); await loadVariables()
  } catch (error) { if (current(ctx)) writeError.value = errorMessage(error, '操作失败，请重试。') }
  finally { if (current(ctx)) writing.value = false }
}
async function confirmDelete(text) { confirmationOpen = true; try { await ElMessageBox.confirm(text, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', closeOnClickModal: false }); return true } catch { return false } finally { confirmationOpen = false } }
function nameValid(value) { return /^[A-Za-z0-9_]{1,32}$/.test(value) }
const filteredValues = computed(() => {
  const values = currentVariable.value?.values || [], kw = valueSearchKeyword.value.trim().toLowerCase()
  return kw ? values.filter(v => String(v.value).toLowerCase().includes(kw) || (v.description || '').toLowerCase().includes(kw)) : values
})
function openVariableDialog(v) { if (!beginDialog()) return; editingVariable.value = v; variableForm.name = v?.name || ''; variableForm.description = v?.description || ''; showVariableDialog.value = true }
async function handleSubmitVariable() {
  if (!matchesDialog()) return
  if (!nameValid(variableForm.name)) { writeError.value = '变量名只允许 1–32 个字母、数字或下划线。'; return }
  const ctx = dialogContext, id = editingVariable.value?.id, payload = { name: variableForm.name, description: variableForm.description }
  await executeWrite(ctx, team => id ? updateVariable(team, id, payload) : createVariable(team, payload), result => { if (!id && result?.id) currentVariableId.value = result.id; showVariableDialog.value = false; ElMessage.success(id ? '已更新变量' : '已创建变量') })
}
async function handleDeleteVariable(v) {
  if (!canWrite.value || writing.value) return
  const ctx = snapshot()
  if (!await confirmDelete('确定删除变量「' + v.name + '」？将同时清理所有候选值和分组。')) return
  await executeWrite(ctx, team => deleteVariable(team, v.id), () => { if (currentVariableId.value === v.id) currentVariableId.value = null; ElMessage.success('已删除变量') })
}
function openValueDialog(row) { if (!beginDialog(true)) return; editingValue.value = row; valueForm.value = row?.value || ''; valueForm.description = row?.description || ''; showValueDialog.value = true }
async function handleSubmitValue() {
  if (!matchesDialog() || !dialogVariableId) return
  if (!valueForm.value || valueForm.value.length > 256 || valueForm.description.length > 256) { writeError.value = '请填写 1–256 字符的值，描述最多 256 字符。'; return }
  const ctx = dialogContext, variableId = dialogVariableId, id = editingValue.value?.id, payload = { value: valueForm.value, description: valueForm.description }
  await executeWrite(ctx, team => id ? updateValue(team, variableId, id, payload) : addValue(team, variableId, payload), () => { showValueDialog.value = false; ElMessage.success(id ? '已更新候选值' : '已新增候选值') })
}
async function handleDeleteValue(row) {
  if (!canWrite.value || writing.value || !currentVariable.value) return
  const ctx = snapshot(), variableId = currentVariableId.value
  if (!await confirmDelete('确定删除候选值「' + row.value + '」？')) return
  await executeWrite(ctx, team => deleteValue(team, variableId, row.id), () => ElMessage.success('已删除候选值'))
}
function openBatchDialog() { if (!beginDialog(true)) return; batchText.value = ''; showBatchDialog.value = true }
async function handleBatchInsert() {
  if (!matchesDialog() || !dialogVariableId) return
  const values = []
  for (const raw of batchText.value.split('\n')) {
    const line = raw.trim(); if (!line) continue
    const index = line.indexOf(','), value = (index < 0 ? line : line.slice(0, index)).trim(), description = index < 0 ? '' : line.slice(index + 1).trim()
    if (value) values.push({ value, description })
  }
  if (!values.length) { writeError.value = '没有可导入的条目。'; return }
  if (values.some(v => v.value.length > 256 || v.description.length > 256)) { writeError.value = '每项的值与描述均不得超过 256 字符。'; return }
  const ctx = dialogContext, variableId = dialogVariableId
  await executeWrite(ctx, team => batchInsertValues(team, variableId, values), result => { showBatchDialog.value = false; batchText.value = ''; ElMessage.success('导入完成：新增 ' + result.inserted + '，跳过 ' + result.skipped) })
}
const filteredGroupValues = computed(() => {
  const values = currentVariable.value?.values || [], kw = groupValueSearch.value.trim().toLowerCase()
  return kw ? values.filter(v => String(v.value).toLowerCase().includes(kw) || (v.description || '').toLowerCase().includes(kw)) : values
})
function openGroupDialog(g) {
  if (!beginDialog(true)) return
  syncingSelection = true
  editingGroup.value = g; groupForm.name = g?.name || ''; groupForm.description = g?.description || ''; groupForm.valueIds = [...(g?.valueIds || [])]; groupValueSearch.value = ''; showGroupDialog.value = true; nextTick(syncGroupSelection)
}
function syncGroupSelection() {
  if (!groupValueTableRef.value || !showGroupDialog.value) return
  syncingSelection = true
  groupValueTableRef.value.clearSelection()
  const ids = new Set(groupForm.valueIds)
  for (const row of filteredGroupValues.value) groupValueTableRef.value.toggleRowSelection(row, ids.has(row.id))
  nextTick(() => { syncingSelection = false })
}
function handleGroupSelectionChange(rows) {
  if (syncingSelection) return
  const visible = new Set(filteredGroupValues.value.map(v => v.id))
  groupForm.valueIds = [...new Set([...groupForm.valueIds.filter(id => !visible.has(id)), ...rows.map(r => r.id)])]
}
async function handleSubmitGroup() {
  if (!matchesDialog() || !dialogVariableId) return
  if (!nameValid(groupForm.name)) { writeError.value = '分组名只允许 1–32 个字母、数字或下划线。'; return }
  const ctx = dialogContext, variableId = dialogVariableId, id = editingGroup.value?.id, payload = { name: groupForm.name, description: groupForm.description, valueIds: [...groupForm.valueIds] }
  await executeWrite(ctx, team => id ? updateGroup(team, variableId, id, payload) : createGroup(team, variableId, payload), () => { showGroupDialog.value = false; ElMessage.success(id ? '已更新分组' : '已创建分组') })
}
async function handleDeleteGroup(g) {
  if (!canWrite.value || writing.value || !currentVariable.value) return
  const ctx = snapshot(), variableId = currentVariableId.value
  if (!await confirmDelete('确定删除分组「' + g.name + '」？')) return
  await executeWrite(ctx, team => deleteGroup(team, variableId, g.id), () => ElMessage.success('已删除分组'))
}
watch(currentVariableId, () => { valueSearchKeyword.value = ''; closeDialogs() })
watch(filteredGroupValues, () => { if (showGroupDialog.value) { syncingSelection = true; nextTick(syncGroupSelection) } }, { flush: 'sync' })
</script>

<style scoped>
.team-variables { min-width: 0; display: flex; flex-direction: column; gap: 16px; color: var(--app-text-primary); }
.page-header, .page-header__left, .page-header__right { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }.page-header { justify-content: space-between; }
.page-title { margin: 0; font-size: 26px; font-weight: 600; letter-spacing: -.025em; color: var(--app-text-primary); }
.team-context { font-size: var(--app-font-caption); color: var(--app-text-secondary); padding: 7px 10px; background: var(--app-bg-surface); border-radius: 8px; }.page-header__right :deep(.el-tag--success) { color: var(--app-text-success); background: var(--el-color-success-light-9); }.page-header__right :deep(.el-tag--info) { color: var(--app-text-secondary); background: var(--app-bg-control); }
.panels { display: grid; grid-template-columns: minmax(180px, 230px) minmax(0, 1fr) minmax(200px, 260px); gap: 14px; min-width: 0; align-items: start; }
.panel { min-width: 0; background: var(--app-bg-surface); border-radius: 16px; display: flex; flex-direction: column; }
.panel__header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; padding: 18px 16px 12px; gap: 10px; }.panel__title { font-size: 15px; font-weight: 600; }.panel__subtitle { font-size: 12px; color: var(--app-text-secondary); font-weight: 400; overflow-wrap: anywhere; }
.panel__header-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; min-width: 0; }
.panel__header-actions :deep(.el-button) { height: 32px; min-height: 32px; }.panel__body { min-width: 0; padding: 8px 12px 12px; }.panel__body--table { padding: 0 12px 12px; }
.value-text { font: 13px var(--app-font-mono); color: var(--app-text-primary); }.desc-text { font-size: 13px; color: var(--app-text-regular); }.desc-muted, .empty-hint { font-size: 13px; color: var(--app-text-secondary); }.empty-hint { padding: 24px 16px; text-align: center; }
.variable-item { padding: 12px; border-radius: 10px; cursor: pointer; margin-bottom: 4px; }.variable-item:hover { background: var(--app-bg-control); }.variable-item:focus-visible { outline: 2px solid var(--app-focus-color); outline-offset: 1px; }.variable-item--active { background: var(--app-bg-selected); }.variable-item--active .variable-item__name { color: var(--app-accent); }
.variable-item__name, .group-item__name { font: 500 14px var(--app-font-mono); color: var(--app-text-primary); overflow-wrap: anywhere; }.variable-item__desc, .group-item__desc { font-size: 12px; color: var(--app-text-secondary); margin-top: 5px; overflow-wrap: anywhere; }
.variable-item__actions, .group-item__actions, .row-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 6px; }.row-actions { margin-top: 0; flex-wrap: nowrap; }.row-actions .el-button + .el-button { margin-left: 0; }
.group-item { padding: 12px; background: var(--app-bg-control); border-radius: 10px; margin-bottom: 10px; }.group-item__main { display: flex; justify-content: space-between; gap: 10px; align-items: center; }.group-item__count, .selected-count { font-size: 12px; color: var(--app-accent); }.selected-count { margin-top: 10px; }
.batch-hint { font-size: 13px; line-height: 1.8; color: var(--app-text-secondary); margin-bottom: 12px; }.batch-hint code { padding: 2px 6px; background: var(--app-bg-control); border-radius: 8px; color: var(--app-accent); }
.resource-state { text-align: center; color: var(--app-text-secondary); padding: 32px 20px; }.resource-state p { margin: 10px 0; }.resource-error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); border-radius: 10px; padding: 12px 16px; margin: 0 0 12px; font-size: 13px; overflow-wrap: anywhere; }
@media (max-width: 1200px) { .panels { grid-template-columns: minmax(180px, 230px) minmax(0, 1fr); }.panel--right { grid-column: 1 / -1; } }
@media (max-width: 760px) { .panels { grid-template-columns: minmax(0, 1fr); }.panel--right { grid-column: auto; }.page-title { font-size: 24px; }.panel__header-actions { width: 100%; }.panel__header-actions :deep(.el-input) { width: 100% !important; margin-right: 0 !important; } }
</style>
