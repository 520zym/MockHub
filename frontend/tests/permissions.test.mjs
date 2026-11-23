import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeTeamRole, roleLabel, teamCapability, safeStoredUser, chooseTeam, teamStorageKey } from '../src/utils/permissions.js'

const user = role => ({ globalRole: 'USER', status: 'ACTIVE', teams: [{ teamId: 'a', role, membershipRole: role, accessMode: 'MEMBER' }] })
test('viewer reads but never writes or manages', () => {
  assert.equal(teamCapability(user('VIEWER'), 'a'), true)
  for (const action of ['write', 'manageMembers', 'purge', 'overwriteImport', 'batchDelete']) assert.equal(teamCapability(user('VIEWER'), 'a', action), false)
})
test('editor and legacy member edit team resources but cannot manage members', () => {
  for (const role of ['EDITOR', 'MEMBER']) {
    assert.equal(teamCapability(user(role), 'a', 'write'), true)
    assert.equal(teamCapability(user(role), 'a', 'manageMembers'), false)
  }
})
test('unknown role and unlisted teams never gain rights', () => {
  assert.equal(normalizeTeamRole('SUPER_ADMIN'), null)
  assert.equal(teamCapability(user('UNKNOWN'), 'a', 'write'), false)
  assert.equal(teamCapability(user('TEAM_ADMIN'), 'b', 'write'), false)
  assert.equal(teamCapability(user('TEAM_ADMIN'), 'a', 'unknown-capability'), false)
})
test('team admin and system maintenance are distinct', () => {
  assert.equal(teamCapability(user('TEAM_ADMIN'), 'a', 'manageMembers'), true)
  const system = { globalRole: 'SUPER_ADMIN', teams: [{ teamId: 'a', membershipRole: null, accessMode: 'SYSTEM_MAINTENANCE' }] }
  assert.equal(teamCapability(system, 'a', 'write'), true)
  assert.equal(teamCapability(system, 'unknown', 'write'), false)
  assert.equal(roleLabel(system.teams[0]), '系统维护身份')
})
test('server capabilities override fallback and archived teams remain read only', () => {
  const current = user('TEAM_ADMIN')
  current.teams[0].capabilities = { write: false, manageMembers: false }
  assert.equal(teamCapability(current, 'a', 'write'), false)
  current.teams[0].teamStatus = 'ARCHIVED'
  assert.equal(teamCapability(current, 'a', 'read'), true)
  assert.equal(teamCapability(current, 'a', 'purge'), false)
  current.status = 'DISABLED'
  assert.equal(teamCapability(current, 'a', 'read'), false)
})
test('storage is per account and malformed cached user cannot crash startup', () => {
  for (const bad of ['not-json', '[]', 'false', 'null']) assert.equal(safeStoredUser(bad), null)
  assert.equal(safeStoredUser('{"id":"u"}').id, 'u')
  assert.notEqual(teamStorageKey('a'), teamStorageKey('b'))
  assert.equal(teamStorageKey(null), null)
})
test('team selection revalidates revoked choice without inventing a personal team', () => {
  const teams = [{ id: 'archived', status: 'ARCHIVED' }, { id: 'active', status: 'ACTIVE' }]
  assert.equal(chooseTeam(teams, 'gone'), 'active')
  assert.equal(chooseTeam(teams, 'archived'), 'archived')
  assert.equal(chooseTeam([], 'gone'), null)
})
