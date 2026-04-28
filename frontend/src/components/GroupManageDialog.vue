<template>
  <!--
    分组管理弹窗：列出当前团队的分组，支持新建、行内重命名、删除、拖拽排序。
    权限：仅团队管理员/超管可写；普通成员看到只读提示，按钮禁用。
  -->
  <el-dialog
    :model-value="modelValue"
    title="管理分组"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="group-manager" v-loading="loading">
      <!-- 只读提示（普通成员） -->
      <el-alert
        v-if="!canWrite"
        type="info"
        :closable="false"
        class="group-manager__readonly-hint"
        title="只有团队管理员可以创建/修改/删除分组"
        show-icon
      />

      <!-- 空状态 -->
      <div v-if="!loading && groupList.length === 0" class="group-manager__empty">
        暂无分组，新建第一个分组吧
      </div>

      <!-- 拖拽列表 -->
      <draggable
        v-else-if="groupList.length > 0"
        v-model="groupList"
        item-key="id"
        handle=".drag-handle"
        :disabled="!canWrite"
        :animation="200"
        ghost-class="group-row--ghost"
        class="group-manager__list"
        @end="handleDragEnd"
      >
        <template #item="{ element: g }">
          <div class="group-row">
            <!-- 拖拽手柄 -->
            <el-icon
              class="drag-handle"
              :class="{ 'drag-handle--disabled': !canWrite }"
              title="拖动排序"
            >
              <Rank />
            </el-icon>

            <!-- 编辑态 -->
            <template v-if="editingId === g.id">
              <el-input
                v-model="editingName"
                size="small"
                class="group-row__edit-input"
                maxlength="30"
                @keyup.enter="handleSaveRename(g)"
                @keyup.esc="handleCancelEdit"
              />
              <el-button text type="primary" size="small" @click="handleSaveRename(g)">
                保存
              </el-button>
              <el-button text size="small" @click="handleCancelEdit">取消</el-button>
            </template>

            <!-- 展示态 -->
            <template v-else>
              <span class="group-row__name">{{ g.name }}</span>
              <span class="group-row__count">{{ g.apiCount || 0 }} 个接口</span>
              <span class="group-row__spacer" />
              <el-button
                text
                size="small"
                type="primary"
                :disabled="!canWrite"
                title="重命名"
                @click="handleStartEdit(g)"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button
                text
                size="small"
                type="danger"
                :disabled="!canWrite"
                title="删除"
                @click="handleDelete(g)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </div>
        </template>
      </draggable>

      <!-- 新建区 -->
      <div class="group-manager__create">
        <el-input
          v-model="newGroupName"
          placeholder="新分组名称"
          size="small"
          maxlength="30"
          :disabled="!canWrite"
          class="group-manager__create-input"
          @keyup.enter="handleCreate"
        />
        <el-button
          type="primary"
          size="small"
          :disabled="!canWrite || !newGroupName.trim()"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          新建分组
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 接口分组管理弹窗。
 *
 * 数据流：
 *   - 打开时按 teamId 拉取分组列表（含 apiCount）
 *   - 拖拽排序：在 @end 里检测顺序变化，逐个 PUT 更新 sortOrder（N 通常很小）
 *   - 任何写操作完成后向父组件 emit('changed')，由父组件刷新接口列表
 *
 * 权限：仅团队管理员/超管可写；普通成员仅可查看。
 */
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Plus, Rank } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { useUserStore } from '@/stores/user'
import { getGroups, createGroup, updateGroup, deleteGroup } from '@/api/groups'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  teamId: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'changed'])

const userStore = useUserStore()

const loading = ref(false)
const groupList = ref([])

// 行内编辑态
const editingId = ref(null)
const editingName = ref('')

// 新建态
const newGroupName = ref('')

/** 当前用户是否有写权限（团队管理员或超管） */
const canWrite = computed(() => {
  if (!props.teamId) return false
  return userStore.isTeamAdmin(props.teamId)
})

// ---------- 加载 ----------

async function loadGroups() {
  if (!props.teamId) {
    groupList.value = []
    return
  }
  loading.value = true
  try {
    const list = await getGroups(props.teamId)
    // 后端按 sortOrder 升序，已是期望顺序
    groupList.value = Array.isArray(list) ? list : []
  } catch (e) {
    groupList.value = []
  } finally {
    loading.value = false
  }
}

function handleOpen() {
  // 打开时重置编辑/新建态并加载
  editingId.value = null
  editingName.value = ''
  newGroupName.value = ''
  loadGroups()
}

function handleClose() {
  editingId.value = null
}

// ---------- 新建 ----------

async function handleCreate() {
  const name = newGroupName.value.trim()
  if (!name) return
  if (!canWrite.value) return

  // 同名校验（前端预检，体验更好；后端如有唯一约束会再次拦截）
  if (groupList.value.some(g => g.name === name)) {
    ElMessage.warning('已存在同名分组')
    return
  }

  try {
    // sortOrder 取当前最大值 + 1，新建排在末尾
    const maxOrder = groupList.value.reduce((m, g) => Math.max(m, g.sortOrder || 0), 0)
    await createGroup({
      teamId: props.teamId,
      name,
      sortOrder: maxOrder + 1
    })
    ElMessage.success('分组已创建')
    newGroupName.value = ''
    await loadGroups()
    emit('changed')
  } catch (e) {
    // 错误已由请求拦截器处理
  }
}

// ---------- 重命名 ----------

function handleStartEdit(g) {
  if (!canWrite.value) return
  editingId.value = g.id
  editingName.value = g.name
}

function handleCancelEdit() {
  editingId.value = null
  editingName.value = ''
}

async function handleSaveRename(g) {
  const name = editingName.value.trim()
  if (!name) {
    ElMessage.warning('分组名不能为空')
    return
  }
  if (name === g.name) {
    handleCancelEdit()
    return
  }
  if (groupList.value.some(other => other.id !== g.id && other.name === name)) {
    ElMessage.warning('已存在同名分组')
    return
  }

  try {
    await updateGroup(g.id, {
      teamId: g.teamId,
      name,
      sortOrder: g.sortOrder
    })
    ElMessage.success('已重命名')
    handleCancelEdit()
    await loadGroups()
    emit('changed')
  } catch (e) {
    // 错误已由请求拦截器处理
  }
}

// ---------- 删除 ----------

async function handleDelete(g) {
  if (!canWrite.value) return
  const tip = g.apiCount > 0
    ? `分组「${g.name}」下有 ${g.apiCount} 个接口，删除后这些接口将变为"未分组"。是否继续？`
    : `确认删除分组「${g.name}」？`

  try {
    await ElMessageBox.confirm(tip, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteGroup(g.id)
    ElMessage.success('分组已删除')
    await loadGroups()
    emit('changed')
  } catch (e) {
    // 用户取消或错误，不处理
  }
}

// ---------- 拖拽排序 ----------

/**
 * 拖拽结束后批量更新 sortOrder。
 * 实现：把当前数组顺序写回每个分组的 sortOrder（1..N），仅对实际变化的分组发请求。
 */
async function handleDragEnd() {
  if (!canWrite.value) return

  const updates = []
  groupList.value.forEach((g, idx) => {
    const newOrder = idx + 1
    if (g.sortOrder !== newOrder) {
      updates.push({ ...g, sortOrder: newOrder })
    }
  })

  if (updates.length === 0) return

  try {
    // 并发更新（N 通常 < 20，影响可控）
    await Promise.all(updates.map(g => updateGroup(g.id, {
      teamId: g.teamId,
      name: g.name,
      sortOrder: g.sortOrder
    })))
    ElMessage.success('排序已保存')
    await loadGroups()
    emit('changed')
  } catch (e) {
    // 失败时重新加载恢复服务端顺序
    await loadGroups()
  }
}
</script>

<style lang="scss" scoped>
.group-manager {
  &__readonly-hint {
    margin-bottom: 12px;
  }

  &__empty {
    text-align: center;
    color: #909399;
    padding: 32px 0;
    font-size: 13px;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 12px;
  }

  &__create {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px dashed #E5E7EB;
  }

  &__create-input {
    flex: 1;
  }
}

.group-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #F8F9FB;
  transition: background-color 0.15s;

  &:hover {
    background: #EEF2F7;
  }

  &--ghost {
    opacity: 0.5;
    background: #E0E7FF;
  }

  &__name {
    font-size: 14px;
    color: #1B2559;
    font-weight: 500;
  }

  &__count {
    font-size: 12px;
    color: #909399;
  }

  &__spacer {
    flex: 1;
  }

  &__edit-input {
    flex: 1;
  }
}

.drag-handle {
  cursor: grab;
  color: #B0B7C3;
  font-size: 16px;

  &:active {
    cursor: grabbing;
  }

  &--disabled {
    cursor: not-allowed;
    opacity: 0.4;
  }
}
</style>
