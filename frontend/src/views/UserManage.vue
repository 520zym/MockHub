<!--
  UserManage.vue — 用户管理页面（超级管理员专属）
  功能：用户列表表格、创建/编辑用户、团队分配对话框、删除用户
-->
<template>
  <div class="page-container page-user-manage">
    <!-- 新建按钮 -->
    <div class="page-header">
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建用户
      </el-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="users.length === 0" description="暂无用户" />

    <!-- 用户列表表格（Soft UI 无边框风格） -->
    <div v-else class="soft-card">
      <el-table :data="users" style="width: 100%">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column label="全局角色" width="140">
          <template #default="{ row }">
            <el-tag
              :type="roleTagType(row.globalRole)"
              size="small"
              effect="light"
              round
            >
              {{ roleLabel(row.globalRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属团队" min-width="200">
          <template #default="{ row }">
            <div class="team-tags-cell" v-if="row.teams && row.teams.length > 0">
              <TeamTag
                v-for="t in row.teams"
                :key="t.teamId"
                :identifier="t.identifier"
                :color="getTeamColor(t.teamId)"
              />
            </div>
            <span v-else class="text-secondary">未分配团队</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button text type="primary" size="small" @click="openAssignDialog(row)">
              分配团队
            </el-button>
            <el-button
              text
              type="danger"
              size="small"
              :disabled="row.globalRole === 'SUPER_ADMIN'"
              @click="handleDeleteUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建用户对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建用户"
      width="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="登录时使用的用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input
            v-model="createForm.password"
            type="password"
            placeholder="用户首次登录后需修改"
            show-password
          />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="页面中展示的名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="全局角色" prop="globalRole">
          <el-select v-model="createForm.globalRole" style="width: 100%">
            <el-option label="超级管理员" value="SUPER_ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreateUser">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑用户"
      width="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-position="top"
      >
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="editForm.displayName" placeholder="页面中展示的名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="全局角色" prop="globalRole">
          <el-select
            v-model="editForm.globalRole"
            style="width: 100%"
            :disabled="editingUser?.globalRole === 'SUPER_ADMIN'"
          >
            <el-option label="超级管理员" value="SUPER_ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <!-- 超管不可降级提示 -->
          <div v-if="editingUser?.globalRole === 'SUPER_ADMIN'" class="form-hint">
            超级管理员角色不可降级
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditUser">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 团队分配对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      title="分配团队"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p class="assign-dialog-tip">
        为用户 <strong>{{ assigningUser?.displayName }}</strong> 分配团队和角色：
      </p>

      <!-- 加载状态 -->
      <div v-if="assignLoading" class="loading-container">
        <el-skeleton :rows="4" animated />
      </div>

      <!-- 空状态：无团队可分配 -->
      <el-empty v-else-if="allTeams.length === 0" description="暂无团队可分配" :image-size="80" />

      <!-- 团队分配列表 -->
      <div v-else class="assign-list">
        <div
          v-for="team in allTeams"
          :key="team.id"
          class="assign-row"
        >
          <div class="assign-row__team">
            <TeamTag :identifier="team.identifier" :color="team.color" />
            <span class="assign-row__name">{{ team.name }}</span>
          </div>
          <el-select
            v-model="teamRoleMap[team.id]"
            placeholder="未分配"
            clearable
            size="small"
            class="assign-row__role"
          >
            <el-option label="团队管理员" value="TEAM_ADMIN" />
            <el-option label="普通成员" value="MEMBER" />
          </el-select>
        </div>
      </div>

      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAssignTeams">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 用户管理页面
 * - 表格展示用户列表，含角色标签和团队 TeamTag
 * - 支持创建/编辑/删除用户
 * - 支持团队分配（整体替换模式）
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser, assignTeams } from '@/api/users'
import { getTeams } from '@/api/teams'
import TeamTag from '@/components/TeamTag.vue'

// ========== 用户列表 ==========
const loading = ref(false)
const users = ref([])
const allTeams = ref([]) // 所有团队，用于获取颜色和分配

async function loadUsers() {
  loading.value = true
  try {
    users.value = await getUsers()
  } catch (err) {
    // 拦截器已处理
  } finally {
    loading.value = false
  }
}

// 获取团队颜色（用户的 teams 数组中不含 color，需从 allTeams 获取）
function getTeamColor(teamId) {
  const team = allTeams.value.find(t => t.id === teamId)
  return team?.color || '#6366F1'
}

onMounted(async () => {
  // 并行加载用户列表和团队列表
  const [, teamsData] = await Promise.all([
    loadUsers(),
    getTeams().catch(() => [])
  ])
  if (teamsData) {
    allTeams.value = teamsData
  }
})

// ========== 角色显示工具 ==========
function roleLabel(role) {
  const map = { SUPER_ADMIN: '超级管理员', USER: '普通用户' }
  return map[role] || role
}

function roleTagType(role) {
  // 超管用 danger（醒目），团队管理员用 warning，普通成员用 info
  const map = { SUPER_ADMIN: 'danger', USER: 'info' }
  return map[role] || 'info'
}

// ========== 日期格式化 ==========
function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ========== 创建用户对话框 ==========
const createDialogVisible = ref(false)
const createFormRef = ref(null)
const createSubmitting = ref(false)

const createForm = reactive({
  username: '',
  password: '',
  displayName: '',
  globalRole: 'USER'
})

const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' }
  ],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  globalRole: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function openCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.displayName = ''
  createForm.globalRole = 'USER'
  createDialogVisible.value = true
}

async function handleCreateUser() {
  const formEl = createFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  createSubmitting.value = true
  try {
    await createUser({ ...createForm })
    ElMessage.success('用户已创建')
    createDialogVisible.value = false
    await loadUsers()
  } catch (err) {
    // 拦截器已处理 40201 等错误
  } finally {
    createSubmitting.value = false
  }
}

// ========== 编辑用户对话框 ==========
const editDialogVisible = ref(false)
const editFormRef = ref(null)
const editSubmitting = ref(false)
const editingUser = ref(null)

const editForm = reactive({
  displayName: '',
  globalRole: ''
})

const editRules = {
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  globalRole: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function openEditDialog(user) {
  editingUser.value = user
  editForm.displayName = user.displayName
  editForm.globalRole = user.globalRole
  editDialogVisible.value = true
}

async function handleEditUser() {
  const formEl = editFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  editSubmitting.value = true
  try {
    await updateUser(editingUser.value.id, { ...editForm })
    ElMessage.success('用户已更新')
    editDialogVisible.value = false
    await loadUsers()
  } catch (err) {
    // 拦截器已处理 40203 等错误
  } finally {
    editSubmitting.value = false
  }
}

// ========== 删除用户 ==========
async function handleDeleteUser(user) {
  // 超管不可删除（按钮已 disabled，这里做二次防御）
  if (user.globalRole === 'SUPER_ADMIN') {
    ElMessage.warning('不能删除超级管理员')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除用户「${user.displayName}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteUser(user.id)
    ElMessage.success('用户已删除')
    await loadUsers()
  } catch (err) {
    // 拦截器已处理 40202 等错误
  }
}

// ========== 团队分配对话框 ==========
const assignDialogVisible = ref(false)
const assignLoading = ref(false)
const assignSubmitting = ref(false)
const assigningUser = ref(null)
const teamRoleMap = reactive({}) // { teamId: 'TEAM_ADMIN' | 'MEMBER' | null }

async function openAssignDialog(user) {
  assigningUser.value = user
  assignDialogVisible.value = true

  // 清空映射
  Object.keys(teamRoleMap).forEach(key => delete teamRoleMap[key])

  assignLoading.value = true
  try {
    // 加载最新团队列表
    allTeams.value = await getTeams()

    // 根据用户当前团队信息初始化映射
    if (user.teams) {
      user.teams.forEach(t => {
        teamRoleMap[t.teamId] = t.role
      })
    }
  } catch (err) {
    // 拦截器已处理
  } finally {
    assignLoading.value = false
  }
}

async function handleAssignTeams() {
  // 构建 teamRoles 数组：只包含有角色的团队
  const teamRoles = []
  for (const [teamId, role] of Object.entries(teamRoleMap)) {
    if (role) {
      teamRoles.push({ teamId, role })
    }
  }

  assignSubmitting.value = true
  try {
    await assignTeams(assigningUser.value.id, { teamRoles })
    ElMessage.success('团队分配已保存')
    assignDialogVisible.value = false
    await loadUsers()
  } catch (err) {
    // 拦截器已处理
  } finally {
    assignSubmitting.value = false
  }
}
</script>

<style lang="scss" scoped>
// ========== 页面头部 ==========
.page-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 24px;
}

// ========== 团队标签单元格 ==========
.team-tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.text-secondary {
  color: #A3AED0;
  font-size: 13px;
}

// ========== 表单提示 ==========
.form-hint {
  font-size: 12px;
  color: #A3AED0;
  margin-top: 4px;
}

// ========== 团队分配对话框 ==========
.assign-dialog-tip {
  color: #4A5568;
  font-size: 14px;
  margin: 0 0 16px;
}

.assign-list {
  max-height: 400px;
  overflow-y: auto;
}

.assign-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #F1F5F9;

  &:last-child {
    border-bottom: none;
  }

  &__team {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__name {
    font-size: 14px;
    color: #1B2559;
    font-weight: 500;
  }

  &__role {
    width: 150px;
  }
}

// ========== 加载容器 ==========
.loading-container {
  padding: 40px 0;
}
</style>
