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
        <!-- AI 助手 -->
        <div class="settings-section">
          <h3 class="section-title">
            AI 助手
            <span class="ai-badge">Beta</span>
          </h3>
          <p class="section-desc">配置 MockHub 内置 AI 助手使用的模型服务与可用能力，仅超级管理员可见。</p>

          <div class="ai-switch-row">
            <div>
              <div class="ai-switch-title">启用 AI 助手</div>
              <div class="ai-switch-desc">关闭后隐藏全部 AI 入口，不影响已有接口与规则</div>
            </div>
            <div class="ai-switch-actions">
              <span class="ai-state" :class="{ 'ai-state--on': aiForm.enabled }">
                {{ aiForm.enabled ? '运行中' : '已停用' }}
              </span>
              <el-switch v-model="aiForm.enabled" />
            </div>
          </div>

          <template v-if="aiForm.enabled">
            <el-form-item label="Provider">
              <el-select v-model="aiForm.provider" style="width: 420px" @change="onProviderChange">
                <el-option label="OpenAI 兼容（OpenAI / DeepSeek / Ollama）" value="OPENAI_COMPATIBLE" />
                <el-option label="内置演示模型（离线，无需 API Key）" value="MOCK" />
              </el-select>
              <span class="input-hint">
                {{ aiForm.provider === 'MOCK' ? '本地模板生成，适合演示与离线环境' : '请求从服务端发出，浏览器不直接访问模型服务' }}
              </span>
            </el-form-item>

            <template v-if="aiForm.provider === 'OPENAI_COMPATIBLE'">
              <el-form-item label="Base URL">
                <el-input v-model="aiForm.baseUrl" placeholder="https://api.deepseek.com/v1" style="width: 420px" />
              </el-form-item>
              <el-form-item label="Model">
                <el-input v-model="aiForm.model" placeholder="deepseek-chat" style="width: 420px" />
              </el-form-item>
              <el-form-item label="API Key">
                <div class="ai-key-row">
                  <el-input
                    v-model="aiForm.apiKey"
                    type="password"
                    show-password
                    :disabled="aiClearKey"
                    :placeholder="aiKeyConfigured ? '已配置，留空保持不变' : '请输入 API Key'"
                    style="width: 420px"
                  />
                  <el-tag v-if="aiClearKey" type="warning" effect="plain" round>保存后清除</el-tag>
                  <el-tag v-else-if="aiKeyConfigured" type="success" effect="plain" round>已配置 {{ aiKeyMask }}</el-tag>
                  <el-button v-if="aiKeyConfigured && !aiClearKey" text type="danger" @click="clearAiKey">清除</el-button>
                  <el-button v-if="aiClearKey" text @click="aiClearKey = false">撤销</el-button>
                </div>
                <div class="input-hint">仅写入不回显，保存后前端只返回掩码；不会出现在日志或 localStorage 中</div>
              </el-form-item>
              <el-form-item label="连接测试">
                <div class="ai-test-row">
                  <el-button :loading="aiTesting" @click="handleTestAi">
                    <el-icon><Connection /></el-icon>
                    测试连接
                  </el-button>
                  <span v-if="aiTestResult" class="ai-test-ok">
                    <el-icon><CircleCheck /></el-icon>
                    连接正常 · {{ aiTestResult.latencyMs }} ms
                    <span class="ai-test-model">{{ aiTestResult.model }}</span>
                  </span>
                </div>
              </el-form-item>
            </template>
          </template>

          <el-form-item label="能力开关">
            <div class="ai-cap-list">
              <div class="ai-cap-row">
                <div>
                  <div class="ai-cap-title">生成响应体</div>
                  <div class="ai-cap-desc">在返回体编辑器里根据接口信息生成 JSON / XML 示例</div>
                </div>
                <el-switch v-model="aiForm.capResponseBody" />
              </div>
              <div class="ai-cap-row">
                <div>
                  <div class="ai-cap-title">生成条件规则</div>
                  <div class="ai-cap-desc">把自然语言描述转成现有 conditions 结构，可逐条确认</div>
                </div>
                <el-switch v-model="aiForm.capConditions" />
              </div>
              <div class="ai-cap-row">
                <div>
                  <div class="ai-cap-title">解释匹配结果</div>
                  <div class="ai-cap-desc">在请求日志里解释某次请求为什么命中 / 未命中</div>
                </div>
                <el-switch v-model="aiForm.capExplain" />
              </div>
              <div class="ai-cap-row">
                <div>
                  <div class="ai-cap-title">
                    创建接口草稿
                    <span class="ai-phase">Phase 2</span>
                  </div>
                  <div class="ai-cap-desc">在对话中创建接口草稿，需回到确认卡片才会写入</div>
                </div>
                <el-switch v-model="aiForm.capDrafts" />
              </div>
            </div>
          </el-form-item>

          <el-form-item label="使用范围">
            <div class="ai-scope-wrap">
              <el-radio-group v-model="aiForm.accessScope" class="ai-scope">
                <el-radio value="ALL_MEMBERS">所有成员（继承各自权限）</el-radio>
                <el-radio value="SUPER_ADMIN_ONLY">仅超级管理员</el-radio>
              </el-radio-group>
              <div class="input-hint" style="margin-left: 0">AI 始终继承当前登录用户的权限，团队数据保持隔离</div>
            </div>
          </el-form-item>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="ai-security"
            title="数据安全"
            description="发送给模型前会移除 Authorization、Cookie、token、password 等敏感字段；API Key 仅保存在服务端。所有 AI 写操作都会记录到操作日志。"
          />

          <div class="ai-save-row">
            <el-button type="primary" :loading="aiSaving" @click="handleSaveAi">保存 AI 配置</el-button>
            <span v-if="aiSettingsUpdatedAt" class="input-hint">最近保存：{{ aiSettingsUpdatedAt }}</span>
          </div>
        </div>

        <div class="section-divider"></div>

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
import { Connection, CircleCheck } from '@element-plus/icons-vue'
import {
  cleanOrphanFiles,
  getFileStorageStats,
  getSettings,
  saveSettings,
  scanOrphanFiles
} from '@/api/settings'
import { getAiSettings, saveAiSettings, testAiConnection } from '@/api/ai'
import { useAiStore } from '@/stores/ai'

// ========== 状态 ==========
const aiStore = useAiStore()
const loading = ref(false)
const saving = ref(false)
const orphanScanning = ref(false)
const orphanCleaning = ref(false)
const orphanResult = ref(null)
const storageStats = ref(null)

// AI 配置（与全局设置分开保存）
const aiForm = reactive({
  enabled: false,
  provider: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  model: '',
  apiKey: '',
  capResponseBody: true,
  capConditions: true,
  capExplain: true,
  capDrafts: true,
  accessScope: 'ALL_MEMBERS'
})
const aiLoading = ref(false)
const aiSaving = ref(false)
const aiTesting = ref(false)
const aiKeyConfigured = ref(false)
const aiKeyMask = ref('')
const aiClearKey = ref(false)
const aiTestResult = ref(null)
const aiSettingsUpdatedAt = ref('')

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

async function loadAiSettings() {
  aiLoading.value = true
  try {
    const data = await getAiSettings()
    aiForm.enabled = !!data.enabled
    aiForm.provider = data.provider || 'OPENAI_COMPATIBLE'
    aiForm.baseUrl = data.baseUrl || ''
    aiForm.model = data.model || ''
    aiForm.apiKey = ''
    aiForm.capResponseBody = !!data.capResponseBody
    aiForm.capConditions = !!data.capConditions
    aiForm.capExplain = !!data.capExplain
    aiForm.capDrafts = !!data.capDrafts
    aiForm.accessScope = data.accessScope || 'ALL_MEMBERS'
    aiKeyConfigured.value = !!data.keyConfigured
    aiKeyMask.value = data.keyMask || ''
    aiSettingsUpdatedAt.value = data.updatedAt || ''
    aiClearKey.value = false
  } catch (e) {
    // 拦截器已处理错误提示
  } finally {
    aiLoading.value = false
  }
}

async function handleSaveAi() {
  aiSaving.value = true
  try {
    const payload = {
      enabled: aiForm.enabled,
      provider: aiForm.provider,
      baseUrl: aiForm.baseUrl,
      model: aiForm.model,
      apiKey: aiClearKey.value ? '' : (aiForm.apiKey ? aiForm.apiKey : null),
      capResponseBody: aiForm.capResponseBody,
      capConditions: aiForm.capConditions,
      capExplain: aiForm.capExplain,
      capDrafts: aiForm.capDrafts,
      accessScope: aiForm.accessScope
    }
    const saved = await saveAiSettings(payload)
    aiKeyConfigured.value = !!saved.keyConfigured
    aiKeyMask.value = saved.keyMask || ''
    aiSettingsUpdatedAt.value = saved.updatedAt || ''
    aiForm.apiKey = ''
    aiClearKey.value = false
    aiTestResult.value = null
    await aiStore.load(true)
    ElMessage.success('AI 配置已保存')
  } catch (e) {
    // 拦截器已处理错误提示
  } finally {
    aiSaving.value = false
  }
}

async function handleTestAi() {
  aiTesting.value = true
  aiTestResult.value = null
  try {
    aiTestResult.value = await testAiConnection()
    ElMessage.success('连接正常')
  } catch (e) {
    // 拦截器已处理错误提示
  } finally {
    aiTesting.value = false
  }
}

function clearAiKey() {
  aiClearKey.value = true
  aiForm.apiKey = ''
}

function onProviderChange() {
  aiTestResult.value = null
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
  loadAiSettings()
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
.ai-badge {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 0 6px;
  height: 18px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 700;
  color: #8B5CF6;
  background: #F5F3FF;
  border: 1px solid #E7E3FD;
  vertical-align: middle;
}

.ai-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0 16px;
}

.ai-switch-title {
  font-size: 14px;
  font-weight: 500;
  color: #1B2559;
}

.ai-switch-desc {
  font-size: 12px;
  color: #A3AED0;
  margin-top: 2px;
}

.ai-switch-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-state {
  font-size: 12px;
  color: #A3AED0;
}

.ai-state--on {
  color: #10B981;
}

.ai-key-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ai-test-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-test-ok {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: #0F9D6E;
}

.ai-test-model {
  color: #A3AED0;
  font-size: 11.5px;
}

.ai-cap-list {
  width: 100%;
  border: 1px solid #F1F5F9;
  border-radius: 10px;
  overflow: hidden;
}

.ai-cap-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  background: #fff;
}

.ai-cap-row + .ai-cap-row {
  border-top: 1px solid #F8FAFC;
}

.ai-cap-title {
  font-size: 13px;
  color: #1B2559;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-cap-desc {
  font-size: 11.5px;
  color: #A3AED0;
  margin-top: 2px;
}

.ai-phase {
  font-size: 10px;
  color: #A3AED0;
  background: #F7F8FA;
  border-radius: 5px;
  padding: 0 5px;
  line-height: 16px;
}

.ai-scope-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.ai-scope {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.ai-scope + .input-hint {
  display: block;
  margin-left: 0;
  margin-top: 4px;
}

.ai-security {
  margin-top: 4px;
}

.ai-save-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

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