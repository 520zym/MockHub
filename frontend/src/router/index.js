import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { requiresAuth: false, title: '登录' } },
  { path: '/', component: () => import('@/components/layout/AppLayout.vue'), meta: { requiresAuth: true }, children: [
    { path: '', redirect: '/apis' },
    { path: 'apis', name: 'ApiList', component: () => import('@/views/ApiList.vue'), meta: { title: '接口管理', teamRequired: true } },
    { path: 'apis/trash', name: 'ApiTrash', component: () => import('@/views/ApiTrash.vue'), meta: { title: '接口回收站', teamRequired: true } },
    { path: 'apis/new', name: 'ApiNew', component: () => import('@/views/ApiEdit.vue'), meta: { title: '新建接口', teamRequired: true, requiresTeamWrite: true } },
    { path: 'apis/:id/edit', name: 'ApiEdit', component: () => import('@/views/ApiEdit.vue'), meta: { title: '接口详情', teamRequired: true } },
    { path: 'team', name: 'TeamSettings', component: () => import('@/views/TeamSettings.vue'), meta: { title: '团队设置', teamRequired: true } },
    { path: 'teams', name: 'TeamManage', component: () => import('@/views/TeamManage.vue'), meta: { title: '团队管理', requiresSuperAdmin: true } },
    { path: 'users', name: 'UserManage', component: () => import('@/views/UserManage.vue'), meta: { title: '账号管理', requiresSuperAdmin: true } },
    { path: 'variables', name: 'TeamVariables', component: () => import('@/views/TeamVariables.vue'), meta: { title: '动态变量', teamRequired: true } },
    { path: 'files', name: 'FileServer', component: () => import('@/views/FileServer.vue'), meta: { title: '文件服务器', teamRequired: true } },
    { path: 'stats', name: 'Stats', component: () => import('@/views/Stats.vue'), meta: { title: '使用统计', teamRequired: true } },
    { path: 'logs/operation', name: 'OperationLog', component: () => import('@/views/LogView.vue'), meta: { title: '操作日志', teamRequired: true } },
    { path: 'logs/request', name: 'RequestLog', component: () => import('@/views/LogView.vue'), meta: { title: '请求日志', teamRequired: true } },
    { path: 'mcp-confirm/:id', name: 'McpConfirm', component: () => import('@/views/McpConfirm.vue'), meta: { title: 'AI 草稿确认', teamRequired: true } },
    { path: 'settings', name: 'Settings', component: () => import('@/views/Settings.vue'), meta: { title: '全局设置', requiresSuperAdmin: true } },
    { path: 'account/tokens', name: 'AccountTokens', component: () => import('@/views/AccountTokens.vue'), meta: { title: '个人访问凭证' } },
    { path: 'account', name: 'Account', component: () => import('@/views/Account.vue'), meta: { title: '个人账号' } },
    { path: 'no-access', name: 'NoAccess', component: () => import('@/views/NoAccess.vue'), meta: { title: '无权访问' } },
    { path: ':pathMatch(.*)*', redirect: '/apis' }
  ] }
]
const router = createRouter({ history: createWebHashHistory(), routes })
router.beforeEach(async to => {
  const user = useUserStore(), app = useAppStore(), token = localStorage.getItem('token')
  if (!token && user.token) user.clearSession()
  if (to.meta.requiresAuth === false) {
    if (token && user.identityReady) return '/apis'
    return true
  }
  if (!token) return '/login'
  if (user.token !== token) { user.token = token; user.identityReady = false }
  try { await user.ensureIdentity() } catch { return '/login' }
  if (to.meta.requiresSuperAdmin && !user.isSuperAdmin) return '/no-access'
  if (user.user?.firstLogin && !(to.path === '/account' && to.query.tab === 'security')) return { path: '/account', query: { tab: 'security', required: '1' } }
  if (to.meta.requiresTeamWrite) {
    if (!app.teams.length) { try { await app.loadTeams() } catch { return false } }
    if (app.currentTeamId && !user.canWriteTeam(app.currentTeamId)) return '/no-access'
  }
  return true
})
export default router
