<template>
  <div class="team-settings">
    <header class="page-heading"><div><div class="title-row"><h1>团队设置</h1><span v-if="team" class="identifier">{{ team.identifier }}</span></div></div><span v-if="team" class="badge blue">{{ identityLabel }}</span></header>
    <div v-if="error" class="notice error" role="alert">{{ error }}<button class="text-button" @click="reload">重新加载</button></div>
    <div v-if="!team" class="empty panel"><FilledIcon name="team" :size="32" /><h2>尚未选择团队</h2><p>请在侧栏选择团队。</p><button class="button" @click="reload">刷新团队</button></div>
    <template v-else>
      <div v-if="archived || maintenance" class="notice"><FilledIcon name="shield" :size="18" /><span v-if="archived"><b>团队已归档</b> · 管理写入已冻结，请联系超级管理员恢复。</span><span v-else-if="maintenance"><b>系统维护身份</b> · 以超级管理员权限维护，非团队成员。</span></div>
      <div class="content-grid">
        <section class="panel member-panel" aria-labelledby="member-heading">
          <div class="section-heading"><div><h2 id="member-heading">团队成员 <span class="quiet">{{ members.length }} 位成员</span></h2></div><button v-if="canManageMembers" class="button primary" :disabled="busy || loading" @click="openAdd">添加已有账号</button></div>
          <div class="table-scroll" :aria-busy="loading">
            <table><thead><tr><th>用户名</th><th>显示名</th><th>团队角色</th><th>账号状态</th><th v-if="canManageMembers">操作</th></tr></thead><tbody>
              <tr v-if="loading"><td :colspan="canManageMembers ? 5 : 4" class="table-empty">正在加载成员…</td></tr>
              <template v-else><tr v-for="member in members" :key="member.userId"><td class="mono" :title="member.username">{{ member.username }}</td><td :title="member.displayName">{{ member.displayName }} <small v-if="member.userId === userStore.user?.id" class="self">本人</small></td><td><select v-if="canManageMembers" :value="normalizeRole(member.role)" :aria-label="member.displayName + '的团队角色'" :disabled="busy || isLastAdmin(member)" :title="isLastAdmin(member) ? '最后一位有效管理员，请先交接' : ''" @change="changeRole(member, $event)"><option v-for="(label, role) in roles" :key="role" :value="role">{{ label }}</option></select><span v-else>{{ roles[normalizeRole(member.role)] || '未知角色' }}</span></td><td><span class="badge" :class="member.status === 'ACTIVE' ? 'green' : 'muted'">{{ member.status === 'ACTIVE' ? '正常' : member.status === 'DISABLED' ? '已停用' : '未知' }}</span></td><td v-if="canManageMembers"><button class="text-button" :disabled="busy || isLastAdmin(member)" :title="isLastAdmin(member) ? '请先指派另一位有效管理员' : ''" @click="removeMember(member)">移除</button></td></tr><tr v-if="!members.length"><td :colspan="canManageMembers ? 5 : 4" class="table-empty">暂无成员。请由超级管理员指派有效团队管理员。</td></tr></template>
            </tbody></table>
          </div>
          <footer class="protection"><FilledIcon name="shield" :size="17" /><span v-if="loading">正在核实有效管理员…</span><span v-else-if="adminCount === 0">尚无有效管理员，请由超级管理员指派。</span><span v-else>{{ adminCount }} 位有效管理员{{ adminCount === 1 ? '；最后一位需先交接才能降级或移除。' : '。' }}</span></footer>
        </section>
        <aside class="side-panels">
          <section class="panel"><div class="section-heading"><h2>{{ team.name }}</h2><span class="badge" :class="archived ? 'muted' : 'green'">{{ archived ? '已归档' : '正常' }}</span></div><p class="description">{{ team.description || '暂无团队描述' }}</p><dl><dt>团队标识</dt><dd class="mono">{{ team.identifier }} <small>创建后不可改</small></dd><dt>团队资源</dt><dd>{{ team.apiCount ?? '—' }} 个接口</dd><dt>当前身份</dt><dd>{{ identityLabel }}</dd><dt>团队颜色</dt><dd><span class="color-dot" :style="{ backgroundColor: team.color }"></span>{{ team.color }}</dd></dl><div class="side-actions"><button v-if="canManageTeam" class="button" :disabled="busy" @click="openEdit">编辑团队信息</button><button v-if="selfMember" class="button" :disabled="busy || loading || archived || isLastAdmin(selfMember)" :title="isLastAdmin(selfMember) ? '你是最后一位有效管理员，请先交接' : ''" @click="leave">离开团队</button></div></section>
        </aside>
      </div>
    </template>
    <el-dialog v-model="addVisible" title="添加已有账号" width="min(520px, 94vw)" class="team-settings-dialog" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
      <p class="dialog-note">按完整用户名精确查找，只能添加正常账号。</p><form @submit.prevent="searchCandidate"><label for="candidate-name">完整用户名</label><div class="search-row"><input id="candidate-name" v-model="candidateName" autocomplete="off" placeholder="输入完整用户名" :disabled="busy" @input="resetCandidate" /><button class="button" :disabled="searching || busy || !candidateName.trim()">{{ searching ? '查找中…' : '精确查找' }}</button></div></form><div class="candidate-result" aria-live="polite"><template v-if="candidate"><b>{{ candidate.displayName }}</b><span class="mono">{{ candidate.username }}</span><span class="badge green">正常账号</span></template><span v-else>{{ candidateMessage }}</span></div><label for="new-member-role">本团队角色</label><select id="new-member-role" v-model="candidateRole" :disabled="busy"><option v-for="(label, role) in roles" :key="role" :value="role">{{ label }}</option></select><p class="dialog-note">仅作用于 {{ team?.name }}，不改变全局账号身份。</p><div v-if="dialogError" class="notice error" role="alert">{{ dialogError }}</div><template #footer><button class="button" :disabled="busy" @click="addVisible = false">取消</button><button class="button primary" :disabled="busy || !candidate || !canManageMembers" @click="addMember">{{ busy ? '提交中…' : '添加到团队' }}</button></template>
    </el-dialog>
    <el-dialog v-model="editVisible" title="编辑团队信息" width="min(520px, 94vw)" class="team-settings-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close><form id="team-meta-form" class="meta-form" @submit.prevent="saveMeta"><label>团队名称<input v-model="meta.name" required maxlength="50" :disabled="busy" /></label><label>团队标识<input :value="team?.identifier" disabled /><small>标识用于 Mock 路径，创建后不可修改。</small></label><label>团队描述<textarea v-model="meta.description" rows="3" maxlength="1000" :disabled="busy"></textarea></label><label>团队颜色<input v-model="meta.color" type="color" :disabled="busy" /></label></form><div v-if="dialogError" class="notice error" role="alert">{{ dialogError }}</div><template #footer><button class="button" :disabled="busy" @click="editVisible = false">取消</button><button class="button primary" type="submit" form="team-meta-form" :disabled="busy || !canManageTeam">{{ busy ? '保存中…' : '保存修改' }}</button></template></el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getTeamMembers, getMemberCandidates, addTeamMember, removeTeamMember, updateMemberRole, updateTeam, leaveTeam } from '@/api/teams'

const appStore = useAppStore(), userStore = useUserStore(), router = useRouter()
const roles = { VIEWER: '只读成员', EDITOR: '可编辑成员', TEAM_ADMIN: '团队管理员' }
const normalizeRole = role => role === 'MEMBER' ? 'EDITOR' : role
const teamId = computed(() => appStore.currentTeamId)
const team = computed(() => appStore.currentTeam || appStore.teams.find(t => t.id === teamId.value))
const identityTeam = computed(() => userStore.user?.teams?.find(t => t.teamId === teamId.value))
const archived = computed(() => team.value?.status === 'ARCHIVED')
const maintenance = computed(() => identityTeam.value?.accessMode === 'SYSTEM_MAINTENANCE')
const identityLabel = computed(() => maintenance.value ? '系统维护身份' : roles[normalizeRole(identityTeam.value?.membershipRole ?? identityTeam.value?.role)] || (userStore.isSuperAdmin ? '系统维护身份' : '尚未加入团队'))
const canManageMembers = computed(() => !!teamId.value && !archived.value && userStore.hasCapability(teamId.value, 'manageMembers'))
const canManageTeam = computed(() => !!teamId.value && !archived.value && userStore.hasCapability(teamId.value, 'manageTeam'))
const members = ref([]), loading = ref(false), busy = ref(false), error = ref('')
const selfMember = computed(() => members.value.find(m => m.userId === userStore.user?.id))
const adminCount = computed(() => members.value.filter(m => m.role === 'TEAM_ADMIN' && m.status === 'ACTIVE').length)
const isLastAdmin = member => member?.role === 'TEAM_ADMIN' && member.status === 'ACTIVE' && adminCount.value <= 1
const message = err => err?.msg || err?.response?.data?.msg || err?.message || '操作失败，请重试'
let loadRevision = 0, searchRevision = 0
async function loadMembers(id = teamId.value) {
  const revision = ++loadRevision
  members.value = []
  if (!id) { loading.value = false; return }
  loading.value = true
  try { const data = await getTeamMembers(id); if (revision === loadRevision && id === teamId.value) members.value = data || [] }
  catch (err) { if (revision === loadRevision) error.value = message(err) }
  finally { if (revision === loadRevision) loading.value = false }
}
async function reload() {
  error.value = ''
  try { await userStore.refreshIdentity(); await appStore.loadTeams(); await loadMembers() } catch (err) { error.value = message(err) }
}
watch(teamId, id => { error.value = ''; addVisible.value = false; editVisible.value = false; resetCandidate(); loadMembers(id) })
const addVisible = ref(false), editVisible = ref(false), dialogError = ref('')
const candidateName = ref(''), candidate = ref(null), candidateRole = ref('EDITOR'), searching = ref(false)
const candidateMessage = ref('请输入完整用户名后查找。')
const meta = reactive({ name: '', description: '', color: '#3568d4' })
function resetCandidate() { ++searchRevision; candidate.value = null; searching.value = false; candidateMessage.value = '用户名变更后请重新精确查找。'; dialogError.value = '' }
function openAdd() { if (!canManageMembers.value || busy.value) return; candidateName.value = ''; candidateRole.value = 'EDITOR'; resetCandidate(); addVisible.value = true }
async function searchCandidate() {
  if (!canManageMembers.value || !candidateName.value.trim() || busy.value) return
  const id = teamId.value, username = candidateName.value.trim(), revision = ++searchRevision
  candidate.value = null; searching.value = true; dialogError.value = ''
  try {
    const results = await getMemberCandidates(id, username)
    if (revision !== searchRevision || id !== teamId.value) return
    candidate.value = results?.find(item => item.username === username) || null
    candidateMessage.value = candidate.value ? '' : '未找到此正常账号，或该账号已在本团队中。不提供模糊匹配。'
  } catch (err) { if (revision === searchRevision) dialogError.value = message(err) }
  finally { if (revision === searchRevision) searching.value = false }
}
async function refreshAfterWrite(id) {
  try {
    await userStore.refreshIdentity(); await appStore.loadTeams()
    if (!userStore.hasCapability(id, 'read')) { await router.replace('/apis'); return }
    if (id === teamId.value) await loadMembers(id)
  } catch (err) { error.value = '操作已保存，但身份或列表刷新失败：' + message(err) }
}
async function mutate(operation, success, dialog = false) {
  if (busy.value) return false
  const id = teamId.value
  busy.value = true; error.value = ''; dialogError.value = ''
  try { await operation(id); ElMessage.success(success); await refreshAfterWrite(id); return true }
  catch (err) { if (dialog) dialogError.value = message(err); else error.value = message(err); return false }
  finally { busy.value = false }
}
async function addMember() {
  if (!canManageMembers.value || !candidate.value || busy.value) return
  const data = { userId: candidate.value.userId, role: candidateRole.value }
  if (await mutate(id => addTeamMember(id, data), '成员已添加', true)) addVisible.value = false
}
async function changeRole(member, event) {
  const id = teamId.value, role = event.target.value; event.target.value = normalizeRole(member.role)
  if (!canManageMembers.value || busy.value || isLastAdmin(member) || role === member.role) return
  try { await ElMessageBox.confirm('将「' + member.displayName + '」调整为' + roles[role] + '？仅改变本团队权限。', '调整团队角色', { confirmButtonText: '确认调整', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  if (id !== teamId.value || !canManageMembers.value) return
  await mutate(current => updateMemberRole(current, member.userId, { role }), '团队角色已更新')
}
async function removeMember(member) {
  const id = teamId.value
  if (!canManageMembers.value || busy.value || isLastAdmin(member)) return
  try { await ElMessageBox.confirm('移除「' + member.displayName + '」的本团队权限？平台账号和团队资源均保留。最后一位有效管理员不能移除。', '移除团队成员', { confirmButtonText: '确认移除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  if (id !== teamId.value || !canManageMembers.value) return
  await mutate(current => removeTeamMember(current, member.userId), '成员已移出本团队')
}
function openEdit() { if (!canManageTeam.value || busy.value) return; Object.assign(meta, { name: team.value.name, description: team.value.description || '', color: team.value.color || '#3568d4' }); dialogError.value = ''; editVisible.value = true }
async function saveMeta() {
  if (!canManageTeam.value || !meta.name.trim() || busy.value) return
  if (await mutate(id => updateTeam(id, { name: meta.name.trim(), description: meta.description, color: meta.color }), '团队信息已保存', true)) editVisible.value = false
}
async function leave() {
  const id = teamId.value
  if (!selfMember.value || isLastAdmin(selfMember.value) || archived.value || busy.value) return
  try { await ElMessageBox.confirm('离开「' + team.value.name + '」后将失去本团队成员权限。你的平台账号、已创建的资源和审计记录保留。', '离开团队', { confirmButtonText: '确认离开', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  if (id !== teamId.value || !selfMember.value || isLastAdmin(selfMember.value) || archived.value) return
  if (await mutate(current => leaveTeam(current), '已离开团队')) {
    const next = appStore.teams.find(t => t.id !== id && t.status !== 'ARCHIVED')
    if (appStore.currentTeamId === id) appStore.setFilter(next?.id || null)
    await router.push('/apis')
  }
}
// Register after all dialog state exists; team changes discard stale asynchronous responses.
loadMembers()
</script>

<style scoped>
.team-settings{height:auto;min-height:100%;display:flex;flex-direction:column;gap:16px;color:var(--app-text-primary);font-size:13px}.page-heading,.title-row,.section-heading,.side-actions,.search-row{display:flex;align-items:center;gap:12px}.page-heading,.section-heading{justify-content:space-between}.page-heading{flex:none}.title-row h1{font-size:26px;letter-spacing:-.7px;margin:0;font-weight:650}.description,.dialog-note{color:var(--app-text-secondary);line-height:1.7;margin:6px 0 0}.identifier{background:var(--app-bg-control);border-radius:8px;padding:7px 10px;font:12px var(--app-font-mono)}.badge{display:inline-flex;align-items:center;white-space:nowrap;border-radius:7px;padding:5px 8px;font-size:12px}.blue{background:var(--app-bg-selected);color:var(--app-text-accent)}.green{background:var(--el-color-success-light-9);color:var(--app-text-success)}.muted{background:var(--app-bg-control);color:var(--app-text-secondary)}.notice{display:flex;align-items:center;gap:10px;padding:13px 16px;background:var(--app-bg-selected);color:var(--app-text-accent);line-height:1.7;border-radius:12px;flex:none}.notice.error{background:var(--el-color-danger-light-9);color:var(--app-text-danger);justify-content:space-between}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:16px;flex:none;min-height:0;overflow:visible;align-items:stretch}.panel{background:var(--app-bg-surface);border-radius:var(--app-radius-panel);padding:20px;min-width:0}.member-panel{display:flex;flex-direction:column;min-height:0}.section-heading{flex:none;align-items:flex-start}.section-heading h2{font-size:15px;margin:0;font-weight:600}.section-heading p{font-size:12px}.quiet{font-size:12px;color:var(--app-text-secondary);font-weight:400;margin-left:5px}.table-scroll{overflow:auto;min-height:130px;flex:1;margin-top:16px}table{border-collapse:collapse;width:100%;min-width:570px;text-align:left}th{font-weight:500;font-size:12px;color:var(--app-text-secondary);background:var(--app-bg-surface);position:sticky;top:0;z-index:1}th,td{padding:12px 10px;border:0}td{height:54px;font-size:13px}tbody tr:hover{background:var(--el-fill-color-lighter)}.mono{font-family:var(--app-font-mono)}.self{font-size:12px;color:var(--app-text-accent)}.table-empty{text-align:center;color:var(--app-text-secondary);padding:40px 12px}.protection{display:flex;gap:10px;color:var(--app-text-secondary);font-size:12px;line-height:1.8;padding:15px 8px 0;flex:none}.side-panels{display:flex;flex-direction:column;gap:16px;overflow:visible;min-height:0}.description{margin:16px 0}.side-panels dl{display:grid;grid-template-columns:70px minmax(0,1fr);row-gap:12px;font-size:13px;line-height:1.65}.side-panels dt{color:var(--app-text-secondary)}.side-panels dd{margin:0;overflow-wrap:anywhere}.side-panels small{font-size:12px;color:var(--app-text-secondary);display:block}.color-dot{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:7px}.side-actions{flex-wrap:wrap;margin-top:20px}.button{border:0;background:var(--app-bg-control);border-radius:9px;padding:10px 14px;cursor:pointer;font:inherit;font-size:13px;color:var(--app-text-regular);white-space:nowrap}.button.primary{background:var(--app-primary);color:var(--app-text-inverse)}.button:hover:not(:disabled){background:var(--app-bg-hover)}.button.primary:hover:not(:disabled){background:var(--app-primary-hover)}.button:disabled,.text-button:disabled{opacity:.5;cursor:not-allowed}.text-button{background:none;border:0;color:var(--app-text-accent);font:inherit;font-size:13px;cursor:pointer;padding:6px 4px;min-height:28px}.empty{display:grid;place-content:center;justify-items:center;flex:1;min-height:240px;text-align:center;color:var(--app-text-secondary)}.empty h2{font-size:18px;color:var(--app-text-primary)}input,select,textarea{width:100%;border:1px solid transparent;border-radius:8px;background:var(--app-bg-control);box-sizing:border-box;padding:10px 11px;color:var(--app-text-regular);font:inherit;outline:none}select{min-width:135px;font-size:13px;cursor:pointer}input:focus,select:focus,textarea:focus{border-color:var(--app-focus-color)}input:disabled,select:disabled{color:var(--app-text-disabled);cursor:not-allowed}label{display:block;font-size:13px;color:var(--app-text-regular);margin:16px 0 8px}.meta-form label{display:grid;gap:7px}.meta-form small{color:var(--app-text-secondary);font-size:12px}.meta-form input[type=color]{width:65px;height:38px;padding:4px}.search-row input{min-width:0}.candidate-result{display:flex;flex-wrap:wrap;align-items:center;gap:10px;min-height:56px;padding:12px;background:var(--app-bg-control);border-radius:9px;margin:14px 0;font-size:12px;color:var(--app-text-secondary)}.dialog-note{font-size:12px}:global(.team-settings-dialog){box-shadow:none!important;border-radius:16px}:global(.team-settings-dialog .el-dialog__footer){display:flex;justify-content:flex-end;gap:8px}.team-settings :deep(.el-dialog__footer){display:flex;justify-content:flex-end;gap:8px}.team-settings :deep(.el-overlay){backdrop-filter:none}
@media(max-width:1100px){.content-grid{grid-template-columns:minmax(0,1fr) 250px}.panel{padding:16px}.section-heading{flex-wrap:wrap}}@media(max-width:820px){.team-settings{height:auto;min-height:100%}.content-grid{display:flex;flex-direction:column;overflow:visible}.member-panel{min-height:380px}.table-scroll{max-height:480px}.side-panels{overflow:visible;display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}.page-heading{align-items:flex-start}.page-heading h1{font-size:22px}.notice{align-items:flex-start}.title-row{gap:7px}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}
</style>
