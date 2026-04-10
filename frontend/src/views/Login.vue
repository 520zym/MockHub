<!--
  Login.vue — 登录页面
  功能：用户名密码登录、首次登录强制修改密码
  视觉：Soft UI 风格，全屏渐变背景 + 居中白色登录卡片
-->
<template>
  <div class="login-page">
    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- Logo / 标题 -->
      <div class="login-header">
        <div class="login-logo">
          <svg class="logo-icon" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="40" height="40" rx="10" fill="#6366F1" />
            <path d="M12 20L18 26L28 14" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <h1 class="login-title">MockHub</h1>
        <p class="login-subtitle">接口模拟服务平台</p>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="UserIcon"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="LockIcon"
            size="large"
            show-password
          />
        </el-form-item>

        <!-- 登录错误提示 -->
        <div v-if="loginError" class="login-error">
          {{ loginError }}
        </div>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loginLoading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 首次登录修改密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改初始密码"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="password-dialog"
    >
      <p class="password-dialog-tip">
        首次登录，请修改初始密码后继续使用。
      </p>
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        @keyup.enter="handleChangePassword"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          :loading="passwordLoading"
          @click="handleChangePassword"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { User as UserIcon, Lock as LockIcon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// ========== 登录表单 ==========
const loginFormRef = ref(null)
const loginLoading = ref(false)
const loginError = ref('')

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 暂存登录时的密码，用于首次修改密码时作为 oldPassword
const loginPassword = ref('')

// ========== 修改密码对话框 ==========
const passwordDialogVisible = ref(false)
const passwordFormRef = ref(null)
const passwordLoading = ref(false)

const passwordForm = reactive({
  newPassword: '',
  confirmPassword: ''
})

// 确认密码一致性校验
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// ========== 登录逻辑 ==========
async function handleLogin() {
  const formEl = loginFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  loginLoading.value = true
  loginError.value = ''

  try {
    // 调用登录接口，拦截器已解包，返回的是 { token, user }
    const result = await login({
      username: loginForm.username,
      password: loginForm.password
    })

    // 存储 token 和用户信息到 store（同时写入 localStorage）
    userStore.setLogin(result.token, result.user)

    // 暂存密码用于首次修改密码
    loginPassword.value = loginForm.password

    // 加载团队列表
    await appStore.loadTeams()

    // 检查是否首次登录
    if (result.user.firstLogin) {
      // 弹出修改密码对话框，不跳转
      passwordDialogVisible.value = true
    } else {
      router.push('/')
    }
  } catch (err) {
    // Axios 拦截器已通过 ElMessage 展示错误，这里额外在表单下方显示
    loginError.value = err?.msg || '登录失败，请重试'
  } finally {
    loginLoading.value = false
  }
}

// ========== 修改密码逻辑 ==========
async function handleChangePassword() {
  const formEl = passwordFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  passwordLoading.value = true

  try {
    await changePassword({
      oldPassword: loginPassword.value,
      newPassword: passwordForm.newPassword
    })

    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
    router.push('/')
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    passwordLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  // 全屏渐变背景（与全局背景一致）
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf4 50%, #e0e7f1 100%);
}

.login-card {
  width: 400px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
  padding: 40px 36px 32px;
}

// ========== 头部 Logo + 标题 ==========
.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.logo-icon {
  width: 48px;
  height: 48px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #1B2559;
  margin: 0 0 4px;
  letter-spacing: 1px;
}

.login-subtitle {
  font-size: 14px;
  color: #A3AED0;
  margin: 0;
}

// ========== 表单样式 ==========
.login-card :deep(.el-input__wrapper) {
  background-color: #F7F8FA;
  border: 2px solid transparent;
  border-radius: 10px;
  box-shadow: none;
  transition: all 0.2s ease;

  &:hover {
    background-color: #F0F1F5;
  }

  &.is-focus {
    background-color: #ffffff;
    border-color: #6366F1;
    box-shadow: none;
  }
}

.login-card :deep(.el-input--large .el-input__wrapper) {
  padding: 8px 16px;
}

.login-error {
  color: #EF4444;
  font-size: 13px;
  margin: -8px 0 12px;
  padding: 0 4px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  background: #6366F1;
  letter-spacing: 4px;
  transition: all 0.2s ease;

  &:hover,
  &:focus {
    background: #4F46E5;
    transform: translateY(-1px);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
  }

  &:active {
    transform: translateY(0);
  }
}

// ========== 修改密码对话框 ==========
.password-dialog-tip {
  color: #A3AED0;
  font-size: 14px;
  margin: 0 0 20px;
}
</style>

<style lang="scss">
// 对话框全局样式覆盖（不能 scoped，否则无法命中 Dialog teleport 到 body 的节点）
.password-dialog {
  .el-dialog {
    border-radius: 16px;
    box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
  }

  .el-dialog__header {
    padding: 20px 24px 0;
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 600;
    color: #1B2559;
  }

  .el-dialog__body {
    padding: 16px 24px;
  }

  .el-dialog__footer {
    padding: 0 24px 20px;
  }
}
</style>
