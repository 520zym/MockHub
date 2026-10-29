<template>
  <div class="system-users">
    <header class="page-heading"><div><div class="title-row"><h1>账号管理</h1><span class="badge muted">系统级操作</span></div><p>全局账号身份、团队独立授权与账号生命周期</p></div><button v-if="userStore.isSuperAdmin" class="button primary" :disabled="busy" @click="openCreate"><FilledIcon name="user" :size="15" />{{ createdAccount ? '继续完成建号' : '新建账号' }}</button></header>
    <div v-if="!userStore.isSuperAdmin" class="panel empty"><FilledIcon name="shield" :size="32" /><h2>仅超级管理员可管理账号</h2><p>团队管理员只能管理本团队的已有成员，不能创建或停用平台账号。</p></div>
    <template v-else>
      <div class="notice"><FilledIcon name="shield" :size="18" /><span><b>两层权限，独立授权。</b> 全局角色决定系统管理权；每个团队的角色决定本团队操作。停用优先于删除，团队资源和审计记录始终保留。</span></div>
      <div v-if="createdAccount" class="notice partial" role="status"><span><b>账号 {{ createdAccount.username }} 已创建</b> · 团队分配尚未完成。账号 ID：<code>{{ createdAccount.id }}</code>；重试只会分配团队，不会重复建号。</span><button class="text-button" :disabled="busy" @click="openCreate">继续分配</button><button class="text-button" :disabled="busy" @click="finishWithoutAssignments">稍后分配</button></div>
      <div v-if="error" class="notice error" role="alert">{{ error }}<button class="text-button" :disabled="loading || busy" @click="reload">刷新核实</button></div>
      <div class="toolbar"><input v-model="query" class="search" placeholder="搜索用户名或显示名" aria-label="搜索账号" /><select v-model="statusFilter" aria-label="筛选账号状态"><option value="ALL">全部状态</option><option value="ACTIVE">正常账号</option><option value="DISABLED">已停用</option></select><select v-model="roleFilter" aria-label="筛选全局角色"><option value="ALL">全部身份</option><option value="USER">普通账号</option><option value="SUPER_ADMIN">超级管理员</option></select><span class="toolbar-count">{{ filteredUsers.length }} 个账号</span><button class="text-button" :disabled="loading || busy" @click="reload">刷新</button></div>
      <section class="panel users-panel"><div class="table-scroll" :aria-busy="loading"><table><thead><tr><th>用户名 / 显示名</th><th>全局角色</th><th>账号状态</th><th>团队与独立角色</th><th>创建时间</th><th class="actions-cell">操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="6" class="table-empty">正在加载账号…</td></tr><template v-else><tr v-for="account in filteredUsers" :key="account.id"><td><div class="username mono">{{ account.username }} <small v-if="account.id === userStore.user?.id" class="self">本人</small></div><div class="display-name">{{ account.displayName }}</div></td><td><span class="badge" :class="account.globalRole === 'SUPER_ADMIN' ? 'blue' : 'muted'">{{ account.globalRole === 'SUPER_ADMIN' ? '超级管理员' : '普通账号 USER' }}</span></td><td><span class="badge" :class="account.status === 'ACTIVE' ? 'green' : 'muted'">{{ account.status === 'ACTIVE' ? '正常' : account.status === 'DISABLED' ? '已停用' : '未知状态' }}</span><small v-if="account.firstLogin" class="first-login">下次登录需修改密码</small></td><td><div v-if="account.teams?.length" class="team-roles"><span v-for="membership in account.teams" :key="membership.teamId" class="team-role"><b class="mono">{{ membership.identifier || teamById(membership.teamId)?.identifier || '—' }}</b> {{ roles[normalizeRole(membership.role)] || '未知角色' }}<small v-if="teamById(membership.teamId)?.status === 'ARCHIVED'"> · 已归档</small></span></div><span v-else class="quiet">尚未分配团队</span></td><td class="quiet date-cell">{{ formatDate(account.createdAt) }}</td><td class="actions-cell"><div class="actions"><button class="text-button" :disabled="busy" @click="openEdit(account)">编辑</button><button class="text-button" :disabled="busy" @click="openAssign(account)">分配团队</button><button class="text-button" :disabled="busy" @click="openPassword(account)">重置密码</button><button class="text-button" :class="account.status === 'ACTIVE' ? 'danger' : ''" :disabled="busy || !['ACTIVE', 'DISABLED'].includes(account.status)" @click="changeStatus(account)">{{ pendingId === account.id ? '处理中…' : account.status === 'ACTIVE' ? '停用' : '恢复' }}</button></div></td></tr><tr v-if="!filteredUsers.length"><td colspan="6" class="table-empty">{{ users.length ? '没有匹配的账号' : '暂无账号' }}</td></tr></template></tbody></table></div><footer class="protection"><FilledIcon name="shield" :size="17" /><span>系统必须保留至少一位正常超级管理员；各团队必须保留至少一位正常账号且实际角色为团队管理员的成员。服务端会对停用、降级、离队和整体分配进行事务校验。</span></footer></section>
    </template>

    <el-dialog v-model="createVisible" title="新建账号" width="min(850px, 94vw)" class="system-user-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close>
      <div v-if="createdAccount" class="notice partial"><span>第一步已完成：{{ createdAccount.username }}（{{ createdAccount.id }}）。账号信息已锁定；重试将只提交团队分配。</span></div>
      <form id="create-user-form" @submit.prevent="submitCreate"><div class="create-layout"><div><h2 class="form-title">账号信息</h2><div class="form-grid"><label>用户名 <em>*</em><input v-model="createForm.username" required maxlength="50" autocomplete="off" :disabled="busy || !!createdAccount" placeholder="登录使用，创建后不可改" /></label><label>显示名 <em>*</em><input v-model="createForm.displayName" required maxlength="50" :disabled="busy || !!createdAccount" /></label><label>初始密码 <em>*</em><input v-model="createForm.password" type="password" :required="!createdAccount" minlength="6" autocomplete="new-password" :disabled="busy || !!createdAccount" placeholder="不少于6位" /><small>首次登录必须修改密码。</small></label><label>全局角色 <em>*</em><select v-model="createForm.globalRole" :disabled="busy || !!createdAccount"><option value="USER">普通账号 USER</option><option value="SUPER_ADMIN">超级管理员 SUPER_ADMIN</option></select></label></div><div class="assignment-heading"><div><h2 class="form-title">团队分配</h2><p>一人多队，每个团队角色独立。</p></div><button type="button" class="button" :disabled="busy || !availableTeams(createRows).length" @click="addRow(createRows)">添加团队</button></div><div class="assignment-scroll"><div v-for="(row, index) in createRows" :key="row.key" class="assignment-row"><select v-model="row.teamId" :disabled="busy" :aria-label="'第' + (index + 1) + '个团队'"><option v-for="t in allTeams.filter(t => t.status !== 'ARCHIVED')" :key="t.id" :value="t.id" :disabled="createRows.some((r, i) => i !== index && r.teamId === t.id)">{{ t.identifier }} · {{ t.name }}</option></select><select v-model="row.role" :disabled="busy" aria-label="团队角色"><option v-for="(label, role) in roles" :key="role" :value="role">{{ label }}</option></select><button type="button" class="text-button danger" :disabled="busy" @click="createRows.splice(index, 1)">移除</button></div><div v-if="!createRows.length" class="empty-assign">尚未选择团队。可先建号，稍后分配。</div></div></div><aside class="boundary"><FilledIcon name="shield" :size="27" /><h3>角色不混用</h3><p><b>普通账号</b><br>通过所加入团队访问资源，不能进入系统管理。</p><p><b>超级管理员</b><br>可管理平台账号与团队，并以明确的系统维护身份跨团队访问；不会自动成为团队管理员成员。</p><p>建号与团队分配是两个真实请求。若分配失败，将保留已创建账号，可直接重试分配。</p></aside></div></form><div v-if="createError" class="notice error" role="alert">{{ createError }}</div><template #footer><button class="button" :disabled="busy" @click="createVisible = false">{{ createdAccount ? '保留账号，暂时关闭' : '取消' }}</button><button class="button primary" type="submit" form="create-user-form" :disabled="busy || !userStore.isSuperAdmin">{{ busy ? '提交中…' : createdAccount ? '重试团队分配' : '创建账号并分配' }}</button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" :title="'编辑账号 · ' + (editingUser?.username || '')" width="min(510px, 94vw)" class="system-user-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close><form id="edit-user-form" class="single-form" @submit.prevent="submitEdit"><label>用户名<input :value="editingUser?.username" disabled /></label><label>显示名<input v-model="editForm.displayName" required maxlength="50" :disabled="busy" /></label><label>全局角色<select v-model="editForm.globalRole" :disabled="busy"><option value="USER">普通账号 USER</option><option value="SUPER_ADMIN">超级管理员 SUPER_ADMIN</option></select></label><p class="hint">全局角色不改变已有团队角色。角色变化会撤销该账号的网页会话与 MCP 凭证；不能降级最后一位正常超级管理员。</p></form><div v-if="editError" class="notice error" role="alert">{{ editError }}</div><template #footer><button class="button" :disabled="busy" @click="editVisible = false">取消</button><button class="button primary" type="submit" form="edit-user-form" :disabled="busy">{{ busy ? '保存中…' : '保存修改' }}</button></template></el-dialog>

    <el-dialog v-model="assignVisible" :title="'团队分配 · ' + (assigningUser?.username || '')" width="min(710px, 94vw)" class="system-user-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close><p class="hint">整体替换此账号的所有团队分配。清空列表将取消所有团队成员关系；不会删除平台账号或其创建的团队资源。归档团队现有关系保留且不可改。</p><div v-if="assigningUser?.status === 'DISABLED'" class="notice partial">此账号已停用。不能新增授予团队管理员；若原有管理员角色导致提交受限，请先恢复账号后再调整。</div><div class="assignment-heading"><span>{{ assignRows.length }} 个团队</span><button class="button" :disabled="busy || assignLoading || !assignReady || !availableTeams(assignRows).length" @click="addRow(assignRows)">添加团队</button></div><div v-if="assignLoading" class="empty-assign">正在获取最新团队与成员角色…</div><div v-else class="assignment-scroll"><div v-for="(row, index) in assignRows" :key="row.key" class="assignment-row"><select v-model="row.teamId" :disabled="busy || isFrozen(row)" :aria-label="'第' + (index + 1) + '个团队'"><option v-if="!teamById(row.teamId)" :value="row.teamId">不可用团队 · {{ row.teamId }}</option><option v-for="t in allTeams" :key="t.id" :value="t.id" :disabled="t.status === 'ARCHIVED' || assignRows.some((r, i) => i !== index && r.teamId === t.id)">{{ t.identifier }} · {{ t.name }}{{ t.status === 'ARCHIVED' ? '（已归档）' : '' }}</option></select><select v-model="row.role" :disabled="busy || isFrozen(row)" aria-label="团队独立角色"><option v-for="(label, role) in roles" :key="role" :value="role" :disabled="role === 'TEAM_ADMIN' && assigningUser?.status === 'DISABLED' && row.originalRole !== 'TEAM_ADMIN'">{{ label }}</option></select><button class="text-button danger" :disabled="busy || isFrozen(row)" @click="assignRows.splice(index, 1)">{{ isFrozen(row) ? '已归档' : '移除' }}</button></div><div v-if="!assignRows.length" class="empty-assign">没有团队分配。</div></div><div v-if="assignError" class="notice error" role="alert">{{ assignError }}</div><template #footer><button class="button" :disabled="busy" @click="assignVisible = false">取消</button><button class="button primary" :disabled="busy || assignLoading || !assignReady" @click="submitAssign">{{ busy ? '保存中…' : '保存全部分配' }}</button></template></el-dialog>

    <el-dialog v-model="passwordVisible" :title="'重置密码 · ' + (passwordUser?.username || '')" width="min(510px, 94vw)" class="system-user-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close><form id="reset-password-form" class="single-form" @submit.prevent="submitPassword"><p class="hint">将撤销此账号的全部网页会话和 MCP 凭证。该用户下次登录必须再次修改密码，旧凭证不会恢复。</p><label>新密码<input v-model="newPassword" type="password" required minlength="6" autocomplete="new-password" :disabled="busy" placeholder="不少于6位" /></label></form><div v-if="passwordError" class="notice error" role="alert">{{ passwordError }}</div><template #footer><button class="button" :disabled="busy" @click="passwordVisible = false">取消</button><button class="button primary" type="submit" form="reset-password-form" :disabled="busy">{{ busy ? '提交中…' : '确认重置并撤销凭证' }}</button></template></el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getUsers, createUser, updateUser, assignTeams, resetPassword, updateUserStatus } from '@/api/users'
import { getTeams } from '@/api/teams'
const userStore = useUserStore(), appStore = useAppStore()
const roles = { VIEWER: '只读成员', EDITOR: '可编辑成员', TEAM_ADMIN: '团队管理员' }
const normalizeRole = role => role === 'MEMBER' ? 'EDITOR' : role
const message = err => err?.msg || err?.response?.data?.msg || err?.message || '操作失败，请重试'
const users = ref([]), allTeams = ref([]), loading = ref(false), busy = ref(false), pendingId = ref(null), error = ref('')
const query = ref(''), statusFilter = ref('ALL'), roleFilter = ref('ALL')
const filteredUsers = computed(() => users.value.filter(u => (statusFilter.value === 'ALL' || u.status === statusFilter.value) && (roleFilter.value === 'ALL' || (u.globalRole === 'SUPER_ADMIN' ? 'SUPER_ADMIN' : 'USER') === roleFilter.value) && (u.username + ' ' + u.displayName).toLowerCase().includes(query.value.trim().toLowerCase())))
const teamById = id => allTeams.value.find(t => t.id === id)
const formatDate = value => value ? value.replace('T', ' ').slice(0, 16) : '—'
let loadRevision = 0, rowKey = 0, assignRevision = 0
async function reload() {
  if (!userStore.isSuperAdmin) return
  const revision = ++loadRevision; loading.value = true; error.value = ''
  try { const [accounts, teams] = await Promise.all([getUsers(), getTeams()]); if (revision === loadRevision && userStore.isSuperAdmin) { users.value = accounts || []; allTeams.value = teams || [] } }
  catch (err) { if (revision === loadRevision) error.value = message(err) }
  finally { if (revision === loadRevision) loading.value = false }
}
async function refreshAfterWrite() {
  await reload()
  try { await userStore.refreshIdentity(); await appStore.loadTeams() } catch (err) { error.value = '操作已保存，但身份或团队列表刷新失败：' + message(err) }
}
const createVisible = ref(false), createdAccount = ref(null), createError = ref(''), createRows = ref([])
const createForm = reactive({ username: '', displayName: '', password: '', globalRole: 'USER' })
const availableTeams = rows => allTeams.value.filter(t => t.status !== 'ARCHIVED' && !rows.some(r => r.teamId === t.id))
function addRow(rows) { if (!userStore.isSuperAdmin || busy.value) return; const next = availableTeams(rows)[0]; if (next) rows.push({ key: ++rowKey, teamId: next.id, role: 'EDITOR', originalRole: null }) }
const isFrozen = row => teamById(row.teamId)?.status === 'ARCHIVED'
function prepareTeamRoles(rows) {
  const seen = new Set()
  return rows.map(row => {
    if (!row.teamId || !teamById(row.teamId)) throw new Error('存在不可用团队，请刷新核实后调整。')
    if (seen.has(row.teamId)) throw new Error('同一团队不能重复分配。')
    if (!Object.hasOwn(roles, row.role)) throw new Error('存在未知团队角色，请重新选择。')
    seen.add(row.teamId); return { teamId: row.teamId, role: row.role }
  })
}
function openCreate() {
  if (!userStore.isSuperAdmin || busy.value) return
  if (!createdAccount.value) { Object.assign(createForm, { username: '', displayName: '', password: '', globalRole: 'USER' }); createRows.value = []; createError.value = '' }
  createVisible.value = true
}
async function submitCreate() {
  if (!userStore.isSuperAdmin || busy.value) return
  createError.value = ''
  if (!createdAccount.value && (!createForm.username.trim() || !createForm.displayName.trim() || createForm.password.length < 6 || !['USER', 'SUPER_ADMIN'].includes(createForm.globalRole))) { createError.value = '请填写有效账号信息，密码不少于6位。'; return }
  let teamRoles
  try { teamRoles = prepareTeamRoles(createRows.value) } catch (err) { createError.value = message(err); return }
  busy.value = true
  try {
    if (!createdAccount.value) {
      const account = await createUser({ ...createForm, username: createForm.username.trim(), displayName: createForm.displayName.trim() })
      if (!account?.id) throw new Error('服务器未返回账号 ID，请先刷新账号列表核实，勿重复建号。')
      createdAccount.value = account
      createForm.password = ''
      // Save the returned ID before the second request; failures below must never reissue createUser.
    }
    if (teamRoles.length) await assignTeams(createdAccount.value.id, { teamRoles })
    ElMessage.success(teamRoles.length ? '账号已创建，团队分配已保存' : '账号已创建，尚未分配团队')
    createVisible.value = false; createdAccount.value = null; createRows.value = []; createForm.password = ''
    await refreshAfterWrite()
  } catch (err) {
    createError.value = createdAccount.value ? '账号已创建（' + createdAccount.value.id + '），团队分配未完成：' + message(err) + '。重试仅提交团队分配。' : message(err)
    if (createdAccount.value) await reload()
  } finally { busy.value = false }
}
async function finishWithoutAssignments() {
  if (!createdAccount.value || busy.value) return
  try { await ElMessageBox.confirm('账号「' + createdAccount.value.username + '」已经创建并保留。结束本次分配，可稍后从该账号的「分配团队」继续。', '保留已创建账号', { confirmButtonText: '保留账号，稍后分配', cancelButtonText: '继续当前分配' }) } catch { return }
  createdAccount.value = null; createVisible.value = false; createRows.value = []; createError.value = ''; await reload()
}
const editingUser = ref(null), editVisible = ref(false), editError = ref(''), editForm = reactive({ displayName: '', globalRole: 'USER' })
function openEdit(account) { if (!userStore.isSuperAdmin || busy.value) return; editingUser.value = account; Object.assign(editForm, { displayName: account.displayName, globalRole: account.globalRole === 'SUPER_ADMIN' ? 'SUPER_ADMIN' : 'USER' }); editError.value = ''; editVisible.value = true }
async function submitEdit() {
  if (!userStore.isSuperAdmin || busy.value || !editingUser.value || !editForm.displayName.trim()) return
  const id = editingUser.value.id, payload = { displayName: editForm.displayName.trim(), globalRole: editForm.globalRole }
  if (payload.globalRole !== editingUser.value.globalRole) {
    try { await ElMessageBox.confirm('改变全局角色将撤销该账号的网页会话和 MCP 凭证，不改变团队角色。系统不能失去最后一位正常超级管理员。' + (id === userStore.user?.id ? '你正在修改本人，成功后需重新登录。' : ''), '确认改变全局角色', { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' }) } catch { return }
  }
  if (editingUser.value?.id !== id || !userStore.isSuperAdmin || busy.value) return
  busy.value = true; editError.value = ''
  try { await updateUser(id, payload); ElMessage.success('账号信息已保存'); editVisible.value = false; await refreshAfterWrite() }
  catch (err) { editError.value = message(err) }
  finally { busy.value = false }
}
const assigningUser = ref(null), assignVisible = ref(false), assignRows = ref([]), assignLoading = ref(false), assignReady = ref(false), assignError = ref('')
async function openAssign(account) {
  if (!userStore.isSuperAdmin || busy.value) return
  const revision = ++assignRevision
  assigningUser.value = account; assignRows.value = []; assignError.value = ''; assignReady.value = false; assignLoading.value = true; assignVisible.value = true
  try {
    const [accounts, teams] = await Promise.all([getUsers(), getTeams()])
    if (revision !== assignRevision || !userStore.isSuperAdmin) return
    const latest = accounts.find(u => u.id === account.id)
    if (!latest) throw new Error('账号不存在，请刷新列表。')
    assigningUser.value = latest; allTeams.value = teams
    assignRows.value = (latest.teams || []).map(t => ({ key: ++rowKey, teamId: t.teamId, role: normalizeRole(t.role), originalRole: normalizeRole(t.role) }))
    assignReady.value = true
  } catch (err) { if (revision === assignRevision) assignError.value = message(err) }
  finally { if (revision === assignRevision) assignLoading.value = false }
}
async function submitAssign() {
  if (!userStore.isSuperAdmin || busy.value || !assignReady.value || !assigningUser.value) return
  let teamRoles
  try { teamRoles = prepareTeamRoles(assignRows.value) } catch (err) { assignError.value = message(err); return }
  const id = assigningUser.value.id
  if (!teamRoles.length && assigningUser.value.teams?.length) {
    try { await ElMessageBox.confirm('此操作将取消该账号的所有团队成员关系。资源保留；若涉及最后管理员，服务端会拒绝整个变更。', '清空全部团队分配', { type: 'warning', confirmButtonText: '确认清空分配', cancelButtonText: '取消' }) } catch { return }
  }
  if (assigningUser.value?.id !== id || !userStore.isSuperAdmin || busy.value) return
  busy.value = true; assignError.value = ''
  try { await assignTeams(id, { teamRoles }); ElMessage.success('全部团队分配已保存'); assignVisible.value = false; await refreshAfterWrite() }
  catch (err) { assignError.value = message(err) }
  finally { busy.value = false }
}
const passwordUser = ref(null), passwordVisible = ref(false), newPassword = ref(''), passwordError = ref('')
function openPassword(account) { if (!userStore.isSuperAdmin || busy.value) return; passwordUser.value = account; newPassword.value = ''; passwordError.value = ''; passwordVisible.value = true }
async function submitPassword() {
  if (!userStore.isSuperAdmin || busy.value || !passwordUser.value || newPassword.value.length < 6) return
  busy.value = true; passwordError.value = ''
  try { await resetPassword(passwordUser.value.id, newPassword.value); newPassword.value = ''; passwordVisible.value = false; ElMessage.success('密码已重置，全部旧凭证已撤销；下次登录必须修改密码'); await refreshAfterWrite() }
  catch (err) { passwordError.value = message(err) }
  finally { busy.value = false }
}
async function changeStatus(account) {
  if (!userStore.isSuperAdmin || busy.value) return
  const next = account.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try { await ElMessageBox.confirm(next === 'DISABLED' ? '停用「' + account.username + '」将立即禁止登录并撤销全部网页会话和 MCP 凭证。团队关系、资源与审计记录保留；最后有效管理员不能停用。' + (account.id === userStore.user?.id ? '这是你本人，成功后将退出登录。' : '') : '恢复「' + account.username + '」后可重新登录；旧网页会话与 MCP 凭证仍然失效，不会复活。', next === 'DISABLED' ? '停用账号' : '恢复账号', { type: 'warning', confirmButtonText: next === 'DISABLED' ? '确认停用并撤销凭证' : '确认恢复', cancelButtonText: '取消' }) } catch { return }
  if (!userStore.isSuperAdmin || busy.value) return
  busy.value = true; pendingId.value = account.id; error.value = ''
  try { await updateUserStatus(account.id, next); ElMessage.success(next === 'DISABLED' ? '账号已停用，全部凭证已撤销' : '账号已恢复，旧凭证仍然失效'); await refreshAfterWrite() }
  catch (err) { error.value = message(err) }
  finally { busy.value = false; pendingId.value = null }
}
watch(() => userStore.isSuperAdmin, allowed => { if (allowed) reload(); else { ++loadRevision; ++assignRevision; users.value = []; allTeams.value = []; loading.value = false; createVisible.value = false; editVisible.value = false; assignVisible.value = false; passwordVisible.value = false } }, { immediate: true })
</script>

<style scoped>
.system-users{height:100%;min-height:0;display:flex;flex-direction:column;gap:17px;color:#282d35;font-size:13px}.page-heading,.title-row,.toolbar,.actions,.assignment-heading{display:flex;align-items:center;gap:12px}.page-heading,.assignment-heading{justify-content:space-between}.page-heading h1{font-size:26px;margin:0;font-weight:650;letter-spacing:-.7px}.page-heading p{color:#808a97;margin:7px 0 0;font-size:12px;line-height:1.7}.badge{display:inline-flex;white-space:nowrap;padding:5px 8px;font-size:11px;border-radius:7px}.muted{background:#f1f3f7;color:#7f8998}.blue{background:#edf2fc;color:#3568d4}.green{background:#edf7f2;color:#45876c}.panel{background:#fff;border-radius:17px;padding:20px;min-width:0}.notice{display:flex;align-items:center;gap:10px;padding:13px 16px;border-radius:11px;background:#e9effa;color:#516a93;font-size:12px;line-height:1.8;flex:none}.notice.error{background:#fff1ed;color:#a34737;justify-content:space-between}.notice.partial{background:#fff7e9;color:#946e34;flex-wrap:wrap}.notice.partial>span{flex:1;min-width:200px}.notice code{font-size:11px;overflow-wrap:anywhere}.toolbar{flex-wrap:wrap}.toolbar .search{max-width:330px;min-width:170px;flex:1}.toolbar>select{width:auto;min-width:125px}.toolbar-count{color:#939ca8;font-size:12px;margin-left:auto}.users-panel{min-height:0;flex:1;display:flex;flex-direction:column}.table-scroll{overflow:auto;min-height:100px;flex:1}table{border-collapse:collapse;text-align:left;min-width:1080px;width:100%}th{position:sticky;top:0;z-index:1;background:white;color:#949ca7;font-size:11px;font-weight:400;white-space:nowrap}th,td{padding:14px 12px;border:0}td{font-size:12px;vertical-align:middle}tbody tr:hover{background:#fafbfd}.username{color:#3f4957;white-space:nowrap}.mono{font:11px ui-monospace,SFMono-Regular,monospace}.self{font:10px system-ui;color:#3568d4}.display-name{color:#8c96a5;font-size:11px;margin-top:5px}.first-login{display:block;color:#989fac;font-size:10px;margin-top:6px}.team-roles{display:flex;flex-wrap:wrap;gap:6px;min-width:230px;max-width:340px}.team-role{padding:5px 7px;background:#f5f7fa;border-radius:6px;font-size:10px;color:#7d8796;line-height:1.7}.team-role b{color:#59749c;font-weight:500}.team-role small{font-size:9px;color:#9ba2ad}.quiet{color:#909aa7;font-size:11px}.date-cell{min-width:130px;white-space:nowrap}.actions-cell{position:sticky;right:0;background:white;min-width:240px}.actions{gap:14px;white-space:nowrap}.table-empty{text-align:center;color:#909aa7;padding:45px}.protection{display:flex;align-items:flex-start;gap:10px;font-size:11px;color:#8a95a5;line-height:1.8;padding:14px 8px 0}.button{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:0;background:#f1f4f8;color:#596576;border-radius:9px;padding:10px 14px;font:inherit;font-size:12px;white-space:nowrap;cursor:pointer}.button.primary{background:#1c1e22;color:white}.button:hover:not(:disabled){background:#e6ebf3}.button.primary:hover:not(:disabled){background:#34383f}.button:disabled,.text-button:disabled{opacity:.45;cursor:not-allowed}.text-button{padding:4px 0;border:0;background:none;color:#3568d4;font:inherit;font-size:12px;cursor:pointer;white-space:nowrap}.text-button.danger{color:#bd6556}.create-layout{display:grid;grid-template-columns:minmax(0,1fr) 220px;gap:25px}.form-title{font-size:14px;font-weight:600;color:#475366;margin:4px 0 15px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}label{display:flex;flex-direction:column;gap:8px;color:#5b6779;font-size:12px}label em{font-style:normal;color:#bd6a5b}label small,.hint{font-size:11px;color:#8e99a8;line-height:1.8}.single-form{display:grid;gap:17px}.single-form .hint{margin:0}.assignment-heading{margin:23px 0 12px;font-size:12px;color:#7b8799}.assignment-heading h2{margin:0}.assignment-heading p{font-size:11px;color:#98a1ae;margin:7px 0 0}.assignment-scroll{max-height:300px;overflow:auto;min-height:50px}.assignment-row{display:grid;grid-template-columns:minmax(160px,1fr) 145px 40px;gap:9px;align-items:center;padding:8px 0}.empty-assign{padding:22px 10px;font-size:12px;color:#98a2b1;text-align:center}.boundary{background:#f5f7fa;padding:20px;border-radius:12px;color:#8693a5;font-size:12px;line-height:1.85}.boundary h3{color:#4a5a70;font-size:16px;font-weight:500;margin:12px 0 20px}.boundary b{font-weight:500;color:#5d708e}.boundary p{margin:17px 0}.empty{min-height:240px;display:grid;place-content:center;justify-items:center;text-align:center;gap:12px;color:#8b96a6}.empty h2{margin:0;font-size:17px;color:#506074}.empty p{margin:0;font-size:12px}input,select{box-sizing:border-box;width:100%;padding:10px 11px;border:1px solid transparent;background:#f5f6f9;border-radius:8px;outline:none;color:#465265;font:inherit;font-size:12px}input:focus,select:focus{border-color:#96b0ec}input:disabled,select:disabled{color:#a0a8b5;cursor:not-allowed}:global(.system-user-dialog){box-shadow:none!important;border-radius:16px}:global(.system-user-dialog .el-dialog__footer){display:flex;justify-content:flex-end;gap:8px}
@media(max-width:850px){.create-layout{grid-template-columns:1fr}.boundary{display:none}.page-heading h1{font-size:23px}}@media(max-width:620px){.page-heading{align-items:flex-start}.title-row{flex-wrap:wrap;gap:7px}.form-grid{grid-template-columns:1fr}.assignment-row{grid-template-columns:minmax(130px,1fr) 120px 35px;gap:6px}.notice{align-items:flex-start}.panel{padding:14px}.actions-cell{position:static}.users-panel{min-height:380px}.toolbar>select{flex:1}.toolbar .search{max-width:none;flex-basis:100%}}
</style>
