import { requireRevision } from './revisions.js'

export const HISTORY_ACTIONS = Object.freeze({ CREATE: '创建接口', UPDATE: '更新配置', COPY: '复制为新接口', TOGGLE: '切换启用状态', SOFT_DELETE: '移入回收站', RESTORE: '从回收站恢复', ROLLBACK: '回滚配置', UPDATE_RESPONSE_BODY: '更新返回体', GROUP_UPDATE: '更新共享分组', GROUP_DELETE: '删除共享分组', TAG_UPDATE: '更新共享标签', TAG_DELETE: '删除共享标签', MIGRATION_BASELINE: '升级基线（不代表旧历史）', BATCH_ENABLE: '批量启用', BATCH_DISABLE: '批量停用', BATCH_DELETE: '批量移入回收站', 'BATCH_MOVE-GROUP': '批量移动分组' })
export function historyAction(value) { return HISTORY_ACTIONS[value] || value || '配置变更' }
export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
export function readableError(error, fallback = '操作未完成，请稍后重试') { return error?.msg || error?.message || fallback }
export function isVersionConflict(error) { return [40920, 40921].includes(Number(error?.code ?? error?.response?.data?.code)) }
export function normalizeApiPath(value) {
  if (typeof value !== 'string') throw new Error('接口路径缺失')
  const path = value.trim().split(/[?#]/, 1)[0].trim()
  if (!path) throw new Error('接口路径不能为空')
  return path.startsWith('/') ? path : '/' + path
}
export function parseStructured(value) {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) } catch { return value }
}
function stable(value) {
  if (Array.isArray(value)) return value.map(stable)
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, stable(value[key])]))
  return value
}
export function sameConfiguration(left, right) { return JSON.stringify(stable(left)) === JSON.stringify(stable(right)) }
export function previewValue(value, max = 32000) {
  if (value === undefined || value === null || value === '') return { text: '（空）', truncated: false }
  let text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  if (text.length < 100000 && typeof value === 'string') { const parsed = parseStructured(text); if (parsed && typeof parsed === 'object') text = JSON.stringify(parsed, null, 2) }
  const truncated = text.length > max
  return { text: truncated ? text.slice(0, max) + '\n…（预览已截断，请下载完整快照）' : text, truncated }
}
function row(label, before, after, key) {
  return { key: key || label, label, before, after, changed: !sameConfiguration(before, after), beforePreview: previewValue(before), afterPreview: previewValue(after) }
}
function snapshotConfig(snapshot) {
  const config = { ...(snapshot?.config || {}) }
  if (config.response_body_ref && !Object.hasOwn(config, 'response_body')) config.response_body = snapshot?.responses?.find(r => r.id === config.response_body_ref)?.response_body
  return config
}
export function historyComparisons(before, after) {
  if (!before || !after) return []
  const left = snapshotConfig(before), right = snapshotConfig(after)
  const fields = [['name', '接口名称'], ['type', '接口类型'], ['method', 'HTTP 方法'], ['path', '请求路径'], ['group_id', '分组引用'], ['enabled', '启用状态'], ['response_mode', '返回体选择'], ['response_code', '兼容状态码'], ['content_type', '兼容内容类型'], ['delay_ms', '延迟 / ms'], ['description', '接口描述']]
  const groups = [{ key: 'basic', title: '基本配置', rows: fields.map(([key, label]) => row(label, left[key], right[key], key)) }]
  groups.push({ key: 'headers', title: '接口响应头覆盖', rows: [row('响应头', parseStructured(left.global_header_overrides), parseStructured(right.global_header_overrides))] })
  groups.push({ key: 'soap', title: 'SOAP 结构与 operation 配置', rows: [row('SOAP 配置', parseStructured(left.soap_config), parseStructured(right.soap_config))] })
  groups.push({ key: 'tags', title: '分组与标签关联', rows: [row('分组快照', before.group, after.group), row('标签', before.tags, after.tags)] })
  const indexResponses = responses => {
    const seen = new Map()
    return new Map((responses || []).map(response => {
      const key = String(response.soap_operation_name || 'REST') + ' / ' + String(response.name || '未命名返回体')
      const count = (seen.get(key) || 0) + 1; seen.set(key, count)
      return [key + (count > 1 ? ' #' + count : ''), response]
    }))
  }
  const oldResponses = indexResponses(before.responses), newResponses = indexResponses(after.responses)
  for (const name of new Set([...oldResponses.keys(), ...newResponses.keys()])) {
    const old = oldResponses.get(name), now = newResponses.get(name)
    const meta = value => value ? Object.fromEntries(['name', 'soap_operation_name', 'response_code', 'content_type', 'body_type', 'delay_ms', 'is_active', 'sort_order'].map(key => [key, value[key]])) : null
    const file = value => value?.body_type === 'FILE' ? Object.fromEntries(['file_name', 'download_name', 'file_path', 'file_size'].map(key => [key, value[key]])) : null
    groups.push({ key: 'response:' + name, title: name, added: !old, removed: !now, rows: [row('返回体配置', meta(old), meta(now)), row('匹配条件 conditions', parseStructured(old?.conditions), parseStructured(now?.conditions)), row('响应正文', old?.response_body, now?.response_body), row('附件元信息', file(old), file(now))] })
  }
  if (!(before.responses?.length || after.responses?.length)) groups.push({ key: 'legacy', title: '兼容默认返回体', rows: [row('响应正文', left.response_body, right.response_body)] })
  return groups.map(group => ({ ...group, changed: group.rows.some(item => item.changed) }))
}
export function freezeRows(rows, teamId) {
  if (!teamId || rows.some(row => row.teamId !== teamId)) throw new Error('当前团队上下文已变化，请刷新后重试')
  return rows.map(row => Object.freeze({ id: row.id, teamId, revision: requireRevision(row.revision), name: row.name, method: row.method, path: row.path }))
}
