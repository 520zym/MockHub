import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as Vue from 'vue'
import { parse, compileTemplate } from 'vue/compiler-sfc'
import { isNavigationFailure } from 'vue-router'
import { teamCapability } from '../src/utils/permissions.js'
import { loadSetup, deferred, settle } from './helpers/sfc-unit.mjs'

const editorFile = 'frontend/src/views/ApiEdit.vue'
const editorSource = await readFile(new URL('../src/views/ApiEdit.vue', import.meta.url), 'utf8')
const { descriptor } = parse(editorSource)
const compiledTemplate = compileTemplate({ source: descriptor.template.content, filename: editorFile, id: 'save-lifecycle', compilerOptions: { mode: 'function' } })
assert.deepEqual(compiledTemplate.errors, [])
// Render the actual SFC template to VNodes, with only UI/network dependencies replaced.
// This inspects the real control props rather than assuming that a setup flag reaches every editor.
const render = new Function('Vue', compiledTemplate.code)({ ...Vue, resolveComponent: name => name, resolveDirective: () => ({}), withDirectives: vnode => vnode, withCtx: fn => fn })
function renderedNodes(state) {
  const nodes = []
  const visit = value => {
    if (Array.isArray(value)) { value.forEach(visit); return }
    if (!value || typeof value !== 'object') return
    if (value.type) nodes.push(value)
    if (Array.isArray(value.children)) visit(value.children)
    else if (value.children && typeof value.children === 'object') {
      for (const slot of Object.values(value.children)) if (typeof slot === 'function') visit(slot())
    }
  }
  visit(render(Vue.proxyRefs(state), []))
  return nodes
}

const response = (body = 'original', operation = null) => ({ name: 'Default', responseBody: body, responseCode: 200, contentType: operation ? 'text/xml' : 'application/json', bodyType: 'TEXT', active: true, soapOperationName: operation, conditions: '', delayMs: 0 })
function detail(id = 'a', type = 'REST') {
  return { id, teamId: id === 'b' ? 'b' : 'a', groupId: id === 'b' ? 'group-b' : null, revision: 7, name: 'server-' + id, path: '/' + id, method: type === 'SOAP' ? 'POST' : 'GET', type, enabled: true, responses: [response('original', type === 'SOAP' ? 'GetOrder' : null)], tags: [], globalHeaderOverrides: {}, soapConfig: type === 'SOAP' ? { wsdlFileName: 'order.wsdl', operations: [{ operationName: 'GetOrder', soapAction: 'urn:order', description: 'saved operation' }] } : null }
}
let fixtureId = 0
async function fixture(t, options = {}) {
  const key = 'editor-save-' + fixtureId++, pending = deferred(), calls = [], notices = [], confirmations = [], requests = []
  const capabilities = Vue.reactive({ a: { read: true, write: true }, b: { read: true, write: true } })
  const app = Vue.reactive({ teams: [{ id: 'a', name: 'A', identifier: 'A' }, { id: 'b', name: 'B', identifier: 'B' }], currentTeamId: 'a', apiRevision: 0 })
  app.setFilter = id => { app.currentTeamId = id; return true }
  app.loadTeams = async () => { calls.push(['teams']) }
  app.bumpApiRevision = () => { app.apiRevision++ }
  const user = Vue.reactive({ token: 'fixture-token', identityReady: true, isSuperAdmin: false, user: { teams: ['a', 'b'].map(teamId => ({ teamId, membershipRole: 'EDITOR', capabilities: capabilities[teamId] })) } })
  user.ensureIdentity = async () => user.user
  user.canWriteTeam = teamId => user.identityReady && teamCapability(user.user, teamId, 'write')
  user.hasCapability = (teamId, action) => user.identityReady && teamCapability(user.user, teamId, action)
  user.isTeamAdmin = teamId => user.hasCapability(teamId, 'manageMembers')
  const oldWindow = globalThis.window, oldStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage'), oldNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  globalThis.window = { addEventListener() {}, removeEventListener() {}, location: { origin: 'http://fixture.invalid' } }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, writable: true, value: { getItem: key => key === 'token' ? 'fixture-token' : null } })
  const copies = []
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async value => { copies.push(value) } } } })
  globalThis.__editorSaveContexts ||= new Map()
  globalThis.__editorSaveContexts.set(key, { user, app })
  // Run the repository's actual global guard and route table, changing ONLY browser history
  // and lazy rendering dependencies. In particular ApiNew's write guard still executes.
  let routerSource = await readFile(new URL('../src/router/index.js', import.meta.url), 'utf8')
  routerSource = routerSource
    .replace("import { createRouter, createWebHashHistory } from 'vue-router'", "import { createRouter, createMemoryHistory } from " + JSON.stringify(new URL('../node_modules/vue-router/dist/vue-router.mjs', import.meta.url).href))
    .replace("import { useUserStore } from '@/stores/user'", 'const useUserStore = () => globalThis.__editorSaveContexts.get(' + JSON.stringify(key) + ').user')
    .replace("import { useAppStore } from '@/stores/app'", 'const useAppStore = () => globalThis.__editorSaveContexts.get(' + JSON.stringify(key) + ').app')
    .replaceAll('createWebHashHistory()', 'createMemoryHistory()')
    .replace(/component: \(\) => import\('[^']+'\)/g, 'component: {}')
  const router = (await import('data:text/javascript;base64,' + Buffer.from(routerSource).toString('base64'))).default
  await router.push(options.create ? '/apis/new' : '/apis/a/edit')
  const route = Vue.shallowReactive({ ...router.currentRoute.value })
  const stopRoute = Vue.watch(router.currentRoute, value => Object.assign(route, value), { flush: 'sync' })
  const record = router.currentRoute.value.matched.at(-1)
  const push = router.push.bind(router)
  router.push = target => { calls.push(['route', target]); return push(target) }
  const submit = (kind, id, payload) => { requests.push({ kind, id, payload }); return options.save ? options.save(requests.at(-1)) : pending.promise }
  const unit = await loadSetup(editorFile, {
    // These hooks register into the actual record sets consumed by Vue Router's navigation
    // pipeline, not empty guard stubs or a manual call that skips global guards.
    'vue-router': { useRoute: () => route, useRouter: () => router, onBeforeRouteLeave: guard => record.leaveGuards.add(guard), onBeforeRouteUpdate: guard => record.updateGuards.add(guard) },
    '@/stores/app': { useAppStore: () => app }, '@/stores/user': { useUserStore: () => user },
    '@/api/apis': { getApiDetail: async id => { calls.push(['detail', id]); return options.getDetail ? options.getDetail(id) : detail(id, options.type) }, updateApi: (id, payload) => submit('update', id, payload), createApi: payload => submit('create', null, payload), checkApiPath: async () => ({ conflict: false }) },
    '@/api/groups': { getGroups: async () => [], createGroup: async payload => { calls.push(['group', payload]); return { id: 'new-group' } } },
    '@/api/soap': { uploadWsdl: async () => { calls.push(['wsdl']); return { fileName: 'new.wsdl', operations: [] } } },
    '@/api/settings': { getServerAddress: async () => ({ address: 'http://fixture.invalid' }) },
    'element-plus': { ElMessage: Object.fromEntries(['success', 'error', 'warning', 'info'].map(kind => [kind, message => notices.push([kind, message])])), ElMessageBox: { confirm: (...args) => { confirmations.push(args); return options.confirm ? options.confirm(...args) : Promise.resolve() }, prompt: (...args) => { calls.push(['prompt']); return options.prompt ? options.prompt(...args) : Promise.resolve({ value: 'new group' }) } } }
  })
  const { state: s } = unit.setup({})
  let unmounted = false
  const unmount = () => { if (unmounted) return; unmounted = true; unit.unmount(); record.leaveGuards.clear(); record.updateGuards.clear(); stopRoute() }
  let cleaned = false
  const cleanup = () => {
    if (cleaned) return
    cleaned = true; unmount(); globalThis.__editorSaveContexts.delete(key)
    globalThis.window = oldWindow
    if (oldStorage) Object.defineProperty(globalThis, 'localStorage', oldStorage); else delete globalThis.localStorage
    if (oldNavigator) Object.defineProperty(globalThis, 'navigator', oldNavigator); else delete globalThis.navigator
  }
  t.after(cleanup)
  await unit.mount()
  return { s, unit, pending, calls, notices, confirmations, requests, capabilities, app, user, route, router, unmount, cleanup, copies }
}

for (const type of ['REST', 'SOAP']) test(type + ' deferred success freezes real controls, submits once and marks only a true success saved', async t => {
  const f = await fixture(t, { type }), { s } = f
  s.form.name = 'submitted'; s.headerOverrideList.value = [{ key: 'X-Test', value: 'one' }]
  if (type === 'SOAP') s.expandAllOps()
  const before = s.formSnapshot.value, saving = s.handleSave()
  assert.equal(s.saving.value, true); assert.equal(s.editLocked.value, true)
  assert.equal(s.isDirty.value, true); assert.equal(s.formSnapshot.value, before)
  assert.equal(f.notices.some(n => n[0] === 'success'), false)
  await s.handleSave(); assert.equal(f.requests.length, 1)
  const nodes = renderedNodes(s)
  for (const name of ['RichTextEditor', 'ResponseTabs', 'TagInput', 'ApiHistoryDrawer']) {
    const controls = nodes.filter(node => node.type === name)
    assert.ok(controls.length, name + ' is rendered')
    controls.forEach(node => assert.equal(node.props['read-only'], true, name + ' freezes'))
  }
  nodes.filter(node => node.type === 'el-input').forEach(node => assert.equal(node.props.readonly, true))
  nodes.filter(node => ['el-select', 'el-switch', 'el-radio-group', 'el-upload'].includes(node.type)).forEach(node => assert.equal(node.props.disabled, true))
  const buttons = nodes.filter(node => node.type === 'el-button' && node.props?.onClick === s.handleSave)
  // v1.4.6 起编辑页只保留顶部保存按钮，底部操作栏已移除。
  assert.equal(buttons.length, 1); buttons.forEach(node => assert.equal(node.props.disabled, true))
  const headers = JSON.stringify(s.headerOverrideList.value)
  s.addHeaderOverride(); s.removeHeaderOverride(0); s.handleWsdlFileChange({ raw: {} })
  await s.handleUploadWsdl(); await s.handleGroupSelectChange('__new__'); await s.reloadCurrentVersion()
  await s.historyChanged({ apiId: 'a', teamId: 'a' })
  assert.equal(JSON.stringify(s.headerOverrideList.value), headers)
  assert.equal(s.wsdlFile.value, null); assert.equal(f.calls.some(c => ['group', 'prompt', 'wsdl'].includes(c[0])), false)
  await s.copyDraft(); assert.equal(JSON.parse(f.copies[0]).name, 'submitted')
  assert.ok(nodes.some(node => node.type === 'CopyButton' && !node.props.disabled))
  const request = f.requests[0]
  assert.equal(request.payload.expectedRevision, 7); assert.equal(request.payload.globalHeaderOverrides['X-Test'], 'one')
  assert.equal(request.payload.type, type)
  if (type === 'SOAP') { assert.equal(request.payload.method, 'POST'); assert.equal(request.payload.responses[0].soapOperationName, 'GetOrder'); assert.equal(request.payload.soapConfig.operations[0].responses, undefined) }
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(s.isDirty.value, false); assert.equal(s.saving.value, false)
  assert.equal(f.router.currentRoute.value.path, '/apis'); assert.equal(f.confirmations.length, 0)
  assert.equal(f.notices.filter(n => n[0] === 'success').length, 2) // explicit copy + real save
})

test('deferred failure and 40921 retain the loaded revision and draft without refetch or retry', async t => {
  for (const error of [{ code: 40921 }, { response: { status: 500 } }, { isTimeout: true }, { isNetworkError: true }]) {
    const f = await fixture(t), { s } = f
    s.form.name = 'unsaved'; const baseline = s.formSnapshot.value, saving = s.handleSave()
    f.pending.reject(error); await saving
    assert.equal(s.form.name, 'unsaved'); assert.equal(s.formSnapshot.value, baseline)
    assert.equal(s.isDirty.value, true); assert.equal(s.loadedRevision.value, 7)
    assert.equal(s.revisionConflict.value, error.code === 40921)
    assert.equal(f.requests.length, 1); assert.equal(f.calls.filter(c => c[0] === 'detail').length, 1)
    assert.equal(f.notices.some(n => n[0] === 'success'), false); assert.equal(f.router.currentRoute.value.path, '/apis/a/edit')
    assert.equal(s.editLocked.value, false)
    if (error.isTimeout || error.isNetworkError) assert.match(f.notices.at(-1)[1], /服务端可能已经保存成功/)
    f.cleanup()
  }
})

test('late child edits do not mutate the submitted payload and survive a successful save', async t => {
  const f = await fixture(t), { s } = f
  s.form.name = 'submitted'; s.form.tagIds = ['one']
  const saving = s.handleSave()
  // Simulate a child event already queued before the readonly prop reaches its editor.
  s.form.name = 'later local draft'; s.form.responses[0].responseBody = 'later body'; s.form.tagIds.push('two')
  s.headerOverrideList.value.push({ key: 'X-Later', value: 'keep' })
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(f.requests[0].payload.name, 'submitted'); assert.equal(f.requests[0].payload.responses[0].responseBody, 'original')
  assert.deepEqual(f.requests[0].payload.tagIds, ['one'])
  assert.equal(s.form.name, 'later local draft'); assert.equal(s.isDirty.value, true); assert.equal(s.loadedRevision.value, 8)
  assert.equal(f.calls.some(c => c[0] === 'route'), false)
  assert.equal(JSON.parse(s.formSnapshot.value).name, 'submitted')
})

test('success with blocked navigation does not disable guards for subsequent local edits', async t => {
  const f = await fixture(t, { confirm: () => Promise.reject(new Error('stay')) }), { s } = f
  const remove = f.router.beforeEach(to => to.path === '/apis' ? false : true)
  s.form.name = 'saved'; const saving = s.handleSave(); f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(s.isDirty.value, false); assert.equal(s.saving.value, false); remove()
  s.form.name = 'new unsaved draft'; assert.equal(s.isDirty.value, true)
  const failure = await f.router.push('/account')
  assert.ok(isNavigationFailure(failure)); assert.equal(f.router.currentRoute.value.path, '/apis/a/edit')
  assert.match(f.confirmations.at(-1)[0], /未保存/)
  const event = { prevented: false, preventDefault() { this.prevented = true } }
  s.beforeUnloadHandler(event); assert.equal(event.prevented, true)
})

for (const outcome of ['success', 'failure']) test('unmount ignores deferred ' + outcome + ' without touching local snapshots or navigating', async t => {
  const f = await fixture(t), { s } = f
  s.form.name = 'leaving draft'; const snapshot = s.formSnapshot.value, saving = s.handleSave()
  f.unmount()
  if (outcome === 'success') f.pending.resolve({ id: 'a', revision: 8 }); else f.pending.reject({ code: 40921 })
  await saving
  assert.equal(s.formSnapshot.value, snapshot); assert.equal(s.loadedRevision.value, 7)
  assert.equal(s.revisionConflict.value, false); assert.equal(f.calls.some(c => c[0] === 'route' || c[0] === 'teams'), false)
  assert.equal(f.notices.some(n => n[0] === 'success'), false)
  await s.handleSave(); assert.equal(f.requests.length, 1)
})

test('real leave guard warns that a pending clean save is not canceled, and late success cannot hijack another page', async t => {
  const f = await fixture(t), { s } = f
  assert.equal(s.isDirty.value, false)
  const saving = s.handleSave()
  const event = { preventDefault() { this.prevented = true } }; s.beforeUnloadHandler(event); assert.equal(event.prevented, true)
  await f.router.push('/account')
  assert.match(f.confirmations[0][0], /保存请求已发送.*不会撤销服务端保存/)
  const revisionAfterLeave = s.loadedRevision.value
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(f.router.currentRoute.value.path, '/account')
  assert.equal(f.calls.filter(c => c[0] === 'route').length, 1)
  assert.equal(s.loadedRevision.value, revisionAfterLeave); assert.equal(f.notices.some(n => n[0] === 'success'), false)
})

test('save success during a canceled leave dialog acknowledges the committed draft without starting a competing navigation', async t => {
  const confirm = deferred(), f = await fixture(t, { confirm: () => confirm.promise }), { s } = f
  s.form.name = 'submitted'; const saving = s.handleSave(), navigation = f.router.push('/account')
  await settle(); assert.equal(f.confirmations.length, 1)
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(s.loadedRevision.value, 8); assert.equal(s.isDirty.value, false)
  confirm.reject(new Error('stay')); assert.ok(isNavigationFailure(await navigation))
  assert.equal(f.router.currentRoute.value.path, '/apis/a/edit'); assert.equal(f.calls.filter(c => c[0] === 'route').length, 1)
})

for (const outcome of ['success', 'failure']) test('same-component ID switch uses the real update guard and old ' + outcome + '/finally cannot clear the next save', async t => {
  const first = deferred(), second = deferred(), f = await fixture(t, { save: request => request.id === 'a' ? first.promise : second.promise }), { s } = f
  s.form.name = 'draft a'; const saveA = s.handleSave()
  await f.router.push('/apis/b/edit'); await settle()
  assert.equal(f.confirmations.length, 1); assert.match(f.confirmations[0][0], /不会撤销/)
  assert.equal(s.loadedApiId.value, 'b'); assert.equal(s.form.name, 'server-b')
  assert.equal(s.form.groupId, 'group-b'); assert.equal(s.isDirty.value, false)
  s.form.name = 'draft b'; const saveB = s.handleSave(), snapshotB = s.formSnapshot.value
  if (outcome === 'success') first.resolve({ id: 'a', revision: 8 }); else first.reject({ code: 40921 })
  await saveA
  assert.equal(s.saving.value, true); assert.equal(s.revisionConflict.value, false)
  assert.equal(s.form.name, 'draft b'); assert.equal(s.formSnapshot.value, snapshotB)
  assert.equal(f.notices.some(n => n[0] === 'success'), false)
  assert.equal(f.router.currentRoute.value.path, '/apis/b/edit')
  second.resolve({ id: 'b', revision: 8 }); await saveB
  assert.equal(f.requests[1].payload.expectedRevision, 7); assert.equal(f.router.currentRoute.value.path, '/apis')
})

for (const change of ['sidebar-team', 'form-team', 'read', 'write', 'route-ID']) test('synchronous ' + change + ' A-to-B-to-A invalidates a late success before queued watchers run', async t => {
  const f = await fixture(t), { s } = f
  s.form.name = 'submitted'; const before = s.formSnapshot.value, saving = s.handleSave()
  if (change === 'sidebar-team') { f.app.currentTeamId = 'b'; f.app.currentTeamId = 'a' }
  if (change === 'form-team') { s.form.teamId = 'b'; s.form.teamId = 'a' }
  if (change === 'read' || change === 'write') { f.capabilities.a[change] = false; f.capabilities.a[change] = true }
  if (change === 'route-ID') { f.route.params = { id: 'b' }; f.route.params = { id: 'a' } }
  s.form.name = 'new draft after context change'
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(s.form.name, 'new draft after context change'); assert.equal(s.formSnapshot.value, before)
  assert.equal(s.loadedRevision.value, 7); assert.equal(s.isDirty.value, true)
  assert.equal(f.calls.some(c => c[0] === 'route' || c[0] === 'teams'), false)
  assert.equal(f.notices.some(n => n[0] === 'success'), false)
})

test('write downgrade preserves the draft, and loss of read access never traps the real access redirect', async t => {
  const f = await fixture(t, { confirm: () => Promise.reject(new Error('stay')) }), { s } = f
  s.form.name = 'local draft'; const saving = s.handleSave(), baseline = s.formSnapshot.value
  f.capabilities.a.write = false
  assert.equal(s.readOnly.value, true); assert.equal(s.editLocked.value, true)
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(s.formSnapshot.value, baseline); assert.equal(s.form.name, 'local draft')
  assert.equal(f.notices.some(n => n[0] === 'success'), false)
  f.capabilities.a.read = false; await f.router.replace('/no-access')
  assert.equal(f.router.currentRoute.value.path, '/no-access'); assert.equal(f.confirmations.length, 0)
})

for (const type of ['REST', 'SOAP']) test('new ' + type + ' submits only once and a post-success draft updates the created resource, never creates a duplicate', async t => {
  const first = deferred(), second = deferred(), f = await fixture(t, { create: true, save: request => request.kind === 'create' ? first.promise : second.promise }), { s } = f
  s.form.name = 'created'; s.form.path = '/new'; s.form.type = type
  if (type === 'SOAP') s.form.soapConfig = { wsdlFileName: 'new.wsdl', operations: [{ operationName: 'GetOrder', soapAction: 'urn:new', responses: [{ ...response('soap body', 'GetOrder'), isActive: true }] }] }
  await settle()
  const saving = s.handleSave(); await s.handleSave(); assert.equal(f.requests.length, 1)
  s.form.name = 'post-create local draft'
  first.resolve({ id: 'created-id', revision: 1 }); await saving
  assert.equal(s.loadedApiId.value, 'created-id'); assert.equal(s.loadedRevision.value, 1)
  assert.equal(s.isDirty.value, true); assert.equal(s.form.name, 'post-create local draft')
  assert.equal(f.router.currentRoute.value.path, '/apis/new')
  const update = s.handleSave()
  assert.equal(f.requests.length, 2); assert.equal(f.requests[1].kind, 'update'); assert.equal(f.requests[1].id, 'created-id')
  assert.equal(f.requests[1].payload.expectedRevision, 1); assert.equal(f.requests[1].payload.type, type)
  second.resolve({ id: 'created-id', revision: 2 }); await update
  assert.equal(f.router.currentRoute.value.path, '/apis')
})

for (const leave of [false, true]) test('actual sidebar team switch ' + (leave ? 'leaves without late-save hijack' : 'respects canceled editor guard'), async t => {
  const f = await fixture(t, { confirm: () => leave ? Promise.resolve() : Promise.reject(new Error('stay')) }), { s } = f
  const sidebar = await loadSetup('frontend/src/components/layout/Sidebar.vue', {
    'vue-router': { useRoute: () => f.route, useRouter: () => f.router },
    '@/stores/app': { useAppStore: () => f.app }, '@/stores/user': { useUserStore: () => f.user }
  })
  t.after(() => sidebar.unmount())
  const { state } = sidebar.setup({})
  s.form.name = 'pending draft'; const saving = s.handleSave()
  await state.selectTeam('b')
  assert.equal(f.app.currentTeamId, leave ? 'b' : 'a')
  assert.equal(f.router.currentRoute.value.path, leave ? '/apis' : '/apis/a/edit')
  assert.match(f.confirmations[0][0], /不会撤销服务端保存/)
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
  assert.equal(f.app.currentTeamId, leave ? 'b' : 'a')
  assert.equal(f.router.currentRoute.value.path, leave ? '/apis' : '/apis/a/edit')
  assert.equal(f.calls.filter(c => c[0] === 'route').length, 1)
})

test('project global router guard still denies ApiNew to a read-only team', async t => {
  const f = await fixture(t)
  f.capabilities.a.write = false
  await f.router.push('/apis/new')
  assert.equal(f.router.currentRoute.value.path, '/no-access')
})

test('group/reload prompts started before save cannot later change its submitted draft', async t => {
  const prompt = deferred(), confirm = deferred(), f = await fixture(t, { prompt: () => prompt.promise, confirm: () => confirm.promise }), { s } = f
  const grouping = s.handleGroupSelectChange('__new__'), reloading = s.reloadCurrentVersion()
  s.form.name = 'submitted'; const saving = s.handleSave()
  prompt.resolve({ value: 'late group' }); confirm.resolve(); await grouping; await reloading
  assert.equal(f.calls.some(c => c[0] === 'group'), false); assert.equal(f.calls.filter(c => c[0] === 'detail').length, 1)
  assert.equal(s.form.name, 'submitted'); assert.equal(s.form.groupId, null)
  f.pending.resolve({ id: 'a', revision: 8 }); await saving
})
