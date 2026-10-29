<template>
  <div class="app-layout" :class="{ 'app-layout--compact': appStore.sidebarCollapsed }">
    <Sidebar />
    <div class="app-layout__main">
      <Topbar />
      <main class="app-layout__content" id="main-content">
        <el-skeleton v-if="loading" :rows="8" animated class="shell-loading" />
        <el-result v-else-if="loadError" icon="warning" title="暂时无法读取工作区" sub-title="请检查服务连接后重试，未加载的数据不会被当作空团队。"><template #extra><el-button type="primary" @click="bootstrap">重试</el-button></template></el-result>
        <section v-else-if="!appStore.currentTeamId && route.meta.teamRequired" class="workspace-empty app-surface">
          <FilledIcon name="team" :size="40" /><h1>账号已就绪，等待加入团队</h1>
          <p>接口、规则、变量和文件属于团队。请联系团队管理员，将你的账号加入成员列表。</p>
          <code>{{ userStore.user?.username }}</code>
          <router-link to="/account?tab=teams"><el-button>查看个人账号与团队</el-button></router-link>
        </section>
        <router-view v-else v-slot="{ Component }"><component :is="Component" :key="String(route.name) + String(route.params.id || '')" /></router-view>
      </main>
    </div>
    <AiAssistant v-if="userStore.identityReady && appStore.currentTeamId" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'
import FilledIcon from '@/components/FilledIcon.vue'
import AiAssistant from '@/components/ai/AiAssistant.vue'
const appStore = useAppStore(), userStore = useUserStore(), route = useRoute(), router = useRouter()
const loading = ref(true), loadError = ref(false)
let refreshing = null
async function bootstrap() {
  loading.value = true; loadError.value = false
  try { await userStore.ensureIdentity(); await appStore.loadTeams() }
  catch { loadError.value = true }
  finally { loading.value = false }
}
async function refreshPermissions() {
  if (!userStore.token || refreshing) return
  const previous = appStore.currentTeamId
  refreshing = (async () => {
    await userStore.refreshIdentity(); await appStore.loadTeams()
    if (route.meta.requiresSuperAdmin && !userStore.isSuperAdmin) await router.replace('/no-access')
    else if (previous && !userStore.hasCapability(previous, 'read') && route.meta.teamRequired) await router.replace('/no-access')
  })().catch(() => {}).finally(() => { refreshing = null })
  return refreshing
}
function expired() { userStore.clearSession(); appStore.reset(); router.replace('/login') }
function clearSession() { appStore.reset(); if (!userStore.token && route.path !== '/login') router.replace('/login') }
function resize() { if (window.innerWidth < 900) appStore.sidebarCollapsed = true }
function storageChanged(event) {
  if (event.key !== 'token' || event.newValue === userStore.token) return
  if (!event.newValue) { expired(); return }
  userStore.token = event.newValue; userStore.identityReady = false; appStore.reset()
  bootstrap().then(() => router.replace('/apis'))
}
onMounted(() => {
  resize(); bootstrap()
  window.addEventListener('resize', resize)
  window.addEventListener('storage', storageChanged)
  window.addEventListener('mockhub:permissions-changed', refreshPermissions)
  window.addEventListener('mockhub:authentication-expired', expired)
  window.addEventListener('mockhub:session-cleared', clearSession)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  window.removeEventListener('storage', storageChanged)
  window.removeEventListener('mockhub:permissions-changed', refreshPermissions)
  window.removeEventListener('mockhub:authentication-expired', expired)
  window.removeEventListener('mockhub:session-cleared', clearSession)
})
</script>

<style lang="scss" scoped>
.app-layout { display: grid; grid-template-columns: var(--app-sidebar-width, 228px) minmax(0, 1fr); gap: 14px; padding: 14px; height: 100vh; height: 100dvh; min-width: 0; background: var(--app-bg-page); }
.app-layout--compact { grid-template-columns: 68px minmax(0, 1fr); }
.app-layout__main { min-width: 0; min-height: 0; display: flex; flex-direction: column; gap: 14px; }
.app-layout__content { min-height: 0; min-width: 0; overflow: auto; flex: 1; overscroll-behavior: contain; }
.shell-loading { padding: 24px; background: var(--app-bg-surface); border-radius: 16px; }
.workspace-empty { min-height: 420px; padding: 48px 24px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; text-align: center; }
.workspace-empty h1 { font-size: 22px; margin: 0; } .workspace-empty p { max-width: 460px; color: var(--app-text-secondary); line-height: 1.7; margin: 0; }
.workspace-empty code { padding: 8px 14px; background: var(--app-bg-control); border-radius: 8px; }
@media (max-width: 900px) { .app-layout { grid-template-columns: 68px minmax(0, 1fr); } }
@media (max-width: 600px) { .app-layout { gap: 8px; padding: 8px; grid-template-columns: 56px minmax(0, 1fr); } .app-layout__main { gap: 8px; } .workspace-empty { padding: 28px 16px; } .workspace-empty h1 { font-size: 18px; } }
</style>
