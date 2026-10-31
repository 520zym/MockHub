<template>
  <div class="account-page">
    <header class="account-page__header">
      <div>
        <h1>个人账号</h1>
      </div>
      <el-button :loading="identityLoading" :disabled="busy" @click="reloadIdentity">刷新资料</el-button>
    </header>

    <section v-if="identityLoading && !identity" class="account-card account-state" aria-busy="true" aria-live="polite">
      <el-skeleton :rows="5" />
      <p>正在读取账号资料…</p>
    </section>
    <section v-else-if="identityError" class="account-card account-state" role="alert">
      <FilledIcon name="user" :size="32" />
      <h2>账号资料加载失败</h2>
      <p>{{ identityError }}</p>
      <el-button :loading="identityLoading" @click="loadIdentity">重新加载</el-button>
    </section>

    <template v-else-if="identity">
      <div v-if="identity.firstLogin" class="account-notice" role="status">
        <FilledIcon name="shield" :size="18" />
        <span>这是首次登录，请先修改初始密码。</span>
        <el-button type="primary" link @click="activeTab = 'security'">前往修改</el-button>
      </div>

      <el-tabs v-model="activeTab" class="account-tabs">
        <el-tab-pane label="基本资料" name="profile" lazy>
          <div class="account-two-column">
            <section class="account-card" aria-labelledby="account-profile-title">
              <div class="account-card__header">
                <div><h2 id="account-profile-title">基本资料</h2></div>
                <span class="account-badge">{{ globalRoleLabel }}</span>
              </div>
              <div class="account-profile-hero">
                <span class="account-avatar" aria-hidden="true">{{ avatarText }}</span>
                <div class="account-profile-copy"><h3>{{ identity.displayName || identity.username }}</h3><span class="account-mono">{{ identity.username }}</span></div>
                <span class="account-status" :class="{ 'account-status--inactive': identity.status !== 'ACTIVE' }"><span aria-hidden="true"></span>{{ accountStatus }}</span>
              </div>
              <dl class="account-readonly-grid">
                <div><dt>登录名</dt><dd class="account-mono">{{ identity.username }}</dd></div>
                <div><dt>系统身份</dt><dd>{{ globalRoleLabel }}<span class="account-code">{{ identity.globalRole }}</span></dd></div>
              </dl>
              <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-position="top" class="account-form" @submit.prevent="saveProfile">
                <el-form-item label="显示名" prop="displayName">
                  <el-input v-model="profileForm.displayName" name="displayName" autocomplete="nickname" maxlength="64" show-word-limit :disabled="busy" placeholder="填写显示名" />
                </el-form-item>
                <p class="account-note">显示名为 1–64 个字符，保存时会去除首尾空格。</p>
                <p v-if="profileError" class="account-error" role="alert">{{ profileError }}</p>
                <div class="account-form-actions">
                  <el-button native-type="submit" type="primary" :loading="profileSaving" :disabled="!profileDirty || !!pendingAction">保存资料</el-button>
                  <el-button :disabled="!profileDirty || busy" @click="resetProfile">放弃修改</el-button>
                </div>
              </el-form>
            </section>

            <aside class="account-stack">
              <section class="account-card" aria-labelledby="account-security-summary">
                <div class="account-card__header"><div><h2 id="account-security-summary">安全摘要</h2></div><FilledIcon name="shield" :size="22" /></div>
                <div class="account-summary-row"><div><b>登录密码</b><small v-if="identity.firstLogin">初始密码待修改</small></div><el-button link type="primary" @click="activeTab = 'security'">修改密码</el-button></div>
                <div class="account-summary-row"><div><b>网页登录会话</b></div><el-button link type="primary" @click="activeTab = 'sessions'">查看会话</el-button></div>
                <div class="account-hint">修改密码会使所有网页会话及本人的 MCP 凭证失效；单独撤销网页会话不会撤销 MCP 凭证。</div>
              </section>
              <section class="account-card" aria-labelledby="account-team-summary">
                <div class="account-card__header"><div><h2 id="account-team-summary">团队身份</h2></div><FilledIcon name="team" :size="22" /></div>
                <p class="account-team-count"><strong>{{ memberTeams.length }}</strong> 个所属团队<span v-if="maintenanceTeams.length"> · {{ maintenanceTeams.length }} 个系统维护访问</span></p>
                <el-button type="primary" link @click="activeTab = 'teams'">查看我的团队与访问身份</el-button>
              </section>
            </aside>
          </div>
        </el-tab-pane>

        <el-tab-pane label="账号安全" name="security" lazy>
          <div class="account-two-column">
            <section class="account-card" aria-labelledby="account-password-title">
              <div class="account-card__header"><div><h2 id="account-password-title">修改登录密码</h2></div><FilledIcon name="key" :size="22" /></div>
              <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="account-password-form" @submit.prevent="changePassword">
                <el-form-item label="当前密码" prop="oldPassword"><el-input v-model="passwordForm.oldPassword" name="currentPassword" type="password" autocomplete="current-password" show-password :disabled="busy" /></el-form-item>
                <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" name="newPassword" type="password" autocomplete="new-password" show-password :disabled="busy" placeholder="至少 6 个字符" /></el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="passwordForm.confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" show-password :disabled="busy" placeholder="再次输入新密码" /></el-form-item>
                <p v-if="passwordError" class="account-error" role="alert">{{ passwordError }}</p>
                <div class="account-form-actions"><el-button native-type="submit" type="primary" :loading="pendingAction === 'password'" :disabled="busy && pendingAction !== 'password'">更新密码并重新登录</el-button><el-button :disabled="busy" @click="resetPasswords">清空</el-button></div>
              </el-form>
            </section>
            <aside class="account-stack">
              <section class="account-card"><div class="account-card__header"><div><h2>修改后会发生什么</h2></div></div><ul class="account-guidance"><li>当前浏览器和其它设备上的所有网页会话都会失效，需要重新登录。</li><li>本人已创建的 MCP 访问凭证也将失效，相关客户端需要重新配置凭证。</li><li>不会更改团队成员关系、团队角色或团队资源。</li></ul><div class="account-hint">应用不会把密码写入浏览器本地存储。离开安全标签页时会清空表单。</div></section>
              <section class="account-card"><div class="account-card__header"><div><h2>退出当前登录</h2><p>仅撤销当前网页会话，不影响其它设备或 MCP 凭证。</p></div></div><p v-if="logoutError" class="account-error" role="alert">{{ logoutError }}</p><el-button :loading="pendingAction === 'logout'" :disabled="busy && pendingAction !== 'logout'" @click="logout">退出当前登录</el-button></section>
            </aside>
          </div>
        </el-tab-pane>

        <el-tab-pane label="登录会话" name="sessions" lazy>
          <section class="account-card" aria-labelledby="account-sessions-title" :aria-busy="sessionsLoading">
            <div class="account-card__header account-card__header--wrap"><div><h2 id="account-sessions-title">网页登录会话</h2><p>仅展示本人的有效网页会话，不包含 MCP 凭证。</p></div><div class="account-actions"><span v-if="sessionsLoaded && !sessionsError" class="account-badge">{{ sessions.length }} 个会话</span><el-button :loading="sessionsLoading" :disabled="!!pendingAction" @click="loadSessions">刷新会话</el-button></div></div>
            <div v-if="sessionsLoading && !sessionsLoaded" class="account-state" aria-live="polite"><el-skeleton :rows="4" /><p>正在读取登录会话…</p></div>
            <div v-else-if="sessionsError" class="account-state" role="alert"><h3>会话加载失败</h3><p>{{ sessionsError }}</p><el-button :loading="sessionsLoading" @click="loadSessions">重新加载</el-button></div>
            <div v-else-if="!sessions.length" class="account-state"><FilledIcon name="history" :size="32" /><h3>暂无有效网页会话</h3><p>刷新以确认最新状态；如果当前登录已失效，请重新登录。</p></div>
            <ul v-else class="account-session-list">
              <li v-for="session in sortedSessions" :key="session.id" class="account-session">
                <span class="account-session__icon" aria-hidden="true"><FilledIcon :name="session.current ? 'workspace' : 'history'" :size="22" /></span>
                <div class="account-session__copy"><div class="account-session__title"><h3>{{ deviceLabel(session.userAgent) }}</h3><span v-if="session.current" class="account-badge account-badge--blue">当前会话</span></div><p>IP <span class="account-mono">{{ session.ipAddress || '未提供' }}</span></p><dl class="account-session__times"><div><dt>登录时间</dt><dd>{{ formatTime(session.createdAt) }}</dd></div><div><dt>到期时间</dt><dd>{{ formatTime(session.expiresAt) }}</dd></div></dl><details class="account-user-agent"><summary>浏览器信息</summary><p>{{ session.userAgent || '服务端未提供浏览器信息。' }}</p></details></div>
                <el-button :loading="pendingAction === 'session:' + session.id" :disabled="busy || sessionsLoading" @click="revokeSession(session)">{{ session.current ? '退出此会话' : '撤销会话' }}</el-button>
              </li>
            </ul>
            <p v-if="sessionActionError" class="account-error" role="alert">{{ sessionActionError }}</p>
            <footer class="account-sessions-footer"><div><p>时间使用浏览器当前时区：{{ timeZone }}。</p><p>撤销全部网页会话会同时退出当前浏览器，但不会撤销 MCP 凭证。</p></div><el-button type="danger" plain :loading="pendingAction === 'all-sessions'" :disabled="busy || sessionsLoading || !!sessionsError || !sessionsLoaded || !sessions.length" @click="revokeAllSessions">撤销全部网页会话</el-button></footer>
          </section>
        </el-tab-pane>

        <el-tab-pane label="团队身份" name="teams" lazy>
          <section class="account-card" aria-labelledby="account-teams-title">
            <div class="account-card__header"><div><h2 id="account-teams-title">我的团队与访问身份</h2><p>团队角色由管理员分配，此处只读。</p></div><span class="account-badge">{{ teams.length }} 个可访问团队</span></div>
            <div v-if="!teams.length" class="account-state"><FilledIcon name="team" :size="32" /><h3>尚无可访问的团队</h3><p>请联系团队管理员添加成员关系。</p></div>
            <ul v-else class="account-team-list"><li v-for="team in teams" :key="team.teamId" class="account-team"><span class="account-team__mark account-mono" aria-hidden="true">{{ team.identifier || 'TEAM' }}</span><div class="account-team__copy"><h3>{{ team.teamName || '未命名团队' }}</h3><span v-if="team.identifier" class="account-code account-mono">{{ team.identifier }}</span></div><div class="account-team__actions"><span class="account-badge" :class="{ 'account-badge--blue': isMaintenance(team) || membershipRole(team) === 'TEAM_ADMIN' }">{{ teamRoleLabel(team) }}</span><el-button type="primary" link :disabled="busy || team.capabilities?.read === false" @click="openTeam(team)">进入工作区</el-button></div></li></ul>
          </section>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import FilledIcon from '@/components/FilledIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const tabs = ['profile', 'security', 'sessions', 'teams']
const activeTab = computed({
  get: () => tabs.includes(route.query.tab) ? route.query.tab : 'profile',
  set: value => {
    if (tabs.includes(value) && value !== activeTab.value) {
      router.replace({ path: '/account', query: { ...route.query, tab: value } })
    }
  }
})
const identity = ref(null)
const identityLoading = ref(false)
const identityError = ref('')
const profileForm = reactive({ displayName: '' })
const profileFormRef = ref()
const profileSaving = ref(false)
const profileError = ref('')
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordFormRef = ref()
const passwordError = ref('')
const logoutError = ref('')
const pendingAction = ref('')
const sessions = ref([])
const sessionsLoading = ref(false)
const sessionsLoaded = ref(false)
const sessionsError = ref('')
const sessionActionError = ref('')
const busy = computed(() => identityLoading.value || profileSaving.value || !!pendingAction.value)
const profileDirty = computed(() => !!identity.value && profileForm.displayName.trim() !== (identity.value.displayName || ''))
const teams = computed(() => Array.isArray(identity.value?.teams) ? identity.value.teams : [])
const memberTeams = computed(() => teams.value.filter(team => !isMaintenance(team)))
const maintenanceTeams = computed(() => teams.value.filter(isMaintenance))
const globalRoleLabel = computed(() => ({ SUPER_ADMIN: '超级管理员', USER: '普通账号' })[identity.value?.globalRole] || '未知系统身份')
const avatarText = computed(() => Array.from(identity.value?.displayName || identity.value?.username || '?')[0])
const accountStatus = computed(() => ({ ACTIVE: '账号正常', DISABLED: '账号已停用' })[identity.value?.status] || '状态未知')
const sortedSessions = computed(() => [...sessions.value].sort((a, b) => Number(b.current) - Number(a.current) || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()))
const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || '本地时区'
let identityVersion = 0
let sessionsVersion = 0
let disposed = false

const profileRules = {
  displayName: [{ validator: (_rule, value, callback) => {
    const name = typeof value === 'string' ? value.trim() : ''
    callback(name.length >= 1 && name.length <= 64 ? undefined : new Error('显示名须为 1–64 个字符。'))
  }, trigger: 'blur' }]
}
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码。', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码。', trigger: 'blur' }, { min: 6, message: '新密码至少为 6 个字符。', trigger: 'blur' }],
  confirmPassword: [{ validator: (_rule, value, callback) => callback(!value ? new Error('请再次输入新密码。') : value !== passwordForm.newPassword ? new Error('两次输入的新密码不一致。') : undefined), trigger: 'blur' }]
}

function errorText(error, fallback) {
  return error?.response?.data?.msg || error?.msg || (error?.isTimeout ? '请求超时，请稍后重试。' : error?.isNetworkError ? '网络连接失败，请检查连接后重试。' : error?.message) || fallback
}

async function valid(form) {
  if (!form) return false
  try { return await form.validate() } catch { return false }
}

async function confirm(message, title, confirmButtonText = '确认') {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning', confirmButtonText, cancelButtonText: '取消', closeOnClickModal: false, distinguishCancelAndClose: true })
    return true
  } catch { return false }
}

async function loadIdentity() {
  if (identityLoading.value || busy.value) return
  const version = ++identityVersion
  identityLoading.value = true
  identityError.value = ''
  try {
    const data = await request.get('/auth/me', { skipGlobalNetworkError: true })
    if (disposed || version !== identityVersion) return
    if (!data?.id) throw new Error('服务端未返回有效的账号资料。')
    identity.value = data
    userStore.updateUser(data)
    resetProfile()
  } catch (error) {
    if (!disposed && version === identityVersion) identityError.value = errorText(error, '暂时无法读取账号资料。')
  } finally {
    if (!disposed && version === identityVersion) identityLoading.value = false
  }
}

async function reloadIdentity() {
  if (busy.value || identityLoading.value) return
  if (profileDirty.value && !await confirm('刷新资料会放弃尚未保存的显示名修改。', '放弃未保存的修改？', '放弃并刷新')) return
  await loadIdentity()
}

function resetProfile() {
  profileForm.displayName = identity.value?.displayName || ''
  profileError.value = ''
  nextTick(() => profileFormRef.value?.clearValidate())
}

async function saveProfile() {
  if (busy.value || !identity.value || !await valid(profileFormRef.value)) return
  if (busy.value || !profileDirty.value) return
  profileSaving.value = true
  profileError.value = ''
  try {
    const data = await request.patch('/me/profile', { displayName: profileForm.displayName.trim() }, { skipGlobalNetworkError: true })
    if (disposed) return
    if (!data?.id) throw new Error('服务端未返回更新后的账号资料，请刷新确认。')
    identity.value = data
    userStore.updateUser(data)
    resetProfile()
    ElMessage.success('个人资料已保存。')
  } catch (error) {
    if (!disposed) profileError.value = errorText(error, '资料保存失败，请重试。')
  } finally { profileSaving.value = false }
}

function resetPasswords() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordError.value = ''
  nextTick(() => passwordFormRef.value?.clearValidate())
}

async function returnToLogin(message) {
  resetPasswords()
  userStore.clearSession()
  await router.replace('/login')
  ElMessage.success(message)
}

async function changePassword() {
  if (busy.value || !await valid(passwordFormRef.value)) return
  if (busy.value) return
  const credentials = { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword }
  pendingAction.value = 'password'
  passwordError.value = ''
  try {
    if (!await confirm('更新密码后，当前浏览器和其它设备的所有网页会话、本人已创建的 MCP 凭证都会失效。请先保存工作，随后重新登录。', '更新密码并重新登录？', '更新密码') || disposed || activeTab.value !== 'security') return
    // Passwords are never trimmed, logged or persisted; send exactly the validated input.
    await request.post('/auth/change-password', credentials, { skipGlobalNetworkError: true })
    await returnToLogin('密码已更新，请使用新密码重新登录。')
  } catch (error) {
    if (!disposed) passwordError.value = errorText(error, '密码更新失败，请重试。')
  } finally { pendingAction.value = '' }
}

async function loadSessions() {
  if (!identity.value || sessionsLoading.value || pendingAction.value) return
  const version = ++sessionsVersion
  sessionsLoading.value = true
  sessionsError.value = ''
  sessionActionError.value = ''
  try {
    const data = await request.get('/me/sessions', { skipGlobalNetworkError: true })
    if (disposed || version !== sessionsVersion) return
    if (!Array.isArray(data)) throw new Error('服务端未返回有效的会话列表。')
    sessions.value = data
    sessionsLoaded.value = true
  } catch (error) {
    if (!disposed && version === sessionsVersion) sessionsError.value = errorText(error, '暂时无法读取登录会话。')
  } finally {
    if (!disposed && version === sessionsVersion) sessionsLoading.value = false
  }
}

async function revokeSession(session) {
  if (busy.value || sessionsLoading.value) return
  pendingAction.value = 'session:' + session.id
  sessionActionError.value = ''
  try {
    const message = session.current ? '这会撤销当前浏览器的网页会话并返回登录页，不影响其它设备或 MCP 凭证。' : '该设备将需要重新登录。当前浏览器和本人的 MCP 凭证不受影响。'
    if (!await confirm(message, session.current ? '退出当前会话？' : '撤销此网页会话？', session.current ? '退出并重新登录' : '撤销会话') || disposed) return
    await request.delete('/me/sessions/' + encodeURIComponent(session.id), { skipGlobalNetworkError: true })
    if (session.current) {
      await returnToLogin('当前会话已撤销，请重新登录。')
    } else if (!disposed) {
      sessions.value = sessions.value.filter(item => item.id !== session.id)
      ElMessage.success('该网页会话已撤销。')
    }
  } catch (error) {
    if (!disposed) sessionActionError.value = errorText(error, '会话撤销失败，请刷新后重试。')
  } finally { pendingAction.value = '' }
}

async function revokeAllSessions() {
  if (busy.value || sessionsLoading.value || !sessionsLoaded.value || sessionsError.value || !sessions.value.length) return
  pendingAction.value = 'all-sessions'
  sessionActionError.value = ''
  try {
    if (!await confirm('所有设备上的网页会话（包括当前浏览器）都会失效，需要重新登录。本人的 MCP 凭证不会被撤销。', '撤销全部网页会话？', '全部撤销并重新登录') || disposed) return
    await request.post('/me/revoke-web-sessions', null, { skipGlobalNetworkError: true })
    await returnToLogin('所有网页会话已撤销，请重新登录。')
  } catch (error) {
    if (!disposed) sessionActionError.value = errorText(error, '撤销失败，请重试。')
  } finally { pendingAction.value = '' }
}

async function logout() {
  if (busy.value) return
  pendingAction.value = 'logout'
  logoutError.value = ''
  try {
    if (!await confirm('当前网页会话将被撤销。其它设备和 MCP 凭证不受影响。', '退出当前登录？', '退出登录') || disposed) return
    await userStore.logout()
    resetPasswords()
    await router.replace('/login')
  } catch (error) {
    if (!disposed) logoutError.value = errorText(error, '退出失败，请重试。')
  } finally { pendingAction.value = '' }
}

function isMaintenance(team) { return team.accessMode === 'SYSTEM_MAINTENANCE' }
function membershipRole(team) { return team.membershipRole ?? team.role }
function teamRoleLabel(team) {
  if (isMaintenance(team)) return '系统维护访问'
  return ({ TEAM_ADMIN: '团队管理员', EDITOR: '可编辑成员', MEMBER: '可编辑成员', VIEWER: '只读成员' })[membershipRole(team)] || '未知团队身份'
}
async function openTeam(team) {
  if (busy.value || !team.teamId || team.capabilities?.read === false) return
  await appStore.setFilter(team.teamId)
  await router.push('/apis')
}
function formatTime(value) {
  if (value === null || value === undefined || value === '') return '未提供'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '时间不可用' : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
function deviceLabel(agent = '') {
  const ua = agent || ''
  const browser = /Edg(?:e|A|iOS)?\//.test(ua) ? 'Edge' : /(?:Firefox|FxiOS)\//.test(ua) ? 'Firefox' : /(?:Chrome|CriOS)\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : '未知浏览器'
  const os = /iPhone|iPad|iPod/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android' : /Windows/.test(ua) ? 'Windows' : /Macintosh|Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : '未知设备'
  return browser + ' · ' + os
}

watch(activeTab, (tab, previous) => {
  if (previous === 'security' && tab !== 'security') resetPasswords()
  if (tab === 'sessions' && identity.value) loadSessions()
})
watch(() => passwordForm.newPassword, () => {
  if (passwordForm.confirmPassword) passwordFormRef.value?.validateField('confirmPassword').catch(() => {})
})
watch(identity, (value, previous) => {
  if (value && !previous && activeTab.value === 'sessions') loadSessions()
})
onMounted(loadIdentity)
onBeforeUnmount(() => {
  disposed = true
  identityVersion++
  sessionsVersion++
  resetPasswords()
})
</script>

<style scoped lang="scss">
.account-page { min-width: 0; width: 100%; max-width: 1400px; margin-inline: auto; color: var(--app-text-primary); }
.account-page__header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.account-page__header h1 { font-size: clamp(24px, 2vw, 28px); font-weight: 600; line-height: 1.3; letter-spacing: -.025em; margin: 4px 0 6px; }
.account-card__header p { margin: 4px 0 0; color: var(--app-text-secondary); font-size: 13px; line-height: 1.6; }
.account-tabs { min-width: 0; }
.account-tabs :deep(.el-tabs__header) { margin-bottom: 20px; }
.account-tabs :deep(.el-tabs__nav-wrap::after) { height: 0; }
.account-tabs :deep(.el-tabs__item) { font-size: 14px; }
.account-tabs :deep(.el-tabs__content) { overflow: visible; }
.account-two-column { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(280px, 1fr); align-items: stretch; gap: 14px; min-width: 0; }
.account-stack { display: grid; grid-auto-rows: 1fr; gap: 14px; min-width: 0; }
.account-card { min-width: 0; background: var(--app-bg-surface); border-radius: var(--app-radius-panel); padding: 20px; }
.account-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.account-card__header h2 { margin: 0; font-size: 16px; line-height: 1.5; font-weight: 600; letter-spacing: -.015em; }
.account-card__header > .filled-icon { color: var(--app-text-secondary); flex-shrink: 0; }
.account-card__header--wrap { flex-wrap: wrap; }
.account-badge { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; padding: 4px 8px; font-size: 12px; line-height: 1.5; color: var(--app-text-regular); background: var(--app-bg-control); border-radius: var(--app-radius-small); }
.account-badge--blue { color: var(--app-accent); background: var(--app-bg-selected); }
.account-profile-hero { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; margin-bottom: 24px; }
.account-avatar { display: grid; place-items: center; flex-shrink: 0; width: 56px; height: 56px; border-radius: 16px; color: var(--app-accent); background: var(--app-bg-selected); font-size: 24px; font-weight: 500; }
.account-profile-copy { flex: 1; min-width: 0; }
.account-profile-copy h3 { font-size: 20px; font-weight: 600; line-height: 1.4; margin: 0 0 4px; overflow-wrap: anywhere; }
.account-profile-copy > span { display: block; color: var(--app-text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.account-status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--app-text-secondary); }
.account-status > span { width: 6px; height: 6px; background: var(--app-accent); border-radius: 50%; }
.account-status--inactive > span { background: var(--el-color-warning); }
.account-readonly-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 0 0 24px; }
.account-readonly-grid dt { color: var(--app-text-secondary); font-size: 12px; margin-bottom: 6px; }
.account-readonly-grid dd { margin: 0; min-height: 40px; padding: 10px 12px; background: var(--app-bg-control); border-radius: var(--app-radius-control); font-size: 13px; overflow-wrap: anywhere; }
.account-code { margin-left: 8px; color: var(--app-text-secondary); font-size: 12px; }
.account-mono { font-family: var(--app-font-mono); font-variant-numeric: tabular-nums; }
.account-form, .account-password-form { min-width: 0; }
.account-form :deep(.el-form-item__content), .account-password-form :deep(.el-form-item__content) { min-width: 0; }
.account-form :deep(.el-form-item), .account-password-form :deep(.el-form-item) { margin-bottom: 24px; }
.account-form-actions, .account-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.account-form-actions { margin-top: 24px; }
.account-form-actions > .el-button + .el-button, .account-actions > .el-button + .el-button { margin-left: 0; }
.account-note { font-size: 12px; line-height: 1.75; color: var(--app-text-secondary); margin: 12px 0; }
.account-error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); border-radius: var(--app-radius-small); padding: 10px 12px; font-size: 13px; line-height: 1.65; margin: 16px 0; overflow-wrap: anywhere; }
.account-summary-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 0; }
.account-summary-row > div { min-width: 0; }
.account-summary-row b { display: block; font-size: 14px; font-weight: 500; }
.account-summary-row small { display: block; margin-top: 4px; font-size: 12px; color: var(--app-text-secondary); }
.account-hint { color: var(--app-text-secondary); background: var(--app-bg-control); border-radius: var(--app-radius-control); padding: 14px; margin-top: 16px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.account-team-count { font-size: 13px; color: var(--app-text-secondary); margin: 0 0 12px; }
.account-team-count strong { font-size: 26px; font-weight: 500; color: var(--app-text-primary); }
.account-guidance { padding-left: 20px; margin: 0; color: var(--app-text-regular); font-size: 13px; line-height: 1.8; }
.account-guidance li + li { margin-top: 12px; }
.account-notice { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px 16px; border-radius: var(--app-radius-control); background: var(--app-bg-selected); color: var(--app-text-accent); margin-bottom: 16px; font-size: 13px; }
.account-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 40px 20px; min-width: 0; text-align: center; color: var(--app-text-secondary); }
.account-state h2, .account-state h3 { margin: 0; color: var(--app-text-primary); font-size: 17px; font-weight: 500; }
.account-state p { margin: 0; line-height: 1.8; font-size: 13px; overflow-wrap: anywhere; }
.account-session-list, .account-team-list { list-style: none; padding: 0; margin: 0; min-width: 0; }
.account-session { display: flex; align-items: flex-start; gap: 16px; padding: 24px 0; min-width: 0; }
.account-session + .account-session { margin-top: 4px; }
.account-session__icon { width: 40px; height: 40px; border-radius: var(--app-radius-control); background: var(--app-bg-control); color: var(--app-text-secondary); display: grid; place-items: center; flex-shrink: 0; }
.account-session__copy { flex: 1; min-width: 0; }
.account-session__title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.account-session__title h3 { font-size: 14px; font-weight: 500; margin: 0; }
.account-session__copy > p { font-size: 12px; color: var(--app-text-secondary); margin: 6px 0 8px; overflow-wrap: anywhere; }
.account-session__times { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 8px 24px; margin: 0; font-size: 12px; }
.account-session__times dt { color: var(--app-text-secondary); }
.account-session__times dd { margin: 3px 0 0; color: var(--app-text-regular); }
.account-user-agent { margin-top: 10px; font-size: 12px; color: var(--app-text-secondary); }
.account-user-agent summary { cursor: pointer; width: fit-content; color: var(--app-accent); }
.account-user-agent summary:focus-visible { outline: 2px solid var(--app-focus-color); outline-offset: 3px; border-radius: 4px; }
.account-user-agent p { margin: 8px 0 0; padding: 10px 12px; border-radius: var(--app-radius-small); background: var(--app-bg-control); font-family: var(--app-font-mono); overflow-wrap: anywhere; }
.account-sessions-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-top: 16px; padding-top: 16px; }
.account-sessions-footer > .el-button { border-color: transparent; }
.account-sessions-footer p { color: var(--app-text-secondary); font-size: 12px; line-height: 1.8; margin: 0; }
.account-team-list { display: flex; flex-direction: column; gap: 12px; }
.account-team { display: flex; align-items: center; gap: 16px; min-width: 0; padding: 18px; background: var(--app-bg-control); border-radius: var(--app-radius-control); }
.account-team__mark { flex-shrink: 0; display: grid; place-items: center; width: 48px; min-height: 44px; background: var(--app-bg-surface); color: var(--app-text-regular); border-radius: var(--app-radius-small); padding: 6px; font-size: 12px; line-height: 1.4; overflow-wrap: anywhere; }
.account-team__copy { flex: 1; min-width: 0; }
.account-team__copy h3 { margin: 0; font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.account-team__copy .account-code { display: inline-block; margin: 6px 0 0; }
.account-team__actions { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; flex-shrink: 0; }
.account-team__actions .account-badge:not(.account-badge--blue) { background: var(--app-bg-surface); }
@media (max-width: 900px) { .account-two-column { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 600px) {
  .account-card { padding: 20px 16px; }
  .account-card__header { flex-wrap: wrap; gap: 10px; margin-bottom: 20px; }
  .account-readonly-grid { grid-template-columns: minmax(0, 1fr); }
  .account-tabs :deep(.el-tabs__item) { padding-inline: 12px; font-size: 13px; }
  .account-status { margin-left: 70px; flex-basis: calc(100% - 70px); }
  .account-session { flex-wrap: wrap; gap: 12px; padding-block: 20px; }
  .account-session__copy { flex-basis: calc(100% - 52px); }
  .account-session > .el-button { margin-left: 52px; }
  .account-team { flex-wrap: wrap; gap: 12px; padding: 14px; }
  .account-team__copy { flex-basis: calc(100% - 60px); }
  .account-team__actions { flex-direction: row; align-items: center; flex-wrap: wrap; justify-content: space-between; width: 100%; padding-left: 60px; }
  .account-sessions-footer { align-items: flex-start; }
  .account-form-actions .el-button { max-width: 100%; }
}
</style>
