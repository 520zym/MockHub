<template>
  <main class="login-page">
    <section class="login-intro">
      <router-link to="/login" class="login-brand"><span>M</span><b>MockHub</b></router-link>
      <div class="login-intro__copy"><span class="login-eyebrow">团队协作 · 接口模拟</span><h1>让联调，<br />不再等待。</h1><p>在同一个团队工作区里维护 REST 和 SOAP 接口，使用匹配规则、动态变量与文件响应，复现真实业务场景。</p><div class="login-example" aria-label="Mock 工作流程示意"><div><FilledIcon name="team" /><b>团队资源</b><span>接口与规则共同维护</span></div><i>↓</i><div><FilledIcon name="api" /><b>Mock 响应</b><span>条件匹配 · JSON · XML · 文件</span></div></div></div>
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
import FilledIcon from '@/components/FilledIcon.vue'
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
.login-intro__copy { margin: auto 0; padding: 56px 0; max-width: 460px; } .login-eyebrow { color: var(--app-accent); font-size: 11px; font-weight: 600; letter-spacing: .12em; }
.login-intro h1 { font-size: clamp(36px, 4.5vw, 62px); line-height: 1.16; letter-spacing: -.04em; font-weight: 600; margin: 20px 0; } .login-intro p { font-size: 13px; line-height: 1.9; max-width: 370px; color: var(--app-text-secondary); }
.login-example { display: flex; flex-direction: column; margin-top: 28px; gap: 8px; max-width: 355px; } .login-example div { padding: 15px 18px; display: grid; align-items: center; grid-template-columns: 22px 1fr; gap: 7px 10px; background: var(--app-bg-control); border-radius: 12px; } .login-example svg { grid-row: span 2; color: var(--app-accent); } .login-example b { font-size: 12px; font-weight: 600; } .login-example span { font-size: 10px; color: var(--app-text-secondary); } .login-example i { font-size: 14px; font-style: normal; color: var(--app-text-secondary); text-align: center; }
.login-intro__foot { font-size: 10px; color: var(--app-text-secondary); }
.login-main { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; gap: 24px; min-width: 0; } .login-card { width: 100%; max-width: 400px; background: var(--app-bg-surface); border-radius: 20px; padding: 32px; }
.login-card h2 { font-size: 24px; font-weight: 600; margin: 0 0 10px; } .login-subtitle { font-size: 12px; color: var(--app-text-secondary); margin: 0 0 28px; } .login-submit { width: 100%; margin-top: 8px; }
.login-help,.login-main__foot { font-size: 10px; color: var(--app-text-secondary); line-height: 1.7; text-align: center; } .login-help { margin: 20px 0 0; } .login-error { color: var(--el-color-danger); background: var(--el-color-danger-light-9); padding: 10px; border-radius: 8px; font-size: 11px; line-height: 1.6; margin: 0 0 14px; } .password-note { font-size: 12px; color: var(--app-text-secondary); line-height: 1.7; margin-bottom: 20px; }
@media(max-width: 900px) { .login-page { grid-template-columns: 1fr; } .login-intro { padding: 22px 26px; } .login-intro__copy { padding: 20px 0 0; max-width: none; } .login-intro h1 { font-size: 32px; } .login-intro h1 br { display: none; } .login-example,.login-intro__foot { display: none; } .login-main { padding: 8px 0 24px; } }
@media(max-width: 480px) { .login-page { padding: 10px; gap: 10px; } .login-intro { padding: 18px; } .login-intro h1 { font-size: 28px; margin: 12px 0; } .login-intro p { margin: 0; font-size: 11px; } .login-card { padding: 24px; } }
</style>
