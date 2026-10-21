<!--
  Settings.vue — 全局设置页面
  一个「全局设置」入口 + 左侧分类导航：AI 供应商 / 助手与能力 / MCP Server / 基础设置 / 日志与保留 / 文件存储
-->
<template>
  <div class="page-settings">
    <div class="settings-layout" v-loading="loading || aiLoading">
      <aside class="settings-nav">
        <div v-for="group in navGroups" :key="group.label" class="nav-group">
          <div class="nav-group__title">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ 'nav-item--on': activeSection === item.key }"
            @click="activeSection = item.key"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
          </button>
        </div>
        <div class="settings-nav__hint">仅超级管理员可修改全局设置，保存后对全部团队生效。</div>
      </aside>

      <div class="settings-card">
        <el-form label-position="top" class="settings-form">
          <!-- AI 供应商 -->
          <section v-show="activeSection === 'provider'" class="settings-section">
            <h3 class="section-title">AI 供应商 <span class="api-chip">API 接入</span></h3>
            <p class="section-desc">选择模型服务商，用 API Key 接入。请求全部由服务端发出，浏览器不会直接访问模型服务。</p>

            <div v-if="aiForm.provider === 'MOCK'" class="mock-banner">
              <div class="mock-banner__logo">M</div>
              <div class="mock-banner__main">
                <div class="mock-banner__title">正在使用内置演示模型</div>
                <div class="mock-banner__desc">本地模板生成，不需要 API Key；适合演示、离线环境和 UI 验证。</div>
              </div>
              <el-button text @click="useApiProvider">改用 API 供应商</el-button>
            </div>

            <template v-else>
              <div class="provider-grid" v-loading="providersLoading">
                <button
                  v-for="p in aiProviders"
                  :key="p.key"
                  type="button"
                  class="provider-card"
                  :class="{ 'provider-card--on': aiForm.vendor === p.key }"
                  @click="selectVendor(p)"
                >
                  <span class="provider-logo" :style="providerLogoStyle(p)">{{ providerLetter(p) }}</span>
                  <span class="provider-body">
                    <span class="provider-name">
                      {{ p.name }}
                      <span v-if="p.local" class="provider-local">本地</span>
                    </span>
                    <span class="provider-host">{{ hostOf(p.baseUrl) }}</span>
                  </span>
                  <el-icon v-if="aiForm.vendor === p.key" class="provider-check"><Check /></el-icon>
                </button>
              </div>
              <div class="input-hint provider-hint">选择供应商会自动填入默认地址与模型，仍可手动修改；自定义用于任意 OpenAI 兼容服务。</div>

              <el-form-item label="API 地址">
                <el-input v-model="aiForm.baseUrl" class="ai-field" placeholder="https://api.deepseek.com/v1" />
                <div class="input-hint">服务端会拼接 /chat/completions；也可直接粘贴完整地址。</div>
              </el-form-item>

              <el-form-item label="默认模型">
                <AiModelPicker
                  v-model="aiForm.model"
                  class="ai-field"
                  :recommendations="currentPreset ? currentPreset.models : []"
                  :vendor-name="currentPreset ? currentPreset.name : '服务商'"
                  :can-fetch="canFetchModels"
                  :dirty="aiFormDirty"
                />
                <div class="input-hint">推荐列表随版本维护；可从服务商实时拉取，或手动输入模型 ID。</div>
              </el-form-item>

              <el-form-item label="API Key">
                <div class="ai-key-row">
                  <el-input
                    v-model="aiForm.apiKey"
                    type="password"
                    show-password
                    :disabled="aiClearKey && clearKeySource === 'user'"
                    :placeholder="keyPlaceholder"
                    class="ai-field"
                  />
                  <el-tag v-if="aiClearKey && !aiForm.apiKey" type="warning" effect="plain" round>{{ clearKeySource === 'vendor' ? '切换供应商，保存后清除' : '保存后清除' }}</el-tag>
                  <el-tag v-else-if="aiKeyConfigured" type="success" effect="plain" round>已配置 {{ aiKeyMask }}</el-tag>
                  <el-button v-if="aiKeyConfigured && !aiClearKey" text type="danger" @click="clearAiKey">清除</el-button>
                  <el-button v-if="aiClearKey && clearKeySource === 'user'" text @click="undoClearKey">撤销</el-button>
                  <span v-if="aiClearKey && clearKeySource === 'vendor'" class="input-hint">切换供应商后请重新填写 Key</span>
                </div>
                <div class="input-hint">仅写入不回显；Key 以 AES-GCM 密文保存在服务端，不会进入日志或 localStorage。</div>
              </el-form-item>

              <el-form-item label="连接测试">
                <div class="ai-test-row">
                  <el-button :loading="aiTesting" :disabled="aiFormDirty" @click="handleTestAi">
                    <el-icon><Connection /></el-icon>
                    测试连接
                  </el-button>
                  <span v-if="aiTestResult" class="ai-test-ok">
                    <el-icon><CircleCheck /></el-icon>
                    连接正常 · {{ aiTestResult.latencyMs }} ms
                    <span class="ai-test-model">{{ aiTestResult.model }}</span>
                  </span>
                  <span v-if="aiFormDirty" class="input-hint">保存配置后可测试</span>
                </div>
              </el-form-item>

              <el-alert
                type="info"
                :closable="false"
                show-icon
                class="ai-security"
                title="Key 只存在服务端"
                description="API Key 不会返回前端；备份数据库时请同时备份 data/.ai-key，否则已保存的 Key 无法解密。"
              />
            </template>

            <div class="ai-save-row">
              <el-button type="primary" :loading="aiSaving" @click="handleSaveAi">保存供应商配置</el-button>
              <span v-if="aiSettingsUpdatedAt" class="input-hint">最近保存：{{ aiSettingsUpdatedAt }}</span>
              <el-button v-if="aiForm.provider !== 'MOCK'" text @click="useMockProvider">使用内置演示模型</el-button>
            </div>
          </section>

          <!-- 助手与能力 -->
          <section v-show="activeSection === 'assistant'" class="settings-section">
            <h3 class="section-title">助手与能力 <span class="ai-badge">Beta</span></h3>
            <p class="section-desc">配置助手是否启用、可用的生成能力与使用范围。供应商与 Key 在「AI 供应商」里单独维护。</p>

            <div class="ai-provider-summary">
              <div class="ai-provider-summary__logo">{{ providerSummary.letter }}</div>
              <div class="ai-provider-summary__body">
                <div class="ai-provider-summary__name">{{ providerSummary.name }}</div>
                <div class="ai-provider-summary__meta">{{ providerSummary.meta }}</div>
              </div>
              <span class="ai-provider-summary__state" :class="{ 'is-on': providerSummary.on }">{{ providerSummary.state }}</span>
              <el-button text class="ai-provider-summary__link" @click="activeSection = 'provider'">
                更换供应商
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>

            <div class="ai-switch-row">
              <div>
                <div class="ai-switch-title">启用 AI 助手</div>
                <div class="ai-switch-desc">关闭后隐藏全部 AI 入口，不影响已有接口与规则</div>
              </div>
              <div class="ai-switch-actions">
                <span class="ai-state" :class="{ 'ai-state--on': aiForm.enabled }">{{ aiForm.enabled ? '运行中' : '已停用' }}</span>
                <el-switch v-model="aiForm.enabled" />
              </div>
            </div>

            <el-form-item label="能力开关">
              <div class="ai-cap-list">
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">生成响应体</div><div class="ai-cap-desc">在返回体编辑器里根据接口信息生成 JSON / XML 示例</div></div>
                  <el-switch v-model="aiForm.capResponseBody" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">生成条件规则</div><div class="ai-cap-desc">把自然语言描述转成现有 conditions 结构，可逐条确认</div></div>
                  <el-switch v-model="aiForm.capConditions" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">解释匹配结果</div><div class="ai-cap-desc">在请求日志里解释某次请求为什么命中 / 未命中</div></div>
                  <el-switch v-model="aiForm.capExplain" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">创建 / 修改接口草稿</div><div class="ai-cap-desc">在对话中创建或修改接口，需回到确认卡片才会写入</div></div>
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
              description="发送给模型前会移除 Authorization、Cookie、token、password 等敏感字段；所有 AI 写操作都会记录到操作日志。"
            />

            <div class="ai-save-row">
              <el-button type="primary" :loading="aiSaving" @click="handleSaveAi">保存助手配置</el-button>
            </div>
          </section>

          <!-- MCP Server -->
          <section v-show="activeSection === 'mcp'" class="settings-section">
            <h3 class="section-title">MCP Server</h3>
            <p class="section-desc">把 MockHub 的检索、统计与生成能力开放给 Claude、Cursor、Codex 等外部 AI 工具。</p>

            <div class="ai-switch-row">
              <div>
                <div class="ai-switch-title">启用 MCP Server</div>
                <div class="ai-switch-desc">外部客户端通过 Bearer Token 调用，权限与 Token 所属用户一致</div>
              </div>
              <div class="ai-switch-actions">
                <span class="ai-state" :class="{ 'ai-state--on': mcpForm.enabled }">{{ mcpForm.enabled ? '已启用' : '已停用' }}</span>
                <el-switch v-model="mcpForm.enabled" />
              </div>
            </div>

            <el-form-item label="服务地址">
              <div class="mcp-endpoint">
                <code>{{ mcpEndpoint }}</code>
                <el-button text size="small" @click="copyText(mcpEndpoint)">
                  <el-icon><DocumentCopy /></el-icon>
                  复制
                </el-button>
              </div>
              <div class="input-hint">客户端使用 Streamable HTTP（POST /mcp）连接；返回 JSON 响应，不支持 SSE 流。</div>
            </el-form-item>

            <el-form-item label="写操作策略">
              <el-radio-group v-model="mcpForm.writeMode" class="ai-scope">
                <el-radio value="DRAFT_ONLY">草稿 + Web 确认（推荐）</el-radio>
                <el-radio value="DIRECT">允许直接写入</el-radio>
              </el-radio-group>
              <div class="input-hint" style="margin-left: 0">草稿模式会返回确认链接，用户在 MockHub 页面确认后才落库。</div>
            </el-form-item>

            <el-form-item label="可用工具">
              <div class="ai-cap-list">
                <div v-for="tool in mcpToolOptions" :key="tool.key" class="ai-cap-row">
                  <div>
                    <div class="ai-cap-title"><code>{{ tool.name }}</code></div>
                    <div class="ai-cap-desc">{{ tool.desc }}</div>
                  </div>
                  <el-switch v-model="mcpForm[tool.key]" />
                </div>
              </div>
            </el-form-item>

            <div class="mcp-token-head">
              <div>
                <div class="ai-switch-title">访问 Token</div>
                <div class="ai-switch-desc">Token 只在创建时显示一次，可随时撤销</div>
              </div>
              <el-button type="primary" plain @click="openTokenDialog">+ 新建 Token</el-button>
            </div>

            <el-table :data="mcpTokens" size="small" class="mcp-token-table" empty-text="还没有 Token">
              <el-table-column prop="name" label="名称" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.name }}</span>
                  <span class="mcp-token-owner">{{ row.ownerName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="tokenPrefix" label="前缀" width="120" />
              <el-table-column label="最后使用" width="150">
                <template #default="{ row }">{{ row.lastUsedAt ? formatTime(row.lastUsedAt) : '从未' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="70">
                <template #default="{ row }"><span :class="tokenStatusClass(row)">{{ tokenStatus(row) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="72">
                <template #default="{ row }">
                  <el-button v-if="!row.revoked" text type="danger" size="small" @click="revokeToken(row)">撤销</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="ai-save-row">
              <el-button type="primary" :loading="mcpSaving" @click="handleSaveMcp">保存 MCP 配置</el-button>
            </div>
          </section>

          <!-- 基础设置 -->
          <section v-show="activeSection === 'basic'" class="settings-section">
            <h3 class="section-title">基础设置</h3>
            <p class="section-desc">服务地址与 Mock 跨域。日志保留、文件存储已经拆到左侧独立分类。</p>

            <el-form-item label="服务器地址">
              <el-input v-model="form.serverAddress" class="ai-field" placeholder="如：http://192.168.1.100:8080" clearable />
              <div class="input-hint">用于拼接 Mock URL、MCP 确认链接与导出文件中的地址，为空时自动检测内网 IP。</div>
            </el-form-item>

            <el-form-item label="Mock CORS">
              <div class="cors-switch-row">
                <el-switch v-model="form.mockCorsEnabled" active-text="已启用" inactive-text="已禁用" />
              </div>
              <div class="input-hint" style="margin-left: 0">启用后 Mock 接口允许所有跨域请求，管理接口仍按登录态校验。</div>
            </el-form-item>

            <div class="ai-save-row">
              <el-button type="primary" :loading="saving" @click="handleSave">保存基础设置</el-button>
            </div>
          </section>

          <!-- 日志与保留 -->
          <section v-show="activeSection === 'logs'" class="settings-section">
            <h3 class="section-title">日志与保留</h3>
            <p class="section-desc">配置操作日志和请求日志的自动清理规则。</p>

            <el-form-item label="保留模式">
              <el-radio-group v-model="form.logRetainMode">
                <el-radio value="count">按条数保留</el-radio>
                <el-radio value="days">按天数保留</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.logRetainMode === 'count'" label="保留条数">
              <el-input-number v-model="form.logRetainCount" :min="100" :max="100000" :step="100" controls-position="right" />
              <span class="input-hint">超出部分将自动删除最旧的记录</span>
            </el-form-item>

            <el-form-item v-if="form.logRetainMode === 'days'" label="保留天数">
              <el-input-number v-model="form.logRetainDays" :min="1" :max="365" :step="1" controls-position="right" />
              <span class="input-hint">超出天数的日志将被自动清理</span>
            </el-form-item>

            <div class="ai-save-row">
              <el-button type="primary" :loading="saving" @click="handleSave">保存日志设置</el-button>
            </div>
          </section>

          <!-- 文件存储 -->
          <section v-show="activeSection === 'files'" class="settings-section">
            <h3 class="section-title">文件存储</h3>
            <p class="section-desc">扫描并清理未被任何文件类型返回体引用的存储文件。</p>

            <div class="file-maintenance-actions">
              <el-button :loading="orphanScanning" @click="handleScanOrphans">扫描孤儿文件</el-button>
              <el-button type="danger" plain :disabled="!orphanResult || orphanResult.orphanCount === 0" :loading="orphanCleaning" @click="handleCleanOrphans">清理孤儿文件</el-button>
            </div>

            <div v-if="storageStats" class="storage-stats">
              <div class="storage-stat"><span class="storage-stat__label">存储目录</span><span class="storage-stat__value path-value">{{ storageStats.rootDir }}</span></div>
              <div class="storage-stat"><span class="storage-stat__label">上传上限</span><span class="storage-stat__value">{{ formatSize(storageStats.maxSizeBytes) }}</span></div>
              <div class="storage-stat"><span class="storage-stat__label">文件总数</span><span class="storage-stat__value">{{ storageStats.totalFileCount }} 个 / {{ formatSize(storageStats.totalFileSize) }}</span></div>
              <div class="storage-stat"><span class="storage-stat__label">引用文件</span><span class="storage-stat__value">{{ storageStats.referencedFileCount }} 个</span></div>
              <div class="storage-stat"><span class="storage-stat__label">孤儿文件</span><span class="storage-stat__value">{{ storageStats.orphanCount }} 个 / {{ formatSize(storageStats.orphanSize) }}</span></div>
            </div>

            <div v-if="orphanResult" class="orphan-summary">
              <span>发现 {{ orphanResult.orphanCount }} 个孤儿文件</span>
              <span>合计 {{ formatSize(orphanResult.orphanSize) }}</span>
            </div>

            <el-table v-if="orphanResult && orphanResult.files && orphanResult.files.length" :data="orphanResult.files" size="small" class="orphan-table" max-height="260">
              <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
              <el-table-column prop="filePath" label="存储路径" min-width="240" show-overflow-tooltip />
              <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatSize(row.fileSize) }}</template></el-table-column>
              <el-table-column label="修改时间" width="170"><template #default="{ row }">{{ formatTime(row.lastModifiedAt) }}</template></el-table-column>
            </el-table>
          </section>
        </el-form>
      </div>
    </div>

    <el-dialog v-model="tokenDialogVisible" title="新建 MCP Token" width="440px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="tokenForm.name" maxlength="60" placeholder="如：Cursor 本地" /></el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="tokenForm.expiresInDays" style="width: 100%">
            <el-option label="90 天" :value="90" />
            <el-option label="30 天" :value="30" />
            <el-option label="7 天" :value="7" />
            <el-option label="永不过期" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tokenDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tokenCreating" @click="handleCreateToken">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createdTokenVisible" title="Token 已创建" width="500px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="请立即复制保存，关闭后无法再次查看" />
      <div class="mcp-token-value">
        <code>{{ createdToken.token }}</code>
        <el-button text @click="copyText(createdToken.token)">复制</el-button>
      </div>
      <template #footer><el-button type="primary" @click="createdTokenVisible = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, Check, CircleCheck, Connection, Cpu, Document, DocumentCopy,
  Folder, MagicStick, Setting
} from '@element-plus/icons-vue'
import AiModelPicker from '@/components/ai/AiModelPicker.vue'
import { cleanOrphanFiles, getFileStorageStats, getSettings, saveSettings, scanOrphanFiles } from '@/api/settings'
import { getAiProviders, getAiSettings, saveAiSettings, testAiConnection } from '@/api/ai'
import { getMcpSettings, saveMcpSettings, getMcpTokens, createMcpToken, revokeMcpToken } from '@/api/mcp'
import { useAiStore } from '@/stores/ai'

const aiStore = useAiStore()
const loading = ref(false)
const saving = ref(false)
const aiLoading = ref(false)
const orphanScanning = ref(false)
const orphanCleaning = ref(false)
const orphanResult = ref(null)
const storageStats = ref(null)

const activeSection = ref('provider')
const navGroups = [
  { label: 'AI 能力', items: [
    { key: 'provider', label: 'AI 供应商', icon: Connection, badge: '新' },
    { key: 'assistant', label: '助手与能力', icon: MagicStick },
    { key: 'mcp', label: 'MCP Server', icon: Cpu }
  ] },
  { label: '系统', items: [
    { key: 'basic', label: '基础设置', icon: Setting },
    { key: 'logs', label: '日志与保留', icon: Document },
    { key: 'files', label: '文件存储', icon: Folder }
  ] }
]

const aiProviders = ref([])
const providersLoading = ref(false)
const aiForm = reactive({
  enabled: false, provider: 'OPENAI_COMPATIBLE', vendor: 'deepseek',
  baseUrl: '', model: '', apiKey: '',
  capResponseBody: true, capConditions: true, capExplain: true, capDrafts: true,
  accessScope: 'ALL_MEMBERS'
})
const aiSaved = reactive({ provider: 'OPENAI_COMPATIBLE', vendor: 'deepseek', baseUrl: '', model: '', keyConfigured: false })
const aiSaving = ref(false)
const aiTesting = ref(false)
const aiKeyConfigured = ref(false)
const aiKeyMask = ref('')
const aiClearKey = ref(false)
const clearKeySource = ref('')
const aiTestResult = ref(null)
const aiSettingsUpdatedAt = ref('')

const providerColors = {
  deepseek: { background: '#EEF0FF', color: '#4F46E5' },
  kimi: { background: '#F3F4F6', color: '#111827' },
  minimax: { background: '#FFF7ED', color: '#C2410C' },
  glm: { background: '#EFF6FF', color: '#2563EB' },
  qwen: { background: '#F5F3FF', color: '#7C3AED' },
  openai: { background: '#ECFDF5', color: '#059669' },
  ollama: { background: '#F1F5F9', color: '#475569' },
  custom: { background: '#F7F8FA', color: '#64748B' }
}

const currentPreset = computed(() => aiProviders.value.find(p => p.key === aiForm.vendor) || null)

const keyPlaceholder = computed(() => {
  if (aiKeyConfigured.value) return '已配置，留空保持不变'
  if (aiForm.vendor === 'ollama') return '可选，Ollama 默认不需要'
  return '请输入 API Key'
})

const aiFormDirty = computed(() => {
  return aiForm.provider !== aiSaved.provider
    || aiForm.vendor !== aiSaved.vendor
    || (aiForm.baseUrl || '') !== (aiSaved.baseUrl || '')
    || (aiForm.model || '') !== (aiSaved.model || '')
    || !!aiForm.apiKey
    || aiClearKey.value
})

const canFetchModels = computed(() => {
  if (aiFormDirty.value || aiForm.provider !== 'OPENAI_COMPATIBLE') return false
  return aiKeyConfigured.value || aiForm.vendor === 'ollama'
})

const providerSummary = computed(() => {
  if (aiForm.provider === 'MOCK') {
    return { letter: 'M', name: '内置演示模型', meta: '本地模板生成 · 无需 API Key', state: '运行中', on: true }
  }
  const preset = currentPreset.value
  const name = preset ? preset.name : '自定义供应商'
  const ready = aiKeyConfigured.value || aiForm.vendor === 'ollama'
  return {
    letter: name.slice(0, 1),
    name,
    meta: (aiForm.model || '未配置模型') + ' · ' + (ready ? (aiForm.vendor === 'ollama' ? '本地模型' : 'API Key 已配置') : '未配置 Key'),
    state: ready ? '已连接' : '未配置',
    on: ready
  }
})

const mcpForm = reactive({
  enabled: false, writeMode: 'DRAFT_ONLY',
  toolSearchApis: true, toolGetApiDetail: true, toolGetApiStats: true, toolCreateApiDraft: true,
  toolApplyApiDraft: false, toolGenerateResponseBody: true, toolGenerateConditions: true, toolExplainMatchResult: true
})
const mcpTokens = ref([])
const mcpSaving = ref(false)
const tokenDialogVisible = ref(false)
const tokenCreating = ref(false)
const tokenForm = reactive({ name: '', expiresInDays: 90 })
const createdTokenVisible = ref(false)
const createdToken = ref({ token: '' })

const mcpToolOptions = [
  { key: 'toolSearchApis', name: 'mockhub.search_apis', desc: '按关键字搜索接口' },
  { key: 'toolGetApiDetail', name: 'mockhub.get_api_detail', desc: '读取接口与返回体配置' },
  { key: 'toolGetApiStats', name: 'mockhub.get_api_stats', desc: '查询调用次数与热门接口' },
  { key: 'toolCreateApiDraft', name: 'mockhub.create_api_draft', desc: '根据参数创建接口草稿（支持多返回体）' },
  { key: 'toolUpdateApiDraft', name: 'mockhub.update_api_draft', desc: '修改接口、替换或追加返回体' },
  { key: 'toolApplyApiDraft', name: 'mockhub.apply_api_draft', desc: '应用草稿（默认需 Web 确认）' },
  { key: 'toolGenerateResponseBody', name: 'mockhub.generate_response_body', desc: '生成 JSON / XML 响应体' },
  { key: 'toolGenerateConditions', name: 'mockhub.generate_conditions', desc: '自然语言转条件规则' },
  { key: 'toolExplainMatchResult', name: 'mockhub.explain_match_result', desc: '解释一次请求的命中链路' }
]

const form = reactive({ logRetainMode: 'count', logRetainCount: 1000, logRetainDays: 30, mockCorsEnabled: true, serverAddress: '' })

const mcpEndpoint = computed(() => {
  const base = (form.serverAddress || window.location.origin || '').replace(/\/$/, '')
  return base + '/mcp'
})

function inferVendor(baseUrl) {
  const url = String(baseUrl || '').toLowerCase()
  if (url.includes('api.deepseek.com')) return 'deepseek'
  if (url.includes('moonshot')) return 'kimi'
  if (url.includes('minimax')) return 'minimax'
  if (url.includes('bigmodel')) return 'glm'
  if (url.includes('dashscope')) return 'qwen'
  if (url.includes('api.openai.com')) return 'openai'
  if (url.includes('11434')) return 'ollama'
  return 'custom'
}

function providerLetter(p) { return (p.name || '?').slice(0, 1) }
function providerLogoStyle(p) { return providerColors[p.key] || providerColors.custom }
function hostOf(baseUrl) {
  if (!baseUrl) return 'OpenAI 兼容接口'
  return String(baseUrl).replace(/^https?:\/\//, '').replace(/\/.*$/, '')
}

function selectVendor(p) {
  if (aiForm.provider === 'OPENAI_COMPATIBLE' && aiForm.vendor === p.key) return
  aiForm.provider = 'OPENAI_COMPATIBLE'
  aiForm.vendor = p.key
  aiForm.baseUrl = p.baseUrl || ''
  aiForm.model = p.defaultModel || ''
  aiForm.apiKey = ''
  aiTestResult.value = null
  aiClearKey.value = aiSaved.keyConfigured && p.key !== aiSaved.vendor
  clearKeySource.value = aiClearKey.value ? 'vendor' : ''
  aiKeyConfigured.value = p.key === aiSaved.vendor ? aiSaved.keyConfigured : false
}

function useMockProvider() {
  aiForm.provider = 'MOCK'
  aiForm.apiKey = ''
  aiClearKey.value = false
  clearKeySource.value = ''
  aiTestResult.value = null
}

function useApiProvider() {
  aiForm.provider = 'OPENAI_COMPATIBLE'
  if (!aiForm.vendor || aiForm.vendor === 'custom') {
    const first = aiProviders.value.find(p => p.key !== 'custom') || aiProviders.value[0]
    if (first) {
      aiForm.vendor = first.key
      aiForm.baseUrl = first.baseUrl || ''
      aiForm.model = first.defaultModel || ''
    }
  }
  aiClearKey.value = false
  clearKeySource.value = ''
  aiKeyConfigured.value = aiSaved.keyConfigured && aiForm.vendor === aiSaved.vendor
  aiTestResult.value = null
}

async function loadProviders() {
  providersLoading.value = true
  try { aiProviders.value = await getAiProviders() } catch (e) { /* 拦截器已处理 */ }
  finally { providersLoading.value = false }
}

async function loadSettings() {
  loading.value = true
  try {
    const data = await getSettings()
    form.logRetainMode = data.logRetainMode || 'count'
    form.logRetainCount = data.logRetainCount || 1000
    form.logRetainDays = data.logRetainDays || 30
    form.mockCorsEnabled = data.mockCorsEnabled !== false
    form.serverAddress = data.serverAddress || ''
  } catch (err) { /* 拦截器已处理 */ }
  finally { loading.value = false }
}

async function loadAiSettings() {
  aiLoading.value = true
  try {
    const data = await getAiSettings()
    aiForm.enabled = !!data.enabled
    aiForm.provider = data.provider || 'OPENAI_COMPATIBLE'
    aiForm.vendor = (data.vendor || inferVendor(data.baseUrl) || 'custom').toLowerCase()
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
    clearKeySource.value = ''
    Object.assign(aiSaved, {
      provider: aiForm.provider, vendor: aiForm.vendor, baseUrl: aiForm.baseUrl,
      model: aiForm.model, keyConfigured: !!data.keyConfigured
    })
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiLoading.value = false }
}

async function handleSaveAi() {
  aiSaving.value = true
  try {
    const payload = {
      enabled: aiForm.enabled,
      provider: aiForm.provider,
      vendor: aiForm.vendor,
      baseUrl: aiForm.baseUrl,
      model: aiForm.model,
      apiKey: aiForm.apiKey ? aiForm.apiKey : (aiClearKey.value ? '' : null),
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
    clearKeySource.value = ''
    aiTestResult.value = null
    Object.assign(aiSaved, {
      provider: aiForm.provider, vendor: aiForm.vendor, baseUrl: aiForm.baseUrl,
      model: aiForm.model, keyConfigured: !!saved.keyConfigured
    })
    await aiStore.load(true)
    ElMessage.success('AI 配置已保存')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiSaving.value = false }
}

async function handleTestAi() {
  if (aiFormDirty.value) { ElMessage.warning('请先保存配置，再测试连接'); return }
  if (aiForm.provider === 'MOCK') { ElMessage.info('内置演示模型无需连接测试'); return }
  if (!aiKeyConfigured.value && aiForm.vendor !== 'ollama') {
    ElMessage.warning('请先填写并保存 API Key')
    return
  }
  aiTesting.value = true
  aiTestResult.value = null
  try {
    aiTestResult.value = await testAiConnection()
    ElMessage.success('连接正常')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiTesting.value = false }
}

function clearAiKey() { aiClearKey.value = true; clearKeySource.value = 'user'; aiForm.apiKey = '' }
function undoClearKey() { aiClearKey.value = false; clearKeySource.value = '' }

async function loadMcpSettings() {
  try {
    const data = await getMcpSettings()
    mcpForm.enabled = !!data.enabled
    mcpForm.writeMode = data.writeMode || 'DRAFT_ONLY'
    mcpForm.toolSearchApis = !!data.toolSearchApis
    mcpForm.toolGetApiDetail = !!data.toolGetApiDetail
    mcpForm.toolGetApiStats = !!data.toolGetApiStats
    mcpForm.toolCreateApiDraft = !!data.toolCreateApiDraft
    mcpForm.toolUpdateApiDraft = !!data.toolUpdateApiDraft
    mcpForm.toolApplyApiDraft = !!data.toolApplyApiDraft
    mcpForm.toolGenerateResponseBody = !!data.toolGenerateResponseBody
    mcpForm.toolGenerateConditions = !!data.toolGenerateConditions
    mcpForm.toolExplainMatchResult = !!data.toolExplainMatchResult
  } catch (e) { /* 拦截器已处理 */ }
}

async function loadMcpTokens() {
  try { mcpTokens.value = await getMcpTokens() } catch (e) { mcpTokens.value = [] }
}

async function handleSaveMcp() {
  mcpSaving.value = true
  try {
    await saveMcpSettings({ ...mcpForm })
    ElMessage.success('MCP 配置已保存')
  } catch (e) { /* 拦截器已处理 */ }
  finally { mcpSaving.value = false }
}

function openTokenDialog() {
  tokenForm.name = ''
  tokenForm.expiresInDays = 90
  tokenDialogVisible.value = true
}

async function handleCreateToken() {
  if (!tokenForm.name.trim()) { ElMessage.warning('请填写 Token 名称'); return }
  tokenCreating.value = true
  try {
    createdToken.value = await createMcpToken({ name: tokenForm.name.trim(), expiresInDays: tokenForm.expiresInDays })
    tokenDialogVisible.value = false
    createdTokenVisible.value = true
    await loadMcpTokens()
  } catch (e) { /* 拦截器已处理 */ }
  finally { tokenCreating.value = false }
}

async function revokeToken(row) {
  try {
    await ElMessageBox.confirm('撤销后使用该 Token 的客户端会立即失效，是否继续？', '撤销 Token', { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' })
    await revokeMcpToken(row.id)
    ElMessage.success('Token 已撤销')
    await loadMcpTokens()
  } catch (e) { /* 取消或拦截器已处理 */ }
}

function tokenStatus(row) {
  if (row.revoked) return '已撤销'
  if (row.expiresAt && new Date(row.expiresAt).getTime() < Date.now()) return '已过期'
  return '生效中'
}

function tokenStatusClass(row) {
  return tokenStatus(row) === '生效中' ? 'mcp-token-status mcp-token-status--on' : 'mcp-token-status mcp-token-status--off'
}

async function copyText(text) {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') }
  catch (e) { ElMessage.warning('复制失败，请手动选择') }
}

async function loadStorageStats() {
  try { storageStats.value = await getFileStorageStats() } catch (err) { /* 拦截器已处理 */ }
}

async function handleSave() {
  saving.value = true
  try {
    await saveSettings({
      logRetainMode: form.logRetainMode, logRetainCount: form.logRetainCount, logRetainDays: form.logRetainDays,
      mockCorsEnabled: form.mockCorsEnabled, serverAddress: form.serverAddress
    })
    ElMessage.success('设置已保存')
  } catch (err) { /* 拦截器已处理 */ }
  finally { saving.value = false }
}

async function handleScanOrphans() {
  orphanScanning.value = true
  try { orphanResult.value = await scanOrphanFiles(); await loadStorageStats() }
  catch (err) { /* 拦截器已处理 */ }
  finally { orphanScanning.value = false }
}

async function handleCleanOrphans() {
  if (!orphanResult.value || orphanResult.value.orphanCount === 0) return
  try {
    await ElMessageBox.confirm('确定清理 ' + orphanResult.value.orphanCount + ' 个孤儿文件？该操作不可恢复。', '确认清理', { type: 'warning' })
    orphanCleaning.value = true
    const result = await cleanOrphanFiles()
    ElMessage.success('已清理 ' + result.deletedCount + ' 个孤儿文件')
    orphanResult.value = await scanOrphanFiles()
    await loadStorageStats()
  } catch (err) { /* 取消或拦截器已处理 */ }
  finally { orphanCleaning.value = false }
}

function formatSize(size) {
  const bytes = Number(size || 0)
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const pad = value => String(value).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
    + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
}

onMounted(() => {
  loadSettings()
  loadStorageStats()
  loadProviders()
  loadAiSettings()
  loadMcpSettings()
  loadMcpTokens()
})
</script>

<style lang="scss" scoped>
.page-settings { padding: 0; max-width: 1120px; }
.settings-layout { display: grid; grid-template-columns: 212px minmax(0, 1fr); gap: 20px; align-items: start; }
.settings-nav { position: sticky; top: 0; padding: 10px; background: #fff; border-radius: 12px; }
.nav-group + .nav-group { margin-top: 14px; padding-top: 12px; border-top: 1px solid #F6F8FB; }
.nav-group__title { padding: 6px 10px; font-size: 10.5px; font-weight: 700; letter-spacing: .6px; color: #A3AED0; text-transform: uppercase; }
.nav-item { position: relative; display: flex; align-items: center; gap: 9px; width: 100%; height: 38px; padding: 0 10px; border: none; border-radius: 8px; background: transparent; font-size: 13px; color: #4A5568; text-align: left; cursor: pointer; }
.nav-item:hover { background: #F7F8FA; }
.nav-item--on { background: #EEF0FF; color: #4F46E5; font-weight: 600; }
.nav-item--on::before { content: ""; position: absolute; left: 0; top: 8px; bottom: 8px; width: 3px; border-radius: 2px; background: #6366F1; }
.nav-item .el-icon { color: #8A93A8; }
.nav-item--on .el-icon { color: #4F46E5; }
.nav-badge { margin-left: auto; padding: 0 5px; border-radius: 5px; background: #F5F3FF; border: 1px solid #E7E3FD; color: #8B5CF6; font-size: 9.5px; font-weight: 600; line-height: 16px; }
.settings-nav__hint { margin-top: 14px; padding: 10px; border-radius: 8px; background: #FAFBFC; color: #A3AED0; font-size: 11px; line-height: 1.65; }
.settings-card { min-height: 640px; padding: 26px 30px; background: #fff; border-radius: 12px; }
.settings-section .section-title { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: #1B2559; letter-spacing: -.2px; }
.settings-section .section-desc { margin: 0 0 20px; font-size: 12.5px; color: #A3AED0; line-height: 1.75; }
.api-chip { display: inline-flex; align-items: center; height: 22px; padding: 0 8px; margin-left: 6px; border-radius: 6px; background: #EEF0FF; color: #4F46E5; font-size: 11px; font-weight: 600; vertical-align: middle; }
.settings-form :deep(.el-form-item__label) { font-size: 13px; font-weight: 600; color: #4A5568; }
.settings-form :deep(.el-radio__label) { color: #4A5568; }
.settings-form :deep(.el-input-number) { width: 200px; }
.ai-field { width: 100% !important; max-width: 520px; }
.input-hint { margin-left: 12px; font-size: 12px; color: #A3AED0; }
.provider-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.provider-card { position: relative; display: flex; align-items: center; gap: 10px; height: 74px; padding: 0 12px; border: 1px solid #EDF0F5; border-radius: 10px; background: #fff; text-align: left; cursor: pointer; }
.provider-card:hover { border-color: #DDE2EE; }
.provider-card--on { border-color: #C7D2FE; background: #FBFCFF; box-shadow: 0 0 0 2px rgba(99, 102, 241, .08); }
.provider-logo { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: 9px; font-size: 14px; font-weight: 700; }
.provider-body { min-width: 0; }
.provider-name { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #1B2559; white-space: nowrap; }
.provider-local { padding: 0 4px; border-radius: 4px; background: #F1F5F9; color: #64748B; font-size: 9px; font-weight: 600; line-height: 16px; }
.provider-host { display: block; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #A3AED0; font-size: 10.5px; }
.provider-check { position: absolute; top: 8px; right: 8px; color: #6366F1; }
.provider-hint { margin: 8px 0 18px; }
.mock-banner { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid #F1F5F9; border-radius: 10px; background: #FBFBFE; }
.mock-banner__logo { display: grid; place-items: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 10px; background: #EEF0FF; color: #4F46E5; font-size: 15px; font-weight: 700; }
.mock-banner__main { flex: 1; }
.mock-banner__title { font-size: 13px; font-weight: 600; color: #1B2559; }
.mock-banner__desc { margin-top: 2px; font-size: 11.5px; color: #A3AED0; }
.ai-badge { display: inline-flex; align-items: center; margin-left: 6px; padding: 0 6px; height: 18px; border-radius: 6px; font-size: 10.5px; font-weight: 700; color: #8B5CF6; background: #F5F3FF; border: 1px solid #E7E3FD; vertical-align: middle; }
.ai-provider-summary { display: flex; align-items: center; gap: 12px; margin: 0 0 18px; padding: 12px 14px; border: 1px solid #F1F5F9; border-radius: 10px; background: #FBFBFE; }
.ai-provider-summary__logo { display: grid; place-items: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 10px; background: #EEF0FF; color: #4F46E5; font-size: 15px; font-weight: 700; }
.ai-provider-summary__body { flex: 1; }
.ai-provider-summary__name { font-size: 13px; font-weight: 600; color: #1B2559; }
.ai-provider-summary__meta { margin-top: 1px; font-size: 11.5px; color: #A3AED0; }
.ai-provider-summary__state { font-size: 11.5px; color: #A3AED0; }
.ai-provider-summary__state.is-on { color: #10B981; }
.ai-provider-summary__state.is-on::before { content: ""; display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: #10B981; }
.ai-provider-summary__link { color: #6366F1; }
.ai-switch-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 0 16px; }
.ai-switch-title { font-size: 14px; font-weight: 500; color: #1B2559; }
.ai-switch-desc { margin-top: 2px; font-size: 12px; color: #A3AED0; }
.ai-switch-actions { display: flex; align-items: center; gap: 10px; }
.ai-state { font-size: 12px; color: #A3AED0; }
.ai-state--on { color: #10B981; }
.ai-key-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; width: 100%; }
.ai-test-row { display: flex; align-items: center; gap: 12px; }
.ai-test-ok { display: inline-flex; align-items: center; gap: 5px; font-size: 12.5px; color: #0F9D6E; }
.ai-test-model { color: #A3AED0; font-size: 11.5px; }
.ai-cap-list { width: 100%; border: 1px solid #F1F5F9; border-radius: 10px; overflow: hidden; }
.ai-cap-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 12px; background: #fff; }
.ai-cap-row + .ai-cap-row { border-top: 1px solid #F8FAFC; }
.ai-cap-title { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #1B2559; }
.ai-cap-desc { margin-top: 2px; font-size: 11.5px; color: #A3AED0; }
.ai-scope-wrap { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.ai-scope { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.ai-security { margin-top: 4px; }
.ai-save-row { display: flex; align-items: center; gap: 12px; margin-top: 18px; }
.cors-switch-row { padding: 4px 0; }
.file-maintenance-actions { display: flex; align-items: center; gap: 12px; }
.storage-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 20px; margin-top: 16px; padding: 14px 0 2px; border-top: 1px solid #F1F5F9; }
.storage-stat { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.storage-stat__label { font-size: 12px; color: #A3AED0; }
.storage-stat__value { font-size: 13px; color: #4A5568; overflow-wrap: anywhere; }
.path-value { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace; }
.orphan-summary { display: flex; gap: 20px; margin-top: 14px; font-size: 13px; color: #4A5568; }
.orphan-table { width: 100%; margin-top: 14px; }
.mcp-endpoint { display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 12px; border-radius: 10px; background: #F7F8FA; }
.mcp-endpoint code { flex: 1; font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace; font-size: 12.5px; color: #4A5568; overflow-wrap: anywhere; }
.mcp-token-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 8px 0 10px; }
.mcp-token-table { width: 100%; }
.mcp-token-owner { display: block; margin-top: 1px; font-size: 11px; color: #A3AED0; }
.mcp-token-status { font-size: 12px; }
.mcp-token-status--on { color: #10B981; }
.mcp-token-status--off { color: #A3AED0; }
.mcp-token-value { display: flex; align-items: center; gap: 10px; margin-top: 14px; padding: 10px 12px; border-radius: 10px; background: #F7F8FA; }
.mcp-token-value code { flex: 1; font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace; font-size: 12.5px; color: #1B2559; overflow-wrap: anywhere; }
@media (max-width: 1200px) { .settings-layout { grid-template-columns: 180px minmax(0, 1fr); } .provider-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>