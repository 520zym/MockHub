<template>
  <div class="team-variables">
    <!-- 顶部工具条：团队选择器 + 当前角色提示 -->
    <div class="page-header">
      <div class="page-header__left">
        <h2 class="page-title">动态变量</h2>
        <el-select
          v-model="selectedTeamId"
          placeholder="选择团队"
          size="default"
          class="team-picker"
          @change="handleTeamChange"
        >
          <el-option
            v-for="team in accessibleTeams"
            :key="team.id"
            :label="team.name"
            :value="team.id"
          >
            <TeamTag :identifier="team.identifier" :color="team.color" />
            <span style="margin-left: 8px">{{ team.name }}</span>
          </el-option>
        </el-select>
      </div>
      <div class="page-header__right">
        <el-tag v-if="canWrite" type="success" effect="light">可编辑</el-tag>
        <el-tag v-else type="info" effect="light">只读</el-tag>
      </div>
    </div>

    <!-- 三栏主内容 -->
    <div v-if="selectedTeamId" class="panels">
      <!-- 左栏：变量列表 -->
      <section class="panel panel--left">
        <div class="panel__header">
          <span class="panel__title">变量</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!canWrite"
            @click="openVariableDialog(null)"
          >
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
        <div class="panel__body">
          <div v-if="variables.length === 0" class="empty-hint">暂无变量</div>
          <div
            v-for="v in variables"
            :key="v.id"
            class="variable-item"
            :class="{ 'variable-item--active': currentVariableId === v.id }"
            @click="currentVariableId = v.id"
          >
            <div class="variable-item__name">{{ v.name }}</div>
            <div v-if="v.description" class="variable-item__desc">{{ v.description }}</div>
            <div class="variable-item__actions" @click.stop>
              <el-button
                type="text"
                size="small"
                :disabled="!canWrite"
                @click="openVariableDialog(v)"
              >编辑</el-button>
              <el-button
                type="text"
                size="small"
                style="color: var(--el-color-danger)"
                :disabled="!canWrite"
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
              :disabled="!canWrite || !currentVariable"
              @click="showBatchDialog = true"
            >批量粘贴</el-button>
            <el-button
              type="primary"
              size="small"
              :disabled="!canWrite || !currentVariable"
              @click="openValueDialog(null)"
            >
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
        </div>
        <div class="panel__body">
          <el-table
            v-if="currentVariable"
            :data="filteredValues"
            size="small"
            stripe
            height="100%"
          >
            <el-table-column prop="value" label="值" min-width="140" />
            <el-table-column prop="description" label="描述" min-width="180" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="text"
                  size="small"
                  :disabled="!canWrite"
                  @click="openValueDialog(row)"
                >编辑</el-button>
                <el-button
                  type="text"
                  size="small"
                  style="color: var(--el-color-danger)"
                  :disabled="!canWrite"
                  @click="handleDeleteValue(row)"
                >删除</el-button>
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
            :disabled="!canWrite || !currentVariable"
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
                type="text"
                size="small"
                :disabled="!canWrite"
                @click="openGroupDialog(g)"
              >编辑</el-button>
              <el-button
                type="text"
                size="small"
                style="color: var(--el-color-danger)"
                :disabled="!canWrite"
                @click="handleDeleteGroup(g)"
              >删除</el-button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-else class="empty-hint" style="padding: 48px 0; text-align: center">
      请选择一个团队查看动态变量
    </div>

    <!-- 变量新建/编辑对话框 -->
    <el-dialog
      v-model="showVariableDialog"
      :title="editingVariable ? '编辑变量' : '新建变量'"
      width="480px"
    >
      <el-form :model="variableForm" label-width="80px">
        <el-form-item label="变量名" required>
          <el-input
            v-model="variableForm.name"
            placeholder="如 airport，只允许字母数字下划线 1~32 字符"
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
        <el-button type="primary" @click="handleSubmitVariable">保存</el-button>
      </template>
    </el-dialog>

    <!-- 单条值新建/编辑对话框 -->
    <el-dialog
      v-model="showValueDialog"
      :title="editingValue ? '编辑候选值' : '新增候选值'"
      width="480px"
    >
      <el-form :model="valueForm" label-width="80px">
        <el-form-item label="值" required>
          <el-input v-model="valueForm.value" placeholder="如 ZBAA" maxlength="256" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="valueForm.description"
            placeholder="如 北京首都（仅展示用，不参与替换输出）"
            maxlength="256"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showValueDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitValue">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量粘贴对话框 -->
    <el-dialog v-model="showBatchDialog" title="批量粘贴候选值" width="640px">
      <div class="batch-hint">
        每行一条。可用制表符或多空格分隔 <code>值</code> 和 <code>描述</code>，
        描述可省略。重复值将被跳过。
      </div>
      <el-input
        v-model="batchText"
        type="textarea"
        :rows="10"
        placeholder="ZBAA  北京首都&#10;ZSPD  上海浦东&#10;ZGGG  广州白云"
      />
      <template #footer>
        <el-button @click="showBatchDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBatchInsert">导入</el-button>
      </template>
    </el-dialog>

    <!-- 分组新建/编辑对话框 -->
    <el-dialog
      v-model="showGroupDialog"
      :title="editingGroup ? '编辑分组' : '新建分组'"
      width="720px"
    >
      <el-form :model="groupForm" label-width="80px">
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
        <el-button type="primary" @click="handleSubmitGroup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 团队动态变量维护页
 *
 * 三栏布局：左侧变量列表，中间候选值表格，右侧分组列表。
 * 顶部团队选择器：超管可切所有团队，其他人只看自己所在团队。
 * 所有写入按钮按角色禁用（canWrite = 团队管理员 或 超管）。
 */
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import TeamTag from '@/components/TeamTag.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  getTeamVariables,
  createVariable, updateVariable, deleteVariable,
  addValue, updateValue, deleteValue, batchInsertValues,
  createGroup, updateGroup, deleteGroup
} from '@/api/customVariable'

const appStore = useAppStore()
const userStore = useUserStore()

// ---------- 团队选择 ----------
const selectedTeamId = ref(null)

// 当前用户可访问的团队列表（超管看全部，其他人按 userTeamIds 过滤）
const accessibleTeams = computed(() => {
  if (userStore.isSuperAdmin) {
    return appStore.teams
  }
  const ids = new Set(userStore.userTeamIds)
  return appStore.teams.filter(t => ids.has(t.id))
})

// 当前团队是否可写入（团队管理员或超管）
const canWrite = computed(() => {
  if (!selectedTeamId.value) return false
  return userStore.isTeamAdmin(selectedTeamId.value)
})

// ---------- 变量列表 ----------
const variables = ref([])
const currentVariableId = ref(null)
const currentVariable = computed(() =>
  variables.value.find(v => v.id === currentVariableId.value) || null
)

async function loadVariables() {
  if (!selectedTeamId.value) {
    variables.value = []
    return
  }
  try {
    variables.value = await getTeamVariables(selectedTeamId.value)
    // 保持选中或默认选第一个
    if (variables.value.length > 0) {
      if (!variables.value.find(v => v.id === currentVariableId.value)) {
        currentVariableId.value = variables.value[0].id
      }
    } else {
      currentVariableId.value = null
    }
  } catch (e) {
    // 全局拦截器会弹错，这里静默
  }
}

function handleTeamChange() {
  currentVariableId.value = null
  loadVariables()
}

// ---------- 值搜索 ----------
const valueSearchKeyword = ref('')
const filteredValues = computed(() => {
  if (!currentVariable.value) return []
  const kw = valueSearchKeyword.value.trim().toLowerCase()
  if (!kw) return currentVariable.value.values
  return currentVariable.value.values.filter(v =>
    v.value.toLowerCase().includes(kw) ||
    (v.description && v.description.toLowerCase().includes(kw))
  )
})

// ---------- 变量对话框 ----------
const showVariableDialog = ref(false)
const editingVariable = ref(null)
const variableForm = reactive({ name: '', description: '' })

function openVariableDialog(v) {
  editingVariable.value = v
  variableForm.name = v ? v.name : ''
  variableForm.description = v ? (v.description || '') : ''
  showVariableDialog.value = true
}

async function handleSubmitVariable() {
  const payload = { name: variableForm.name, description: variableForm.description }
  try {
    if (editingVariable.value) {
      await updateVariable(selectedTeamId.value, editingVariable.value.id, payload)
      ElMessage.success('已更新')
    } else {
      const created = await createVariable(selectedTeamId.value, payload)
      currentVariableId.value = created.id
      ElMessage.success('已创建')
    }
    showVariableDialog.value = false
    await loadVariables()
  } catch (e) {
    // 全局拦截器已弹错
  }
}

async function handleDeleteVariable(v) {
  try {
    await ElMessageBox.confirm(
      `确定删除变量「${v.name}」？将同时清理所有候选值和分组。`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteVariable(selectedTeamId.value, v.id)
    ElMessage.success('已删除')
    if (currentVariableId.value === v.id) {
      currentVariableId.value = null
    }
    await loadVariables()
  } catch (e) {
    // 用户取消或后端报错
  }
}

// ---------- 值对话框 ----------
const showValueDialog = ref(false)
const editingValue = ref(null)
const valueForm = reactive({ value: '', description: '' })

function openValueDialog(row) {
  editingValue.value = row
  valueForm.value = row ? row.value : ''
  valueForm.description = row ? (row.description || '') : ''
  showValueDialog.value = true
}

async function handleSubmitValue() {
  const payload = { value: valueForm.value, description: valueForm.description }
  try {
    if (editingValue.value) {
      await updateValue(
        selectedTeamId.value,
        currentVariable.value.id,
        editingValue.value.id,
        payload
      )
      ElMessage.success('已更新')
    } else {
      await addValue(selectedTeamId.value, currentVariable.value.id, payload)
      ElMessage.success('已新增')
    }
    showValueDialog.value = false
    await loadVariables()
  } catch (e) {
    // 全局拦截器已弹错
  }
}

async function handleDeleteValue(row) {
  try {
    await ElMessageBox.confirm(`确定删除候选值「${row.value}」？`, '确认删除', { type: 'warning' })
    await deleteValue(selectedTeamId.value, currentVariable.value.id, row.id)
    ElMessage.success('已删除')
    await loadVariables()
  } catch (e) {
    // ignore
  }
}

// ---------- 批量粘贴 ----------
const showBatchDialog = ref(false)
const batchText = ref('')

async function handleBatchInsert() {
  const lines = batchText.value.split('\n')
  const values = []
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    // 按制表符或连续两个及以上空白分隔
    const parts = line.split(/\t+|\s{2,}/)
    const value = parts[0].trim()
    const description = parts.length > 1 ? parts.slice(1).join(' ').trim() : ''
    if (value) {
      values.push({ value, description })
    }
  }
  if (values.length === 0) {
    ElMessage.warning('没有可导入的条目')
    return
  }
  try {
    const result = await batchInsertValues(
      selectedTeamId.value,
      currentVariable.value.id,
      values
    )
    ElMessage.success(`导入完成：新增 ${result.inserted}，跳过 ${result.skipped}`)
    showBatchDialog.value = false
    batchText.value = ''
    await loadVariables()
  } catch (e) {
    // ignore
  }
}

// ---------- 分组对话框 ----------
const showGroupDialog = ref(false)
const editingGroup = ref(null)
const groupForm = reactive({ name: '', description: '', valueIds: [] })
const groupValueSearch = ref('')
const groupValueTableRef = ref(null)

// 分组对话框里的候选值表格（过滤+选择）
const filteredGroupValues = computed(() => {
  if (!currentVariable.value) return []
  const kw = groupValueSearch.value.trim().toLowerCase()
  if (!kw) return currentVariable.value.values
  return currentVariable.value.values.filter(v =>
    v.value.toLowerCase().includes(kw) ||
    (v.description && v.description.toLowerCase().includes(kw))
  )
})

function openGroupDialog(g) {
  editingGroup.value = g
  groupForm.name = g ? g.name : ''
  groupForm.description = g ? (g.description || '') : ''
  groupForm.valueIds = g ? [...g.valueIds] : []
  groupValueSearch.value = ''
  showGroupDialog.value = true
  // 等 dialog + table 渲染完再勾选已有成员
  nextTick(() => {
    syncGroupSelection()
  })
}

// 把 groupForm.valueIds 同步到表格的 selection（用于编辑时回显选中态）
function syncGroupSelection() {
  if (!groupValueTableRef.value || !currentVariable.value) return
  const selectedSet = new Set(groupForm.valueIds)
  for (const row of currentVariable.value.values) {
    groupValueTableRef.value.toggleRowSelection(row, selectedSet.has(row.id))
  }
}

function handleGroupSelectionChange(rows) {
  groupForm.valueIds = rows.map(r => r.id)
}

async function handleSubmitGroup() {
  const payload = {
    name: groupForm.name,
    description: groupForm.description,
    valueIds: groupForm.valueIds
  }
  try {
    if (editingGroup.value) {
      await updateGroup(
        selectedTeamId.value,
        currentVariable.value.id,
        editingGroup.value.id,
        payload
      )
      ElMessage.success('已更新')
    } else {
      await createGroup(selectedTeamId.value, currentVariable.value.id, payload)
      ElMessage.success('已创建')
    }
    showGroupDialog.value = false
    await loadVariables()
  } catch (e) {
    // ignore
  }
}

async function handleDeleteGroup(g) {
  try {
    await ElMessageBox.confirm(`确定删除分组「${g.name}」？`, '确认删除', { type: 'warning' })
    await deleteGroup(selectedTeamId.value, currentVariable.value.id, g.id)
    ElMessage.success('已删除')
    await loadVariables()
  } catch (e) {
    // ignore
  }
}

// ---------- 初始化 ----------
onMounted(async () => {
  if (appStore.teams.length === 0) {
    await appStore.loadTeams()
  }
  if (accessibleTeams.value.length > 0) {
    selectedTeamId.value = accessibleTeams.value[0].id
    await loadVariables()
  }
})

watch(currentVariableId, () => {
  valueSearchKeyword.value = ''
})
</script>

<style scoped>
.team-variables {
  padding: 24px 32px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-header__left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1B2559;
  margin: 0;
}

.team-picker {
  width: 240px;
}

.page-header__right {
  display: flex;
  gap: 8px;
}

.panels {
  flex: 1;
  display: grid;
  grid-template-columns: 260px 1fr 300px;
  gap: 16px;
  min-height: 0;
}

.panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(27, 37, 89, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #F1F5F9;
  gap: 8px;
  flex-wrap: wrap;
}

.panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1B2559;
}

.panel__subtitle {
  font-size: 13px;
  color: #A3AED0;
  font-weight: 400;
}

.panel__header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.panel__body {
  flex: 1;
  padding: 8px;
  overflow: auto;
  min-height: 0;
}

.panel--middle .panel__body {
  padding: 0;
}

.empty-hint {
  padding: 24px 16px;
  text-align: center;
  color: #A3AED0;
  font-size: 13px;
}

.variable-item {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 4px;
}

.variable-item:hover {
  background: #F7F8FA;
}

.variable-item--active {
  background: #E0E7FF;
}

.variable-item--active .variable-item__name {
  color: #6366F1;
}

.variable-item__name {
  font-size: 14px;
  font-weight: 600;
  color: #1B2559;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.variable-item__desc {
  font-size: 12px;
  color: #64748B;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.variable-item__actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}

.group-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #F7F8FA;
  margin-bottom: 8px;
}

.group-item__main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.group-item__name {
  font-size: 14px;
  font-weight: 600;
  color: #1B2559;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.group-item__count {
  font-size: 12px;
  color: #6366F1;
}

.group-item__desc {
  font-size: 12px;
  color: #64748B;
  margin-top: 4px;
}

.group-item__actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.batch-hint {
  font-size: 12px;
  color: #64748B;
  margin-bottom: 12px;
}

.batch-hint code {
  background: #F1F5F9;
  padding: 1px 6px;
  border-radius: 4px;
  color: #6366F1;
}

.selected-count {
  margin-top: 8px;
  font-size: 12px;
  color: #6366F1;
}
</style>
