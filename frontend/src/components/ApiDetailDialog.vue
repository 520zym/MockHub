<template>
  <el-dialog
    :model-value="modelValue"
    class="api-detail-dialog"
    width="min(920px, 94vw)"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="detail-heading">
        <div class="detail-identity">
          <HttpMethodTag :method="summary?.method || 'GET'" />
          <code class="detail-path" :title="summary?.path">{{ summary?.path || '—' }}</code>
          <span class="detail-chip">{{ summary?.type || 'REST' }}</span>
          <span class="detail-state" :class="enabled ? 'is-on' : 'is-off'">{{ enabled ? '已启用' : '已停用' }}</span>
        </div>
        <h2 class="detail-name">{{ summary?.name || '接口详情' }}</h2>
        <p class="detail-sub">
          <span>{{ teamName || '当前团队' }}</span>
          <span v-if="revision"> · 版本 v{{ revision }}</span>
          <span v-if="tags.length" class="detail-tags"><span v-for="tag in tags" :key="tag.id" class="detail-tag"><i :style="{ backgroundColor: tag.color }" />{{ tag.name }}</span></span>
        </p>
      </div>
    </template>

    <div v-if="loading" class="detail-loading" role="status">正在读取接口详情…</div>
    <div v-else-if="error" class="detail-error" role="alert">{{ error }} <el-button text @click="load">重新加载</el-button></div>
    <template v-else>
      <dl class="detail-facts">
        <div><dt>分组</dt><dd>{{ groupName }}</dd></div>
        <div><dt>创建人</dt><dd>{{ detail?.createdByName || '未记录' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ formatDate(detail?.createdAt) }}</dd></div>
        <div><dt>最后修改</dt><dd>{{ detail?.updatedByName || '未记录' }}</dd></div>
        <div><dt>修改时间</dt><dd>{{ formatDate(detail?.updatedAt) }}</dd></div>
        <div><dt>命中次数</dt><dd>{{ formatCount(summary?.hitCount) }}</dd></div>
        <div><dt>最近调用</dt><dd>{{ summary?.lastCalledAt ? formatDate(summary.lastCalledAt) : '—' }}</dd></div>
      </dl>

      <section v-if="mockUrl" class="detail-section">
        <h3>Mock 地址</h3>
        <div class="detail-url"><code>{{ mockUrl }}</code><CopyButton :text="mockUrl" :show-label="false" /></div>
      </section>

      <section v-if="descriptionText" class="detail-section">
        <h3>接口描述</h3>
        <p class="detail-description">{{ descriptionText }}</p>
      </section>

      <section class="detail-section">
        <h3>返回体 <span class="detail-count">{{ responseCount }} 个</span><span v-if="isSoap" class="detail-count">· 按 Operation 分组</span></h3>
        <p v-if="!responseCount" class="detail-empty">没有可展示的返回体。</p>
        <div v-for="group in displayGroups" :key="group.key" class="response-group">
          <header v-if="group.title" class="response-group__head">
            <code class="response-group__name">{{ group.title }}</code>
            <span v-if="group.subtitle" class="response-group__action" :title="group.subtitle">{{ group.subtitle }}</span>
            <span class="detail-count">{{ group.variants.length }} 个返回体</span>
          </header>
          <p v-if="group.description" class="response-group__desc">{{ group.description }}</p>
          <p v-if="!group.variants.length" class="detail-empty">该 Operation 还没有配置返回体。</p>
          <article v-for="variant in group.variants" :key="variant.key" class="variant">
            <header class="variant-head">
              <span class="variant-index">{{ variant.index }}</span>
              <b class="variant-name">{{ variant.name }}</b>
              <span class="variant-code">{{ variant.responseCode }}</span>
              <span class="variant-state" :class="variant.active ? 'is-on' : 'is-off'">{{ variant.active ? '生效中' : '未启用' }}</span>
              <span v-if="variant.fallback" class="variant-chip">兜底</span>
              <span v-if="variant.delayMs" class="variant-chip">延迟 {{ variant.delayMs }}ms</span>
              <span v-if="variant.bodyType === 'FILE'" class="variant-chip">文件</span>
            </header>
            <p class="variant-rule">{{ variant.rule }}</p>
            <pre v-if="variant.preview" class="variant-body">{{ variant.preview }}</pre>
            <p v-else-if="variant.bodyType === 'FILE'" class="variant-file">{{ variant.fileLabel }}</p>
          </article>
        </div>
      </section>
    </template>

    <template #footer>
      <div class="detail-footer">
        <span class="detail-revision">{{ revision ? '当前版本 v' + revision : '' }}</span>
        <div class="detail-actions">
          <el-button :disabled="loading" @click="emit('history')">变更记录</el-button>
          <el-button v-if="canWrite && !loading && !error" @click="emit('edit')">编辑</el-button>
          <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import CopyButton from '@/components/CopyButton.vue'
import { useAppStore } from '@/stores/app'
import { getApiDetail } from '@/api/apis'
import { formatDate, readableError } from '@/utils/api-lifecycle'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  summary: { type: Object, default: null },
  serverAddress: { type: String, default: '' },
  canWrite: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'history', 'edit'])
const app = useAppStore()
const detail = ref(null), loading = ref(false), error = ref('')
let request = 0

const teamName = computed(() => app.teams.find(team => team.id === props.summary?.teamId)?.name || '')
const revision = computed(() => Number(detail.value?.revision || props.summary?.revision) || 0)
const enabled = computed(() => (detail.value ? detail.value.enabled !== false : props.summary?.enabled !== false))
const tags = computed(() => detail.value?.tags || props.summary?.tags || [])
const groupName = computed(() => {
  const row = props.summary || {}, record = detail.value || {}
  const groupId = record.groupId !== undefined ? record.groupId : row.groupId
  if (!groupId) return '未分组'
  return row.groupName || '—'
})
const descriptionText = computed(() => htmlToText(detail.value?.description))
const mockUrl = computed(() => {
  const row = props.summary || {}, record = detail.value || {}
  const path = record.path || row.path || ''
  const type = record.type || row.type || 'REST'
  const identifier = row.teamIdentifier || app.currentTeam?.identifier || ''
  const origin = props.serverAddress || (typeof window !== 'undefined' ? window.location.origin : '')
  if (!path || !identifier || !origin) return ''
  return origin.replace(/\/$/, '') + '/mock/' + encodeURIComponent(identifier) + path + (type === 'SOAP' ? '?wsdl' : '')
})

const SOURCE_LABELS = { BODY: 'Body', QUERY: 'Query', HEADER: 'Header' }
const OPERATOR_LABELS = { EQ: '等于', NE: '不等于', CONTAINS: '包含', IS_EMPTY: '为空', IN: '在列表中', GT: '大于', GTE: '大于等于', LT: '小于', LTE: '小于等于', REGEX: '匹配正则' }

function htmlToText(html) {
  if (!html) return ''
  const raw = String(html)
  const text = typeof DOMParser !== 'undefined'
    ? (new DOMParser().parseFromString(raw, 'text/html').body.textContent || '')
    : raw.replace(/<[^>]*>/g, ' ')
  return text.replace(/\u00a0/g, ' ').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim()
}
function formatCount(value) { return Number(value || 0).toLocaleString('zh-CN') }
function formatSize(bytes) {
  const size = Number(bytes) || 0
  if (size >= 1048576) return (size / 1048576).toFixed(1) + ' MB'
  if (size >= 1024) return (size / 1024).toFixed(1) + ' KB'
  return size + ' B'
}
function previewBody(body) {
  const text = String(body || '').trim()
  if (!text) return ''
  return text.length > 1200 ? text.slice(0, 1200) + '\n…（已截断）' : text
}
function parseConditions(raw) {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    const list = Array.isArray(parsed) ? parsed : parsed?.conditions
    return Array.isArray(list) ? list.filter(item => item && item.path) : []
  } catch { return [] }
}
function conditionText(item) {
  const source = SOURCE_LABELS[item.source] || item.source || 'Body'
  const operator = OPERATOR_LABELS[item.operator] || item.operator || '等于'
  if (item.operator === 'IS_EMPTY') return source + '.' + item.path + ' ' + operator
  const type = String(item.valueType || 'STRING').toUpperCase()
  const raw = item.value == null ? '' : String(item.value)
  const value = type === 'NUMBER' || type === 'BOOLEAN' || item.operator === 'IN' ? raw : '"' + raw + '"'
  return source + '.' + item.path + ' ' + operator + ' ' + value
}
function toVariant(item, index, multiple) {
  const conditions = parseConditions(item.conditions)
  const bodyType = String(item.bodyType || 'TEXT').toUpperCase()
  return {
    key: item.id || 'response-' + (item.soapOperationName || '') + '-' + index,
    index: index + 1,
    name: item.name || '返回体 ' + (index + 1),
    responseCode: item.responseCode || 200,
    active: item.active !== undefined ? item.active : !!item.isActive,
    fallback: !conditions.length && multiple,
    delayMs: Number(item.delayMs) || 0,
    bodyType,
    rule: conditions.length ? conditions.map(conditionText).join(' 且 ') : (multiple ? '无条件，作为兜底' : '无条件'),
    preview: bodyType === 'FILE' ? '' : previewBody(item.responseBody),
    fileLabel: (item.fileName || '文件返回') + (item.fileSize ? ' · ' + formatSize(item.fileSize) : '')
  }
}

const isSoap = computed(() => (detail.value?.type || props.summary?.type || 'REST') === 'SOAP')

/** 详情接口返回的 soapConfig 是 JSON 字符串（和列表/编辑器一致），这里解析成对象再用。 */
const soapConfig = computed(() => {
  const raw = detail.value?.soapConfig
  if (!raw) return null
  if (typeof raw === 'object') return raw
  try { return JSON.parse(raw) } catch { return null }
})

/** REST: 接口下所有返回体同属一个集合；SOAP: 每个 Operation 是独立集合，不能混在一起算“多个返回体”。 */
const variants = computed(() => {
  const record = detail.value
  if (!record) return []
  const entries = (Array.isArray(record.responses) ? record.responses : [])
    .filter(item => !item.soapOperationName)
    .slice()
    .sort((left, right) => (Number(left.sortOrder) || 0) - (Number(right.sortOrder) || 0))
  const multiple = entries.length > 1
  return entries.map((item, index) => toVariant(item, index, multiple))
})

const responseCount = computed(() => {
  const record = detail.value
  return record && Array.isArray(record.responses) ? record.responses.length : 0
})

const displayGroups = computed(() => {
  if (!isSoap.value) return [{ key: 'rest', title: '', subtitle: '', description: '', variants: variants.value }]
  const record = detail.value
  if (!record) return []
  const groups = [], byName = new Map()
  for (const op of (Array.isArray(soapConfig.value?.operations) ? soapConfig.value.operations : [])) {
    const name = op?.operationName || ''
    const group = { key: 'op:' + name, title: name || '未命名 Operation', subtitle: op?.soapAction || '', description: op?.description || '', raw: [] }
    groups.push(group)
    if (name) byName.set(name, group)
  }
  for (const item of (Array.isArray(record.responses) ? record.responses : [])) {
    const name = item.soapOperationName || ''
    let group = byName.get(name)
    if (!group) {
      group = { key: 'op:' + (name || 'unlinked'), title: name || '未关联 Operation', subtitle: '', description: '', raw: [] }
      groups.push(group)
      if (name) byName.set(name, group)
    }
    group.raw.push(item)
  }
  return groups.map(group => {
    const sorted = group.raw.slice().sort((left, right) => (Number(left.sortOrder) || 0) - (Number(right.sortOrder) || 0))
    const multiple = sorted.length > 1
    return { key: group.key, title: group.title, subtitle: group.subtitle, description: group.description, variants: sorted.map((item, index) => toVariant(item, index, multiple)) }
  })
})

async function load() {
  const id = props.summary?.id
  if (!id) return
  const token = ++request
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    const data = await getApiDetail(id)
    if (token === request) detail.value = data
  } catch (failure) {
    if (token === request) error.value = readableError(failure, '详情读取失败')
  } finally {
    if (token === request) loading.value = false
  }
}
watch([() => props.modelValue, () => props.summary?.id], ([open]) => {
  if (open) load()
  else { request++; loading.value = false; error.value = '' }
})
</script>

<style scoped>
.detail-heading{display:flex;flex-direction:column;gap:6px;min-width:0}
.detail-identity{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.detail-path{font:13px/1.6 var(--app-font-mono);color:var(--app-text-primary);overflow-wrap:anywhere;background:var(--app-bg-control);border-radius:6px;padding:3px 8px}
.detail-chip{font-size:12px;color:var(--app-text-secondary);background:var(--app-bg-control);border-radius:6px;padding:3px 8px}
.detail-state{font-size:12px;padding:3px 8px;border-radius:6px;background:var(--el-color-success-light-9);color:var(--el-color-success-dark-2)}
.detail-state.is-off{background:var(--app-bg-control);color:var(--app-text-secondary)}
.detail-name{margin:0;font-size:20px;line-height:1.4;color:var(--app-text-primary);overflow-wrap:anywhere}
.detail-sub{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin:0;font-size:12px;color:var(--app-text-secondary)}
.detail-tags{display:inline-flex;flex-wrap:wrap;gap:6px;margin-left:4px}
.detail-tag{display:inline-flex;align-items:center;gap:5px;font-size:12px;color:var(--app-text-secondary);background:var(--app-bg-control);border-radius:6px;padding:2px 7px}
.detail-tag i{width:7px;height:7px;border-radius:50%}
.detail-loading,.detail-empty{padding:22px 0;font-size:13px;color:var(--app-text-secondary)}
.detail-error{padding:18px 0;font-size:13px;color:var(--el-color-danger-dark-2)}
.detail-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px 20px;margin:4px 0 0;padding:14px 16px;background:var(--app-bg-control);border-radius:12px}
.detail-facts div{min-width:0}
.detail-facts dt{font-size:12px;color:var(--app-text-secondary);margin-bottom:4px}
.detail-facts dd{margin:0;font-size:13px;color:var(--app-text-primary);overflow-wrap:anywhere}
.detail-section{margin-top:18px}
.detail-section h3{display:flex;align-items:center;gap:8px;margin:0 0 10px;font-size:14px;color:var(--app-text-primary)}
.detail-count{font-size:12px;font-weight:400;color:var(--app-text-secondary)}
.detail-url{display:flex;align-items:center;gap:8px;background:var(--app-bg-control);border-radius:9px;padding:8px 10px}
.detail-url code{flex:1;min-width:0;font:12px/1.7 var(--app-font-mono);color:var(--app-text-regular);overflow-wrap:anywhere}
.detail-description{margin:0;font-size:13px;line-height:1.9;color:var(--app-text-regular);white-space:pre-wrap;overflow-wrap:anywhere}
.response-group{display:flex;flex-direction:column;gap:8px;min-width:0}
.response-group + .response-group{margin-top:16px;padding-top:16px;border-top:1px solid var(--app-divider)}
.response-group__head{display:flex;align-items:center;flex-wrap:wrap;gap:8px;min-width:0}
.response-group__name{font:600 12px var(--app-font-mono);color:var(--app-text-primary);background:var(--app-bg-control);border-radius:7px;padding:4px 8px}
.response-group__action{font:11px var(--app-font-mono);color:var(--app-text-secondary);overflow-wrap:anywhere;min-width:0}
.response-group__desc{margin:0;font-size:12px;line-height:1.7;color:var(--app-text-secondary);overflow-wrap:anywhere}
.response-group .detail-empty{padding:6px 0}
.variant{background:var(--app-bg-control);border-radius:10px;padding:10px 12px;margin-bottom:8px}
.variant:last-child{margin-bottom:0}
.variant-head{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.variant-index{display:inline-flex;align-items:center;justify-content:center;min-width:20px;height:20px;border-radius:6px;background:var(--app-bg-surface);font:12px/1 var(--app-font-mono);color:var(--app-text-secondary)}
.variant-name{font-size:13px;color:var(--app-text-primary);overflow-wrap:anywhere}
.variant-code{font:12px/1 var(--app-font-mono);color:var(--app-text-secondary)}
.variant-state{font-size:12px;color:var(--el-color-success-dark-2)}
.variant-state.is-off{color:var(--app-text-secondary)}
.variant-chip{font-size:12px;color:var(--app-text-secondary);border:1px solid var(--app-divider);border-radius:6px;padding:2px 6px}
.variant-rule{margin:7px 0 0;font-size:12px;line-height:1.8;color:var(--app-text-secondary);overflow-wrap:anywhere}
.variant-body{margin:8px 0 0;padding:8px 10px;background:var(--app-bg-surface);border-radius:8px;font:12px/1.7 var(--app-font-mono);color:var(--app-text-regular);max-height:180px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere}
.variant-file{margin:8px 0 0;font-size:12px;color:var(--app-text-secondary)}
.detail-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%}
.detail-revision{font-size:12px;color:var(--app-text-secondary)}
.detail-actions{display:flex;align-items:center;gap:8px}
.api-detail-dialog :deep(.el-dialog__body){max-height:70vh;overflow:auto}
</style>
