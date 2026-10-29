import { createApp, computed, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useUserStore } from '../../../src/stores/user'
import { useAppStore } from '../../../src/stores/app'
import ApiHistoryDrawer from '../../../src/components/ApiHistoryDrawer.vue'
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
  const teams = ['a', 'b'].map((key, index) => ({ teamId: 'team-' + key, id: 'team-' + key, name: index ? '支付中心' : '用户中心', identifier: index ? 'PAY' : 'USER', role, membershipRole: role, accessMode: 'MEMBER', status: 'ACTIVE', teamStatus: 'ACTIVE', capabilities: { ...caps } }))
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
  revokeRead() { user.updateUser({ ...user.user, teams: [] }); appStore.setFilter(null) },
  calls: fixtureState.calls,
  state: fixtureState
}
const Root = {
  components: { ApiHistoryDrawer },
  setup() { return { open, changed, currentRevision, teamId: computed(() => appStore.currentTeamId), apiId: computed(() => appStore.currentTeamId === 'team-b' ? 'api-b' : 'api-a'), onChanged(result) { changed.value++; currentRevision.value = result.revision } } },
  template: '<main class="fixture-background"><span class="fixture-label">真实 Vue / Element Plus · API STUB（非后端联调）</span><h1>接口管理</h1><button type="button" @click="open = true">打开历史</button><span id="fixture-changed">{{ changed }}</span><ApiHistoryDrawer v-model="open" :api-id="apiId" :team-id="teamId" :current-revision="currentRevision" @changed="onChanged" /></main>'
}
createApp(Root).use(pinia).use(ElementPlus, { locale: zhCn }).mount('#app')
