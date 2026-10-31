<template>
  <div class="system-teams">
    <header class="page-heading"><div><div class="title-row"><h1>团队管理</h1><span class="badge muted">系统级操作</span></div><p>管理全部团队 · 创建、指派首位管理员与归档恢复</p></div><button v-if="userStore.isSuperAdmin" class="button primary" :disabled="busy" @click="openCreate"><FilledIcon name="team" :size="15" /> 新建团队</button></header>
    <div v-if="!userStore.isSuperAdmin" class="panel empty"><FilledIcon name="shield" :size="32" /><h2>仅超级管理员可访问</h2><p>团队管理员请进入本团队的「团队设置」维护团队信息和成员。</p></div>
    <template v-else>
      <div class="notice"><FilledIcon name="shield" :size="18" /><span><b>系统管理与团队成员身份独立。</b> 超级管理员可以跨团队维护，但不自动成为团队成员，也不替代真实有效团队管理员。</span></div>
      <div v-if="error" class="notice error" role="alert">{{ error }}<button class="text-button" :disabled="loading" @click="reload">重新加载</button></div>
      <div class="toolbar"><label class="search"><input v-model="query" placeholder="搜索团队名称或标识" aria-label="搜索团队" /></label><select v-model="statusFilter" aria-label="筛选团队状态"><option value="ALL">全部状态</option><option value="ACTIVE">正常团队</option><option value="ARCHIVED">已归档</option></select><span class="toolbar-count">{{ filteredTeams.length }} 个团队</span><button class="text-button" :disabled="loading || busy" @click="reload">刷新</button></div>
      <div class="cards-scroll" :aria-busy="loading"><div v-if="loading" class="empty">正在加载团队…</div><div v-else-if="!filteredTeams.length" class="panel empty"><FilledIcon name="team" :size="30" /><h2>{{ teams.length ? '没有匹配的团队' : '尚未创建团队' }}</h2><p>创建团队时必须同时指派首位正常账号作为团队管理员。</p></div><div v-else class="team-grid"><article v-for="team in filteredTeams" :key="team.id" class="panel team-card"><div class="card-heading"><span class="team-mark" :title="team.identifier" :style="{ color: team.color || '#3568d4' }">{{ team.identifier }}</span><span class="badge" :class="team.status === 'ARCHIVED' ? 'muted' : 'green'">{{ team.status === 'ARCHIVED' ? '已归档' : '正常' }}</span></div><h2 :title="team.name">{{ team.name }}</h2><p class="description">{{ team.description || '暂无团队描述' }}</p><div class="stats"><div><b>{{ team.memberCount ?? '—' }}</b><span>成员</span></div><div><b>{{ team.apiCount ?? '—' }}</b><span>接口</span></div><div><b>{{ team.effectiveAdminCount ?? '—' }}</b><span>有效管理员</span></div></div><div v-if="team.adminRepairRequired || team.effectiveAdminCount === 0" class="repair"><FilledIcon name="shield" :size="16" /><span>缺少有效管理员。{{ team.status === 'ARCHIVED' ? '先恢复团队，再进入维护指派正常账号。' : '请进入团队维护，明确指派正常账号；不会随机提权。' }}</span></div><p v-else-if="team.status === 'ARCHIVED'" class="archive-note">保留标识与资源，管理写入已冻结；Mock 不会自动停服。</p><div class="card-actions"><button class="button primary" :disabled="busy" @click="enterTeam(team)">维护团队</button><button class="text-button" :disabled="busy || team.status === 'ARCHIVED'" @click="openEdit(team)">编辑信息</button><button class="text-button" :disabled="busy" @click="changeStatus(team)">{{ pendingTeamId === team.id ? '处理中…' : team.status === 'ARCHIVED' ? '恢复团队' : '归档' }}</button></div></article></div></div>
      <footer class="page-foot">归档不释放团队标识，不删除成员与资源，不自动停止 Mock 服务。团队不提供物理删除入口。</footer>
    </template>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑团队信息' : '新建团队'" width="min(800px, 94vw)" class="system-team-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" destroy-on-close>
      <div class="create-layout"><form id="system-team-form" class="team-form" @submit.prevent="submit"><label>团队名称 <em>*</em><input v-model="form.name" required maxlength="50" :disabled="busy" placeholder="团队展示名称" /></label><label>团队标识 <em>*</em><input v-model="form.identifier" required pattern="[A-Z0-9]{2,8}" minlength="2" maxlength="8" :disabled="busy || !!editingId" placeholder="2~8 位大写字母或数字" @input="form.identifier = form.identifier.toUpperCase().replace(/[^A-Z0-9]/g, '')" /><small>决定 Mock 地址前缀，创建后不可修改；归档后仍保留。</small></label><label class="wide">团队描述<textarea v-model="form.description" rows="3" maxlength="1000" :disabled="busy" placeholder="一句话说明团队负责什么"></textarea></label><label>团队颜色<input v-model="form.color" type="color" :disabled="busy" /></label><div v-if="editingId" class="wide team-admins"><div class="team-admins__head"><b>团队管理员</b><small>本团队至少保留一位正常管理员；成员与角色的其余调整在「团队设置」里完成。</small></div><p v-if="memberLoading" class="team-admins__quiet">正在加载成员…</p><ul v-else-if="teamAdmins.length" class="team-admins__list"><li v-for="member in teamAdmins" :key="member.userId"><span class="team-admins__monogram" aria-hidden="true">{{ (member.displayName || member.username || '?').slice(0, 1).toUpperCase() }}</span><span class="team-admins__identity"><b>{{ member.displayName || member.username }}</b><small class="mono">{{ member.username }}</small></span><span v-if="member.status === 'DISABLED'" class="badge">已停用</span><span v-else class="badge green">正常账号</span><button type="button" class="team-admins__action" :disabled="memberBusy || (member.status !== 'DISABLED' && activeAdminCount <= 1)" :title="member.status !== 'DISABLED' && activeAdminCount <= 1 ? '最后一位正常管理员不能撤销' : '改为可编辑成员'" @click="removeTeamAdmin(member)">撤销管理员</button></li></ul><p v-else class="team-admins__quiet">当前没有管理员，请先添加一位。</p><UserPicker :model-value="adminToAdd" :users="adminAddCandidates" :loading="adminLoading" :disabled="memberBusy" aria-label="添加团队管理员" placeholder="搜索账号，选中后设为管理员" @update:model-value="addTeamAdmin" /><small>选中的账号若还不在本团队，会同时加入团队并设为管理员。</small></div><div v-if="!editingId" class="wide admin-search"><label for="initial-admin">首位团队管理员 <em>*</em></label><UserPicker id="initial-admin" :model-value="selectedAdmin" :users="adminCandidates" :loading="adminLoading" :disabled="busy" aria-label="选择首位团队管理员" placeholder="输入显示名或用户名筛选" @update:model-value="onAdminPicked" /><div class="admin-result" aria-live="polite"><span v-if="!selectedAdmin">{{ adminHint }}</span></div><small>从账号列表中选择一位正常账号；创建团队与首位管理员在同一事务内完成。</small></div></form><aside class="preview"><FilledIcon name="team" :size="28" /><h3>{{ form.name || '团队名称' }}</h3><span class="identifier">{{ form.identifier || 'TEAM' }}</span><p>{{ form.description || '团队资源与权限独立，成员在各团队中的角色互不影响。' }}</p><hr /><b>{{ editingId ? '团队信息维护' : '创建后立即启用' }}</b><p>{{ editingId ? '不会修改团队标识、状态或成员角色。' : '首位管理员可以继续添加已有账号。创建者不会自动获得额外团队角色或资源所有权。' }}</p></aside></div><div v-if="dialogError" class="notice error" role="alert">{{ dialogError }}</div><template #footer><button class="button" :disabled="busy" @click="dialogVisible = false">取消</button><button class="button primary" type="submit" form="system-team-form" :disabled="busy || (!editingId && !selectedAdmin)">{{ busy ? '保存中…' : editingId ? '保存修改' : '创建并指派管理员' }}</button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getTeams, createTeam, updateTeam, updateTeamStatus, getTeamMembers, addTeamMember, updateMemberRole } from '@/api/teams'
import { getUsers } from '@/api/users'
import UserPicker from '@/components/UserPicker.vue'
const userStore = useUserStore(), appStore = useAppStore(), router = useRouter()
const teams = ref([]), loading = ref(false), busy = ref(false), error = ref(''), query = ref(''), statusFilter = ref('ALL'), pendingTeamId = ref(null)
const filteredTeams = computed(() => teams.value.filter(t => (statusFilter.value === 'ALL' || t.status === statusFilter.value) && (t.name + ' ' + t.identifier).toLowerCase().includes(query.value.trim().toLowerCase())))
const dialogVisible = ref(false), editingId = ref(null), dialogError = ref(''), selectedAdmin = ref(null), adminCandidates = ref([]), adminLoading = ref(false), adminHint = ref('搜索并选中一位正常账号作为首位团队管理员。')
// Administrators are editable from this dialog; fine-grained membership stays in team settings.
const editMembers = ref([]), memberLoading = ref(false), memberBusy = ref(false), adminToAdd = ref(null)
const roleText = role => ({ VIEWER: '只读成员', EDITOR: '可编辑成员', TEAM_ADMIN: '团队管理员' })[String(role || '').toUpperCase()] || '成员'
const teamAdmins = computed(() => editMembers.value.filter(member => String(member.role || '').toUpperCase() === 'TEAM_ADMIN'))
const activeAdminCount = computed(() => teamAdmins.value.filter(member => member.status !== 'DISABLED').length)
const adminAddCandidates = computed(() => {
  const taken = new Set(teamAdmins.value.map(member => member.userId))
  return adminCandidates.value.filter(user => !taken.has(user.id))
})
const form = reactive({ name: '', identifier: '', description: '', color: '#3568d4' })
const message = err => err?.msg || err?.response?.data?.msg || err?.message || '操作失败，请重试'
let loadRevision = 0, adminRevision = 0
// Candidates are loaded once per dialog open; the picker filters them locally.
async function loadAdminCandidates() {
  if (!userStore.isSuperAdmin) return
  const revision = ++adminRevision
  adminLoading.value = true; adminHint.value = ''
  try {
    const users = await getUsers()
    if (revision === adminRevision) adminCandidates.value = users.map(u => ({ id: u.id, username: u.username, displayName: u.displayName || u.username, status: u.status, globalRole: u.globalRole }))
  } catch (err) {
    if (revision === adminRevision) adminHint.value = message(err)
  } finally {
    if (revision === adminRevision) adminLoading.value = false
  }
}
async function reload() {
  if (!userStore.isSuperAdmin) return
  const revision = ++loadRevision; loading.value = true; error.value = ''
  try { const data = await getTeams(); if (revision === loadRevision && userStore.isSuperAdmin) teams.value = data || [] }
  catch (err) { if (revision === loadRevision) error.value = message(err) }
  finally { if (revision === loadRevision) loading.value = false }
}
watch(() => userStore.isSuperAdmin, allowed => { if (allowed) reload(); else { ++loadRevision; teams.value = []; loading.value = false; dialogVisible.value = false } }, { immediate: true })
function resetAdmin() { ++adminRevision; selectedAdmin.value = null; adminCandidates.value = []; adminLoading.value = false; adminHint.value = '搜索并选中一位正常账号作为首位团队管理员。'; dialogError.value = '' }
function openCreate() {
  if (!userStore.isSuperAdmin || busy.value) return
  editingId.value = null; Object.assign(form, { name: '', identifier: '', description: '', color: '#3568d4' }); resetAdmin(); dialogVisible.value = true
  loadAdminCandidates()
}
function openEdit(team) {
  if (!userStore.isSuperAdmin || busy.value || team.status === 'ARCHIVED') return
  editingId.value = team.id; Object.assign(form, { name: team.name, identifier: team.identifier, description: team.description || '', color: team.color || '#3568d4' }); dialogError.value = ''; dialogVisible.value = true
  // The dialog also manages administrators, so it needs the current member list.
  adminToAdd.value = null; editMembers.value = []; loadEditMembers(team.id); loadAdminCandidates()
}
function onAdminPicked(user) {
  selectedAdmin.value = user
  adminHint.value = ''
  dialogError.value = ''
}

async function loadEditMembers(teamId) {
  if (!userStore.isSuperAdmin || !teamId) return
  memberLoading.value = true
  try {
    const members = await getTeamMembers(teamId)
    if (editingId.value === teamId) editMembers.value = members || []
  } catch (err) {
    if (editingId.value === teamId) dialogError.value = message(err)
  } finally {
    memberLoading.value = false
  }
}

async function addTeamAdmin(user) {
  if (!user || memberBusy.value || !editingId.value) return
  const teamId = editingId.value
  memberBusy.value = true; dialogError.value = ''
  try {
    const existing = editMembers.value.find(member => member.userId === user.id)
    if (existing) await updateMemberRole(teamId, user.id, { role: 'TEAM_ADMIN' })
    else await addTeamMember(teamId, { userId: user.id, role: 'TEAM_ADMIN' })
    ElMessage.success(existing ? '已设为团队管理员' : '已加入团队并设为管理员')
    await loadEditMembers(teamId); await reload()
  } catch (err) { dialogError.value = message(err) }
  finally { memberBusy.value = false; adminToAdd.value = null }
}

async function removeTeamAdmin(member) {
  if (memberBusy.value || !editingId.value) return
  if (member.status !== 'DISABLED' && activeAdminCount.value <= 1) return
  const teamId = editingId.value
  try {
    await ElMessageBox.confirm('将「' + (member.displayName || member.username) + '」改为可编辑成员？本团队必须保留至少一位正常管理员。', '撤销团队管理员', { confirmButtonText: '改为可编辑成员', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  memberBusy.value = true; dialogError.value = ''
  try {
    await updateMemberRole(teamId, member.userId, { role: 'EDITOR' })
    ElMessage.success('已改为可编辑成员')
    await loadEditMembers(teamId); await reload()
  } catch (err) { dialogError.value = message(err) }
  finally { memberBusy.value = false }
}
async function refreshAfterWrite() {
  await reload()
  try { await userStore.refreshIdentity(); await appStore.loadTeams() } catch (err) { error.value = '操作已保存，但身份或团队列表刷新失败：' + message(err) }
}
async function submit() {
  if (!userStore.isSuperAdmin || busy.value || !form.name.trim()) return
  dialogError.value = ''
  if (!editingId.value && (!/^[A-Z0-9]{2,8}$/.test(form.identifier) || !selectedAdmin.value || selectedAdmin.value.status !== 'ACTIVE')) { dialogError.value = '请填写有效团队标识，并从列表中选中首位正常管理员。'; return }
  if (!editingId.value && teams.value.some(t => t.identifier.toUpperCase() === form.identifier)) { dialogError.value = '此团队标识已被使用（含已归档团队）。'; return }
  busy.value = true
  try {
    const payload = { name: form.name.trim(), description: form.description, color: form.color }
    if (editingId.value) await updateTeam(editingId.value, payload)
    else await createTeam({ ...payload, identifier: form.identifier, initialAdminUserId: selectedAdmin.value.id })
    ElMessage.success(editingId.value ? '团队信息已保存' : '团队已创建并指派首位管理员'); dialogVisible.value = false
    await refreshAfterWrite()
  } catch (err) { dialogError.value = message(err) }
  finally { busy.value = false }
}
async function changeStatus(team) {
  if (!userStore.isSuperAdmin || busy.value) return
  const next = team.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED'
  try { await ElMessageBox.confirm(next === 'ARCHIVED' ? '归档「' + team.name + '」将冻结团队管理写入。成员、资源和标识均保留，不会自动停止 Mock 服务。' : '恢复「' + team.name + '」后将按原有成员角色开放管理写入，资源与标识不变。', next === 'ARCHIVED' ? '归档团队' : '恢复团队', { type: 'warning', confirmButtonText: next === 'ARCHIVED' ? '确认归档' : '确认恢复', cancelButtonText: '取消' }) } catch { return }
  if (!userStore.isSuperAdmin || busy.value) return
  busy.value = true; pendingTeamId.value = team.id; error.value = ''
  try { await updateTeamStatus(team.id, next); ElMessage.success(next === 'ARCHIVED' ? '团队已归档；Mock 服务不会自动停止' : '团队已恢复'); await refreshAfterWrite() }
  catch (err) { error.value = message(err) }
  finally { busy.value = false; pendingTeamId.value = null }
}
async function enterTeam(team) {
  if (!userStore.isSuperAdmin || busy.value) return
  busy.value = true; error.value = ''
  try { await appStore.loadTeams(); appStore.setFilter(team.id); await router.push('/team') }
  catch (err) { error.value = message(err) }
  finally { busy.value = false }
}
</script>

<style scoped>
.system-teams{height:100%;min-height:0;display:flex;flex-direction:column;gap:16px;color:var(--app-text-primary);font-size:13px}.page-heading,.title-row,.toolbar,.card-heading,.card-actions,.search-row{display:flex;align-items:center;gap:12px}.page-heading,.card-heading{justify-content:space-between}.page-heading h1{font-size:26px;letter-spacing:-.7px;margin:0;font-weight:650}.page-heading p,.description{color:var(--app-text-secondary);line-height:1.75;margin:6px 0 0}.panel{background:var(--app-bg-surface);border-radius:var(--app-radius-panel);padding:20px;min-width:0}.notice{display:flex;align-items:center;gap:11px;background:var(--app-bg-selected);color:var(--app-text-accent);border-radius:11px;padding:13px 16px;font-size:12px;line-height:1.75}.notice.error{background:var(--el-color-danger-light-9);color:var(--app-text-danger);justify-content:space-between}.badge{padding:5px 8px;font-size:12px;border-radius:7px;white-space:nowrap}.green{background:var(--el-color-success-light-9);color:var(--app-text-success)}.muted{background:var(--app-bg-control);color:var(--app-text-secondary)}.toolbar{flex-wrap:wrap}.search{flex:1;min-width:170px;max-width:340px}.toolbar>select{width:auto;min-width:120px}.toolbar-count{margin-left:auto;color:var(--app-text-secondary);font-size:12px}.cards-scroll{overflow:auto;flex:1;min-height:0;padding:1px}.team-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:16px}.team-card{display:flex;flex-direction:column;min-height:300px}.team-mark{padding:9px 11px;border-radius:9px;background:var(--app-bg-control);font:12px var(--app-font-mono);max-width:150px;overflow:hidden;text-overflow:ellipsis}.team-card h2{font-size:17px;font-weight:600;margin:19px 0 0}.description{font-size:12px;min-height:42px;overflow-wrap:anywhere}.stats{display:flex;gap:30px;padding:20px 0}.stats div{display:flex;flex-direction:column;gap:4px}.stats b{font-size:24px;font-weight:600}.stats span{font-size:12px;color:var(--app-text-secondary)}.repair{display:flex;gap:8px;padding:12px;border-radius:8px;background:var(--el-color-warning-light-9);color:var(--app-text-warning);font-size:12px;line-height:1.7;margin-bottom:15px}.archive-note{color:var(--app-text-secondary);font-size:12px;line-height:1.7;margin:0 0 14px}.card-actions{padding-top:16px;border-top:1px solid var(--app-divider);flex-wrap:wrap;margin-top:auto;gap:12px}.button{display:inline-flex;align-items:center;justify-content:center;gap:7px;background:var(--app-bg-control);color:var(--app-text-regular);border:0;border-radius:9px;padding:7px 14px;min-height:33px;font:inherit;font-size:13px;cursor:pointer;white-space:nowrap}.button.primary{background:var(--app-primary);color:var(--app-text-inverse)}.button:hover:not(:disabled){background:var(--app-bg-hover)}.button.primary:hover:not(:disabled){background:var(--app-primary-hover)}.button:disabled,.text-button:disabled{opacity:.45;cursor:not-allowed}.text-button{border:0;background:none;color:var(--app-text-accent);cursor:pointer;font:inherit;font-size:13px;padding:7px 0;min-height:33px}.page-foot{color:var(--app-text-secondary);font-size:12px;line-height:1.8}.empty{min-height:230px;display:grid;place-content:center;justify-items:center;color:var(--app-text-secondary);text-align:center;gap:10px}.empty h2{font-size:17px;color:var(--app-text-primary);margin:0}.empty p{margin:0;font-size:12px}.create-layout{display:grid;grid-template-columns:minmax(0,1fr) 225px;gap:24px}.team-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;align-content:start}.team-form label{font-size:13px;color:var(--app-text-regular);display:grid;grid-template-columns:max-content 1fr;align-items:center;column-gap:4px;row-gap:8px}
.team-form label>input,.team-form label>select,.team-form label>textarea,.team-form label>small{grid-column:1/-1}.wide{grid-column:1/-1}.team-form em{font-style:normal;color:var(--app-text-danger)}.team-form small{font-size:12px;color:var(--app-text-secondary);font-weight:400;line-height:1.7}input,select,textarea{box-sizing:border-box;width:100%;border:1px solid transparent;border-radius:8px;background:var(--app-bg-control);padding:10px 11px;color:var(--app-text-regular);outline:none;font:inherit;font-size:13px}input:focus,textarea:focus,select:focus{border-color:var(--app-focus-color)}input:disabled{color:var(--app-text-disabled);cursor:not-allowed}input[type=color]{width:65px;height:38px;padding:4px}.admin-search>label{margin-bottom:8px}.admin-result{display:flex;gap:9px;flex-wrap:wrap;align-items:center;min-height:52px;padding:12px;background:var(--app-bg-control);border-radius:9px;margin:13px 0;color:var(--app-text-secondary);font-size:12px}.mono,.identifier{font-family:var(--app-font-mono);font-size:12px}.preview{padding:22px;background:var(--app-bg-control);border-radius:12px;line-height:1.8;color:var(--app-text-secondary);font-size:12px}.preview h3{font-size:17px;font-weight:500;color:var(--app-text-primary);margin:13px 0}.preview .identifier{background:var(--app-bg-selected);color:var(--app-text-accent);padding:6px 9px;border-radius:6px}.preview p{margin:18px 0;overflow-wrap:anywhere}.preview hr{border:0;border-top:1px solid var(--app-divider);margin:23px 0}.preview b{font-weight:500;color:var(--app-text-regular)}:global(.system-team-dialog){box-shadow:none!important;border-radius:16px}:global(.system-team-dialog .el-dialog__footer){display:flex;justify-content:flex-end;gap:8px}
@media(max-width:900px){.team-grid{grid-template-columns:repeat(auto-fill,minmax(250px,1fr))}.create-layout{grid-template-columns:1fr}.preview{display:none}}@media(max-width:620px){.page-heading{align-items:flex-start;gap:10px}.title-row{flex-wrap:wrap;gap:7px}.page-heading h1{font-size:22px}.page-heading p{font-size:12px}.team-form{grid-template-columns:1fr}.notice{align-items:flex-start}.team-grid{grid-template-columns:1fr}}
.team-admins{display:grid;gap:10px;padding:14px;background:var(--app-bg-control);border-radius:10px}
.team-admins__head{display:grid;gap:4px}
.team-admins__head b{font-size:13px;font-weight:600;color:var(--app-text-primary)}
.team-admins__head small,.team-admins>small{font-size:var(--app-font-caption);line-height:1.6;color:var(--app-text-secondary)}
.team-admins__quiet{margin:0;font-size:var(--app-font-caption);color:var(--app-text-secondary)}
.team-admins__list{list-style:none;margin:0;padding:0;display:grid;gap:2px}
.team-admins__list li{display:flex;align-items:center;gap:10px;min-height:40px;padding:4px 6px;border-radius:8px;background:var(--app-bg-surface)}
.team-admins__monogram{width:26px;height:26px;flex:0 0 26px;display:grid;place-items:center;border-radius:8px;background:var(--app-bg-hover);color:var(--app-text-regular);font-size:var(--app-font-micro);font-weight:600}
.team-admins__identity{display:grid;min-width:0;line-height:1.35;margin-right:auto}
.team-admins__identity b{font-size:13px;font-weight:500;color:var(--app-text-primary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.team-admins__identity small{font-size:var(--app-font-caption);color:var(--app-text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.team-admins__action{flex:0 0 auto;min-height:28px;padding:0 10px;border:1px solid var(--app-divider);border-radius:7px;background:var(--app-bg-surface);color:var(--app-text-secondary);font-size:var(--app-font-caption);cursor:pointer}
.team-admins__action:hover:not(:disabled){border-color:var(--app-text-danger);color:var(--app-text-danger)}
.team-admins__action:disabled{color:var(--app-text-disabled);cursor:not-allowed}
</style>
