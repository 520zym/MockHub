import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { parse, compileScript, babelParse as parseJs } from 'vue/compiler-sfc'
import * as Vue from 'vue'

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
let counter = 0
export function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
export async function settle() { await Vue.nextTick(); await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
export async function loadSetup(file, stubs = {}) {
  const filename = path.resolve(workspaceRoot, file), source = await readFile(filename, 'utf8')
  const { descriptor } = parse(source, { filename })
  let content = compileScript(descriptor, { id: 'unit-' + counter, inlineTemplate: false }).content
  const key = 'sfc-' + counter++, hooks = { mounted: [], unmounted: [] }
  const vue = { ...Vue, onMounted: fn => hooks.mounted.push(fn), onBeforeUnmount: fn => hooks.unmounted.push(fn) }
  globalThis.__mockhubSfcUnits ||= new Map()
  const modules = { vue, ...stubs }
  globalThis.__mockhubSfcUnits.set(key, modules)
  const imports = parseJs(content, { sourceType: 'module' }).program.body.filter(node => node.type === 'ImportDeclaration')
  for (const node of imports.reverse()) {
    const moduleName = node.source.value
    const lines = node.specifiers.map(specifier => {
      const imported = specifier.type === 'ImportDefaultSpecifier' ? 'default' : specifier.type === 'ImportNamespaceSpecifier' ? null : (specifier.imported.name || specifier.imported.value)
      const base = 'globalThis.__mockhubSfcUnits.get(' + JSON.stringify(key) + ')[' + JSON.stringify(moduleName) + ']'
      return 'const ' + specifier.local.name + ' = ' + (imported == null ? '(' + base + ' || {})' : '(' + base + '?.[' + JSON.stringify(imported) + '] ?? {})')
    }).join('\n')
    content = content.slice(0, node.start) + lines + content.slice(node.end)
  }
  // Browser-independent setup-unit tests: imported network/rendering dependencies are explicit stubs.
  // These tests do not claim DOM, Monaco browser integration or backend HTTP coverage.
  const component = (await import('data:text/javascript;base64,' + Buffer.from(content).toString('base64'))).default
  return {
    hooks,
    setup(props) { const events = []; let exposed; const state = component.setup(Vue.reactive(props), { expose: value => { exposed = value }, emit: (...args) => events.push(args) }); return { state, events, get exposed() { return exposed } } },
    async mount() { for (const fn of hooks.mounted) await fn(); await settle() },
    unmount() { for (const fn of hooks.unmounted) fn(); globalThis.__mockhubSfcUnits.delete(key) }
  }
}
