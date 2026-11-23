import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { reactive } from 'vue'
import { loadSetup, deferred, settle } from './helpers/sfc-unit.mjs'
import { teamCapability } from '../src/utils/permissions.js'

// Real SFC setup and real capability utility; request, dialogs, icons, route and stores
// are explicit unit stubs. This is not browser integration or real-backend coverage.
const file = 'frontend/src/views/McpConfirm.vue'
const draft = (extra = {}) => ({ id: 'draft-a', teamId: 'team-a', actionType: 'CREATE_API', status: 'PENDING', baseRevision: 0,
  expiresAt: new Date(Date.now() + 120000).toISOString(), summary: { teamId: 'team-a', name: '用户接口', method: 'GET', path: '/users' },
  responseMode: 'CONDITION', responses: [{ name: 'R', active: true, responseCode: 200, bodyType: 'TEXT', body: '{"ok":true}', conditions: '' }], preview: '{"ok":true}', ...extra })
function stores(role = 'EDITOR') {
  const switches = [], user = reactive({ token: 'session-a', identityReady: true, user: { id: 'user-a', status: 'ACTIVE', globalRole: 'USER', teams: [
    { teamId: 'team-a', membershipRole: role, teamStatus: 'ACTIVE' }, { teamId: 'team-b', membershipRole: 'EDITOR', teamStatus: 'ACTIVE' }
  ] }, hasCapability(id, capability = 'read') { return this.identityReady && teamCapability(this.user, id, capability) },
  canWriteTeam(id) { return this.identityReady && teamCapability(this.user, id, 'write') } })
  const app = reactive({ teams: [{ id: 'team-a', name: '团队 A', status: 'ACTIVE' }, { id: 'team-b', name: '团队 B', status: 'ACTIVE' }], currentTeamId: 'team-a', apiRevision: 0,
    get currentTeam() { return this.teams.find(team => team.id === this.currentTeamId) || null },
    setFilter(id) { switches.push(id); if (id != null && !this.teams.some(team => team.id === id)) return false; this.currentTeamId = id || null; return true },
    bumpApiRevision() { this.apiRevision++ } })
  return { user, app, switches }
}
async function page(t, { role, get = async id => draft({ id }), post = async (_, suffix) => suffix === 'confirm' ? { status: 'CONFIRMED', apiId: 'api-created' } : { status: 'CANCELLED' }, confirm = async () => {}, before } = {}) {
  const context = stores(role), route = reactive({ params: { id: 'draft-a' } }), calls = [], prompts = [], notices = [], navigations = []
  before?.(context)
  const timer = { callback: null, cleared: [] }, priorWindow = globalThis.window
  globalThis.window = { setInterval(fn) { timer.callback = fn; return 17 }, clearInterval(id) { timer.cleared.push(id) } }
  const unit = await loadSetup(file, {
    'vue-router': { useRoute: () => route, useRouter: () => ({ back() { navigations.push('back') }, push(url) { navigations.push(url) } }) },
    '@/stores/app': { useAppStore: () => context.app }, '@/stores/user': { useUserStore: () => context.user },
    '@/api/request': { default: {
      get(url, config) { const id = decodeURIComponent(url.split('/').at(-1)); calls.push({ method: 'get', id, url, config }); return get(id, config) },
      post(url, data, config) { const parts = url.split('/'), suffix = parts.at(-1), id = decodeURIComponent(parts.at(-2)); calls.push({ method: 'post', id, suffix, url, data, config }); return post(id, suffix, config) }
    } },
    'element-plus': { ElMessage: { success(message) { notices.push(['success', message]) } }, ElMessageBox: { confirm(...args) { prompts.push(args); return confirm(...args) } } },
    '@element-plus/icons-vue': Object.fromEntries(['ArrowLeft', 'Check', 'Clock', 'EditPen', 'Refresh'].map(name => [name, {}]))
  })
  const instance = unit.setup({})
  t.after(() => { unit.unmount(); globalThis.window = priorWindow })
  return { ...context, route, calls, prompts, notices, navigations, timer, unit, ...instance, s: instance.state }
}
test('owned legacy-baseline drafts can be cancelled without enabling confirmation', async t => {
  const c = await page(t, { role: 'VIEWER', get: async id => draft({ id, baseRevision: null, requiresRegeneration: true }) })
  await c.unit.mount()
  assert.equal(c.s.canConfirm.value, false); assert.equal(c.s.canCancel.value, true)
  await c.s.handleCancel(); assert.equal(c.s.action.value.status, 'CANCELLED'); assert.equal(c.app.apiRevision, 0)
})
const posts = c => c.calls.filter(call => call.method === 'post')
const gets = c => c.calls.filter(call => call.method === 'get')

test('VIEWER cannot confirm even through a direct handler, but may cancel their owned pending draft (API stub)', async t => {
  const c = await page(t, { role: 'VIEWER' }); await c.s.load()
  assert.equal(c.s.canViewAction.value, true); assert.equal(c.s.canConfirm.value, false); assert.equal(c.s.canCancel.value, true)
  await c.s.handleConfirm(); assert.equal(c.prompts.length, 0); assert.equal(posts(c).length, 0)
  assert.match(c.s.confirmDisabledReason.value, /只读/)
  await c.s.handleCancel()
  assert.equal(posts(c)[0].suffix, 'cancel'); assert.equal(c.s.action.value.status, 'CANCELLED')
  assert.equal(c.app.apiRevision, 0); assert.equal(c.notices.length, 1)
})

test('actual action team controls capability checks; summary cannot redirect rights or fabricate ownership (API stub)', async t => {
  for (const [label, detail] of [
    ['conflicting team fields', draft({ teamId: 'team-b' })],
    ['missing actual team', draft({ teamId: undefined })],
    ['wrong action id', draft({ id: 'someone-else' })],
    ['explicit foreign owner even for super-admin', draft({ userId: 'other-user' })],
    ['unknown actual team', draft({ teamId: 'unknown', summary: { teamId: 'unknown' } })]
  ]) await t.test(label, async sub => {
    const c = await page(sub, { get: async () => detail, before: c => { c.user.user.globalRole = 'SUPER_ADMIN' } }); await c.s.load()
    assert.equal(c.s.action.value, null); assert.ok(c.s.loadError.value); assert.equal(c.s.canViewAction.value, false)
    await c.s.handleConfirm(); await c.s.handleCancel(); assert.equal(posts(c).length, 0); assert.equal(c.notices.length, 0); assert.deepEqual(c.switches, [])
  })
  await t.test('summary team absence does not replace actual team capability', async sub => {
    const c = await page(sub, { get: async () => draft({ teamId: 'team-b', summary: { name: 'B' } }), before: c => { c.app.currentTeamId = 'team-b'; c.user.user.teams[1].membershipRole = 'VIEWER' } })
    await c.s.load(); assert.equal(c.s.targetTeamId.value, 'team-b'); assert.equal(c.s.canConfirm.value, false); assert.equal(c.s.canCancel.value, true)
  })
})

test('authorized cross-sidebar draft names its actual target and switches only after explicit setFilter action (API stub)', async t => {
  const c = await page(t, { get: async () => draft({ teamId: 'team-b', summary: { teamId: 'team-b', name: 'B', path: '/b' } }) })
  await c.s.load()
  assert.equal(c.s.targetTeamId.value, 'team-b'); assert.equal(c.s.teamMatches.value, false); assert.equal(c.s.canConfirm.value, false)
  assert.equal(c.s.teamLabel('team-b'), '团队 B'); assert.equal(c.app.currentTeamId, 'team-a'); assert.deepEqual(c.switches, [])
  await c.s.handleConfirm(); assert.equal(posts(c).length, 0)
  c.s.switchToTargetTeam(); await settle()
  assert.deepEqual(c.switches, ['team-b']); assert.equal(c.app.currentTeamId, 'team-b'); assert.equal(c.s.canConfirm.value, true)
})

test('write confirmation requires ACTIVE team, supported pending action, stored revision, intact payload and valid expiry (API stub)', async t => {
  for (const [label, detail, change] of [
    ['archived team', draft(), c => { c.app.teams[0].status = 'ARCHIVED'; c.user.user.teams[0].teamStatus = 'ARCHIVED' }],
    ['unsupported action', draft({ actionType: 'DELETE_API' })], ['already processed', draft({ status: 'CONFIRMED' })],
    ['legacy version', draft({ baseRevision: null, requiresRegeneration: true })], ['corrupt payload', draft({ payloadInvalid: true })],
    ['expired', draft({ expiresAt: new Date(Date.now() - 1000).toISOString() })], ['missing expiry', draft({ expiresAt: null })], ['invalid expiry', draft({ expiresAt: 'not a date' })]
  ]) await t.test(label, async sub => {
    const c = await page(sub, { get: async () => detail, before: change }); await c.s.load(); await c.s.handleConfirm()
    assert.equal(c.s.canConfirm.value, false); assert.equal(c.prompts.length, 0); assert.equal(posts(c).length, 0); assert.equal(c.notices.length, 0)
    if (label === 'archived team' || label === 'unsupported action' || label === 'corrupt payload') assert.equal(c.s.canCancel.value, true)
  })
})

test('confirmation is awaited, duplicate mutation/refresh is gated, then exact existing endpoint receives null payload and stored baseline stays untouched (API stub)', async t => {
  const consent = deferred(), result = deferred(), c = await page(t, { get: async () => draft({ actionType: 'UPDATE_API_FULL', baseRevision: 7, sourcePolicy: 'DRAFT_ONLY' }), post: () => result.promise, confirm: () => consent.promise })
  await c.s.load(); const applying = c.s.handleConfirm()
  assert.equal(c.prompts.length, 1); assert.match(c.prompts[0][0], /团队 A/); assert.equal(posts(c).length, 0)
  await c.s.handleConfirm(); await c.s.handleCancel(); await c.s.load()
  assert.equal(c.prompts.length, 1); assert.equal(gets(c).length, 1)
  consent.resolve(); await settle(); assert.equal(posts(c).length, 1)
  const call = posts(c)[0]; assert.equal(call.url, '/ai/actions/draft-a/confirm'); assert.equal(call.data, null)
  assert.equal(call.config.timeout, 120000); assert.equal(call.config.skipGlobalNetworkError, true); assert.ok(call.config.signal instanceof AbortSignal)
  assert.equal(c.s.action.value.baseRevision, 7)
  result.resolve({ status: 'CONFIRMED', apiId: 'api-a', revision: 8, name: 'updated', method: 'POST', path: '/updated' }); await applying
  assert.equal(c.s.action.value.status, 'CONFIRMED'); assert.equal(c.s.action.value.resultApiId, 'api-a'); assert.equal(c.s.action.value.baseRevision, 7)
  assert.equal(c.app.apiRevision, 1); assert.equal(c.notices.length, 1); assert.equal(gets(c).length, 1)
})

test('canceling the human confirmation never posts, loses the preview, or reports success (API stub)', async t => {
  const c = await page(t, { confirm: async () => { throw new Error('cancel') } }); await c.s.load(); const before = JSON.stringify(c.s.action.value)
  await c.s.handleConfirm(); assert.equal(posts(c).length, 0); assert.equal(c.notices.length, 0); assert.equal(c.s.error.value, '')
  assert.equal(JSON.stringify(c.s.action.value), before); assert.equal(c.s.confirming.value, false)
})

const changes = [
  ['route', c => { c.route.params.id = 'draft-b' }],
  ['sidebar team', c => { c.app.currentTeamId = 'team-b' }],
  ['sidebar clear', c => { c.app.currentTeamId = null }],
  ['write revocation', c => { c.user.user.teams[0].membershipRole = 'VIEWER' }],
  ['capability revocation', c => { c.user.user.teams[0].capabilities = { read: true, write: false } }],
  ['same-tick role revoke/restore', c => { c.user.user.teams[0].membershipRole = 'VIEWER'; c.user.user.teams[0].membershipRole = 'EDITOR' }],
  ['read revocation', c => { c.user.user.teams[0].capabilities = { read: false, write: true } }],
  ['team archive', c => { c.app.teams[0].status = 'ARCHIVED' }],
  ['team disable', c => { c.app.teams[0].status = 'DISABLED' }],
  ['user switch', c => { c.user.user.id = 'user-b' }],
  ['session replacement', c => { c.user.token = 'session-b' }],
  ['identity unavailable', c => { c.user.identityReady = false }],
  ['user disabled', c => { c.user.user.status = 'DISABLED' }],
  ['unmount', c => { c.unit.unmount() }]
]

test('every live route/team/user/capability boundary invalidates awaited human confirmation before any POST (API stub)', async t => {
  for (const [label, change] of changes) await t.test(label, async sub => {
    const consent = deferred(), c = await page(sub, { confirm: () => consent.promise }); await c.s.load()
    const pending = c.s.handleConfirm(); change(c); consent.resolve(); await pending; await settle()
    assert.equal(posts(c).length, 0); assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0)
  })
})

test('late detail success/error cannot attach to changed context even when transport ignores AbortSignal (API stub)', async t => {
  for (const [label, change] of changes) for (const outcome of ['success', 'error']) await t.test(label + ' / ' + outcome, async sub => {
    const delayed = deferred(); let count = 0
    const c = await page(sub, { get: id => ++count === 1 ? delayed.promise : Promise.resolve(draft({ id, preview: 'current authorized draft' })) })
    const oldLoad = c.s.load(), signal = gets(c)[0].config.signal; change(c); await settle(); assert.equal(signal.aborted, true)
    const before = JSON.stringify(c.s.action.value), errorBefore = c.s.loadError.value
    if (outcome === 'success') delayed.resolve(draft({ preview: 'stale private payload' })); else delayed.reject({ msg: 'stale private error' })
    await oldLoad; assert.equal(JSON.stringify(c.s.action.value), before); assert.equal(c.s.loadError.value, errorBefore); assert.equal(c.notices.length, 0)
  })
})

test('late confirm/cancel result and error are fenced on context change without pretending abort rolled back the server (API stub)', async t => {
  for (const operation of ['confirm', 'cancel']) for (const outcome of ['success', 'error']) for (const [label, change] of changes) await t.test(operation + ' / ' + outcome + ' / ' + label, async sub => {
    const delayed = deferred(), c = await page(sub, { post: () => delayed.promise }); await c.s.load()
    const pending = operation === 'confirm' ? c.s.handleConfirm() : c.s.handleCancel(); await settle()
    assert.equal(posts(c).length, 1); const signal = posts(c)[0].config.signal
    change(c); await settle(); assert.equal(signal.aborted, true)
    const before = JSON.stringify(c.s.action.value), errorBefore = c.s.error.value
    if (outcome === 'success') delayed.resolve(operation === 'confirm' ? { status: 'CONFIRMED', apiId: 'old-api' } : { status: 'CANCELLED' })
    else delayed.reject({ code: 40926, msg: 'late source failure from old context' })
    await pending; assert.equal(JSON.stringify(c.s.action.value), before); assert.equal(c.s.error.value, errorBefore)
    assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0)
  })
})

test('stored-version/source/expiry/ownership rejection preserves real error and draft without automatic refresh or retry (API stub)', async t => {
  for (const code of [40921, 40920, 40926, 40901, 40102, 40305]) await t.test(String(code), async sub => {
    const c = await page(sub, { get: async () => draft({ actionType: 'UPDATE_API', baseRevision: 11 }), post: async () => { throw { code, msg: 'real server refusal ' + code } } })
    await c.s.load(); const before = JSON.stringify(c.s.action.value); await c.s.handleConfirm()
    assert.equal(c.s.error.value, 'real server refusal ' + code); assert.equal(JSON.stringify(c.s.action.value), before)
    assert.equal(c.s.canConfirm.value, false); assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0); assert.equal(gets(c).length, 1)
    await c.s.handleConfirm(); assert.equal(posts(c).length, 1)
    await c.s.load(); assert.equal(c.s.action.value.baseRevision, 11); assert.equal(c.s.error.value, 'real server refusal ' + code); assert.equal(c.s.canConfirm.value, false)
    assert.ok(c.calls.every(call => call.url.startsWith('/ai/actions/')))
  })
})

test('a failed owner cancellation cannot unlock a source/version-rejected draft or hide its real error (API stub)', async t => {
  for (const code of [40921, 40926]) await t.test(String(code), async sub => {
    const c = await page(sub, { post: async (_, suffix) => { throw { code: suffix === 'confirm' ? code : 50001, msg: suffix === 'confirm' ? 'stored draft rejected' : 'cancel failed' } } })
    await c.s.load(); await c.s.handleConfirm(); assert.equal(c.s.canCancel.value, true)
    await c.s.handleCancel(); assert.match(c.s.error.value, /stored draft rejected/); assert.match(c.s.error.value, /cancel failed/)
    assert.equal(c.s.canConfirm.value, false); await c.s.handleConfirm(); assert.equal(posts(c).length, 2); assert.equal(c.notices.length, 0)
  })
})

test('malformed or non-success responses never manufacture confirmation/cancellation success (API stub)', async t => {
  for (const [operation, result] of [['confirm', {}], ['confirm', { status: 'PENDING', apiId: 'x' }], ['confirm', { status: 'CONFIRMED' }], ['confirm', { status: 'CONFIRMED', apiId: 'x', teamId: 'other' }], ['cancel', undefined], ['cancel', { status: 'PENDING' }]]) await t.test(operation + ' ' + JSON.stringify(result), async sub => {
    const c = await page(sub, { post: async () => result }); await c.s.load(); await (operation === 'confirm' ? c.s.handleConfirm() : c.s.handleCancel())
    assert.equal(c.s.action.value.status, 'PENDING'); assert.ok(c.s.error.value); assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0)
  })
  await t.test('cancel server error keeps owned draft', async sub => {
    const c = await page(sub, { role: 'VIEWER', post: async () => { throw { msg: 'cancel denied' } } }); await c.s.load(); await c.s.handleCancel()
    assert.equal(c.s.action.value.status, 'PENDING'); assert.equal(c.s.error.value, 'cancel denied'); assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0)
  })
})

test('expiry is reactive and is rechecked after dialog and after actual POST (API stub)', async t => {
  const oldNow = Date.now; let time = oldNow(); Date.now = () => time; t.after(() => { Date.now = oldNow })
  await t.test('timer disables expired draft', async sub => {
    const c = await page(sub, { get: async () => draft({ expiresAt: new Date(time + 1000).toISOString() }) }); await c.unit.mount()
    assert.equal(c.s.canConfirm.value, true); time += 1001; c.timer.callback(); assert.equal(c.s.canConfirm.value, false); assert.equal(c.s.canCancel.value, false)
    c.unit.unmount(); assert.ok(c.timer.cleared.includes(17))
  })
  for (const phase of ['dialog', 'post']) await t.test(phase, async sub => {
    const delayed = deferred(), c = await page(sub, { get: async () => draft({ expiresAt: new Date(time + 1000).toISOString() }), confirm: phase === 'dialog' ? () => delayed.promise : async () => {}, post: phase === 'post' ? () => delayed.promise : undefined })
    await c.s.load(); const pending = c.s.handleConfirm(); await settle(); time += 1001
    delayed.resolve({ status: 'CONFIRMED', apiId: 'after-expiry' }); await pending
    assert.equal(posts(c).length, phase === 'dialog' ? 0 : 1); assert.equal(c.s.action.value.status, 'PENDING'); assert.equal(c.notices.length, 0); assert.equal(c.app.apiRevision, 0)
  })
})

test('load failure and true empty response remain distinct; retry really reads the backend contract (API stub)', async t => {
  let fail = true
  const c = await page(t, { get: async () => { if (fail) throw { msg: 'owner/read rejected by server' }; return draft() } }); await c.s.load()
  assert.equal(c.s.action.value, null); assert.equal(c.s.loadError.value, 'owner/read rejected by server')
  fail = false; await c.s.load(); assert.equal(c.s.loadError.value, ''); assert.ok(c.s.action.value); assert.equal(gets(c).length, 2)
  await t.test('true empty response', async sub => {
    const empty = await page(sub, { get: async () => null }); await empty.s.load(); assert.equal(empty.s.action.value, null); assert.equal(empty.s.loadError.value, '')
  })
})

test('all returned response/rule/RANDOM/SOAP fields and body text remain intact; no client full-payload or fallback fiction (API stub)', async t => {
  const detail = draft({ actionType: 'UPDATE_API_FULL', baseRevision: 5, apiType: 'SOAP', soapOperationName: 'GetUser', responseMode: 'RANDOM', oldPreview: 'old…', preview: '<script>alert("not HTML")</script>', responses: [
    { name: 'VIP', active: true, responseCode: 202, bodyType: 'TEXT', soapOperationName: 'GetUser', conditions: '{"conditions":[{"source":"HEADER","path":"X-Env","operator":"IN","value":["vip","test"],"valueType":"STRING"}]}', body: 'x'.repeat(9000) + '\n{{uuid}}\n' },
    { name: 'Fallback', active: false, bodyType: 'FILE', conditions: '', body: 'summary…' }
  ] })
  const c = await page(t, { get: async () => detail }); await c.s.load()
  assert.equal(JSON.stringify(c.s.action.value), JSON.stringify(detail)); assert.equal(c.s.responses.value.length, 2); assert.equal(c.s.responseModeLabel.value, '随机返回')
  assert.match(c.s.formatConditions(detail.responses[0].conditions), /HEADER X-Env IN \["vip","test"\]/)
  assert.match(c.s.formatConditions('not JSON'), /原文（未解析）/); assert.equal(c.s.formatConditions('{"unknown":true}'), '{"unknown":true}')
  assert.equal(c.s.teamLabel('unknown-id'), '团队 unknown-id'); assert.equal(c.s.teamLabel(null), '未选择团队')
})

test('viewing a confirmed interface is read-only and stays on the deliberately selected target team (API stub)', async t => {
  const c = await page(t, { role: 'VIEWER', get: async () => draft({ status: 'CONFIRMED', resultApiId: 'api/id' }) }); await c.s.load(); c.s.openApi()
  assert.deepEqual(c.navigations, ['/apis/api%2Fid/edit']); assert.equal(posts(c).length, 0)
  c.app.currentTeamId = 'team-b'; c.s.openApi(); assert.equal(c.navigations.length, 1)
})

test('source-level flat UI and honest summary contract use real disabled handlers, not v-html or fake full preview (not DOM coverage)', async () => {
  const source = await readFile(new URL('../src/views/McpConfirm.vue', import.meta.url), 'utf8')
  for (const binding of [':disabled="!canConfirm"', ':disabled="!canCancel"', '@click="switchToTargetTeam"', '@click="load"', '可能已截断', 'SOAP 操作列表', '中止本地请求不等于回滚', '生成时版本']) assert.ok(source.includes(binding), binding)
  for (const token of ['#EEF1F4', '#FFFFFF', '#F4F6F9', '#17191D', '#3568D4']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /v-html|gradient\(|backdrop-filter|filter:\s*blur|scale\(|zoom\s*:/)
  assert.doesNotMatch(source, /box-shadow:(?!\s*none\s*;)[^;]+;/)
  assert.doesNotMatch(source, /border(?:-(?:top|right|bottom|left))?:(?!\s*0\s*;)[^;]+;/)
  assert.doesNotMatch(source, /getApiDetail|expectedRevision|DRAFT_APPLY/)
})
