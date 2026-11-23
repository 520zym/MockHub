import test from 'node:test'
import assert from 'node:assert/strict'
import { deflateRawSync } from 'node:zlib'
import { readImportManifest, planImport, fetchTargetVersions } from '../src/utils/api-import.js'
const manifest = { apis: [{ id: 'source', name: 'Source', method: 'GET', path: 'users?x=1', revision: 999 }] }
const target = { id: 'target', teamId: 'a', method: 'GET', path: '/users', revision: 7 }
function zipJson(data, method = 8) {
  const name = Buffer.from('mockhub-export.json'), raw = Buffer.from(JSON.stringify(data)), compressed = method === 8 ? deflateRawSync(raw) : raw
  const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50); local.writeUInt16LE(20, 4); local.writeUInt16LE(method, 8); local.writeUInt32LE(compressed.length, 18); local.writeUInt32LE(raw.length, 22); local.writeUInt16LE(name.length, 26)
  const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6); central.writeUInt16LE(method, 10); central.writeUInt32LE(compressed.length, 20); central.writeUInt32LE(raw.length, 24); central.writeUInt16LE(name.length, 28)
  const end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50); end.writeUInt16LE(1, 8); end.writeUInt16LE(1, 10); end.writeUInt32LE(central.length + name.length, 12); end.writeUInt32LE(local.length + name.length + compressed.length, 16)
  return new File([local, name, compressed, central, name, end], 'export.zip', { type: 'application/zip' })
}
test('override preview uses current target versions, never source revision', () => {
  const plan = planImport(manifest, [target], 'override')
  assert.deepEqual(plan.expectedRevisions, { target: 7 }); assert.equal(plan.items[0].targetRevision, 7); assert.equal(plan.counts.override, 1)
  assert.deepEqual(planImport(manifest, [target], 'merge').counts, { create: 0, skip: 1, override: 0 })
  assert.throws(() => planImport({ apis: [...manifest.apis, { ...manifest.apis[0], id: 'duplicate', path: '/users#fragment' }] }, [target], 'override'), /重复/)
})
test('both JSON and exported-style DEFLATE ZIP manifests are really parsed', async () => {
  const json = await readImportManifest(new File(['\uFEFF' + JSON.stringify(manifest)], 'export.json'))
  assert.equal(json.data.apis[0].revision, 999)
  for (const method of [0, 8]) { const zip = await readImportManifest(zipJson(manifest, method)); assert.equal(zip.format, 'ZIP'); assert.equal(zip.data.apis[0].id, 'source') }
})
test('portable SOAP bundle 2.3 keeps explicit WSDL attachment metadata in JSON and ZIP preview', async () => {
  const data = { ...manifest, version: '2.3', apiWsdlFiles: { source: 'wsdl/11111111-1111-4111-8111-111111111111.wsdl' } }
  for (const file of [new File([JSON.stringify(data)], 'soap.json'), zipJson(data)]) {
    const parsed = await readImportManifest(file)
    assert.equal(parsed.data.version, '2.3'); assert.deepEqual(parsed.data.apiWsdlFiles, data.apiWsdlFiles)
    assert.deepEqual(planImport(parsed.data, [target], 'override').expectedRevisions, { target: 7 })
  }
})
test('unavailable local ZIP preview never invents counts or weakens target version mapping', async () => {
  const result = await readImportManifest(zipJson(manifest), { decompressor: null })
  assert.equal(result.data, null); assert.ok(result.warning)
  const plan = planImport(result.data, [target], 'override'); assert.equal(plan.counts, null); assert.deepEqual(plan.expectedRevisions, { target: 7 })
})
test('paged target reads stay scoped and late context changes stop additional pages', async () => {
  const rows = Array.from({ length: 101 }, (_, index) => ({ ...target, id: 'id-' + index })), calls = []
  const all = await fetchTargetVersions(async params => { calls.push(params); return { items: rows.slice((params.page - 1) * 100, params.page * 100), total: rows.length } }, 'a')
  assert.equal(all.length, 101); assert.equal(calls.length, 2); assert.ok(calls.every(call => call.teamId === 'a'))
  let current = true, attempts = 0
  await assert.rejects(fetchTargetVersions(async () => { attempts++; current = false; return { items: rows.slice(0, 100), total: 101 } }, 'a', () => current), { name: 'AbortError' })
  assert.equal(attempts, 1)
  await assert.rejects(fetchTargetVersions(async () => ({ items: [{ ...target, teamId: 'b' }], total: 1 }), 'a'), /其他团队/)
  await assert.rejects(fetchTargetVersions(async () => ({ items: [], total: 0 }), ''), /必须选择/)
})
