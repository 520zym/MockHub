import { normalizeApiPath } from './api-lifecycle.js'
import { requireRevision } from './revisions.js'
const MAX_BYTES = 100 * 1024 * 1024
const MAX_ENTRIES = 1000
function fail(message) { throw new Error(message) }
function decode(bytes) { return new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/, '') }
function validateManifest(data) {
  if (!data || typeof data !== 'object' || !Array.isArray(data.apis)) fail('文件必须包含 apis 接口数组')
  if (data.apis.length > 10000) fail('单次预览最多支持 10000 个接口，请拆分导入包')
  return data
}
async function boundedText(stream, limit) {
  const reader = stream.getReader(), chunks = []; let size = 0
  try { for (;;) { const { done, value } = await reader.read(); if (done) break; size += value.byteLength; if (size > limit) { await reader.cancel(); fail('导入 JSON 解压后超过 100 MB') } chunks.push(value) } }
  finally { reader.releaseLock() }
  const bytes = new Uint8Array(size); let offset = 0
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength }
  return decode(bytes)
}
export async function readImportManifest(file, { maxBytes = MAX_BYTES, decompressor = globalThis.DecompressionStream } = {}) {
  if (!file || file.size <= 0) fail('请选择非空 JSON 或 ZIP 文件')
  if (file.size > maxBytes) fail('导入文件不能超过 100 MB')
  const prefix = new DataView(await file.slice(0, 4).arrayBuffer())
  const zip = prefix.byteLength >= 4 && [0x04034b50, 0x06054b50].includes(prefix.getUint32(0, true))
  if (!zip) {
    let data
    try { data = JSON.parse(decode(await file.arrayBuffer())) } catch { fail('JSON 文件无法解析，请检查格式') }
    return { data: validateManifest(data), format: 'JSON', warning: '' }
  }
  const tailOffset = Math.max(0, file.size - 65557), tail = new DataView(await file.slice(tailOffset).arrayBuffer())
  let end = -1
  for (let index = tail.byteLength - 22; index >= 0; index--) if (tail.getUint32(index, true) === 0x06054b50) { end = index; break }
  if (end < 0) fail('ZIP 目录损坏，无法读取导入包')
  const count = tail.getUint16(end + 10, true), centralSize = tail.getUint32(end + 12, true), centralOffset = tail.getUint32(end + 16, true)
  if (count > MAX_ENTRIES) fail('ZIP 条目过多，最多支持 1000 个条目')
  if (centralOffset + centralSize > file.size || centralSize > 10 * 1024 * 1024) fail('ZIP 目录大小或偏移非法')
  const directory = new DataView(await file.slice(centralOffset, centralOffset + centralSize).arrayBuffer())
  let cursor = 0, entry = null
  for (let index = 0; index < count; index++) {
    if (cursor + 46 > directory.byteLength || directory.getUint32(cursor, true) !== 0x02014b50) fail('ZIP 条目目录损坏')
    const nameSize = directory.getUint16(cursor + 28, true), extraSize = directory.getUint16(cursor + 30, true), commentSize = directory.getUint16(cursor + 32, true)
    if (cursor + 46 + nameSize + extraSize + commentSize > directory.byteLength) fail('ZIP 条目长度非法')
    const name = decode(new Uint8Array(directory.buffer, cursor + 46, nameSize))
    if (name === 'mockhub-export.json') {
      if (entry) fail('ZIP 重复定义 mockhub-export.json')
      entry = { flags: directory.getUint16(cursor + 8, true), method: directory.getUint16(cursor + 10, true), compressed: directory.getUint32(cursor + 20, true), size: directory.getUint32(cursor + 24, true), offset: directory.getUint32(cursor + 42, true) }
    }
    cursor += 46 + nameSize + extraSize + commentSize
  }
  if (!entry) fail('ZIP 中缺少 mockhub-export.json')
  if (entry.flags & 1) fail('不支持加密导入包，请导出为普通 ZIP 或 JSON')
  if (entry.size > maxBytes || entry.compressed > maxBytes) fail('导入 JSON 超过 100 MB')
  if (![0, 8].includes(entry.method) || (entry.method === 8 && !decompressor)) return { data: null, format: 'ZIP', warning: '当前浏览器无法解包此 ZIP 预览。可以提交给服务端完整验证；界面不会推测新增或覆盖数量。' }
  const local = new DataView(await file.slice(entry.offset, entry.offset + 30).arrayBuffer())
  if (local.byteLength < 30 || local.getUint32(0, true) !== 0x04034b50) fail('ZIP 本地条目损坏')
  const start = entry.offset + 30 + local.getUint16(26, true) + local.getUint16(28, true)
  if (start + entry.compressed > centralOffset) fail('ZIP 内容偏移非法')
  let stream = file.slice(start, start + entry.compressed).stream()
  if (entry.method === 8) {
    try { stream = stream.pipeThrough(new decompressor('deflate-raw')) } catch { return { data: null, format: 'ZIP', warning: '当前浏览器不支持 ZIP 解压预览。服务端仍会完整验证；数量以最终导入结果为准。' } }
  }
  let data
  try { data = JSON.parse(await boundedText(stream, maxBytes)) } catch (error) { fail('ZIP 内的 JSON 无法读取：' + error.message) }
  return { data: validateManifest(data), format: 'ZIP', warning: '' }
}
export async function fetchTargetVersions(getApis, teamId, stillCurrent = () => true) {
  if (!teamId) fail('必须选择当前团队，不会回退到全团队查询')
  const rows = new Map(); let expectedTotal = null
  for (let page = 1; page <= 1000; page++) {
    if (!stillCurrent()) throw new DOMException('团队上下文已变化', 'AbortError')
    const response = await getApis({ teamId, page, size: 100, sortBy: 'createdAt', sortDir: 'asc' })
    if (!stillCurrent()) throw new DOMException('团队上下文已变化', 'AbortError')
    if (!Array.isArray(response.items) || !Number.isSafeInteger(response.total) || response.total < 0) fail('目标接口列表响应无效')
    if (expectedTotal === null) expectedTotal = response.total
    if (response.total !== expectedTotal) fail('目标团队接口数量已变化，请重新预览')
    for (const row of response.items) {
      if (row.teamId !== teamId) fail('目标列表包含其他团队的数据，已拒绝导入')
      requireRevision(row.revision); rows.set(row.id, row)
    }
    if (page * 100 >= expectedTotal) { if (rows.size !== expectedTotal) fail('目标分页列表已变化，请重新预览'); return [...rows.values()] }
  }
  fail('目标团队接口过多，请拆分或由管理员核对后导入')
}
export function planImport(data, targets, mode = 'merge') {
  if (!['merge', 'override'].includes(mode)) fail('导入模式无效')
  const targetIndex = new Map(targets.map(target => [String(target.method).toUpperCase() + ' ' + normalizeApiPath(target.path), target]))
  if (!data) return { opaque: true, items: [], counts: null, expectedRevisions: mode === 'override' ? Object.fromEntries(targets.map(target => [target.id, requireRevision(target.revision)])) : {} }
  validateManifest(data)
  const seen = new Set(), ids = new Set(), expectedRevisions = {}, counts = { create: 0, skip: 0, override: 0 }
  const items = data.apis.map(source => {
    if (!source?.id || ids.has(source.id)) fail('源包接口 ID 缺失或重复'); ids.add(source.id)
    if (!source.method || typeof source.method !== 'string') fail('源包 HTTP 方法缺失')
    const method = source.method.trim().toUpperCase(), path = normalizeApiPath(source.path), route = method + ' ' + path
    if (seen.has(route)) fail('源包存在规范化后重复的路径与方法：' + route); seen.add(route)
    const target = targetIndex.get(route), status = target ? mode === 'override' ? 'override' : 'skip' : 'create'
    counts[status]++
    if (status === 'override') expectedRevisions[target.id] = requireRevision(target.revision)
    return Object.freeze({ sourceId: source.id, name: source.name || path, method, path, status, targetId: target?.id || null, targetRevision: target?.revision || null })
  })
  return { opaque: false, items, counts, expectedRevisions }
}
