import test from 'node:test'
import assert from 'node:assert/strict'
import { requireRevision, selectedRevisions } from '../src/utils/revisions.js'
test('a mutation requires the version originally loaded by its view', () => {
  assert.equal(requireRevision(3), 3)
  for (const value of [null, undefined, '3', 0, -1, 1.2, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => requireRevision(value), { code: 40920 })
})
test('batch maps each selected original row version without rereading current state', () => {
  assert.deepEqual(selectedRevisions(['b', 'a'], [{ id: 'a', revision: 2 }, { id: 'b', revision: 5 }]), { b: 5, a: 2 })
  assert.throws(() => selectedRevisions(['missing'], [{ id: 'a', revision: 1 }]), { code: 40920 })
})
