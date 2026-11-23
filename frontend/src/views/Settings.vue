<!--
  Settings.vue — 全局设置页面
  一个「全局设置」入口 + 左侧分类导航：AI 供应商 / 助手与能力 / MCP Server / 基础设置 / 日志与保留 / 文件存储
-->
<template>
  <div class="page-settings">
    <div v-if="userStore.isSuperAdmin" class="settings-layout" v-loading="loading || aiLoading">
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
            <FilledIcon :name="item.icon" :size="17" />
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
          </button>
        </div>
      </aside>

      <div class="settings-card">
        <el-form label-position="top" class="settings-form">
          <!-- AI 供应商 -->
          <section v-show="activeSection === 'provider'" class="settings-section">
            <h3 class="section-title">AI 供应商</h3>

            <div v-if="aiForm.provider === 'MOCK'" class="mock-banner">
              <div class="mock-banner__logo">M</div>
              <div class="mock-banner__main">
                <div class="mock-banner__title">正在使用内置演示模型</div>
              </div>
              <el-button text @click="useApiProvider">改用 API 供应商</el-button>
            </div>

            <template v-else>
              <div class="provider-grid" v-loading="providersLoading">
                <button
                  v-for="p in displayProviders"
                  :key="p.key"
                  type="button"
                  class="provider-card"
                  :class="{ 'provider-card--on': isProviderSelected(p) }"
                  @click="selectVendor(p)"
                >
                  <span class="provider-logo" :style="providerLogoStyle(p)">{{ providerLetter(p) }}</span>
                  <span class="provider-body">
                    <span class="provider-name">
                      {{ p.name }}
                      <span v-if="p.local" class="provider-local">本地</span>
                    </span>
                    <span class="provider-host" :title="hostOf(p.baseUrl)">{{ hostOf(p.baseUrl) }}</span>
                  </span>
                  <FilledIcon v-if="aiForm.vendor === p.key" class="provider-check" name="shield" :size="17" />
                </button>
              </div>

              <div v-if="isQwenProvider" class="qwen-plan-row">
                <span class="qwen-plan-row__label">计费方式</span>
                <el-radio-group v-model="qwenPlanMode" size="small">
                  <el-radio-button value="token-plan">Token Plan</el-radio-button>
                  <el-radio-button value="payg">按量计费</el-radio-button>
                </el-radio-group>
              </div>

              <el-form-item label="API 地址">
                <el-input v-model="aiForm.baseUrl" class="ai-field" placeholder="https://api.deepseek.com/v1" />
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
                  <span v-if="aiClearKey && clearKeySource === 'vendor'" class="input-hint">保存后需重新填写 Key</span>
                </div>
                <div v-if="currentPreset?.consoleUrl" class="ai-console-row">
                  <a :href="currentPreset.consoleUrl" target="_blank" rel="noopener noreferrer">创建 API Key <span aria-hidden="true">↗</span></a>
                </div>
              </el-form-item>

              <el-form-item label="连接测试">
                <div class="ai-test-row">
                  <el-button :loading="aiTesting" :disabled="!canTestConnection" @click="handleTestAi">
                    <FilledIcon name="api" :size="16" />
                    测试连接
                  </el-button>
                  <span v-if="aiTestResult" class="ai-test-ok">
                    <FilledIcon name="shield" :size="16" />
                    连接正常 · {{ aiTestResult.latencyMs }} ms
                    <span v-if="aiTestResult.modelCount" class="ai-test-model">发现 {{ aiTestResult.modelCount }} 个模型</span>
                    <span v-else-if="aiTestResult.model" class="ai-test-model">已验证 · {{ aiTestResult.model }}</span>
                  </span>
                </div>
              </el-form-item>
            </template>

            <div class="ai-save-row">
              <el-button v-if="aiForm.provider !== 'MOCK'" type="primary" :loading="aiSaving" :disabled="!canSaveConnection" @click="handleSaveConnection">保存连接配置</el-button>
              <span v-if="aiSettingsUpdatedAt" class="input-hint">最近保存：{{ aiSettingsUpdatedAt }}</span>
              <el-button v-if="aiForm.provider !== 'MOCK'" text @click="useMockProvider">使用内置演示模型</el-button>
            </div>

            <section v-if="aiConnectionReady" class="ai-model-section">
              <div class="ai-model-section__head">
                <h4>选择默认模型</h4>
                <span v-if="aiSaved.model" class="ai-test-model">当前：{{ aiSaved.model }}</span>
              </div>
              <AiModelPicker
                v-model="aiForm.model"
                class="ai-field"
                :recommendations="currentPreset ? currentPreset.models : []"
                :vendor-name="currentPreset ? currentPreset.name : '服务商'"
                :can-fetch="canFetchModels"
                :auto-fetch="autoFetchModels"
                :dirty="aiModelDirty"
              />
              <div class="ai-model-actions">
                <el-button type="primary" :loading="aiSaving" :disabled="!canSaveModel" @click="handleSaveModel">保存模型</el-button>
              </div>
            </section>
          </section>

          <!-- 助手与能力 -->
          <section v-show="activeSection === 'assistant'" class="settings-section">
            <h3 class="section-title">助手与能力 <span class="ai-badge">Beta</span></h3>

            <div class="ai-provider-summary">
              <div class="ai-provider-summary__logo">{{ providerSummary.letter }}</div>
              <div class="ai-provider-summary__body">
                <div class="ai-provider-summary__name">{{ providerSummary.name }}</div>
                <div class="ai-provider-summary__meta">{{ providerSummary.meta }}</div>
              </div>
              <span class="ai-provider-summary__state" :class="{ 'is-on': providerSummary.on }">{{ providerSummary.state }}</span>
              <el-button text class="ai-provider-summary__link" @click="activeSection = 'provider'">
                更换供应商
                <FilledIcon name="api" :size="16" />
              </el-button>
            </div>

            <div class="ai-switch-row">
              <div>
                <div class="ai-switch-title">启用 AI 助手</div>
              </div>
              <div class="ai-switch-actions">
                <span class="ai-state" :class="{ 'ai-state--on': aiForm.enabled }">{{ aiForm.enabled ? '运行中' : '已停用' }}</span>
                <el-switch v-model="aiForm.enabled" />
              </div>
            </div>

            <el-form-item label="能力开关">
              <div class="ai-cap-list">
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">生成响应体</div></div>
                  <el-switch v-model="aiForm.capResponseBody" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">生成条件规则</div></div>
                  <el-switch v-model="aiForm.capConditions" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">解释匹配结果</div></div>
                  <el-switch v-model="aiForm.capExplain" />
                </div>
                <div class="ai-cap-row">
                  <div><div class="ai-cap-title">创建 / 修改接口草稿</div></div>
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

          <!-- Instance policy only. Personal credentials live in /account/tokens. -->
          <section v-show="activeSection === 'mcp'" class="settings-section mcp-policy">
            <div class="policy-heading"><div><h3 class="section-title">MCP 实例策略</h3></div><span class="policy-badge">仅超级管理员</span></div>
            <div class="policy-notice"><FilledIcon name="key" :size="19" /><span><b>个人凭证已移到个人账号</b><br>此处不创建或显示个人 Token。<router-link to="/account/tokens">管理我的个人凭证</router-link>。</span></div>
            <div v-if="mcpError" class="policy-error" role="alert">{{ mcpError }} <el-button text :disabled="mcpSaving" @click="loadMcpSettings">重新加载</el-button></div>
            <div class="mcp-endpoint"><span class="policy-http">POST</span><code>{{ mcpEndpoint }}</code><el-button text size="small" @click="copyText(mcpEndpoint)">复制地址</el-button></div>
            <div class="input-hint">Streamable HTTP · POST /mcp · JSON 响应，非 SSE。地址不包含凭证。</div>
            <div class="ai-switch-row"><div><div class="ai-switch-title">启用 MCP Server</div><div class="ai-switch-desc">停用后所有 MCP 客户端停止接入，关联 MCP 草稿也不能继续确认。</div></div><div class="ai-switch-actions"><span class="ai-state" :class="{ 'ai-state--on': mcpSaved?.enabled }">{{ mcpReady ? (mcpSaved?.enabled ? '已保存：启用' : '已保存：停用') : '状态待加载' }}</span><el-switch v-model="mcpForm.enabled" :disabled="!mcpReady || mcpSaving" /></div></div>
            <el-form-item label="实例写入策略"><el-radio-group v-model="mcpForm.writeMode" class="ai-scope" :disabled="!mcpReady || mcpSaving"><el-radio value="DRAFT_ONLY">草稿 + Web 明确确认（推荐）</el-radio><el-radio value="DIRECT">允许 MCP 应用来源草稿</el-radio></el-radio-group><div class="input-hint" style="margin-left: 0">DIRECT 不会绕过权限：仍需来源快照、凭证 DRAFT_APPLY 与角色工具开关同时允许。</div></el-form-item>
            <div class="policy-tools"><table><thead><tr><th>工具</th><th>对应个人能力</th><th>实例允许</th></tr></thead><tbody><tr v-for="tool in mcpToolOptions" :key="tool.key"><td><code>{{ tool.name }}</code><p>{{ tool.desc }}</p></td><td><span class="policy-scope">{{ tool.scope }}</span><small v-if="tool.key === 'toolApplyApiDraft' && mcpForm.writeMode !== 'DIRECT'">DRAFT_ONLY 下不可用</small></td><td><el-switch v-model="mcpForm[tool.key]" :disabled="!mcpReady || mcpSaving || (tool.key === 'toolApplyApiDraft' && mcpForm.writeMode !== 'DIRECT')" :aria-label="tool.name + ' 实例开关'" /></td></tr></tbody></table></div>
            <p class="policy-foot">禁用工具会同时从 tools/list 隐藏并拒绝直接调用。</p>
            <div class="ai-save-row"><span v-if="mcpDirty" class="unsaved-note">有未保存修改，尚未影响实例</span><el-button type="primary" :loading="mcpSaving" :disabled="!mcpReady || !mcpDirty || !userStore.isSuperAdmin" @click="handleSaveMcp">保存实例 MCP 策略</el-button></div>
          </section>

          <!-- 基础设置 -->
          <section v-show="activeSection === 'basic'" class="settings-section">
            <h3 class="section-title">基础设置</h3>

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

            <el-form-item label="保留模式">
              <el-radio-group v-model="form.logRetainMode">
                <el-radio value="count">按条数保留</el-radio>
                <el-radio value="days">按天数保留</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.logRetainMode === 'count'" label="保留条数">
              <el-input-number v-model="form.logRetainCount" :min="100" :max="100000" :step="100" controls-position="right" />
            </el-form-item>

            <el-form-item v-if="form.logRetainMode === 'days'" label="保留天数">
              <el-input-number v-model="form.logRetainDays" :min="1" :max="365" :step="1" controls-position="right" />
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

    <div v-if="!userStore.isSuperAdmin" class="policy-access-denied"><FilledIcon name="shield" :size="30" /><h2>仅超级管理员可配置实例策略</h2><p>个人访问凭证请前往个人账号。</p></div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useUserStore } from '@/stores/user'
import AiModelPicker from '@/components/ai/AiModelPicker.vue'
import { cleanOrphanFiles, getFileStorageStats, getSettings, saveSettings, scanOrphanFiles } from '@/api/settings'
import { getAiProviders, getAiSettings, saveAiSettings, testAiConnection } from '@/api/ai'
import { getMcpSettings, saveMcpSettings } from '@/api/mcp'
import { useAiStore } from '@/stores/ai'

const aiStore = useAiStore()
const userStore = useUserStore()
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
    { key: 'provider', label: 'AI 供应商', icon: 'api', badge: '新' },
    { key: 'assistant', label: '助手与能力', icon: 'assistant' },
    { key: 'mcp', label: 'MCP Server', icon: 'shield' }
  ] },
  { label: '系统', items: [
    { key: 'basic', label: '基础设置', icon: 'settings' },
    { key: 'logs', label: '日志与保留', icon: 'logs' },
    { key: 'files', label: '文件存储', icon: 'files' }
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
const aiConnectionTested = ref(false)
const aiSettingsUpdatedAt = ref('')

const providerColors = {
  deepseek: { background: '#EEF0FF', color: '#3568d4' },
  kimi: { background: '#F3F4F6', color: '#111827' },
  minimax: { background: '#FFF7ED', color: '#C2410C' },
  glm: { background: '#EFF6FF', color: '#2563EB' },
  qwen: { background: '#F5F3FF', color: '#7C3AED' },
  'qwen-token-plan': { background: '#F5F3FF', color: '#7C3AED' },
  volcengine: { background: '#EEF4FF', color: '#2563EB' },
  qianfan: { background: '#EAF6FF', color: '#1D4ED8' },
  hunyuan: { background: '#EAF7FF', color: '#0369A1' },
  siliconflow: { background: '#F5F3FF', color: '#7C3AED' },
  openai: { background: '#ECFDF5', color: '#047857' },
  ollama: { background: '#F1F5F9', color: '#475569' },
  custom: { background: '#F7F8FA', color: 'var(--app-text-muted)' }
}

const currentPreset = computed(() => aiProviders.value.find(p => p.key === aiForm.vendor) || null)
const displayProviders = computed(() => aiProviders.value.filter(p => p.key !== 'qwen-token-plan'))
const isQwenProvider = computed(() => ['qwen', 'qwen-token-plan'].includes(aiForm.vendor))
const qwenPlanMode = computed({
  get: () => aiForm.vendor === 'qwen-token-plan' ? 'token-plan' : 'payg',
  set: value => selectQwenPlan(value)
})
const QWEN_PLAN_URLS = {
  'token-plan': 'https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1',
  payg: 'https://maas.qianwenaiapi.com/compatible-mode/v1'
}

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

const aiConnectionDirty = computed(() => {
  return aiForm.provider !== aiSaved.provider
    || aiForm.vendor !== aiSaved.vendor
    || (aiForm.baseUrl || '') !== (aiSaved.baseUrl || '')
    || !!aiForm.apiKey
    || aiClearKey.value
})

const aiConnectionReady = computed(() => {
  return aiForm.provider === 'OPENAI_COMPATIBLE'
    && !!aiSaved.baseUrl
    && (aiSaved.keyConfigured || aiSaved.vendor === 'ollama')
    && !aiConnectionDirty.value
})

const aiModelDirty = computed(() => (aiForm.model || '') !== (aiSaved.model || ''))

const canTestConnection = computed(() => {
  if (aiForm.provider !== 'OPENAI_COMPATIBLE' || !aiForm.baseUrl.trim()) return false
  return !!aiForm.apiKey || aiKeyConfigured.value || aiForm.vendor === 'ollama'
})

const canSaveConnection = computed(() => {
  if (!canTestConnection.value) return false
  return !aiConnectionDirty.value || aiConnectionTested.value
})

const canSaveModel = computed(() => aiConnectionReady.value && aiModelDirty.value && !!aiForm.model && !aiSaving.value)

const canFetchModels = computed(() => aiConnectionReady.value && aiForm.provider === 'OPENAI_COMPATIBLE')
const autoFetchModels = computed(() => !['qwen', 'qwen-token-plan'].includes(aiForm.vendor))

watch([() => aiForm.provider, () => aiForm.vendor, () => aiForm.baseUrl, () => aiForm.apiKey, aiClearKey], () => {
  aiTestResult.value = null
  aiConnectionTested.value = false
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
    meta: (aiForm.model || '待选择模型') + ' · ' + (ready ? (aiForm.vendor === 'ollama' ? '本地模型' : 'API Key 已配置') : '未配置 Key'),
    state: ready ? (aiForm.model ? '已连接' : '待选模型') : '未配置',
    on: ready
  }
})

const mcpForm = reactive({
  enabled: false, writeMode: 'DRAFT_ONLY',
  toolSearchApis: true, toolGetApiDetail: true, toolGetApiStats: true, toolCreateApiDraft: true, toolUpdateApiDraft: true,
  toolApplyApiDraft: false, toolGenerateResponseBody: true, toolGenerateConditions: true, toolExplainMatchResult: true
})
const mcpSaving = ref(false), mcpReady = ref(false), mcpError = ref(''), mcpSaved = ref(null)
const mcpDirty = computed(() => !!mcpSaved.value && Object.keys(mcpForm).some(key => mcpForm[key] !== mcpSaved.value[key]))

const mcpToolOptions = [
  { key: 'toolSearchApis', scope: 'API_READ', name: 'mockhub.search_apis', desc: '按关键字搜索接口' },
  { key: 'toolGetApiDetail', scope: 'API_READ', name: 'mockhub.get_api_detail', desc: '读取接口与返回体配置' },
  { key: 'toolGetApiStats', scope: 'STATS_READ', name: 'mockhub.get_api_stats', desc: '查询调用次数与热门接口' },
  { key: 'toolCreateApiDraft', scope: 'DRAFT_CREATE', name: 'mockhub.create_api_draft', desc: '根据参数创建接口草稿（支持多返回体）' },
  { key: 'toolUpdateApiDraft', scope: 'DRAFT_UPDATE', name: 'mockhub.update_api_draft', desc: '修改接口、替换或追加返回体' },
  { key: 'toolApplyApiDraft', scope: 'DRAFT_APPLY', name: 'mockhub.apply_api_draft', desc: '应用草稿（默认需 Web 确认）' },
  { key: 'toolGenerateResponseBody', scope: 'AI_GENERATE', name: 'mockhub.generate_response_body', desc: '生成 JSON / XML 响应体' },
  { key: 'toolGenerateConditions', scope: 'AI_GENERATE', name: 'mockhub.generate_conditions', desc: '自然语言转条件规则' },
  { key: 'toolExplainMatchResult', scope: 'AI_GENERATE', name: 'mockhub.explain_match_result', desc: '解释一次请求的命中链路' }
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
  if (url.includes('token-plan.maas.qianwenaiapi.com')) return 'qwen-token-plan'
  if (url.includes('qianwenaiapi') || url.includes('dashscope')) return 'qwen'
  if (url.includes('volces.com')) return 'volcengine'
  if (url.includes('qianfan.baidubce.com')) return 'qianfan'
  if (url.includes('hunyuan.cloud.tencent.com') || url.includes('tokenhub')) return 'hunyuan'
  if (url.includes('siliconflow')) return 'siliconflow'
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

function isProviderSelected(p) {
  return aiForm.vendor === p.key || (p.key === 'qwen' && aiForm.vendor === 'qwen-token-plan')
}

function selectVendor(p) {
  if (aiForm.provider === 'OPENAI_COMPATIBLE' && aiForm.vendor === p.key) return
  if (p.key === 'qwen' && isQwenProvider.value) return
  aiForm.provider = 'OPENAI_COMPATIBLE'
  aiForm.vendor = p.key
  aiForm.baseUrl = p.baseUrl || ''
  aiForm.model = ''
  aiForm.apiKey = ''
  aiTestResult.value = null
  aiClearKey.value = aiSaved.keyConfigured && p.key !== aiSaved.vendor
  clearKeySource.value = aiClearKey.value ? 'vendor' : ''
  aiKeyConfigured.value = p.key === aiSaved.vendor ? aiSaved.keyConfigured : false
}

function selectQwenPlan(mode) {
  const target = mode === 'token-plan' ? 'qwen-token-plan' : 'qwen'
  if (aiForm.vendor === target) return
  aiForm.provider = 'OPENAI_COMPATIBLE'
  aiForm.vendor = target
  aiForm.baseUrl = QWEN_PLAN_URLS[mode]
  aiForm.apiKey = ''
  aiTestResult.value = null
  aiConnectionTested.value = false
  aiClearKey.value = aiSaved.keyConfigured && target !== aiSaved.vendor
  clearKeySource.value = aiClearKey.value ? 'vendor' : ''
  aiKeyConfigured.value = target === aiSaved.vendor ? aiSaved.keyConfigured : false
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
      aiForm.model = ''
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
    aiTestResult.value = null
    aiConnectionTested.value = false
    Object.assign(aiSaved, {
      provider: aiForm.provider, vendor: aiForm.vendor, baseUrl: aiForm.baseUrl,
      model: aiForm.model, keyConfigured: !!data.keyConfigured
    })
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiLoading.value = false }
}

function aiSettingsPayload() {
  return {
    enabled: aiForm.enabled,
    provider: aiForm.provider,
    vendor: aiForm.vendor,
    baseUrl: aiForm.baseUrl,
    model: aiForm.model || null,
    apiKey: aiForm.apiKey ? aiForm.apiKey : (aiClearKey.value ? '' : null),
    capResponseBody: aiForm.capResponseBody,
    capConditions: aiForm.capConditions,
    capExplain: aiForm.capExplain,
    capDrafts: aiForm.capDrafts,
    accessScope: aiForm.accessScope
  }
}

function applySavedAi(saved) {
  aiKeyConfigured.value = !!saved.keyConfigured
  aiKeyMask.value = saved.keyMask || ''
  aiSettingsUpdatedAt.value = saved.updatedAt || ''
  aiForm.model = saved.model || ''
  aiForm.apiKey = ''
  aiClearKey.value = false
  clearKeySource.value = ''
  Object.assign(aiSaved, {
    provider: aiForm.provider, vendor: aiForm.vendor, baseUrl: aiForm.baseUrl,
    model: aiForm.model, keyConfigured: !!saved.keyConfigured
  })
}

async function handleSaveAi() {
  if (aiConnectionDirty.value && !aiConnectionTested.value) {
    ElMessage.warning('请先测试连接，通过后再保存')
    return
  }
  aiSaving.value = true
  try {
    const saved = await saveAiSettings(aiSettingsPayload())
    const wasConnectionDirty = aiConnectionDirty.value
    applySavedAi(saved)
    aiTestResult.value = null
    aiConnectionTested.value = false
    await aiStore.load(true)
    ElMessage.success(wasConnectionDirty ? '连接配置已保存，请选择模型' : 'AI 配置已保存')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiSaving.value = false }
}

async function handleSaveConnection() {
  if (!canSaveConnection.value || aiSaving.value) return
  aiSaving.value = true
  try {
    const saved = await saveAiSettings(aiSettingsPayload())
    applySavedAi(saved)
    aiTestResult.value = null
    aiConnectionTested.value = false
    await aiStore.load(true)
    ElMessage.success('连接配置已保存，请选择模型')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiSaving.value = false }
}

async function handleSaveModel() {
  if (!canSaveModel.value || aiSaving.value) return
  aiSaving.value = true
  try {
    const saved = await saveAiSettings(aiSettingsPayload())
    applySavedAi(saved)
    await aiStore.load(true)
    ElMessage.success('模型已保存')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiSaving.value = false }
}

async function handleTestAi() {
  if (!canTestConnection.value) {
    ElMessage.warning('请填写服务地址与 API Key')
    return
  }
  aiTesting.value = true
  aiTestResult.value = null
  try {
    aiTestResult.value = await testAiConnection({
      provider: aiForm.provider,
      vendor: aiForm.vendor,
      baseUrl: aiForm.baseUrl,
      apiKey: aiForm.apiKey ? aiForm.apiKey : (aiClearKey.value ? '' : null),
      model: aiForm.model || currentPreset.value?.defaultModel || ''
    })
    aiConnectionTested.value = true
    const count = aiTestResult.value?.modelCount || 0
    ElMessage.success(count ? '连接正常，发现 ' + count + ' 个模型' : '连接正常')
  } catch (e) { /* 拦截器已处理 */ }
  finally { aiTesting.value = false }
}

function clearAiKey() { aiClearKey.value = true; clearKeySource.value = 'user'; aiForm.apiKey = '' }
function undoClearKey() { aiClearKey.value = false; clearKeySource.value = '' }

async function loadMcpSettings() {
  if (!userStore.isSuperAdmin || mcpSaving.value) return
  mcpReady.value = false; mcpError.value = ''
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
    mcpSaved.value = { ...mcpForm }; mcpReady.value = true
  } catch (err) { mcpError.value = err?.msg || err?.response?.data?.msg || err?.message || '加载实例策略失败' }
}

async function handleSaveMcp() {
  if (!userStore.isSuperAdmin || !mcpReady.value || !mcpDirty.value || mcpSaving.value) return
  const payload = { ...mcpForm }; mcpSaving.value = true; mcpError.value = ''
  try {
    const previous = mcpSaved.value
    const danger = payload.enabled && ((!previous.enabled) || (payload.writeMode === 'DIRECT' && previous.writeMode !== 'DIRECT') || (payload.toolApplyApiDraft && !previous.toolApplyApiDraft))
    if (danger) {
      try { await ElMessageBox.confirm('此实例策略可能启用外部 MCP 接入或自动应用草稿。具备显式能力的个人凭证可在当前团队权限内执行。确认已评估所有团队影响？DIRECT 仍受来源快照、凭证范围与工具开关约束。', '确认高风险实例策略', { type: 'warning', confirmButtonText: '确认保存实例策略', cancelButtonText: '取消' }) } catch { return }
    } else if (previous.enabled && !payload.enabled) {
      try { await ElMessageBox.confirm('停用后所有 MCP 客户端将无法接入；来源 MCP 的待确认草稿也会被拒绝确认。不会停用 Mock 运行服务。', '停用实例 MCP', { type: 'warning', confirmButtonText: '确认停用 MCP', cancelButtonText: '取消' }) } catch { return }
    }
    if (!userStore.isSuperAdmin) return
    const saved = await saveMcpSettings(payload)
    for (const key of Object.keys(mcpForm)) mcpForm[key] = saved?.[key] ?? payload[key]
    mcpSaved.value = { ...mcpForm }; ElMessage.success('实例 MCP 策略已保存')
  } catch (err) { mcpError.value = err?.msg || err?.response?.data?.msg || err?.message || '保存失败' }
  finally { mcpSaving.value = false }
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
  if (!userStore.isSuperAdmin) return
  loadSettings()
  loadStorageStats()
  loadProviders()
  loadAiSettings()
  loadMcpSettings()
})
</script>

<style lang="scss" scoped>
.page-settings { padding: 0; max-width: 1120px; }
.settings-layout { display: grid; grid-template-columns: 212px minmax(0, 1fr); gap: 20px; align-items: start; }
.settings-nav { position: sticky; top: 0; padding: 10px; background: #fff; border-radius: 12px; }
.nav-group + .nav-group { margin-top: 14px; padding-top: 12px; border-top: 1px solid #F6F8FB; }
.nav-group__title { padding: 6px 10px; font-size: var(--app-font-caption); font-weight: 700; letter-spacing: .5px; color: var(--app-text-secondary); text-transform: uppercase; }
.nav-item { position: relative; display: flex; align-items: center; gap: 9px; width: 100%; height: 38px; padding: 0 10px; border: none; border-radius: 8px; background: transparent; font-size: 13px; color: #4A5568; text-align: left; cursor: pointer; }
.nav-item:hover { background: #F7F8FA; }
.nav-item--on { background: var(--app-bg-selected); color: var(--app-text-accent); font-weight: 600; }
.nav-item--on::before { content: ""; position: absolute; left: 0; top: 8px; bottom: 8px; width: 3px; border-radius: 2px; background: #3568d4; }
.nav-item .el-icon { color: var(--app-text-secondary); }
.nav-item--on .el-icon { color: var(--app-accent); }
.nav-badge { margin-left: auto; padding: 0 5px; border-radius: 5px; background: var(--app-bg-selected); border: 1px solid var(--app-divider); color: var(--app-text-accent); font-size: var(--app-font-micro); font-weight: 600; line-height: 16px; }
.settings-card { min-height: 640px; padding: 20px; background: #fff; border-radius: 12px; }
.settings-section .section-title { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: #1B2559; letter-spacing: -.2px; }
.settings-section .section-title + :not(.section-desc) { margin-top: 16px; }
.settings-section .section-desc { margin: 0 0 20px; font-size: 13px; color: var(--app-text-secondary); line-height: 1.75; }
.settings-form :deep(.el-form-item__label) { font-size: 13px; font-weight: 600; color: #4A5568; }
.settings-form :deep(.el-radio__label) { color: #4A5568; }
.settings-form :deep(.el-input-number) { width: 200px; }
.ai-field { width: 100% !important; max-width: 520px; }
.ai-console-row { display: flex; align-items: center; gap: 6px; margin-top: 7px; font-size: var(--app-font-caption); color: var(--app-text-muted); }.ai-console-row a { color: var(--app-accent); text-decoration: none; }.ai-console-row a:hover { color: var(--app-accent-hover); text-decoration: underline; }
.input-hint { margin-left: 12px; font-size: var(--app-font-caption); line-height: 1.6; color: var(--app-text-muted); }
.provider-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.qwen-plan-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: -4px 0 16px; padding: 10px 12px; border: 1px solid #EDF0F5; border-radius: 9px; background: #FBFCFE; }
.qwen-plan-row__label { font-size: var(--app-font-caption); color: var(--app-text-secondary); }
.provider-card { position: relative; display: flex; align-items: center; gap: 10px; height: 74px; padding: 0 12px; border: 1px solid #EDF0F5; border-radius: 10px; background: #fff; text-align: left; cursor: pointer; }
.provider-card:hover { border-color: #DDE2EE; }
.provider-card--on { border-color: #C7D2FE; background: #FBFCFF; box-shadow: none; }
.provider-logo { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: 9px; font-size: 14px; font-weight: 700; }
.provider-body { min-width: 0; }
.provider-name { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #1B2559; white-space: nowrap; }
.provider-local { padding: 0 4px; border-radius: 4px; background: #F1F5F9; color: var(--app-text-muted); font-size: var(--app-font-micro); font-weight: 600; line-height: 16px; }
.provider-host { display: block; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--app-text-muted); font-size: var(--app-font-caption); line-height: 1.5; }
.provider-check { position: absolute; top: 8px; right: 8px; color: var(--app-accent); }
.mock-banner { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid #F1F5F9; border-radius: 10px; background: #FBFBFE; }
.mock-banner__logo { display: grid; place-items: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 10px; background: #EEF0FF; color: var(--app-accent); font-size: 15px; font-weight: 700; }
.mock-banner__main { flex: 1; }
.mock-banner__title { font-size: 13px; font-weight: 600; color: #1B2559; }
.ai-badge { display: inline-flex; align-items: center; margin-left: 6px; padding: 0 6px; height: 18px; border-radius: 6px; font-size: var(--app-font-micro); font-weight: 700; color: var(--app-text-accent); background: var(--app-bg-selected); border: 1px solid var(--app-divider); vertical-align: middle; }
.ai-provider-summary { display: flex; align-items: center; gap: 12px; margin: 0 0 18px; padding: 12px 14px; border: 1px solid #F1F5F9; border-radius: 10px; background: #FBFBFE; }
.ai-provider-summary__logo { display: grid; place-items: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 10px; background: #EEF0FF; color: var(--app-accent); font-size: 15px; font-weight: 700; }
.ai-provider-summary__body { flex: 1; }
.ai-provider-summary__name { font-size: 13px; font-weight: 600; color: #1B2559; }
.ai-provider-summary__meta { margin-top: 1px; font-size: var(--app-font-caption); color: var(--app-text-muted); }
.ai-provider-summary__state { font-size: var(--app-font-caption); color: var(--app-text-muted); }
.ai-provider-summary__state.is-on { color: var(--app-text-success); }
.ai-provider-summary__state.is-on::before { content: ""; display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: var(--app-text-success); }
.ai-provider-summary__link { color: var(--app-text-accent); }
.ai-switch-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 0 16px; }
.ai-switch-title { font-size: 14px; font-weight: 500; color: #1B2559; }
.ai-switch-desc { margin-top: 2px; font-size: var(--app-font-caption); color: var(--app-text-muted); }
.ai-switch-actions { display: flex; align-items: center; gap: 10px; }
.ai-state { font-size: var(--app-font-caption); color: var(--app-text-muted); }
.ai-state--on { color: var(--app-text-success); }
.ai-key-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; width: 100%; }
.ai-test-row { display: flex; align-items: center; gap: 12px; }
.ai-test-ok { display: inline-flex; align-items: center; gap: 5px; font-size: 13px; color: var(--app-text-success); }
.ai-test-model { color: var(--app-text-muted); font-size: var(--app-font-caption); }
.ai-cap-list { width: 100%; border: 1px solid #F1F5F9; border-radius: 10px; overflow: hidden; }
.ai-cap-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 12px; background: #fff; }
.ai-cap-row + .ai-cap-row { border-top: 1px solid #F8FAFC; }
.ai-cap-title { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #1B2559; }
.ai-scope-wrap { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.ai-scope { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.ai-security { margin-top: 4px; }
.ai-save-row { display: flex; align-items: center; gap: 12px; margin-top: 18px; }.ai-save-row .input-hint { margin-left: 0; }
.ai-model-section { margin-top: 22px; padding-top: 18px; border-top: 1px solid #F1F5F9; }
.ai-model-section__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.ai-model-section__head h4 { margin: 0; font-size: 14px; font-weight: 600; color: #1B2559; }
.ai-model-actions { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.cors-switch-row { padding: 4px 0; }
.file-maintenance-actions { display: flex; align-items: center; gap: 12px; }
.storage-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 20px; margin-top: 16px; padding: 14px 0 2px; border-top: 1px solid #F1F5F9; }
.storage-stat { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.storage-stat__label { font-size: var(--app-font-caption); color: var(--app-text-muted); }
.storage-stat__value { font-size: 13px; color: #4A5568; overflow-wrap: anywhere; }
.path-value { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace; }
.orphan-summary { display: flex; gap: 20px; margin-top: 14px; font-size: 13px; color: #4A5568; }
.orphan-table { width: 100%; margin-top: 14px; }
.mcp-endpoint { display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 12px; border-radius: 10px; background: #F7F8FA; }
.mcp-endpoint code { flex: 1; font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace; font-size: 12.5px; color: #4A5568; overflow-wrap: anywhere; }
@media (max-width: 1200px) { .settings-layout { grid-template-columns: 180px minmax(0, 1fr); } .provider-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.page-settings { max-width:none; height:100%; min-height:0; color:#303844; }
.settings-layout { height:100%; min-height:0; grid-template-columns:190px minmax(0,1fr); align-items:stretch; }
.settings-nav { overflow:auto; min-height:0; position:static; }
.settings-card { overflow:auto; min-height:0; box-shadow:none; border-radius:16px; }
.nav-item--on { background: var(--app-bg-selected); color: var(--app-text-accent); }
.policy-heading { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; margin-bottom:16px; }
.policy-badge { background:#f2f4f8; color:var(--app-text-secondary); padding:6px 9px; border-radius:7px; font-size:var(--app-font-micro); white-space:nowrap; }
.policy-notice { display:flex; align-items:flex-start; gap:12px; padding:14px; background:#eaf0fb; color:var(--app-text-secondary); border-radius:11px; font-size:var(--app-font-caption); line-height:1.9; margin-bottom:22px; }
.policy-notice a { color:var(--app-text-accent); text-decoration:none; }
.policy-error { padding:12px 14px; background:#fff0eb; color:var(--app-text-danger); border-radius:9px; line-height:1.8; font-size:var(--app-font-caption); margin:14px 0; }
.policy-http { font-family:ui-monospace,monospace; font-size:var(--app-font-micro); font-weight:600; color:var(--app-text-accent); }
.policy-tools { overflow:auto; margin:22px 0; }
.policy-tools table { border-collapse:collapse; width:100%; min-width:550px; text-align:left; }
.policy-tools th { font-size:var(--app-font-micro); color:var(--app-text-secondary); font-weight:400; }
.policy-tools td,.policy-tools th { padding:13px 10px; border:0; }
.policy-tools code { font-family:ui-monospace,monospace; font-size:var(--app-font-micro); color:var(--app-text-secondary); }
.policy-tools p { font-size:var(--app-font-caption); color:var(--app-text-muted); margin:5px 0 0; line-height:1.7; }
.policy-scope { font-family:ui-monospace,monospace; font-size:var(--app-font-micro); color:var(--app-text-secondary); background:#f1f5fc; padding:5px 7px; border-radius:6px; white-space:nowrap; }
.policy-tools small { display:block; font-size:var(--app-font-caption); color:var(--app-text-muted); margin-top:8px; }
.policy-foot,.unsaved-note { font-size:var(--app-font-caption); line-height:1.8; color:var(--app-text-secondary); }
.ai-save-row { display:flex; align-items:center; gap:12px; }
.page-settings :deep(.el-tag--success) { --el-tag-text-color: var(--app-text-success); }
.page-settings :deep(.el-tag--warning) { --el-tag-text-color: var(--app-text-warning); }
.page-settings :deep(.el-button--primary) { background:#1d2025; border-color:#1d2025; box-shadow:none; }
.page-settings :deep(.el-button--primary.is-plain) { background:var(--app-bg-selected); border-color:transparent; color:var(--app-text-accent); }
.page-settings :deep(.el-switch) { --el-switch-on-color:#3568d4; }
.page-settings :deep(.el-input__wrapper),.page-settings :deep(.el-select__wrapper) { box-shadow:none; background:#f5f7fa; }
.policy-access-denied { min-height:280px; display:grid; place-content:center; justify-items:center; gap:12px; background:white; border-radius:16px; color:var(--app-text-muted); }
.policy-access-denied h2 { font-size:17px; color:var(--app-text-regular); }
@media(max-width:850px) { .settings-layout { grid-template-columns:150px minmax(0,1fr); gap:12px; }.settings-card { padding:16px; }.policy-heading { flex-wrap:wrap; } }
@media(max-width:620px) { .page-settings { height:auto; }.settings-layout { display:flex; flex-direction:column; }.settings-nav { display:flex; flex-wrap:wrap; gap:10px; }.nav-group { flex:1; min-width:135px; }.settings-card { overflow:visible; } }
</style>