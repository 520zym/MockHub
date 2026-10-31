<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }" aria-label="主导航">
    <router-link to="/apis" class="sidebar__brand" aria-label="MockHub 首页"><span class="sidebar__mark">M</span><b class="sidebar__copy">MockHub</b><small class="sidebar__copy">1.4</small></router-link>
    <div class="sidebar__scope sidebar__copy">{{ systemContext ? '实例范围' : '团队上下文' }}</div>
    <el-dropdown trigger="click" placement="bottom-start" @command="selectTeam" class="sidebar__team-dropdown">
      <button type="button" class="sidebar__team" :disabled="!appStore.teams.length && !userStore.isSuperAdmin" aria-label="切换团队">
        <span class="sidebar__team-mark">{{ systemContext ? 'SYS' : appStore.currentTeam?.identifier?.slice(0, 4) || '—' }}</span>
        <span class="sidebar__team-text sidebar__copy"><b>{{ systemContext ? '系统管理' : appStore.currentTeam?.name || '尚未加入团队' }}</b><small>{{ systemContext ? '全局范围' : currentRole }}</small></span>
        <el-icon class="sidebar__copy"><ArrowDown /></el-icon>
      </button>
      <template #dropdown><el-dropdown-menu class="team-switch-menu">
        <el-dropdown-item v-for="team in appStore.teams" :key="team.id" :command="team.id"><span class="switch-team__code">{{ team.identifier }}</span><span class="switch-team__info"><b>{{ team.name }}</b><small>{{ roleLabel(team) }}{{ team.status === 'ARCHIVED' ? ' · 已归档' : '' }}</small></span><el-icon v-if="team.id === appStore.currentTeamId && !systemContext"><Check /></el-icon></el-dropdown-item>
        <el-dropdown-item v-if="userStore.isSuperAdmin" command="@system" :divided="appStore.teams.length > 0"><FilledIcon name="shield" :size="16" /><span>系统管理</span></el-dropdown-item>
      </el-dropdown-menu></template>
    </el-dropdown>
    <div class="sidebar__scroll">
      <template v-if="appStore.currentTeamId">
        <div class="sidebar__label sidebar__copy">当前团队工作区</div>
        <nav class="sidebar__nav" aria-label="团队工作区"><router-link v-for="item in workspaceItems" :key="item.path" :to="item.path" :title="item.label" class="sidebar__nav-item" :class="{ 'is-active': isActive(item.path) }"><FilledIcon :name="item.icon" :size="17" /><span class="sidebar__copy">{{ item.label }}</span><span v-if="item.path === '/apis'" class="sidebar__count sidebar__copy">{{ appStore.currentTeam?.apiCount || 0 }}</span></router-link></nav>
        <div class="sidebar__label sidebar__copy">团队</div>
        <router-link to="/team" title="团队设置" class="sidebar__nav-item" :class="{ 'is-active': route.path === '/team' }"><FilledIcon name="team" :size="17" /><span class="sidebar__copy">团队设置</span></router-link>
      </template>
      <template v-if="userStore.isSuperAdmin"><div class="sidebar__label sidebar__copy">系统管理</div><nav class="sidebar__nav" aria-label="系统管理"><router-link v-for="item in systemItems" :key="item.path" :to="item.path" :title="item.label" class="sidebar__nav-item" :class="{ 'is-active': isActive(item.path) }"><FilledIcon :name="item.icon" :size="17" /><span class="sidebar__copy">{{ item.label }}</span></router-link></nav></template>
    </div>
    <div class="sidebar__foot">
      <el-dropdown trigger="click" placement="top-start" @command="accountCommand" class="sidebar__account-dropdown">
        <button type="button" class="sidebar__account" :class="{ 'is-current': route.path.startsWith('/account') }" aria-label="个人账号与安全"><span class="sidebar__avatar">{{ initial }}</span><span class="sidebar__account-text sidebar__copy"><b>{{ userStore.user?.displayName || userStore.user?.username }}</b><small>{{ userStore.isSuperAdmin ? '超级管理员' : '普通账号' }}</small></span><el-icon class="sidebar__copy"><MoreFilled /></el-icon></button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人资料</el-dropdown-item><el-dropdown-item command="security">账号安全</el-dropdown-item><el-dropdown-item command="sessions">登录会话</el-dropdown-item><el-dropdown-item command="teams">我的团队</el-dropdown-item><el-dropdown-item command="tokens">个人访问凭证</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </div>
  </aside>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { roleLabel } from '@/utils/permissions'
import FilledIcon from '@/components/FilledIcon.vue'
const appStore = useAppStore(), userStore = useUserStore(), route = useRoute(), router = useRouter()
const systemContext = computed(() => route.meta.requiresSuperAdmin)
const currentRole = computed(() => roleLabel(appStore.currentTeam))
const initial = computed(() => (userStore.user?.displayName || userStore.user?.username || 'U').slice(0, 1).toUpperCase())
const workspaceItems = [{ path: '/apis', label: '接口管理', icon: 'api' }, { path: '/files', label: '文件服务器', icon: 'files' }, { path: '/variables', label: '动态变量', icon: 'variables' }, { path: '/stats', label: '使用统计', icon: 'stats' }, { path: '/logs/request', label: '日志记录', icon: 'logs' }]
const systemItems = [{ path: '/users', label: '账号管理', icon: 'user' }, { path: '/teams', label: '团队管理', icon: 'team' }, { path: '/settings', label: '全局设置', icon: 'settings' }]
function isActive(path) { return path === '/apis' ? route.path.startsWith('/apis') : path === '/logs/request' ? route.path.startsWith('/logs') : route.path === path }
async function selectTeam(id) {
  if (id === '@system') { await router.push('/users'); return }
  if (id === appStore.currentTeamId && !systemContext.value) return
  if (route.name === 'ApiEdit' || route.name === 'ApiNew') {
    // Let the editor's unsaved guard decide first; a canceled navigation must not switch the sidebar context.
    const failure = await router.push('/apis')
    if (failure) return
    appStore.setFilter(id)
    return
  }
  if (!appStore.setFilter(id)) return
  if (systemContext.value || route.path.startsWith('/account') || route.name === 'ApiEdit' || route.name === 'ApiNew' || route.path === '/no-access') await router.push('/apis')
}
async function accountCommand(command) {
  if (command !== 'logout') { await router.push(command === 'tokens' ? '/account/tokens' : { path: '/account', query: { tab: command } }); return }
  try { await userStore.logout() } catch { /* 网络错误已提示，本地认证也会被清除 */ }
  appStore.reset(); await router.replace('/login')
}
</script>
<style lang="scss" scoped>
.sidebar { min-height: 0; min-width: 0; display: flex; flex-direction: column; background: var(--app-bg-surface); border-radius: 20px; padding: 16px 12px 10px; overflow: hidden; color: var(--app-text-primary); }
.sidebar__brand { display: flex; align-items: center; gap: 9px; text-decoration: none; color: inherit; margin: 4px 8px 25px; }
.sidebar__mark { width: 28px; height: 28px; display: grid; place-items: center; background: var(--app-primary); color: #fff; border-radius: 9px; font-weight: 700; flex-shrink: 0; }
.sidebar__brand b { font-size: 17px; letter-spacing: -.4px; } .sidebar__brand small { margin-left: auto; font-size: 12px; color: var(--app-text-secondary); }
.sidebar__scope { color: var(--app-text-secondary); font-size: 12px; margin: 0 10px 7px; }
.sidebar__team-dropdown { display: block; width: 100%; }
.sidebar__team { display: flex; align-items: center; gap: 9px; border: 0; border-radius: 12px; padding: 11px 10px; background: var(--app-bg-control); width: 100%; color: inherit; cursor: pointer; text-align: left; min-height: 58px; }
.sidebar__team:disabled { cursor: default; } .sidebar__team-mark { font: 600 10px var(--app-font-mono); background: var(--app-bg-surface); padding: 7px 5px; border-radius: 7px; color: var(--app-accent); min-width: 30px; text-align: center; }
.sidebar__team-text { display: flex; flex-direction: column; flex: 1; min-width: 0; gap: 4px; } .sidebar__team-text b { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .sidebar__team-text small { font-size: 12px; color: var(--app-text-secondary); }
.sidebar__scroll { flex: 1; min-height: 0; overflow-y: auto; overflow-x: hidden; padding-top: 12px; }
.sidebar__label { color: var(--app-text-secondary); font-size: 12px; padding: 8px 10px 7px; }
.sidebar__nav { display: flex; flex-direction: column; gap: 4px; }
.sidebar__nav-item { display: flex; align-items: center; gap: 10px; height: 36px; padding: 0 12px; margin: 2px 0; border-radius: 10px; text-decoration: none; color: var(--app-text-secondary); font-size: 12px; white-space: nowrap; }
.sidebar__nav-item:hover { background: var(--app-bg-control); color: var(--app-text-primary); } .sidebar__nav-item.is-active { background: var(--app-primary); color: white; }
.sidebar__count { margin-left: auto; font: 11px var(--app-font-mono); opacity: .85; }
.sidebar__foot { margin-top: auto; padding-top: 12px; } .sidebar__notice { display: flex; align-items: center; gap: 7px; margin: 0 10px 14px; font-size: 12px; line-height: 1.5; color: var(--app-text-secondary); } .sidebar__notice i { width: 5px; height: 5px; border-radius: 50%; background: var(--app-accent); }
.sidebar__account-dropdown { width: 100%; } .sidebar__account { display: flex; align-items: center; gap: 9px; width: 100%; border: 0; background: transparent; padding: 8px; border-radius: 11px; cursor: pointer; text-align: left; color: inherit; } .sidebar__account:hover,.sidebar__account.is-current { background: var(--app-bg-selected); }
.sidebar__avatar { width: 30px; height: 30px; border-radius: 9px; background: var(--app-bg-control); display: grid; place-items: center; font-size: 12px; font-weight: 600; flex-shrink: 0; }
.sidebar__account-text { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; } .sidebar__account-text b { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .sidebar__account-text small { font-size: 12px; color: var(--app-text-secondary); }
.sidebar--collapsed { padding: 16px 6px 10px; } .sidebar--collapsed .sidebar__copy { display: none; } .sidebar--collapsed .sidebar__brand { margin: 4px auto 25px; } .sidebar--collapsed .sidebar__team { padding: 7px 0; justify-content: center; } .sidebar--collapsed .sidebar__nav-item { justify-content: center; padding: 0; } .sidebar--collapsed .sidebar__account { justify-content: center; padding: 6px 0; }
@media(max-width: 900px) { .sidebar { padding: 16px 6px 10px; } .sidebar__copy { display: none; } .sidebar__brand { margin: 4px auto 25px; } .sidebar__team { padding: 7px 0; justify-content: center; } .sidebar__nav-item { justify-content: center; padding: 0; } .sidebar__account { justify-content: center; padding: 6px 0; } }
</style>
<style>
.team-switch-menu { min-width: 245px; } .team-switch-menu .el-dropdown-menu__item { gap: 10px; padding-top: 9px; padding-bottom: 9px; }
.switch-team__code { font: 11px var(--app-font-mono); color: var(--app-accent); background: var(--app-bg-control); border-radius: 6px; padding: 5px; min-width: 38px; text-align: center; }
.switch-team__info { display: flex; flex: 1; flex-direction: column; gap: 3px; } .switch-team__info b { font-size: 12px; font-weight: 600; } .switch-team__info small { font-size: 12px; color: var(--app-text-secondary); }
</style>
