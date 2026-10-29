import { createApp, computed, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useUserStore } from '../../../src/stores/user'
import { useAppStore } from '../../../src/stores/app'
import ApiHistoryDrawer from '../../../src/components/ApiHistoryDrawer.vue'
import ApiList from '../../../src/views/ApiList.vue'
import AppLayout from '../../../src/components/layout/AppLayout.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import * as icons from '@element-plus/icons-vue'
import { fixtureState } from './request-stub.js'
import 'element-plus/dist/index.css'
import '../../../src/styles/variables.scss'
import '../../../src/styles/element-overrides.scss'
import '../../../src/styles/global.scss'
import './fixture.css'

const pinia = createPinia(), user = useUserStore(pinia), appStore = useAppStore(pinia)
function setIdentity(role = 'EDITOR') {
  fixtureState.role = role
  const caps = { read: true, write: role !== 'VIEWER', manageMembers: role === 'TEAM_ADMIN', manageTeam: role === 'TEAM_ADMIN', purge: role === 'TEAM_ADMIN', overwriteImport: role === 'TEAM_ADMIN', batchDelete: role === 'TEAM_ADMIN' }
  const teams = ['a', 'b'].map((key, index) => ({ teamId: 'team-' + key, id: 'team-' + key, name: index ? '支付中心' : '用户中心', identifier: index ? 'PAY' : 'USER', apiCount: index ? 1 : 6, role, membershipRole: role, accessMode: 'MEMBER', status: 'ACTIVE', teamStatus: 'ACTIVE', capabilities: { ...caps } }))
  window.fixtureTeams = teams; window.fixtureUser = { id: 'user-1', username: 'fixture', displayName: '王琳', globalRole: 'USER', status: 'ACTIVE', teams }
  user.setLogin('component-fixture-token', window.fixtureUser); appStore.teams = teams
}
setIdentity(new URLSearchParams(location.search).get('role') || 'EDITOR'); appStore.setFilter('team-a')
const open = ref(true), changed = ref(0), currentRevision = ref(3)
window.fixture = {
  apiStub: true,
  setRole: setIdentity,
  setTeam(id) { fixtureState.teamId = id; appStore.setFilter(id); currentRevision.value = fixtureState.revisions[id === 'team-b' ? 'api-b' : 'api-a'] },
  open() { open.value = true },
  close() { open.value = false },
  conflict(value) { fixtureState.conflict = value },
  holdHistory(value) { fixtureState.holdHistory = value },
  release() { fixtureState.holdHistory = false; fixtureState.deferred.splice(0).forEach(resolve => resolve()) },
  revokeRead() { user.updateUser({ ...user.user, teams: [] }); appStore.teams = []; appStore.setFilter(null) },
  holdList(teamId) { fixtureState.holdListTeam = teamId },
  releaseLists() { fixtureState.holdListTeam = ''; fixtureState.listDeferred.splice(0).forEach(resolve => resolve()) },
  bumpTarget() { const row = fixtureState.rows.find(row => row.id === 'api-a'); row.revision++; fixtureState.revisions['api-a'] = row.revision },
  importConflict(value) { fixtureState.importConflict = value },
  calls: fixtureState.calls,
  state: fixtureState
}
const Root = {
  components: { ApiHistoryDrawer },
  setup() { return { open, changed, currentRevision, teamId: computed(() => appStore.currentTeamId), apiId: computed(() => appStore.currentTeamId === 'team-b' ? 'api-b' : 'api-a'), onChanged(result) { changed.value++; currentRevision.value = result.revision } } },
  template: '<main class="fixture-background"><span class="fixture-label">真实 Vue / Element Plus · API STUB（非后端联调）</span><h1>接口管理</h1><button type="button" @click="open = true">打开历史</button><span id="fixture-changed">{{ changed }}</span><ApiHistoryDrawer v-model="open" :api-id="apiId" :team-id="teamId" :current-revision="currentRevision" @changed="onChanged" /></main>'
}
const view = new URLSearchParams(location.search).get('view') || 'history'
let instance
if (view === 'history') instance = createApp(Root).use(pinia)
else {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: AppLayout, children: [{ path: 'apis', component: ApiList, meta: { title: '接口管理', teamRequired: true } }, { path: 'apis/:id/edit', component: { template: '<div>API EDIT 由父任务负责</div>' }, meta: { title: '接口详情', teamRequired: true } }, { path: 'apis/trash', component: { template: '<div>回收站将在独立功能提交中挂载真实组件。</div>' }, meta: { title: '回收站', teamRequired: true } }] }, { path: '/account', component: { template: '<div>个人账号不属于本组件测试范围。</div>' } }] })
  instance = createApp({ template: '<router-view />' }).use(pinia).use(router)
  await router.push('/apis'); await router.isReady()
}
for (const [name, component] of Object.entries(icons)) instance.component(name, component)
instance.use(ElementPlus, { locale: zhCn }).mount('#app')
const watermark = document.createElement('span'); watermark.className = 'fixture-watermark'; watermark.textContent = 'API STUB · 真实组件测试（非后端联调）'; document.body.appendChild(watermark)
