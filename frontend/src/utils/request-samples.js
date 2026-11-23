import { parseXmlSampleTree } from './xmlSampleTree.js'

export const SAMPLE_SOURCES = ['BODY', 'QUERY', 'HEADER']
const utf8Size = value => new TextEncoder().encode(value).length

/** Servlet-style first value, including URLSearchParams '+' and percent decoding. */
export function parseQuerySample(text = '') {
  let query = text.trim().split('#')[0]
  const question = query.indexOf('?')
  if (question >= 0) query = query.slice(question + 1)
  const first = new Map()
  for (const [key, value] of new URLSearchParams(query)) if (!first.has(key)) first.set(key, value)
  return validateSampleMap(Object.fromEntries(first), false)
}

/** Names are case-insensitive; keep the first occurrence and split only at the first colon. */
export function parseHeaderSample(text = '') {
  const raw = text.trim()
  if (!raw) return {}
  let entries
  if (raw.startsWith('{')) {
    let parsed
    try { parsed = JSON.parse(raw) } catch { throw new Error('Header JSON 格式无效，请使用对象或 Name: value') }
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Header 示例必须为 JSON 对象')
    entries = Object.entries(parsed).map(([key, value]) => {
      if (value !== null && typeof value === 'object') throw new Error('Header 值必须是单个文本值')
      return [key, value == null ? '' : String(value)]
    })
  } else {
    entries = raw.split(/\r?\n/).filter(line => line.trim()).map(line => {
      const colon = line.indexOf(':')
      if (colon <= 0) throw new Error('每行 Header 请使用 Name: value 格式')
      return [line.slice(0, colon).trim(), line.slice(colon + 1).trim()]
    })
  }
  const seen = new Set(), first = []
  for (const [key, value] of entries) {
    const normalized = key.toLowerCase()
    if (!seen.has(normalized)) { seen.add(normalized); first.push([key, value]) }
  }
  return validateSampleMap(Object.fromEntries(first), true)
}

export function validateSampleMap(values, headers) {
  const entries = Object.entries(values)
  if (entries.length > 100) throw new Error('Header / Query 各最多 100 项')
  let bytes = 0
  for (const [key, value] of entries) {
    if (!key.trim() || key.length > 256 || value.length > 8192) throw new Error('字段名最多 256 字符，值最多 8192 字符')
    if (headers && (!/^[!#$%&'*+.^_\x60|~0-9A-Za-z-]+$/.test(key) || /[\r\n]/.test(value))) throw new Error('Header 名称或值格式无效')
    bytes += utf8Size(key) + utf8Size(value)
  }
  if (bytes > 64 * 1024) throw new Error('Header / Query 各最多 64 KiB')
  return values
}

/** Only prepares samples and field paths, never evaluates a Java matching operator. */
export function sampleTreeText(source, text = '', kind = 'json') {
  if (!text.trim()) return ''
  if (source === 'QUERY') return JSON.stringify(parseQuerySample(text), null, 2)
  if (source === 'HEADER') return JSON.stringify(parseHeaderSample(text), null, 2)
  if (utf8Size(text) > 1024 * 1024) throw new Error('Body 示例最多 1 MiB（UTF-8）')
  if (kind === 'xml') parseXmlSampleTree(text)
  else {
    try { JSON.parse(text) } catch { throw new Error('Body JSON 格式无效，请检查后再解析') }
  }
  return text
}

export function buildPreviewSample(texts, kind = 'json') {
  const body = texts.BODY || ''
  if (utf8Size(body) > 1024 * 1024) throw new Error('Body 示例最多 1 MiB（UTF-8）')
  // The server owns malformed-body behavior. Only the field-tree parser requires valid JSON/XML.
  const headers = parseHeaderSample(texts.HEADER || '')
  const explicitType = Object.entries(headers).find(([key]) => key.toLowerCase() === 'content-type')?.[1]
  return { body, query: parseQuerySample(texts.QUERY || ''), headers, contentType: explicitType ?? (kind === 'xml' ? 'application/soap+xml' : 'application/json') }
}

/** Selection indexes must survive unchanged. Omit response bodies and storage paths entirely. */
export function previewResponses(responses, operationName, selectedIndex, conditions) {
  if (responses.length > 50) throw new Error('返回体最多 50 条，请先减少返回体数量')
  return responses.map((response, index) => ({
    id: response.id || undefined,
    clientKey: response.id || 'draft-' + index,
    soapOperationName: operationName || null,
    name: response.name,
    responseCode: response.responseCode,
    bodyType: response.bodyType || 'TEXT',
    active: !!(response.isActive ?? response.active),
    sortOrder: response.sortOrder ?? index,
    conditions: index === selectedIndex ? conditions : (response.conditions || '')
  }))
}
