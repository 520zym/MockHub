import test from 'node:test'
import assert from 'node:assert/strict'
import { reactive, ref, nextTick } from 'vue'
import { loadSetup, deferred, settle } from './helpers/sfc-unit.mjs'

const response = (name, type = 'TEXT') => ({ name, responseBody: 'original', bodyType: type, contentType: 'application/json', responseCode: 200, isActive: true, conditions: '', filePath: '' })
const ai = { capabilities: {}, load: async () => {} }
const baseStubs = {
  '@/stores/ai': { useAiStore: () => ai },
  '@/constants/dynamicVariables': { DYNAMIC_VARIABLES: [{ name: 'uuid', desc: 'UUID' }] },
  '@/api/customVariable': { getTeamVariables: async () => [] },
  '@/api/apis': { uploadResponseFile: async () => ({ filePath: 'new' }) },
  'element-plus': { ElMessage: { info() {}, error() {}, warning() {}, success() {} }, ElMessageBox: { confirm: async () => {} } }
}
const tabProps = (modelValue, readOnly) => ({ modelValue, readOnly, teamId: 'a', selectionMode: 'CONDITION', operationName: null, defaultContentType: 'application/json', editorLanguage: null, apiContext: {} })

function editorStubs(getDetail, canWrite = ref(true)) {
  const route = reactive({ params: { id: 'a' }, query: {} }), calls = []
  const app = reactive({ teams: [{ id: 'a', name: 'A', identifier: 'A' }], currentTeamId: 'a', apiRevision: 0 })
  app.setFilter = id => { app.currentTeamId = id }
  app.loadTeams = async () => {}; app.bumpApiRevision = () => { app.apiRevision++ }
  const user = { isSuperAdmin: false, canWriteTeam: () => canWrite.value, isTeamAdmin: () => canWrite.value, hasCapability: (_, action) => action === 'read' || canWrite.value }
  globalThis.window = { addEventListener() {}, removeEventListener() {}, location: { origin: 'http://test' } }
  return { route, app, calls, canWrite, stubs: {
    'vue-router': { useRoute: () => route, useRouter: () => ({ push: async path => { calls.push(['route', path]) }, back() {} }), onBeforeRouteLeave() {}, onBeforeRouteUpdate() {} },
    '@/stores/app': { useAppStore: () => app }, '@/stores/user': { useUserStore: () => user },
    '@/api/apis': { getApiDetail: getDetail, updateApi: async (...args) => { calls.push(['update', ...args]); return { id: 'a', revision: 2 } }, createApi: async () => { calls.push(['create']); return { id: 'new' } }, checkApiPath: async () => ({ conflict: false }) },
    '@/api/groups': { getGroups: async () => [], createGroup: async () => ({ id: 'new-group' }) },
    '@/api/settings': { getServerAddress: async () => ({ address: 'http://test' }) },
    'element-plus': baseStubs['element-plus']
  } }
}
function detail(revision = 1) { return { id: 'a', teamId: 'a', revision, name: 'server-' + revision, path: '/a', method: 'GET', type: 'REST', responses: [{ ...response('Default'), active: true }], tags: [], globalHeaderOverrides: {}, createdBy: 'teammate' } }

test('readonly response handlers never mutate the draft or emit write changes', async () => {
  const unit = await loadSetup('frontend/src/components/ResponseTabs.vue', baseStubs)
  const rows = [response('first')], original = JSON.stringify(rows)
  const instance = unit.setup(tabProps(rows, true)), s = instance.state
  s.addResponse(); s.startEditTabName(0); s.changeSelectionMode('RANDOM'); s.handleAiInsert('changed'); s.clearResponseFile(); s.removeResponse(0)
  await s.toggleEnabled(0); await s.handleUploadFile({ raw: {}, name: 'forbidden.txt' })
  assert.equal(JSON.stringify(rows), original)
  assert.equal(instance.events.length, 0)
  assert.equal(s.editingTabIndex.value, -1)
  unit.unmount()
})

test('file upload completes only into its captured response, never the newly selected tab', async () => {
  const upload = deferred()
  const unit = await loadSetup('frontend/src/components/ResponseTabs.vue', { ...baseStubs, '@/api/apis': { uploadResponseFile: () => upload.promise } })
  const rows = [response('first', 'FILE'), response('second', 'FILE')]
  const { state: s } = unit.setup(tabProps(rows, false))
  const pending = s.handleUploadFile({ raw: { type: 'text/plain' }, name: 'file.txt', size: 12 })
  s.activeIndex.value = 1
  upload.resolve({ fileName: 'file.txt', filePath: 'owned-by-a', fileSize: 12 })
  await pending
  assert.equal(rows[0].filePath, 'owned-by-a'); assert.equal(rows[1].filePath, '')
  unit.unmount()
})

test('revocation during upload keeps the existing draft untouched', async () => {
  const upload = deferred(), props = reactive(tabProps([response('first', 'FILE')], false))
  const unit = await loadSetup('frontend/src/components/ResponseTabs.vue', { ...baseStubs, '@/api/apis': { uploadResponseFile: () => upload.promise } })
  const { state: s } = unit.setup(props)
  const pending = s.handleUploadFile({ raw: { type: 'text/plain' }, name: 'file.txt', size: 12 })
  props.readOnly = true; await nextTick(); upload.resolve({ filePath: 'should-not-attach' }); await pending
  assert.equal(props.modelValue[0].filePath, '')
  unit.unmount()
})

test('readonly condition table rejects all mutation handlers and preserves inferred type', async () => {
  const unit = await loadSetup('frontend/src/components/match/ConditionTable.vue')
  const rows = [{ source: 'BODY', path: 'amount', operator: 'EQ', value: '10', valueType: 'NUMBER' }]
  const { state: s, events } = unit.setup({ modelValue: rows, defaultSource: 'HEADER', readOnly: true })
  s.addEmpty(); s.removeAt(0); s.emitChange(); s.onOperatorChange(rows[0])
  assert.equal(rows.length, 1); assert.equal(rows[0].valueType, 'NUMBER'); assert.equal(events.length, 0)
  unit.unmount()
})

test('manual header conditions keep the selected HEADER source', async () => {
  const unit = await loadSetup('frontend/src/components/match/ConditionTable.vue')
  const { state: s, events } = unit.setup({ modelValue: [], defaultSource: 'HEADER', readOnly: false })
  s.addEmpty(); assert.equal(events[0][1][0].source, 'HEADER')
  unit.unmount()
})

test('readonly detail never submits a save, even when an internal handler is invoked', async () => {
  const context = editorStubs(async () => detail(), ref(false)), unit = await loadSetup('frontend/src/views/ApiEdit.vue', context.stubs), { state: s } = unit.setup({})
  await s.loadApiDetail(); s.form.name = 'local draft'; await s.handleSave()
  assert.equal(context.calls.some(c => c[0] === 'update' || c[0] === 'create'), false)
  unit.unmount()
})

test('a delayed detail load cannot attach the former resource to a new route identity', async () => {
  const request = deferred(), context = editorStubs(() => request.promise), unit = await loadSetup('frontend/src/views/ApiEdit.vue', context.stubs), { state: s } = unit.setup({})
  const loading = s.loadApiDetail(); context.route.params.id = 'b'; request.resolve(detail()); await loading
  assert.equal(s.loadedApiId.value, null); assert.equal(s.form.name, '')
  await s.handleSave(); assert.equal(context.calls.some(c => c[0] === 'update'), false)
  unit.unmount()
})

test('external revisions preserve an unsaved draft instead of silently reloading it', async () => {
  let revision = 1
  const context = editorStubs(async () => detail(revision)), unit = await loadSetup('frontend/src/views/ApiEdit.vue', context.stubs), { state: s } = unit.setup({})
  await s.loadApiDetail(); s.captureSnapshot(); s.form.name = 'unsaved local change'
  revision = 2; context.app.apiRevision++; await settle()
  assert.equal(s.form.name, 'unsaved local change'); assert.equal(s.loadedRevision.value, 1); assert.equal(s.revisionConflict.value, true)
  context.canWrite.value = false; await settle(); assert.equal(s.readOnly.value, true); assert.equal(s.form.name, 'unsaved local change')
  unit.unmount()
})

test('editors submit the loaded revision while editing a teammates API', async () => {
  const context = editorStubs(async () => detail()), unit = await loadSetup('frontend/src/views/ApiEdit.vue', context.stubs), { state: s } = unit.setup({})
  await s.loadApiDetail(); s.captureSnapshot(); s.form.name = 'allowed teammate edit'; await s.handleSave()
  const update = context.calls.find(c => c[0] === 'update')
  assert.equal(update?.[1], 'a'); assert.equal(update?.[2].expectedRevision, 1); assert.equal(update?.[2].name, 'allowed teammate edit')
  unit.unmount()
})

test('Monaco completions are model-scoped and readonly blocks programmatic insertion', async () => {
  globalThis.window = {}
  const editors = [], providers = new Map()
  const monaco = {
    languages: { CompletionItemKind: { Variable: 1 }, CompletionItemInsertTextRule: { InsertAsSnippet: 1 }, registerCompletionItemProvider(language, provider) { providers.set(language, provider); return { dispose() {} } } },
    editor: { defineTheme() {}, setModelMarkers() {}, setModelLanguage() {}, create(_, options) {
      let value = options.value, changed = () => {}, disposed = false
      const model = { getValueInRange: range => range.startColumn === 1 ? '{{' : '', isDisposed: () => disposed, dispose() { disposed = true }, getValue: () => value }
      const instance = { model, options, inserts: 0, getModel: () => model, getValue: () => value, setValue(v) { value = v; changed() }, updateOptions(o) { Object.assign(options, o) }, onDidChangeModelContent(fn) { changed = fn }, layout() {}, dispose() {}, focus() {}, getPosition: () => ({ lineNumber: 1, column: 3 }), executeEdits() { this.inserts++ } }
      editors.push(instance); return instance
    } }, Range: class { constructor() {} }
  }
  const unit = await loadSetup('frontend/src/components/MonacoEditor.vue', { 'monaco-editor': monaco, '@/constants/dynamicVariables': baseStubs['@/constants/dynamicVariables'] })
  const propsA = reactive({ modelValue: '{{', language: 'json', readOnly: false, dynamicVariables: [{ name: 'a_private', desc: 'A' }] })
  const propsB = reactive({ modelValue: '{{', language: 'json', readOnly: false, dynamicVariables: [{ name: 'b_private', desc: 'B' }] })
  const a = unit.setup(propsA), b = unit.setup(propsB)
  a.state.editorContainer.value = {}; b.state.editorContainer.value = {}; await unit.mount()
  const provider = providers.get('json'), position = { lineNumber: 1, column: 3 }
  assert.deepEqual(provider.provideCompletionItems(editors[0].model, position).suggestions.map(x => x.label), ['{{a_private}}'])
  assert.deepEqual(provider.provideCompletionItems(editors[1].model, position).suggestions.map(x => x.label), ['{{b_private}}'])
  propsA.readOnly = true; await nextTick(); a.exposed.insertAtCursor('forbidden')
  assert.equal(editors[0].options.readOnly, true); assert.equal(editors[0].inserts, 0)
  propsA.modelValue = 'external update'; await nextTick(); assert.equal(a.events.length, 0)
  unit.unmount(); assert.equal(editors[0].model.isDisposed(), true); assert.equal(editors[1].model.isDisposed(), true)
})

test('rich descriptions stay readonly and initial normalization cannot dirty the form', async () => {
  const props = reactive({ modelValue: '<p>saved</p>', placeholder: '', height: 150, readOnly: false })
  const unit = await loadSetup('frontend/src/components/RichTextEditor.vue'), { state: s, events } = unit.setup(props)
  let disabled = 0, enabled = 0
  const editor = { getHtml: () => '<p>normalized</p>', disable() { disabled++ }, enable() { enabled++ }, destroy() {} }
  s.handleCreated(editor); s.handleChange(editor); assert.equal(events.length, 0)
  s.markUserEditing(); s.handleChange(editor); assert.equal(events.length, 1)
  props.readOnly = true; await nextTick(); s.handleChange(editor)
  assert.equal(disabled, 1); assert.equal(events.length, 1)
  props.readOnly = false; await nextTick(); assert.equal(enabled, 1)
  unit.unmount()
})

test('clipboard failure never displays a false copy success', async () => {
  const notices = [], documentBefore = globalThis.document, navigatorBefore = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async () => { throw new Error('denied') } } } })
  globalThis.document = { createElement: () => ({ style: {}, select() {} }), body: { appendChild() {}, removeChild() {} }, execCommand: () => false }
  const unit = await loadSetup('frontend/src/components/CopyButton.vue', { 'element-plus': { ElMessage: { success: m => notices.push(['success',m]), warning: m => notices.push(['warning',m]) } } }), { state: s } = unit.setup({ text: 'safe fixture', showLabel: true })
  try { await s.handleCopy(); assert.equal(s.copied.value, false); assert.equal(notices.filter(n => n[0] === 'success').length, 0); assert.equal(notices.filter(n => n[0] === 'warning').length, 1) }
  finally { unit.unmount(); globalThis.document = documentBefore; if (navigatorBefore) Object.defineProperty(globalThis,'navigator',navigatorBefore); else delete globalThis.navigator }
})

test('late tag results from the former team cannot populate the current team', async () => {
  const a = deferred(), b = deferred()
  const unit = await loadSetup('frontend/src/components/TagInput.vue', { '@/api/tags': { getTags: id => id === 'a' ? a.promise : b.promise }, 'element-plus': baseStubs['element-plus'] })
  const props = reactive({ modelValue: [], teamId: 'a', readOnly: true }), { state: s, events } = unit.setup(props)
  props.teamId = 'b'; await settle(); b.resolve([{ id: 'b-tag', name: 'B' }]); await settle(); a.resolve([{ id: 'a-tag', name: 'A' }]); await settle()
  assert.equal(s.allTags.value[0].id, 'b-tag')
  s.toggleTag({ id: 'b-tag' }); s.removeTag('b-tag'); await s.handleCreateTag(); assert.equal(events.length, 0)
  unit.unmount()
})
