import test from 'node:test'
import assert from 'node:assert/strict'
import { freezeRows, historyComparisons, previewValue, sameConfiguration, normalizeApiPath, isVersionConflict } from '../src/utils/api-lifecycle.js'

test('snapshot comparison groups real body, rules, SOAP and file differences without mutating published snapshots', () => {
  const before = { schemaVersion: 1, config: { name: 'Old', method: 'GET', path: '/old', soap_config: '{"operations":[{"operationName":"Get","responseMode":"CONDITION"}]}' }, responses: [{ id: 'old-id', name: 'Default', body_type: 'FILE', response_body: 'old', conditions: '{"conditions":[]}', file_name: 'old.txt', file_size: 3 }], tags: [] }
  const after = structuredClone(before); after.config.path = '/new'; after.config.soap_config = '{"operations":[{"operationName":"Get","responseMode":"RANDOM"}]}'
  after.responses[0] = { ...after.responses[0], id: 'new-id', response_body: 'new', conditions: '{"conditions":[{"path":"status","operator":"EQ","value":"ok"}]}', file_name: 'new.txt' }
  const saved = JSON.stringify(before), groups = historyComparisons(before, after)
  assert.equal(groups.find(group => group.key === 'basic').changed, true)
  assert.equal(groups.find(group => group.key === 'soap').changed, true)
  const response = groups.find(group => group.key.startsWith('response:'))
  assert.equal(response.added, false); assert.equal(response.removed, false)
  assert.equal(response.rows.find(row => row.label === '响应正文').changed, true)
  assert.equal(response.rows.find(row => row.label.includes('conditions')).changed, true)
  assert.equal(response.rows.find(row => row.label === '附件元信息').changed, true)
  assert.equal(JSON.stringify(before), saved)
})
test('large preview truncation is explicit and equal structured objects do not invent diffs', () => {
  const preview = previewValue('x'.repeat(40000))
  assert.equal(preview.truncated, true); assert.match(preview.text, /预览已截断/)
  assert.equal(sameConfiguration({ a: 1, b: 2 }, { b: 2, a: 1 }), true)
})
test('writes freeze actual target revisions and reject cross-team or absent versions', () => {
  const frozen = freezeRows([{ id: 'api', teamId: 'a', revision: 7, name: 'API' }], 'a')
  assert.equal(frozen[0].revision, 7); assert.ok(Object.isFrozen(frozen[0]))
  assert.throws(() => freezeRows([{ id: 'api', teamId: 'b', revision: 7 }], 'a'))
  assert.throws(() => freezeRows([{ id: 'api', teamId: 'a' }], 'a'))
  assert.throws(() => freezeRows([], ''))
  assert.equal(normalizeApiPath(' user/info?query=1#fragment '), '/user/info')
  assert.equal(isVersionConflict({ code: 40921 }), true)
})
