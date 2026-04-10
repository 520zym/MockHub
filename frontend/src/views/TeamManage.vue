<!--
  TeamManage.vue — 团队管理页面（超级管理员专属）
  功能：团队卡片网格展示、创建/编辑团队、成员管理（抽屉）、删除团队
-->
<template>
  <div class="page-container page-team-manage">
    <!-- 页面标题 + 新建按钮 -->
    <div class="page-header">
      <h1 class="page-title">团队管理</h1>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建团队
      </el-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="teams.length === 0" description="暂无团队，点击右上方按钮创建" />

    <!-- 团队卡片网格 -->
    <div v-else class="team-grid">
      <div
        v-for="team in teams"
        :key="team.id"
        class="soft-card team-card"
      >
        <!-- 卡片头部：TeamTag + 团队名 -->
        <div class="team-card__header">
          <TeamTag :identifier="team.identifier" :color="team.color" />
          <span class="team-card__name">{{ team.name }}</span>
        </div>

        <!-- 统计数字 -->
        <div class="team-card__stats">
          <div class="stat-item">
            <span class="stat-value">{{ team.memberCount }}</span>
            <span class="stat-label">成员</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ team.apiCount }}</span>
            <span class="stat-label">接口</span>
          </div>
        </div>

        <!-- 底部操作 -->
        <div class="team-card__actions">
          <el-button text type="primary" size="small" @click="openEditDialog(team)">
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
          <el-button text type="primary" size="small" @click="openMemberDrawer(team)">
            <el-icon><User /></el-icon>
            成员
          </el-button>
          <el-button text type="danger" size="small" @click="handleDeleteTeam(team)">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- 创建/编辑团队对话框 -->
    <el-dialog
      v-model="teamDialogVisible"
      :title="isEditMode ? '编辑团队' : '新建团队'"
      width="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="teamFormRef"
        :model="teamForm"
        :rules="teamRules"
        label-position="top"
      >
        <el-form-item label="团队名称" prop="name">
          <el-input v-model="teamForm.name" placeholder="如：前端团队" maxlength="50" />
        </el-form-item>
        <el-form-item label="团队标识" prop="identifier">
          <el-input
            v-model="teamForm.identifier"
            placeholder="如：FE（2~8 位大写字母/数字）"
            maxlength="8"
            @input="teamForm.identifier = teamForm.identifier.toUpperCase().replace(/[^A-Z0-9]/g, '')"
          />
        </el-form-item>
        <el-form-item label="团队颜色" prop="color">
          <div class="color-picker-row">
            <el-color-picker v-model="teamForm.color" />
            <span class="color-preview-text" :style="{ color: teamForm.color }">{{ teamForm.color }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="teamDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="teamSubmitting" @click="handleSubmitTeam">
          {{ isEditMode ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 成员管理抽屉 -->
    <el-drawer
      v-model="memberDrawerVisible"
      :title="currentTeam?.name + ' — 成员管理'"
      size="520px"
      destroy-on-close
    >
      <!-- 添加成员区域 -->
      <div class="member-add-section">
        <el-select
          v-model="addMemberForm.userId"
          placeholder="选择要添加的用户"
          filterable
          class="member-add-select"
        >
          <el-option
            v-for="user in availableUsers"
            :key="user.id"
            :label="`${user.displayName}（${user.username}）`"
            :value="user.id"
          />
        </el-select>
        <el-select v-model="addMemberForm.role" class="member-add-role">
          <el-option label="团队管理员" value="TEAM_ADMIN" />
          <el-option label="普通成员" value="MEMBER" />
        </el-select>
        <el-button type="primary" :disabled="!addMemberForm.userId" @click="handleAddMember">
          添加
        </el-button>
      </div>

      <!-- 成员加载状态 -->
      <div v-if="memberLoading" class="loading-container">
        <el-skeleton :rows="4" animated />
      </div>

      <!-- 成员空状态 -->
      <el-empty v-else-if="members.length === 0" description="暂无成员" :image-size="80" />

      <!-- 成员列表表格 -->
      <el-table v-else :data="members" style="width: 100%">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="displayName" label="显示名" width="120" />
        <el-table-column label="角色" width="160">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              @change="(val) => handleChangeRole(row, val)"
            >
              <el-option label="团队管理员" value="TEAM_ADMIN" />
              <el-option label="普通成员" value="MEMBER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="handleRemoveMember(row)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
/**
 * 团队管理页面
 * - 卡片网格展示团队列表
 * - 支持创建/编辑/删除团队
 * - 抽屉管理团队成员（添加、修改角色、移除）
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, User } from '@element-plus/icons-vue'
import {
  getTeams,
  createTeam,
  updateTeam,
  deleteTeam,
  getTeamMembers,
  addTeamMember,
  removeTeamMember,
  updateMemberRole
} from '@/api/teams'
import { getUsers } from '@/api/users'
import TeamTag from '@/components/TeamTag.vue'

// ========== 团队列表 ==========
const loading = ref(false)
const teams = ref([])

async function loadTeams() {
  loading.value = true
  try {
    teams.value = await getTeams()
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTeams()
})

// ========== 创建/编辑团队对话框 ==========
const teamDialogVisible = ref(false)
const teamFormRef = ref(null)
const teamSubmitting = ref(false)
const isEditMode = ref(false)
const editingTeamId = ref(null)

const teamForm = reactive({
  name: '',
  identifier: '',
  color: '#6366F1'
})

const teamRules = {
  name: [{ required: true, message: '请输入团队名称', trigger: 'blur' }],
  identifier: [
    { required: true, message: '请输入团队标识', trigger: 'blur' },
    { pattern: /^[A-Z0-9]{2,8}$/, message: '2~8 位大写字母或数字', trigger: 'blur' }
  ],
  color: [{ required: true, message: '请选择团队颜色', trigger: 'change' }]
}

function openCreateDialog() {
  isEditMode.value = false
  editingTeamId.value = null
  teamForm.name = ''
  teamForm.identifier = ''
  teamForm.color = '#6366F1'
  teamDialogVisible.value = true
}

function openEditDialog(team) {
  isEditMode.value = true
  editingTeamId.value = team.id
  teamForm.name = team.name
  teamForm.identifier = team.identifier
  teamForm.color = team.color
  teamDialogVisible.value = true
}

async function handleSubmitTeam() {
  const formEl = teamFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  teamSubmitting.value = true
  try {
    if (isEditMode.value) {
      await updateTeam(editingTeamId.value, { ...teamForm })
      ElMessage.success('团队已更新')
    } else {
      await createTeam({ ...teamForm })
      ElMessage.success('团队已创建')
    }
    teamDialogVisible.value = false
    // 刷新列表
    await loadTeams()
  } catch (err) {
    // 拦截器已处理 40301/40302 等错误提示
  } finally {
    teamSubmitting.value = false
  }
}

// ========== 删除团队 ==========
async function handleDeleteTeam(team) {
  try {
    await ElMessageBox.confirm(
      `确定删除团队「${team.name}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await deleteTeam(team.id)
    ElMessage.success('团队已删除')
    await loadTeams()
  } catch (err) {
    // 后端返回 40303 时拦截器会显示"团队下有接口，不能删除"
  }
}

// ========== 成员管理抽屉 ==========
const memberDrawerVisible = ref(false)
const memberLoading = ref(false)
const currentTeam = ref(null)
const members = ref([])
const allUsers = ref([])

// 添加成员表单
const addMemberForm = reactive({
  userId: '',
  role: 'MEMBER'
})

// 计算可添加的用户列表：排除已在当前团队中的用户
const availableUsers = computed(() => {
  const memberUserIds = new Set(members.value.map(m => m.userId))
  return allUsers.value.filter(u => !memberUserIds.has(u.id))
})

async function openMemberDrawer(team) {
  currentTeam.value = team
  memberDrawerVisible.value = true
  addMemberForm.userId = ''
  addMemberForm.role = 'MEMBER'

  // 并行加载成员列表和全部用户
  memberLoading.value = true
  try {
    const [membersData, usersData] = await Promise.all([
      getTeamMembers(team.id),
      getUsers()
    ])
    members.value = membersData
    allUsers.value = usersData
  } catch (err) {
    // 拦截器已处理
  } finally {
    memberLoading.value = false
  }
}

// 添加成员
async function handleAddMember() {
  if (!addMemberForm.userId) return

  try {
    await addTeamMember(currentTeam.value.id, {
      userId: addMemberForm.userId,
      role: addMemberForm.role
    })
    ElMessage.success('成员已添加')
    addMemberForm.userId = ''
    addMemberForm.role = 'MEMBER'
    // 刷新成员列表和团队列表（成员数变化）
    const [membersData] = await Promise.all([
      getTeamMembers(currentTeam.value.id),
      loadTeams()
    ])
    members.value = membersData
  } catch (err) {
    // 拦截器已处理
  }
}

// 修改成员角色
async function handleChangeRole(member, newRole) {
  try {
    await updateMemberRole(currentTeam.value.id, member.userId, { role: newRole })
    // 直接更新本地数据
    member.role = newRole
    ElMessage.success('角色已更新')
  } catch (err) {
    // 拦截器已处理
  }
}

// 移除成员
async function handleRemoveMember(member) {
  try {
    await ElMessageBox.confirm(
      `确定移除成员「${member.displayName}」吗？`,
      '移除确认',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await removeTeamMember(currentTeam.value.id, member.userId)
    ElMessage.success('成员已移除')
    // 刷新成员列表和团队列表
    const [membersData] = await Promise.all([
      getTeamMembers(currentTeam.value.id),
      loadTeams()
    ])
    members.value = membersData
  } catch (err) {
    // 拦截器已处理
  }
}
</script>

<style lang="scss" scoped>
// ========== 页面头部 ==========
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;

  .page-title {
    margin-bottom: 0;
  }
}

// ========== 团队卡片网格 ==========
.team-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.team-card {
  display: flex;
  flex-direction: column;
  cursor: default;

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 20px;
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: #1B2559;
  }

  &__stats {
    display: flex;
    gap: 32px;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #F1F5F9;
  }
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1B2559;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #A3AED0;
}

.team-card__actions {
  display: flex;
  gap: 4px;
}

// ========== 颜色选择器行 ==========
.color-picker-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-preview-text {
  font-size: 14px;
  font-weight: 500;
}

// ========== 成员添加区域 ==========
.member-add-section {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.member-add-select {
  flex: 1;
}

.member-add-role {
  width: 140px;
}

// ========== 加载容器 ==========
.loading-container {
  padding: 40px 0;
}
</style>
