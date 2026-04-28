<template>
  <!-- 接口列表页：工具栏筛选 + 数据表格 + 分页 + 导入导出 -->
  <div class="page-api-list">
    <!-- 工具栏 -->
    <div class="toolbar soft-card">
      <div class="toolbar__filters">
        <!-- 搜索框 -->
        <el-input
          v-model="keyword"
          placeholder="搜索接口名称或路径"
          clearable
          prefix-icon="Search"
          class="toolbar__search"
          @clear="handleKeywordChange"
          @keyup.enter="handleKeywordChange"
        />

        <!-- 接口类型筛选：REST / SOAP -->
        <el-select
          v-model="typeFilter"
          placeholder="接口类型"
          clearable
          class="toolbar__select"
          @change="handleTypeChange"
        >
          <el-option label="REST" value="REST" />
          <el-option label="SOAP" value="SOAP" />
        </el-select>

        <!-- 分组筛选：选择特定分组、未分组或全部 -->
        <!-- 未选中团队时禁用：跨团队分组语义混乱 -->
        <el-select
          v-model="groupFilter"
          :placeholder="appStore.currentTeamId ? '分组筛选' : '请先选择团队'"
          clearable
          :disabled="!appStore.currentTeamId"
          class="toolbar__select"
          @change="handleGroupChange"
        >
          <el-option label="未分组" value="__none__" />
          <el-option
            v-for="g in availableGroups"
            :key="g.id"
            :label="g.name"
            :value="g.id"
          />
        </el-select>

        <!-- HTTP 方法筛选 -->
        <el-select
          v-model="methodFilter"
          placeholder="HTTP 方法"
          clearable
          class="toolbar__select"
          @change="handleMethodChange"
        >
          <el-option
            v-for="m in httpMethods"
            :key="m"
            :label="m"
            :value="m"
          />
        </el-select>

        <!-- 启用状态筛选 -->
        <el-select
          v-model="enabledFilter"
          placeholder="启用状态"
          clearable
          class="toolbar__select"
          @change="handleEnabledChange"
        >
          <el-option label="已启用" :value="true" />
          <el-option label="已禁用" :value="false" />
        </el-select>

        <!-- 标签筛选（支持多选，命中任一标签即返回） -->
        <el-select
          v-model="tagFilter"
          placeholder="标签筛选"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          class="toolbar__select"
          @change="handleTagChange"
        >
          <el-option
            v-for="tag in availableTags"
            :key="tag.id"
            :label="tag.name"
            :value="tag.id"
          >
            <span class="tag-option">
              <span class="tag-option__dot" :style="{ backgroundColor: tag.color }" />
              {{ tag.name }}
            </span>
          </el-option>
        </el-select>
      </div>

      <div class="toolbar__actions">
        <!-- 分组管理按钮 -->
        <el-button @click="handleOpenGroupManager">
          <el-icon><Folder /></el-icon>
          管理分组
        </el-button>
        <!-- 标签管理按钮 -->
        <el-button @click="handleOpenTagManager">
          <el-icon><PriceTag /></el-icon>
          标签管理
        </el-button>
        <!-- 导入按钮 -->
        <el-button @click="importDialogVisible = true">
          <el-icon><Upload /></el-icon>
          导入
        </el-button>
        <!-- 导出按钮（有选中时显示数量） -->
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          {{ selectedRows.length > 0 ? `导出(${selectedRows.length})` : '导出' }}
        </el-button>
        <!-- 新建接口按钮 -->
        <el-button type="primary" @click="$router.push('/apis/new')">
          <el-icon><Plus /></el-icon>
          新建接口
        </el-button>
      </div>
    </div>

    <!-- 接口表格 -->
    <div class="table-card soft-card">
      <el-table
        ref="tableRef"
        :data="apiList"
        v-loading="loading"
        style="width: 100%"
        row-class-name="api-row"
        :default-sort="defaultSort"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <!-- 多选框列 -->
        <el-table-column type="selection" width="40" />

        <!-- 空状态 -->
        <template #empty>
          <div class="empty-state">
            <el-icon class="empty-state__icon"><Connection /></el-icon>
            <p class="empty-state__text">暂无接口数据</p>
            <el-button type="primary" size="small" @click="$router.push('/apis/new')">
              新建接口
            </el-button>
          </div>
        </template>

        <!-- 接口类型列（REST / SOAP） -->
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <ApiTypeTag :type="row.type || 'REST'" />
          </template>
        </el-table-column>

        <!-- HTTP 方法列 -->
        <el-table-column label="方法" width="80" align="center">
          <template #default="{ row }">
            <HttpMethodTag :method="row.method" />
          </template>
        </el-table-column>

        <!-- 路径列（可排序） -->
        <el-table-column
          prop="path"
          label="路径"
          min-width="200"
          show-overflow-tooltip
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #default="{ row }">
            <span class="api-path">{{ row.path }}</span>
          </template>
        </el-table-column>

        <!-- 接口名称列（可排序） -->
        <el-table-column
          prop="name"
          label="名称"
          min-width="180"
          show-overflow-tooltip
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        />

        <!-- 分组列：未分组 / 跨团队找不到分组 / 分组已删除——一律显示 "-" -->
        <el-table-column label="分组" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="groupNameMap[row.groupId]" class="group-name">
              {{ groupNameMap[row.groupId] }}
            </span>
            <span v-else class="no-group">-</span>
          </template>
        </el-table-column>

        <!-- 标签列 -->
        <el-table-column label="标签" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="tag-cell" v-if="row.tags && row.tags.length">
              <el-tag
                v-for="tag in row.tags"
                :key="tag.id"
                size="small"
                :color="tag.color"
                effect="dark"
                round
                class="api-tag"
              >{{ tag.name }}</el-tag>
            </div>
            <span v-else class="no-tags">-</span>
          </template>
        </el-table-column>

        <!-- 团队列 -->
        <el-table-column label="团队" width="80" align="center">
          <template #default="{ row }">
            <TeamTag :identifier="row.teamIdentifier" :color="row.teamColor" />
          </template>
        </el-table-column>

        <!--
          修改时间列：完整格式 yyyy-MM-dd HH:mm:ss，可排序。
          位置：刻意放在"创建人"列之前，让"创建人"做与 fixed-right 区的缓冲列。
          这样 sortable 列不与 fixed 列毗邻，规避 EP 已知的 fixed-right 表头双重渲染 bug
          （sortable 排序图标导致 fixed-right 容器克隆表头时裁切不干净，文字会穿透叠字）。
        -->
        <el-table-column
          prop="updatedAt"
          label="修改时间"
          width="170"
          align="center"
          sortable="custom"
          :sort-orders="['descending', 'ascending']"
        >
          <template #default="{ row }">
            <span class="time-cell">{{ formatFull(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <!-- 创建人列（兼任 fixed-right 区前的缓冲列） -->
        <el-table-column label="创建人" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.createdByName">{{ row.createdByName }}</span>
            <span v-else class="no-creator">-</span>
          </template>
        </el-table-column>

        <!-- 启用开关列（固定右侧，与操作列一起常驻视野） -->
        <el-table-column label="启用" width="65" align="center" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              size="small"
              @click.stop
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>

        <!-- 操作列（固定在右侧，横滚时也始终可见） -->
        <el-table-column label="操作" width="200" align="center" class-name="action-col" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button
                text
                size="small"
                @click="handleCopyUrl(row)"
                :title="row.type === 'SOAP' ? '复制 WSDL 地址' : '复制 Mock 地址'"
              >
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
              <el-button text size="small" type="primary" @click="handleEdit(row)" title="编辑">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button text size="small" @click="handleCopy(row)" title="复制接口">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
              <el-button text size="small" type="danger" @click="handleDelete(row)" title="删除">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 分组管理弹窗 -->
    <GroupManageDialog
      v-model="groupManagerVisible"
      :team-id="appStore.currentTeamId"
      @changed="handleGroupChanged"
    />

    <!-- 标签管理抽屉 -->
    <el-drawer
      v-model="tagDrawerVisible"
      title="标签管理"
      direction="rtl"
      size="400px"
      class="tag-drawer"
      @close="handleTagDrawerClose"
    >
      <!-- 标签列表 -->
      <div class="tag-manager">
        <div v-if="tagList.length === 0 && !tagLoading" class="tag-manager__empty">
          暂无标签
        </div>
        <div v-loading="tagLoading" class="tag-manager__list">
          <div
            v-for="tag in tagList"
            :key="tag.id"
            class="tag-manager__item"
          >
            <!-- 编辑态 -->
            <template v-if="editingTagId === tag.id">
              <el-color-picker
                v-model="editingTagColor"
                size="small"
                :predefine="PRESET_COLORS"
              />
              <el-input
                v-model="editingTagName"
                size="small"
                class="tag-manager__edit-input"
                @keyup.enter="handleSaveTag(tag)"
              />
              <el-button text size="small" type="primary" @click="handleSaveTag(tag)">
                保存
              </el-button>
              <el-button text size="small" @click="handleCancelEdit">
                取消
              </el-button>
            </template>
            <!-- 展示态 -->
            <template v-else>
              <span class="tag-manager__dot" :style="{ backgroundColor: tag.color }" />
              <span class="tag-manager__name">{{ tag.name }}</span>
              <span class="tag-manager__spacer" />
              <el-button text size="small" type="primary" @click="handleStartEdit(tag)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button text size="small" type="danger" @click="handleDeleteTag(tag)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </div>
        </div>

        <!-- 新建标签区域 -->
        <div class="tag-manager__create">
          <el-color-picker
            v-model="newTagColor"
            size="small"
            :predefine="PRESET_COLORS"
          />
          <el-input
            v-model="newTagName"
            placeholder="新标签名称"
            size="small"
            class="tag-manager__create-input"
            @keyup.enter="handleCreateTag"
          />
          <el-button type="primary" size="small" @click="handleCreateTag" :disabled="!newTagName.trim()">
            添加
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入接口"
      width="500"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="目标团队">
          <el-select v-model="importForm.teamId" placeholder="选择导入到哪个团队" style="width: 100%">
            <el-option
              v-for="team in appStore.teams"
              :key="team.id"
              :label="team.name"
              :value="team.id"
            >
              <span style="display: flex; align-items: center; gap: 8px">
                <TeamTag :identifier="team.identifier" :color="team.color" />
                {{ team.name }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="导入模式">
          <el-radio-group v-model="importForm.mode">
            <el-radio value="merge">合并（已存在的跳过）</el-radio>
            <el-radio value="override">覆盖（已存在的替换）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".json"
            :on-change="handleImportFileChange"
          >
            <el-button>选择 JSON 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 接口列表页
 * 功能：接口的搜索/筛选、分页展示、启用/禁用切换、复制、删除、导入/导出
 * 数据流：筛选条件来自 appStore（团队/分组）和 apiStore（关键词/方法/状态/标签/分页）
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useApiStore } from '@/stores/api'
import { useUserStore } from '@/stores/user'
import { getApis, deleteApi, copyApi, toggleApi, importApis, exportApis } from '@/api/apis'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tags'
import { getGroups } from '@/api/groups'
import { getServerAddress } from '@/api/settings'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import ApiTypeTag from '@/components/ApiTypeTag.vue'
import TeamTag from '@/components/TeamTag.vue'
import GroupManageDialog from '@/components/GroupManageDialog.vue'
import { formatFull } from '@/utils/time'

const router = useRouter()
const appStore = useAppStore()
const apiStore = useApiStore()
const userStore = useUserStore()

// --- 列表数据 ---
const apiList = ref([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref(null)
const selectedRows = ref([])

/** 多选变化 */
function handleSelectionChange(rows) {
  selectedRows.value = rows
}

// --- 工具栏筛选状态（双向绑定到输入控件，变化后同步到 store） ---
const keyword = ref(apiStore.listParams.keyword)
const methodFilter = ref(apiStore.listParams.method)
const enabledFilter = ref(apiStore.listParams.enabled)
const typeFilter = ref(apiStore.listParams.type)
// 多标签筛选：数组形式,兼容 store 里可能残留的旧字段
const tagFilter = ref(Array.isArray(apiStore.listParams.tagIds) ? [...apiStore.listParams.tagIds] : [])
// 分组筛选：null=全部，'__none__'=未分组，其余=分组 ID
const groupFilter = ref(apiStore.listParams.groupId)
const currentPage = ref(apiStore.listParams.page)
const pageSize = ref(apiStore.listParams.size)

const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

// 当前可选标签（基于当前团队筛选加载）
const availableTags = ref([])

// 当前可选分组（基于当前团队加载，用于筛选下拉和表格分组列名映射）
const availableGroups = ref([])

// 分组 ID → 分组名 映射，给表格列展示用
const groupNameMap = computed(() => {
  const map = {}
  availableGroups.value.forEach(g => { map[g.id] = g.name })
  return map
})

// 分组管理弹窗显示状态
const groupManagerVisible = ref(false)

/**
 * el-table 的 default-sort：把 store 中的 sortBy/sortDir 转成 el-table 期望的 {prop, order}。
 * order 取值：'ascending' / 'descending' / null。
 */
const defaultSort = computed(() => ({
  prop: apiStore.listParams.sortBy || 'updatedAt',
  order: apiStore.listParams.sortDir === 'asc' ? 'ascending' : 'descending'
}))

// --- 服务器地址（用于拼接 Mock URL） ---
const serverAddress = ref('')

// --- 标签管理相关 ---

/** 预设色板 */
const PRESET_COLORS = [
  '#6366F1', '#8B5CF6', '#EC4899', '#EF4444', '#F59E0B',
  '#10B981', '#06B6D4', '#3B82F6', '#6B7280', '#D97706'
]

const tagDrawerVisible = ref(false)
const tagList = ref([])
const tagLoading = ref(false)

// 编辑态
const editingTagId = ref(null)
const editingTagName = ref('')
const editingTagColor = ref('')

// 新建态
const newTagName = ref('')
const newTagColor = ref(PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)])

/** 打开标签管理抽屉，未选团队时自动取用户所属的第一个团队 */
function handleOpenTagManager() {
  if (!appStore.currentTeamId) {
    if (userStore.userTeamIds.length > 0) {
      appStore.setFilter(userStore.userTeamIds[0])
    } else if (appStore.teams.length > 0) {
      // 超管没有所属团队时取团队列表第一个
      appStore.setFilter(appStore.teams[0].id)
    } else {
      ElMessage.warning('请先选择一个团队')
      return
    }
  }
  tagDrawerVisible.value = true
  loadTagList()
}

/** 加载标签管理列表 */
async function loadTagList() {
  tagLoading.value = true
  try {
    tagList.value = await getTags(appStore.currentTeamId)
  } catch (e) {
    tagList.value = []
  } finally {
    tagLoading.value = false
  }
}

/** 关闭抽屉后刷新接口列表（标签可能变了） */
function handleTagDrawerClose() {
  editingTagId.value = null
  loadApis()
  loadTags()
}

/** 开始行内编辑 */
function handleStartEdit(tag) {
  editingTagId.value = tag.id
  editingTagName.value = tag.name
  editingTagColor.value = tag.color
}

/** 取消编辑 */
function handleCancelEdit() {
  editingTagId.value = null
}

/** 保存编辑 */
async function handleSaveTag(tag) {
  if (!editingTagName.value.trim()) {
    ElMessage.warning('标签名称不能为空')
    return
  }
  try {
    await updateTag(tag.id, {
      teamId: appStore.currentTeamId,
      name: editingTagName.value.trim(),
      color: editingTagColor.value
    })
    ElMessage.success('标签已更新')
    editingTagId.value = null
    loadTagList()
  } catch (e) {
    // 错误已由拦截器处理
  }
}

/** 删除标签 */
async function handleDeleteTag(tag) {
  try {
    await ElMessageBox.confirm(
      `删除标签「${tag.name}」将移除所有接口上的该标签，确认？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTag(tag.id)
    ElMessage.success('标签已删除')
    loadTagList()
  } catch (e) {
    // 取消或错误，不处理
  }
}

/** 创建新标签 */
async function handleCreateTag() {
  if (!newTagName.value.trim()) return
  try {
    await createTag({
      teamId: appStore.currentTeamId,
      name: newTagName.value.trim(),
      color: newTagColor.value
    })
    ElMessage.success('标签已创建')
    newTagName.value = ''
    // 随机选一个新的默认颜色
    newTagColor.value = PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)]
    loadTagList()
  } catch (e) {
    // 错误已由拦截器处理
  }
}

// --- 导入相关 ---
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importUploadRef = ref(null)
const importForm = ref({
  teamId: '',
  mode: 'merge'
})
const importFile = ref(null)

// --- 数据加载 ---

/** 加载接口列表，组合侧边栏筛选 + 工具栏筛选参数 */
async function loadApis() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    // 侧边栏团队筛选
    if (appStore.currentTeamId) {
      params.teamId = appStore.currentTeamId
    }
    // 工具栏筛选
    if (keyword.value) params.keyword = keyword.value
    if (methodFilter.value) params.method = methodFilter.value
    if (typeFilter.value) params.type = typeFilter.value
    if (enabledFilter.value !== null && enabledFilter.value !== undefined && enabledFilter.value !== '') {
      params.enabled = enabledFilter.value
    }
    // 多标签以逗号拼接传给后端(命中任一即返回)
    if (tagFilter.value && tagFilter.value.length) {
      params.tagIds = tagFilter.value.join(',')
    }
    // 分组筛选：'__none__' 转为空字符串传给后端(后端语义：空串 = 未分组)
    if (groupFilter.value === '__none__') {
      params.groupId = ''
    } else if (groupFilter.value) {
      params.groupId = groupFilter.value
    }
    // 排序参数（后端有白名单兜底，前端传错值不会出错，只会回退默认）
    if (apiStore.listParams.sortBy) {
      params.sortBy = apiStore.listParams.sortBy
    }
    if (apiStore.listParams.sortDir) {
      params.sortDir = apiStore.listParams.sortDir
    }

    const res = await getApis(params)
    apiList.value = res.items || []
    total.value = res.total || 0
  } catch (e) {
    apiList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 加载当前团队的分组列表（用于筛选下拉和表格分组列名映射）
 * 未选团队时清空：跨团队分组语义混乱，前端不做聚合
 */
async function loadGroups() {
  if (!appStore.currentTeamId) {
    availableGroups.value = []
    return
  }
  try {
    const list = await getGroups(appStore.currentTeamId)
    availableGroups.value = Array.isArray(list) ? list : []
  } catch (e) {
    availableGroups.value = []
  }
}

/** 加载可选标签（按当前团队加载，未选团队时加载所有团队标签） */
async function loadTags() {
  try {
    if (appStore.currentTeamId) {
      availableTags.value = await getTags(appStore.currentTeamId)
    } else {
      // 没有选中团队时，加载所有团队标签合集
      const allTags = []
      for (const team of appStore.teams) {
        try {
          const tags = await getTags(team.id)
          allTags.push(...tags)
        } catch (e) {
          // 单个团队失败不影响其他
        }
      }
      availableTags.value = allTags
    }
  } catch (e) {
    availableTags.value = []
  }
}

// --- 筛选条件变化处理 ---

function handleKeywordChange() {
  apiStore.setParam('keyword', keyword.value)
  currentPage.value = 1
  loadApis()
}

function handleMethodChange(val) {
  apiStore.setParam('method', val)
  currentPage.value = 1
  loadApis()
}

function handleTypeChange(val) {
  apiStore.setParam('type', val)
  currentPage.value = 1
  loadApis()
}

function handleEnabledChange(val) {
  apiStore.setParam('enabled', val)
  currentPage.value = 1
  loadApis()
}

function handleTagChange(val) {
  // val 为选中的 tagId 数组
  apiStore.setParam('tagIds', val)
  currentPage.value = 1
  loadApis()
}

/** 分组筛选变化：null=全部，'__none__'=未分组，其余=分组 ID */
function handleGroupChange(val) {
  apiStore.setParam('groupId', val || null)
  currentPage.value = 1
  loadApis()
}

/** 打开分组管理弹窗，未选团队时自动取用户所属第一个团队（与标签管理逻辑一致） */
function handleOpenGroupManager() {
  if (!appStore.currentTeamId) {
    if (userStore.userTeamIds.length > 0) {
      appStore.setFilter(userStore.userTeamIds[0])
    } else if (appStore.teams.length > 0) {
      appStore.setFilter(appStore.teams[0].id)
    } else {
      ElMessage.warning('请先选择一个团队')
      return
    }
  }
  groupManagerVisible.value = true
}

/**
 * 表头排序变化：order = 'ascending' / 'descending' / null（取消排序）。
 * null 时回退到默认排序（updatedAt desc），避免后端无序返回引起列表抖动。
 */
function handleSortChange({ prop, order }) {
  if (!order) {
    apiStore.setParam('sortBy', 'updatedAt')
    apiStore.setParam('sortDir', 'desc')
  } else {
    apiStore.setParam('sortBy', prop)
    apiStore.setParam('sortDir', order === 'ascending' ? 'asc' : 'desc')
  }
  currentPage.value = 1
  loadApis()
}

/** 分组弹窗中发生 CRUD 后回调：重新加载分组列表和接口列表 */
async function handleGroupChanged() {
  await loadGroups()
  // 当前分组可能已被删除，校正筛选
  if (groupFilter.value && groupFilter.value !== '__none__') {
    const stillExists = availableGroups.value.some(g => g.id === groupFilter.value)
    if (!stillExists) {
      groupFilter.value = null
      apiStore.setParam('groupId', null)
    }
  }
  loadApis()
}

function handlePageChange(page) {
  apiStore.setParam('page', page)
  loadApis()
}

function handleSizeChange(size) {
  apiStore.setParam('size', size)
  pageSize.value = size
  currentPage.value = 1
  apiStore.setParam('page', 1)
  loadApis()
}

// 监听侧边栏团队筛选变化，自动刷新列表/标签/分组
// 团队切换后，原选中的分组 ID 已不属于当前团队，需重置避免错误筛选
watch(
  () => appStore.currentTeamId,
  () => {
    currentPage.value = 1
    apiStore.setParam('page', 1)
    if (groupFilter.value) {
      groupFilter.value = null
      apiStore.setParam('groupId', null)
    }
    loadApis()
    loadTags()
    loadGroups()
  }
)

// 搜索框防抖：输入时延迟触发
let keywordTimer = null
watch(keyword, (val) => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    apiStore.setParam('keyword', val)
    currentPage.value = 1
    loadApis()
  }, 300)
})

// --- 行操作 ---

/** 编辑 */
function handleEdit(row) {
  router.push(`/apis/${row.id}/edit`)
}

/**
 * 复制接口地址到剪贴板。
 * SOAP 接口复制 WSDL 托管地址（`{mockUrl}?wsdl`，方案 A），
 * REST 接口复制普通 Mock 地址（`{mockUrl}`）。
 */
async function handleCopyUrl(row) {
  const base = serverAddress.value || window.location.origin
  const mockUrl = `${base}/mock/${row.teamIdentifier || ''}${row.path || '/'}`
  const isSoap = row.type === 'SOAP'
  const url = isSoap ? `${mockUrl}?wsdl` : mockUrl
  const label = isSoap ? 'WSDL 地址' : 'Mock 地址'
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(`${label}已复制`)
  } catch (err) {
    // 降级方案：无权限使用 Clipboard API 时改用 textarea + execCommand
    const textarea = document.createElement('textarea')
    textarea.value = url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success(`${label}已复制`)
  }
}

/** 复制接口 */
async function handleCopy(row) {
  try {
    await copyApi(row.id)
    ElMessage.success('复制成功')
    loadApis()
    appStore.loadTeams()
  } catch (e) {
    // 错误已由拦截器处理
  }
}

/** 删除接口 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除接口「${row.name}」(${row.method} ${row.path})？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteApi(row.id)
    ElMessage.success('删除成功')
    loadApis()
    appStore.loadTeams()
  } catch (e) {
    // 取消或错误，不处理
  }
}

/** 启用/禁用切换 */
async function handleToggle(row) {
  try {
    const res = await toggleApi(row.id)
    row.enabled = res.enabled
    ElMessage.success(res.enabled ? '已启用' : '已禁用')
  } catch (e) {
    // 错误已由拦截器处理
  }
}

// --- 导入/导出 ---

function handleImportFileChange(uploadFile) {
  importFile.value = uploadFile.raw
}

async function handleImport() {
  if (!importForm.value.teamId) {
    ElMessage.warning('请选择目标团队')
    return
  }
  if (!importFile.value) {
    ElMessage.warning('请选择导入文件')
    return
  }

  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    formData.append('teamId', importForm.value.teamId)
    formData.append('mode', importForm.value.mode)

    const res = await importApis(formData)
    ElMessage.success(`导入完成：新增 ${res.imported} 个，跳过 ${res.skipped} 个，覆盖 ${res.overridden} 个`)
    importDialogVisible.value = false
    importFile.value = null
    loadApis()
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    importLoading.value = false
  }
}

async function handleExport() {
  // 有选中行时导出选中的接口，否则导出整个团队
  if (selectedRows.value.length > 0) {
    // 导出选中的接口：构造 JSON 并下载
    const exportData = {
      version: '1.0',
      exportedAt: new Date().toISOString(),
      teamName: selectedRows.value[0].teamName || '',
      apis: selectedRows.value
    }
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const fileName = `mockhub-export-selected-${selectedRows.value.length}-${new Date().toISOString().slice(0, 10)}.json`
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${selectedRows.value.length} 个接口`)
    return
  }

  // 没选中时按团队导出全部
  const teamId = appStore.currentTeamId
  if (!teamId) {
    ElMessage.warning('请先选择接口或在左侧选择一个团队再导出')
    return
  }

  try {
    const blob = await exportApis(teamId)
    const team = appStore.teams.find(t => t.id === teamId)
    const fileName = `mockhub-export-${team ? team.identifier : 'all'}-${new Date().toISOString().slice(0, 10)}.json`
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    // 错误已由拦截器处理
  }
}

// --- 工具函数 ---

function hexToRgba(hex, alpha) {
  const h = (hex || '#6366F1').replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** 根据状态码返回颜色 class */
function responseCodeClass(code) {
  if (code >= 200 && code < 300) return 'response-code--success'
  if (code >= 300 && code < 400) return 'response-code--redirect'
  if (code >= 400 && code < 500) return 'response-code--client-error'
  if (code >= 500) return 'response-code--server-error'
  return ''
}

// --- 初始化 ---
onMounted(async () => {
  loadApis()
  loadTags()
  loadGroups()
  // 加载服务器地址用于拼接 Mock URL
  try {
    const data = await getServerAddress()
    serverAddress.value = data.address || ''
  } catch (e) {
    // 获取失败时降级使用 window.location.origin
  }
})
</script>

<style lang="scss" scoped>
.page-api-list {
}

// 工具栏
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  &__filters {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__search {
    width: 240px;
  }

  &__select {
    width: 130px;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

// 标签选项（下拉中带色点）
.tag-option {
  display: flex;
  align-items: center;
  gap: 8px;

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }
}

// 表格卡片
.table-card {
  padding: 0;

  :deep(.el-table) {
    border-radius: 16px;
  }

  // 防御 EP 已知 bug：横向滚动时 fixed-right 列与左侧普通列表头/单元格叠字
  // 原因：EP 2.x 用 position: sticky 实现 fixed 列，sticky 单元格本身需有不透明背景
  // 才能挡住下方滚过来的内容；个别情况（特别是表头）背景被清掉或 z-index 不够，
  // 导致下层列文字穿透。这里统一加白底 + 高 z-index。
  :deep(.el-table) {
    th.el-table-fixed-column--right,
    td.el-table-fixed-column--right {
      background-color: #FFFFFF !important;
      z-index: 3 !important;
    }
    // 第一根 fixed-right 列左侧补一条细分隔线，明确边界（避免被相邻列覆盖时分不清）
    th.el-table-fixed-column--right.is-first-column::before,
    td.el-table-fixed-column--right.is-first-column::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: -1px;
      width: 1px;
      background-color: #EBEEF5;
    }
    // 表头排序图标列降级层级，避免它在水平滚动期间漂到 fixed 区上方
    th.el-table__cell.is-sortable {
      z-index: 1;
    }
  }
}

// 表格行样式
.api-row {
  height: 52px;

  // 确保所有单元格内容垂直居中
  :deep(td .cell) {
    display: flex;
    align-items: center;
    min-height: 36px;
  }
  // 居中列的 cell 也要水平居中
  :deep(td.is-center .cell) {
    justify-content: center;
  }
}

.api-path {
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  color: #1B2559;
}

.group-name {
  font-size: 13px;
  color: #4A5568;
}

.no-group {
  font-size: 13px;
  color: #B0B7C3;
}

// 修改时间列：完整时间显示，等宽数字字体让多行对齐
.time-cell {
  font-size: 12px;
  color: #6B7280;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  white-space: nowrap;
}

.no-creator {
  font-size: 13px;
  color: #B0B7C3;
}

// 状态码颜色
.response-code {
  font-weight: 600;
  font-size: 13px;

  &--success { color: #10B981; }
  &--redirect { color: #F59E0B; }
  &--client-error { color: #EF4444; }
  &--server-error { color: #DC2626; }
}

.delay-text {
  font-size: 13px;
  color: #A3AED0;
}

// 标签列
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.api-tag {
  border: none;
  font-size: 12px;
  color: #fff;
}

.no-tags {
  color: #A3AED0;
}

// 操作按钮
.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding-right: 8px;
  white-space: nowrap;
}

// 操作列 cell 不裁切
:deep(.action-col .cell) {
  overflow: visible !important;
}

// 分页
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
}

// 标签管理抽屉 Soft UI 风格
.tag-manager {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__empty {
    text-align: center;
    color: #A3AED0;
    padding: 40px 0;
    font-size: 14px;
  }

  &__list {
    flex: 1;
    overflow-y: auto;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 10px;
    transition: background 0.2s;

    &:hover {
      background: #F7F8FA;
    }
  }

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__name {
    font-size: 14px;
    color: #1B2559;
  }

  &__spacer {
    flex: 1;
  }

  &__edit-input {
    flex: 1;
  }

  // 新建标签区域固定在底部
  &__create {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 16px 0 0;
    margin-top: 12px;
    border-top: 1px solid #F1F5F9;
  }

  &__create-input {
    flex: 1;
  }
}

// 空状态
.empty-state {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  &__icon {
    font-size: 48px;
    color: #A3AED0;
  }

  &__text {
    font-size: 14px;
    color: #A3AED0;
  }
}
</style>

<!-- 标签管理抽屉全局样式覆盖：大圆角 Soft UI -->
<style lang="scss">
.tag-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding-bottom: 16px;
    border-bottom: 1px solid #F1F5F9;
  }

  .el-drawer__body {
    padding: 16px 20px;
  }

  &.el-drawer {
    border-radius: 16px 0 0 16px;
  }
}
</style>
