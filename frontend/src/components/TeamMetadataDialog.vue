<template>
  <el-dialog :model-value="modelValue" :title="'管理共享' + kindLabel" width="min(560px, 95vw)" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" @update:model-value="emit('update:modelValue', $event)">
    <p class="metadata-note">{{ teamName }} · {{ kind === 'groups' ? '分组用于整理接口，不影响团队内的协作权限。' : '标签对整个团队共享，不是个人私有标记。' }}</p>
    <div v-if="!canWrite" class="metadata-readonly">当前为只读或归档上下文，仅可查看共享{{ kindLabel }}。</div>
    <div v-if="error" class="metadata-error" role="alert">{{ error }}</div>
    <div class="metadata-list" v-loading="loading">
      <draggable v-if="kind === 'groups' && rows.length" v-model="rows" item-key="id" handle=".metadata-handle" :disabled="!canWrite || busy" :animation="120" @end="saveOrder"><template #item="{ element: row }"><div class="metadata-row"><button type="button" class="metadata-handle" :disabled="!canWrite || busy" aria-label="拖动分组排序">⠿</button><template v-if="editingId === row.id"><el-input v-model="editingName" maxlength="30" aria-label="分组名称" @keyup.enter="saveEdit(row)" /><el-button text :disabled="busy" @click="saveEdit(row)">保存</el-button><el-button text :disabled="busy" @click="cancelEdit">取消</el-button></template><template v-else><div class="metadata-row__copy"><b>{{ row.name }}</b><span>{{ row.apiCount ?? 0 }} 个活跃接口</span></div><el-button text :disabled="!canWrite || busy" @click="startEdit(row)">重命名</el-button><el-button text type="danger" :disabled="!canWrite || busy" @click="prepareDelete(row)">删除</el-button></template></div></template></draggable>
      <template v-else-if="kind === 'tags'"><div v-for="row in rows" :key="row.id" class="metadata-row"><span class="metadata-dot" :style="{ backgroundColor: row.color }" /><template v-if="editingId === row.id"><el-input v-model="editingName" maxlength="30" aria-label="标签名称" @keyup.enter="saveEdit(row)" /><input v-model="editingColor" type="color" class="metadata-color" aria-label="标签颜色" :disabled="busy" /><el-button text :disabled="busy" @click="saveEdit(row)">保存</el-button><el-button text :disabled="busy" @click="cancelEdit">取消</el-button></template><template v-else><div class="metadata-row__copy"><b>{{ row.name }}</b><span>团队共享标签</span></div><el-button text :disabled="!canWrite || busy" @click="startEdit(row)">编辑</el-button><el-button text type="danger" :disabled="!canWrite || busy" @click="prepareDelete(row)">删除</el-button></template></div></template>
      <p v-if="!loading && !rows.length" class="metadata-empty">暂无共享{{ kindLabel }}{{ canWrite ? '，可以在下方创建。' : '。' }}</p>
    </div>
    <div v-if="canWrite" class="metadata-create"><el-input v-model="newName" :placeholder="'新' + kindLabel + '名称'" :aria-label="'新' + kindLabel + '名称'" maxlength="30" :disabled="busy" @keyup.enter="create" /><input v-if="kind === 'tags'" v-model="newColor" type="color" class="metadata-color" aria-label="新标签颜色" :disabled="busy" /><el-button type="primary" :disabled="!newName.trim() || busy" @click="create">新建{{ kindLabel }}</el-button></div>
    <p class="metadata-note metadata-note--bottom">共享{{ kindLabel }}变更会推进涉及接口的版本（包括回收站）。保存后请重新加载旧编辑版本。</p>
    <template #footer><el-button :disabled="busy" @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
  <el-dialog v-model="deleteOpen" :title="'删除共享' + kindLabel" width="min(440px, 94vw)" :close-on-click-modal="false" append-to-body>
    <p class="metadata-confirm">确认删除「{{ frozenDelete?.name }}」？{{ kind === 'groups' ? '关联接口会变为未分组，接口本身不会删除。' : '当前团队接口的该标签关联会被移除，接口本身不会删除。' }}回收站与历史恢复会按当前可用引用处理。</p>
    <template #footer><el-button :disabled="busy" @click="deleteOpen = false">取消</el-button><el-button type="danger" :loading="busy" :disabled="!canWrite" @click="confirmDelete">确认删除</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getGroups, createGroup, updateGroup, deleteGroup } from '@/api/groups'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tags'
import { readableError } from '@/utils/api-lifecycle'
const props = defineProps({ modelValue: Boolean, teamId: { type: String, default: '' }, kind: { type: String, default: 'groups' } })
const emit = defineEmits(['update:modelValue', 'changed'])
const user = useUserStore(), app = useAppStore(), rows = ref([]), loading = ref(false), busy = ref(false), error = ref(''), editingId = ref(''), editingName = ref(''), editingColor = ref('#3568d4'), newName = ref(''), newColor = ref('#3568d4'), deleteOpen = ref(false), frozenDelete = ref(null)
let generation = 0, request = 0
const kindLabel = computed(() => props.kind === 'tags' ? '标签' : '分组')
const teamName = computed(() => app.teams.find(team => team.id === props.teamId)?.name || '当前团队')
const canRead = computed(() => !!props.teamId && props.teamId === app.currentTeamId && user.hasCapability(props.teamId, 'read'))
const canWrite = computed(() => canRead.value && app.currentTeam?.status !== 'ARCHIVED' && user.hasCapability(props.teamId, 'write'))
const key = computed(() => [props.teamId, props.kind, app.currentTeamId, app.currentTeam?.status, user.user?.id, user.token, user.identityReady, canRead.value, canWrite.value].join('|'))
const api = computed(() => props.kind === 'tags' ? { get: getTags, create: createTag, update: updateTag, delete: deleteTag } : { get: getGroups, create: createGroup, update: updateGroup, delete: deleteGroup })
function scope() { return { generation, key: key.value, teamId: props.teamId } }
function live(captured, write = false) { return props.modelValue && captured.generation === generation && captured.key === key.value && (write ? canWrite.value : canRead.value) }
function clear() { generation++; request++; rows.value = []; loading.value = false; busy.value = false; error.value = ''; cancelEdit(); newName.value = ''; newColor.value = '#3568d4'; deleteOpen.value = false; frozenDelete.value = null }
watch([() => props.modelValue, key], () => { clear(); if (props.modelValue && canRead.value) load(); else if (props.modelValue) emit('update:modelValue', false) }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
async function load() {
  if (!canRead.value || !props.modelValue) return
  const captured = scope(), id = ++request; loading.value = true
  try { const list = await api.value.get(captured.teamId); if (!live(captured) || id !== request) return; if (!Array.isArray(list) || list.some(row => row.teamId !== captured.teamId)) throw new Error('共享配置团队范围不匹配'); rows.value = list }
  catch (failure) { if (live(captured) && id === request) { error.value = readableError(failure); rows.value = [] } }
  finally { if (live(captured) && id === request) loading.value = false }
}
function cancelEdit() { editingId.value = ''; editingName.value = ''; editingColor.value = '#3568d4' }
function startEdit(row) { if (!canWrite.value || busy.value) return; editingId.value = row.id; editingName.value = row.name; editingColor.value = /^#[0-9a-f]{6}$/i.test(row.color || '') ? row.color : '#3568d4' }
async function mutate(work, message) {
  if (!canWrite.value || busy.value) return
  const captured = scope(); busy.value = true; error.value = ''
  try { await work(captured); if (!live(captured, true)) return; ElMessage.success(message); cancelEdit(); newName.value = ''; await load(); if (live(captured)) emit('changed', { teamId: captured.teamId, kind: props.kind }) }
  catch (failure) { if (live(captured)) error.value = readableError(failure) }
  finally { if (live(captured)) busy.value = false }
}
function create() {
  const name = newName.value.trim(); if (!name || !canWrite.value) return
  if (rows.value.some(row => row.name === name)) { error.value = '已存在同名' + kindLabel.value; return }
  return mutate(captured => api.value.create({ teamId: captured.teamId, name, ...(props.kind === 'tags' ? { color: newColor.value } : { sortOrder: Math.max(0, ...rows.value.map(row => row.sortOrder || 0)) + 1 }) }), kindLabel.value + '已创建')
}
function saveEdit(row) {
  const name = editingName.value.trim(); if (!name || !canWrite.value || row.teamId !== props.teamId) return
  if (rows.value.some(other => other.id !== row.id && other.name === name)) { error.value = '已存在同名' + kindLabel.value; return }
  const payload = { teamId: row.teamId, name, ...(props.kind === 'tags' ? { color: editingColor.value } : { sortOrder: row.sortOrder }) }
  return mutate(() => api.value.update(row.id, payload), kindLabel.value + '已保存，相关接口版本已更新')
}
function prepareDelete(row) { if (!canWrite.value || busy.value || row.teamId !== props.teamId) return; frozenDelete.value = Object.freeze({ ...scope(), id: row.id, name: row.name }); deleteOpen.value = true }
async function confirmDelete() {
  const captured = frozenDelete.value; if (!captured || !live(captured, true)) { deleteOpen.value = false; return }
  await mutate(() => api.value.delete(captured.id), kindLabel.value + '已删除，相关接口版本已更新')
  if (live(captured)) deleteOpen.value = false
}
async function saveOrder() {
  if (!canWrite.value || busy.value) return
  const captured = scope(), updates = rows.value.map((row, index) => ({ ...row, sortOrder: index + 1 })).filter((row, index) => row.sortOrder !== rows.value[index].sortOrder)
  if (!updates.length) return
  busy.value = true; error.value = ''; let saved = 0
  try { for (const row of updates) { if (!live(captured, true)) return; await updateGroup(row.id, { teamId: captured.teamId, name: row.name, sortOrder: row.sortOrder }); saved++ } if (live(captured)) ElMessage.success('分组排序已保存') }
  catch (failure) { if (live(captured)) error.value = '排序仅部分完成，已保存 ' + saved + ' 项；正在同步服务端顺序。' + readableError(failure) }
  finally { if (live(captured)) { await load(); busy.value = false; emit('changed', { teamId: captured.teamId, kind: 'groups' }) } }
}
</script>
<style scoped>
.metadata-note{font-size:12px;color:var(--app-text-secondary);line-height:1.9;margin:0 0 14px}.metadata-note--bottom{margin:16px 0 0;font-size:12px}.metadata-readonly,.metadata-error{padding:12px;background:var(--app-bg-control);border-radius:9px;font-size:12px;line-height:1.8;margin-bottom:12px}.metadata-error{background:var(--el-color-danger-light-9);color:var(--app-text-danger)}.metadata-list{max-height:420px;overflow:auto;min-height:80px}.metadata-row{display:flex;gap:8px;align-items:center;padding:12px 8px;margin-bottom:6px;background:var(--app-bg-control);border-radius:10px;min-width:0}.metadata-row__copy{flex:1;min-width:0;display:flex;flex-direction:column;gap:5px}.metadata-row__copy b{font-size:13px;font-weight:500;color:var(--app-text-primary);overflow-wrap:anywhere}.metadata-row__copy span{font-size:12px;color:var(--app-text-secondary)}.metadata-row :deep(.el-button){flex:none;margin:0;padding:7px 6px;font-size:12px}.metadata-row :deep(.el-input){flex:1;min-width:60px}.metadata-handle{border:0;background:transparent;font-size:22px;color:var(--app-text-secondary);cursor:grab;padding:0 5px}.metadata-handle:disabled{cursor:default;color:var(--app-text-disabled)}.metadata-dot{width:10px;height:10px;border-radius:50%;flex:none;margin:0 6px}.metadata-create{display:flex;gap:9px;margin-top:16px}.metadata-create :deep(.el-input){min-width:0;flex:1}.metadata-color{width:28px;height:30px;border:0;background:transparent;padding:0;flex:none;cursor:pointer}.metadata-empty{padding:24px 8px;text-align:center;font-size:13px;color:var(--app-text-secondary)}.metadata-confirm{font-size:14px;line-height:1.9;overflow-wrap:anywhere;color:var(--app-text-regular)}
@media(max-width:450px){.metadata-row{gap:4px;padding:10px 6px;flex-wrap:wrap}.metadata-row__copy{min-width:140px}.metadata-row :deep(.el-button){font-size:12px;padding:6px 4px}.metadata-create{flex-wrap:wrap}.metadata-create :deep(.el-button){font-size:12px}}
</style>
