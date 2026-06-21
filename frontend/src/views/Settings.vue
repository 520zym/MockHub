<!--
  Settings.vue — 全局设置页面
  功能：超级管理员配置日志保留策略和 Mock CORS 开关
  视觉：Soft UI 风格卡片式布局
-->
<template>
  <div class="page-settings">
    <!-- 设置卡片 -->
    <div class="settings-card" v-loading="loading">
      <el-form label-position="top" class="settings-form">
        <!-- 日志保留策略 -->
        <div class="settings-section">
          <h3 class="section-title">日志保留策略</h3>
          <p class="section-desc">配置操作日志和请求日志的自动清理规则</p>

          <el-form-item label="保留模式">
            <el-radio-group v-model="form.logRetainMode">
              <el-radio value="count">按条数保留</el-radio>
              <el-radio value="days">按天数保留</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="form.logRetainMode === 'count'" label="保留条数">
            <el-input-number
              v-model="form.logRetainCount"
              :min="100"
              :max="100000"
              :step="100"
              controls-position="right"
            />
            <span class="input-hint">超出部分将自动删除最旧的记录</span>
          </el-form-item>

          <el-form-item v-if="form.logRetainMode === 'days'" label="保留天数">
            <el-input-number
              v-model="form.logRetainDays"
              :min="1"
              :max="365"
              :step="1"
              controls-position="right"
            />
            <span class="input-hint">超出天数的日志将被自动清理</span>
          </el-form-item>
        </div>

        <!-- 分割线 -->
        <div class="section-divider"></div>

        <!-- 服务器地址 -->
        <div class="settings-section">
          <h3 class="section-title">服务器地址</h3>
          <p class="section-desc">用于拼接 Mock URL，为空时自动检测内网 IP</p>

          <el-form-item label="服务器地址">
            <el-input
              v-model="form.serverAddress"
              placeholder="如：http://192.168.1.100:8080"
              clearable
              style="width: 400px"
            />
          </el-form-item>
        </div>

        <!-- 分割线 -->
        <div class="section-divider"></div>

        <!-- Mock CORS -->
        <div class="settings-section">
          <h3 class="section-title">Mock CORS</h3>
          <p class="section-desc">启用后，Mock 接口允许所有跨域请求</p>

          <div class="cors-switch-row">
            <el-switch
              v-model="form.mockCorsEnabled"
              active-text="已启用"
              inactive-text="已禁用"
            />
          </div>
        </div>

        <!-- 分割线 -->
        <div class="section-divider"></div>

        <!-- 文件存储维护 -->
        <div class="settings-section">
          <h3 class="section-title">文件存储维护</h3>
          <p class="section-desc">扫描并清理未被任何文件类型返回体引用的存储文件</p>

          <div class="file-maintenance-actions">
            <el-button :loading="orphanScanning" @click="handleScanOrphans">
              扫描孤儿文件
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="!orphanResult || orphanResult.orphanCount === 0"
              :loading="orphanCleaning"
              @click="handleCleanOrphans"
            >
              清理孤儿文件
            </el-button>
          </div>

          <div v-if="storageStats" class="storage-stats">
            <div class="storage-stat">
              <span class="storage-stat__label">存储目录</span>
              <span class="storage-stat__value path-value">{{ storageStats.rootDir }}</span>
            </div>
            <div class="storage-stat">
              <span class="storage-stat__label">上传上限</span>
              <span class="storage-stat__value">{{ formatSize(storageStats.maxSizeBytes) }}</span>
            </div>
            <div class="storage-stat">
              <span class="storage-stat__label">文件总数</span>
              <span class="storage-stat__value">{{ storageStats.totalFileCount }} 个 / {{ formatSize(storageStats.totalFileSize) }}</span>
            </div>
            <div class="storage-stat">
              <span class="storage-stat__label">引用文件</span>
              <span class="storage-stat__value">{{ storageStats.referencedFileCount }} 个</span>
            </div>
            <div class="storage-stat">
              <span class="storage-stat__label">孤儿文件</span>
              <span class="storage-stat__value">{{ storageStats.orphanCount }} 个 / {{ formatSize(storageStats.orphanSize) }}</span>
            </div>
          </div>

          <div v-if="orphanResult" class="orphan-summary">
            <span>发现 {{ orphanResult.orphanCount }} 个孤儿文件</span>
            <span>合计 {{ formatSize(orphanResult.orphanSize) }}</span>
          </div>

          <el-table
            v-if="orphanResult && orphanResult.files && orphanResult.files.length"
            :data="orphanResult.files"
            size="small"
            class="orphan-table"
            max-height="260"
          >
            <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="filePath" label="存储路径" min-width="240" show-overflow-tooltip />
            <el-table-column label="大小" width="100">
              <template #default="{ row }">
                {{ formatSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column label="修改时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.lastModifiedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 保存按钮 -->
        <div class="settings-footer">
          <el-button
            type="primary"
            :loading="saving"
            class="save-btn"
            @click="handleSave"
          >
            保存设置
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cleanOrphanFiles,
  getFileStorageStats,
  getSettings,
  saveSettings,
  scanOrphanFiles
} from '@/api/settings'

// ========== 状态 ==========
const loading = ref(false)
const saving = ref(false)
const orphanScanning = ref(false)
const orphanCleaning = ref(false)
const orphanResult = ref(null)
const storageStats = ref(null)

const form = reactive({
  logRetainMode: 'count',
  logRetainCount: 1000,
  logRetainDays: 30,
  mockCorsEnabled: true,
  serverAddress: ''
})

// ========== 数据加载 ==========
async function loadSettings() {
  loading.value = true
  try {
    const data = await getSettings()
    form.logRetainMode = data.logRetainMode || 'count'
    form.logRetainCount = data.logRetainCount || 1000
    form.logRetainDays = data.logRetainDays || 30
    form.mockCorsEnabled = data.mockCorsEnabled !== false
    form.serverAddress = data.serverAddress || ''
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

async function loadStorageStats() {
  try {
    storageStats.value = await getFileStorageStats()
  } catch (err) {
    // 拦截器已处理错误提示
  }
}

// ========== 保存 ==========
async function handleSave() {
  saving.value = true
  try {
    await saveSettings({
      logRetainMode: form.logRetainMode,
      logRetainCount: form.logRetainCount,
      logRetainDays: form.logRetainDays,
      mockCorsEnabled: form.mockCorsEnabled,
      serverAddress: form.serverAddress
    })
    ElMessage.success('设置已保存')
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    saving.value = false
  }
}

async function handleScanOrphans() {
  orphanScanning.value = true
  try {
    orphanResult.value = await scanOrphanFiles()
    await loadStorageStats()
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    orphanScanning.value = false
  }
}

async function handleCleanOrphans() {
  if (!orphanResult.value || orphanResult.value.orphanCount === 0) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定清理 ${orphanResult.value.orphanCount} 个孤儿文件？该操作不可恢复。`,
      '确认清理',
      { type: 'warning' }
    )
    orphanCleaning.value = true
    const result = await cleanOrphanFiles()
    ElMessage.success(`已清理 ${result.deletedCount} 个孤儿文件`)
    orphanResult.value = await scanOrphanFiles()
    await loadStorageStats()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      // 拦截器已处理错误提示
    }
  } finally {
    orphanCleaning.value = false
  }
}

function formatSize(size) {
  const bytes = Number(size || 0)
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(timestamp) {
  if (!timestamp) {
    return '-'
  }
  const date = new Date(timestamp)
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// ========== 初始化 ==========
onMounted(() => {
  loadSettings()
  loadStorageStats()
})
</script>

<style lang="scss" scoped>
.page-settings {
  padding: 0;
  max-width: 720px;
}

// ========== 设置卡片 ==========
// 扁平化：去阴影 + 去边框，靠页面底色与白色卡片色差区分；圆角收紧到 12px
.settings-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 28px 32px;
}

// ========== 区块 ==========
.settings-section {
  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: #1B2559;
    margin: 0 0 4px;
  }

  .section-desc {
    font-size: 13px;
    color: #A3AED0;
    margin: 0 0 20px;
  }
}

.section-divider {
  height: 1px;
  background: #F1F5F9;
  margin: 24px 0;
}

// ========== 表单项 ==========
.settings-form {
  :deep(.el-form-item__label) {
    font-size: 14px;
    font-weight: 500;
    color: #4A5568;
  }

  :deep(.el-radio__label) {
    color: #4A5568;
  }

  :deep(.el-input-number) {
    width: 200px;
  }
}

.input-hint {
  margin-left: 12px;
  font-size: 13px;
  color: #A3AED0;
}

.cors-switch-row {
  padding: 4px 0;
}

.file-maintenance-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.orphan-summary {
  display: flex;
  gap: 20px;
  margin-top: 14px;
  font-size: 13px;
  color: #4A5568;
}

.storage-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  margin-top: 16px;
  padding: 14px 0 2px;
  border-top: 1px solid #F1F5F9;
}

.storage-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.storage-stat__label {
  font-size: 12px;
  color: #A3AED0;
}

.storage-stat__value {
  font-size: 13px;
  color: #4A5568;
  overflow-wrap: anywhere;
}

.path-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.orphan-table {
  margin-top: 14px;
  width: 100%;
}

// ========== 保存按钮 ==========
.settings-footer {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #F1F5F9;
}

.save-btn {
  height: 40px;
  padding: 0 32px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  background: #6366F1;
  transition: all 0.2s ease;

  &:hover,
  &:focus {
    background: #4F46E5;
    transform: translateY(-1px);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
  }

  &:active {
    transform: translateY(0);
  }
}
</style>
