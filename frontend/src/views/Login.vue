<template>
  <main class="login-page">
    <section class="login-intro">
      <router-link to="/login" class="login-brand"><span>M</span><b>MockHub</b></router-link>
      <div class="login-intro__copy"><span class="login-eyebrow">团队协作 · 接口模拟</span><h1>接口与规则，<br />都放在团队工作区里。</h1><p>维护 REST 与 SOAP 接口，用匹配规则、动态变量和文件响应复现真实业务场景。</p></div>
      <LoginMotion class="login-intro__art" />
      <small class="login-intro__foot">适用于内网与离线部署 · 无需外部账号</small>
    </section>
    <section class="login-main"><div class="login-card">
      <h2>欢迎回来</h2><p class="login-subtitle">登录你的 MockHub 账号</p>
      <el-form ref="loginRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" autocomplete="username" placeholder="输入用户名" size="large" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" autocomplete="current-password" placeholder="输入密码" size="large" show-password @keyup.enter="handleLogin" /></el-form-item>
        <p v-if="error" class="login-error" role="alert">{{ error }}</p>
        <el-button native-type="submit" type="primary" size="large" :loading="loading" class="login-submit">登录</el-button>
      </el-form>
      <p class="login-help">没有账号或忘记密码？请联系系统管理员。</p>
    </div><small class="login-main__foot">账号身份与团队角色独立，登录后可切换已授权团队。</small></section>
    <el-dialog v-model="initialPasswordOpen" title="修改初始密码" width="420px" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p class="password-note">首次登录或管理员重置密码后，请设置新密码。保存后会撤销旧凭证，并使用新密码重新登录。</p>
      <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-position="top" @submit.prevent="changeInitialPassword">
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" show-password /></el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" show-password @keyup.enter="changeInitialPassword" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="cancelInitialPassword">退出登录</el-button><el-button type="primary" :loading="passwordLoading" @click="changeInitialPassword">保存并继续</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import LoginMotion from '@/components/LoginMotion.vue'
const router = useRouter(), user = useUserStore(), app = useAppStore()
const form = reactive({ username: '', password: '' }), loginRef = ref(), loading = ref(false), error = ref('')
const rules = { username: [{ required: true, message: '请输入用户名', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }] }
const initialPasswordOpen = ref(false), passwordLoading = ref(false), passwordRef = ref(), passwordForm = reactive({ newPassword: '', confirmPassword: '' })
const passwordRules = { newPassword: [{ required: true, min: 6, message: '密码至少 6 个字符', trigger: 'blur' }], confirmPassword: [{ validator: (_, value, callback) => callback(value && value === passwordForm.newPassword ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }] }
async function handleLogin() {
  if (loading.value || !await loginRef.value.validate().catch(() => false)) return
  loading.value = true; error.value = ''
  try {
    const result = await login({ ...form }); app.reset(); user.setLogin(result.token, result.user)
    if (result.user.firstLogin) { passwordForm.newPassword = ''; passwordForm.confirmPassword = ''; initialPasswordOpen.value = true; return }
    form.password = ''; await router.replace('/apis')
  } catch (e) { error.value = e.msg || '登录失败，请检查账号、密码或服务连接' }
  finally { loading.value = false }
}
async function changeInitialPassword() {
  if (passwordLoading.value || !await passwordRef.value.validate().catch(() => false)) return
  passwordLoading.value = true
  try {
    await changePassword({ oldPassword: form.password, newPassword: passwordForm.newPassword })
    user.clearSession(); app.reset(); initialPasswordOpen.value = false
    const result = await login({ username: form.username, password: passwordForm.newPassword })
    user.setLogin(result.token, result.user); form.password = ''; passwordForm.newPassword = ''; passwordForm.confirmPassword = ''
    ElMessage.success('密码已更新，旧登录与个人凭证已撤销'); await router.replace('/apis')
  } catch (e) { error.value = e.msg || '操作未完成；如果密码已保存，请使用新密码登录' }
  finally { passwordLoading.value = false }
}
async function cancelInitialPassword() { try { await user.logout() } catch {} finally { app.reset(); initialPasswordOpen.value = false; form.password = '' } }
</script>
<style lang="scss" scoped>
.login-page { display: grid; grid-template-columns: minmax(360px, 1fr) minmax(400px, .9fr); min-height: 100vh; min-height: 100dvh; background: var(--app-bg-page); padding: 16px; gap: 16px; }
.login-intro { border-radius: 20px; background: var(--app-bg-surface); padding: 30px 40px; display: flex; flex-direction: column; }
.login-brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: inherit; font-size: 20px; } .login-brand span { background: var(--app-primary); color: #fff; width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; font-weight: 700; }
.login-intro__copy { margin-top: clamp(28px, 6vh, 64px); max-width: 460px; } .login-eyebrow { color: var(--app-text-accent); font-size: var(--app-font-caption); font-weight: 600; letter-spacing: .1em; }
.login-intro h1 { font-size: clamp(24px, 2.4vw, 32px); line-height: 1.34; letter-spacing: -.01em; font-weight: 600; margin: 12px 0 10px; }
.login-intro p { font-size: 13px; line-height: 1.85; max-width: 400px; color: var(--app-text-secondary); margin: 0; }
.login-intro__art { flex: 1; min-height: 0; display: flex; align-items: center; margin: clamp(18px, 4vh, 40px) 0; }
.login-intro__foot { font-size: var(--app-font-caption); line-height: 1.6; color: var(--app-text-secondary); }
.login-main { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; gap: 24px; min-width: 0; } .login-card { width: 100%; max-width: 400px; background: var(--app-bg-surface); border-radius: 20px; padding: 32px; }
.login-card h2 { font-size: 24px; font-weight: 600; margin: 0 0 10px; } .login-subtitle { font-size: var(--app-font-caption); color: var(--app-text-secondary); margin: 0 0 28px; } .login-submit { width: 100%; margin-top: 8px; }
.login-help,.login-main__foot { font-size: var(--app-font-caption); color: var(--app-text-secondary); line-height: 1.7; text-align: center; } .login-help { margin: 20px 0 0; } .login-error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); padding: 10px; border-radius: 8px; font-size: 13px; line-height: 1.6; margin: 0 0 14px; } .password-note { font-size: 13px; color: var(--app-text-secondary); line-height: 1.7; margin-bottom: 20px; }
@media(max-width: 900px) { .login-page { grid-template-columns: 1fr; } .login-intro { padding: 22px 26px 18px; } .login-intro__copy { margin-top: 18px; max-width: none; } .login-intro h1 { font-size: 22px; } .login-intro h1 br { display: none; } .login-intro__art { margin: 10px 0 0; } .login-intro__foot { display: none; } .login-main { padding: 8px 0 24px; } }
@media(max-width: 480px) { .login-page { padding: 10px; gap: 10px; } .login-intro { padding: 18px; } .login-intro h1 { font-size: 20px; margin: 10px 0 8px; } .login-intro p { font-size: 13px; } .login-intro__art { margin-top: 4px; } .login-card { padding: 24px; } }
</style>
