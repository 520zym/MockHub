<template>
  <div class="topbar">
    <div class="topbar__left">
      <h1 class="topbar__title">{{ pageTitle }}</h1>
    </div>
    <div class="topbar__right">
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="topbar__user">
          <el-avatar :size="32" class="topbar__avatar">
            {{ userInitial }}
          </el-avatar>
          <span class="topbar__username">{{ userStore.user?.displayName || userStore.user?.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="changePassword">
              <el-icon><Lock /></el-icon>
              修改密码
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="400px" :close-on-click-modal="false">
      <el-form :model="pwdForm" label-width="80px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="不少于6位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitle = computed(() => {
  return route.meta.title || 'MockHub'
})

const userInitial = computed(() => {
  const name = userStore.user?.displayName || userStore.user?.username || ''
  return name.charAt(0).toUpperCase()
})

/** 下拉菜单命令分发 */
function handleCommand(command) {
  if (command === 'changePassword') {
    pwdDialogVisible.value = true
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } else if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

// --- 修改密码 ---
const pwdDialogVisible = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

/** 提交修改密码 */
async function handleChangePassword() {
  if (!pwdForm.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于6位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功')
    pwdDialogVisible.value = false
  } catch (e) {
    // API 层已有错误提示
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.topbar {
  height: 60px;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.03);
  z-index: 50;

  &__left {
    display: flex;
    align-items: center;
  }

  &__title {
    font-size: 20px;
    font-weight: 700;
    color: #1B2559;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 8px;
    transition: background 0.2s;

    &:hover {
      background: #F7F8FA;
    }
  }

  &__avatar {
    background: linear-gradient(135deg, #6366F1, #4F46E5);
    color: #FFFFFF;
    font-weight: 600;
  }

  &__username {
    font-size: 14px;
    font-weight: 500;
    color: #1B2559;
  }
}
</style>
