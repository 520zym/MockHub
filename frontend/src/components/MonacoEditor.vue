<template>
  <div ref="editorContainer" class="monaco-editor-container" />
</template>

<script>
import { hasPlaceholder, isValidJsonWithPlaceholders } from '@/utils/json-placeholders'

// Shared across component instances: script-setup declarations are instance-scoped.
const modelVariableLists = new WeakMap()
let markersPatched = false
const markerOriginalKey = Symbol.for('mockhub.monaco.originalSetModelMarkers')
</script>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'
import { DYNAMIC_VARIABLES } from '@/constants/dynamicVariables'

/**
 * 全局一次性注册动态变量补全 Provider
 *
 * 使用模块级标志位防止多实例/HMR 重复注册。对 json / xml / plaintext 三种语言
 * 分别注册。触发字符为 `{`，在 provideCompletionItems 内判断用户已输入 `{{`
 * 才返回建议项，避免单个 `{` 时就弹出噪音。
 */
/**
 * 全局一次性拦截 Monaco 的 setModelMarkers
 *
 * 目的：保留 JSON 内置语法校验，但动态变量占位符常在返回体里写成裸值
 * （`"id": {{uuid}}`），JSON 校验会报 "Expected comma"，满屏红波浪线却找不到错。
 * 做法（只作用于 JSON 诊断，XML/纯文本不受影响）：
 * - 占位符换成 null 后如果仍是合法 JSON，说明这些报错全部来自占位符，清空 JSON 诊断；
 * - 换成 null 后依然不合法，说明确有真实错误，只丢掉贴着占位符的那几个 marker，
 *   其余真实错误照常显示。
 * - 占位符语法与后端 DynamicVariableUtil 一致；幂等：模块级 flag 防止重复包裹。
 */
function patchSetModelMarkers() {
  if (markersPatched) return
  markersPatched = true

  const PLACEHOLDER_RE = /\{\{\w+(?:\.\w+)?\}\}/g
  const original = monaco.editor.setModelMarkers[markerOriginalKey] || monaco.editor.setModelMarkers.bind(monaco.editor)

  // 占位符行列区间，左右各放宽 2 列，吸收紧贴占位符的 "Expected comma"
  function placeholderRanges(model, text) {
    const ranges = []
    let m
    PLACEHOLDER_RE.lastIndex = 0
    while ((m = PLACEHOLDER_RE.exec(text)) !== null) {
      const start = model.getPositionAt(m.index)
      const end = model.getPositionAt(m.index + m[0].length)
      ranges.push({
        sLine: start.lineNumber, sCol: Math.max(1, start.column - 2),
        eLine: end.lineNumber,   eCol: end.column + 2
      })
    }
    return ranges
  }

  monaco.editor.setModelMarkers = function (model, owner, markers) {
    if (!model || !markers || markers.length === 0) {
      return original(model, owner, markers)
    }
    const languageId = model.getLanguageId ? model.getLanguageId() : ''
    if (owner !== 'json' && languageId !== 'json') {
      return original(model, owner, markers)
    }
    const text = model.getValue()
    if (!hasPlaceholder(text)) {
      return original(model, owner, markers)
    }
    if (isValidJsonWithPlaceholders(text)) {
      return original(model, owner, markers.filter((mk) => !isJsonMarker(mk, owner)))
    }
    const ranges = placeholderRanges(model, text)
    const filtered = markers.filter((mk) => {
      return !ranges.some((r) => rangesIntersect(mk, r))
    })
    return original(model, owner, filtered)
  }
  monaco.editor.setModelMarkers[markerOriginalKey] = original
}

/** 该 marker 是否由 JSON 校验产生（owner 或 source 为 json） */
function isJsonMarker(marker, owner) {
  return owner === 'json' || marker.source === 'json'
}

/**
 * 判断 marker 的范围是否与占位符范围相交
 * 两个范围 [a1,a2]、[b1,b2] 相交 ⟺ a1 <= b2 && b1 <= a2
 */
function rangesIntersect(mk, r) {
  const a1Line = mk.startLineNumber, a1Col = mk.startColumn
  const a2Line = mk.endLineNumber,   a2Col = mk.endColumn
  const b1Line = r.sLine,            b1Col = r.sCol
  const b2Line = r.eLine,            b2Col = r.eCol
  // a1 <= b2 ?
  const aBeforeOrEqB = (a1Line < b2Line) || (a1Line === b2Line && a1Col <= b2Col)
  // b1 <= a2 ?
  const bBeforeOrEqA = (b1Line < a2Line) || (b1Line === a2Line && b1Col <= a2Col)
  return aBeforeOrEqB && bBeforeOrEqA
}

/**
 * 每个文本模型独立的变量补全，避免不同接口/团队/浮层互相覆盖缓存
 *
 * 补全 provider 在模块级只注册一次，但不同 MonacoEditor 实例可能带不同的
 * dynamicVariables（例如不同团队的自定义变量）。用 WeakMap 按文本模型隔离，
 * provider 根据本次请求的 model 读取元数据，销毁时显式删除；不会跨团队串用补全。
 */
function setModelVariables(model, list) {
  if (model) modelVariableLists.set(model, Array.isArray(list) && list.length ? list : DYNAMIC_VARIABLES)
}

/**
 * 注册 Monaco 动态变量补全 provider
 *
 * 注意：模块级 flag 在 Vite HMR 热重载后会被重置，但 Monaco 的
 * registerCompletionItemProvider 不会自动清理旧的 provider，导致每次 HMR
 * 都会叠加一层 → 补全项重复 N 次。为避免这个问题，把所有 disposable
 * 挂到 window 上持久化，重新注册前先 dispose 旧的。
 *
 * 生产环境（非 HMR）也是幂等的：已经注册过就直接返回。
 */
function registerDynamicVariableCompletion() {
  if (typeof window === 'undefined') return
  // HMR 场景：如果存在之前注册的 disposables，先全部释放
  if (Array.isArray(window.__mockhubDynamicVarProviders__)) {
    for (const d of window.__mockhubDynamicVarProviders__) {
      try { d && d.dispose && d.dispose() } catch (e) { /* ignore */ }
    }
  }
  const disposables = []
  const languages = ['json', 'xml', 'plaintext']
  languages.forEach((lang) => {
    const d = monaco.languages.registerCompletionItemProvider(lang, {
      triggerCharacters: ['{'],
      provideCompletionItems(model, position) {
        // 取当前行光标前的文本，判断是否以 `{{` 结尾（允许 `{{` 后紧跟部分变量名）
        const lineText = model.getValueInRange({
          startLineNumber: position.lineNumber,
          startColumn: 1,
          endLineNumber: position.lineNumber,
          endColumn: position.column
        })
        const match = lineText.match(/\{\{([\w.]*)$/)
        if (!match) return { suggestions: [] }

        // 替换范围：从 `{{` 起始位置到当前光标位置，整段替换为 `{{xxx}}`
        const startColumn = position.column - match[0].length
        // 处理 Monaco autoClosingBrackets：用户输入 `{{` 时编辑器会自动补出 `}}`
        // 若光标后紧跟 `}}`，需要把它们一并纳入替换范围，否则会出现 `{{xxx}}}}`
        const afterCursor = model.getValueInRange({
          startLineNumber: position.lineNumber,
          startColumn: position.column,
          endLineNumber: position.lineNumber,
          endColumn: position.column + 2
        })
        const endColumn = afterCursor === '}}' ? position.column + 2 : position.column
        const range = {
          startLineNumber: position.lineNumber,
          startColumn,
          endLineNumber: position.lineNumber,
          endColumn
        }

        const suggestions = (modelVariableLists.get(model) || DYNAMIC_VARIABLES).map((v) => ({
          label: `{{${v.name}}}`,
          kind: monaco.languages.CompletionItemKind.Variable,
          detail: v.desc,
          documentation: { value: `**${v.name}** — ${v.desc}` },
          insertText: `{{${v.name}}}`,
          range
        }))
        return { suggestions }
      }
    })
    disposables.push(d)
  })
  window.__mockhubDynamicVarProviders__ = disposables
}

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'json'
  },
  readOnly: {
    type: Boolean,
    default: false
  },
  /**
   * 动态变量列表（供补全 provider 使用）
   * 每项 { name, desc }。未传时使用默认的内置 5 个变量。
   */
  dynamicVariables: {
    type: Array,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const editorContainer = ref(null)
let editor = null
let syncingExternalValue = false

// 大文本阈值（超过 100KB 禁用 minimap）
const LARGE_TEXT_THRESHOLD = 100 * 1024

/**
 * v1.4.4 性能优化：替换 Monaco 的 automaticLayout（内部 100ms 轮询容器尺寸）
 * 为 ResizeObserver + requestAnimationFrame 去抖。
 *
 * 背景：页面中多个 MonacoEditor 实例并存时（例如 SOAP 多 operation），
 * automaticLayout 会叠加 N 个定时器持续轮询 DOM，拖累滚动帧率。
 * ResizeObserver 是事件驱动的浏览器原生 API，尺寸不变时零开销。
 */
let resizeObserver = null
let resizeRafId = null

onMounted(() => {
  if (!editorContainer.value) return

  const isLargeText = (props.modelValue || '').length > LARGE_TEXT_THRESHOLD

  monaco.editor.defineTheme('mockhub-flat', { base: 'vs', inherit: true, rules: [], colors: { 'editor.background': '#F4F6F9', 'editorGutter.background': '#F4F6F9', 'editorLineNumber.foreground': '#9EA5AF', 'editor.lineHighlightBackground': '#EEF2F7', 'editor.selectionBackground': '#DCE7FB', 'widget.shadow': '#00000000' } })
  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue || '',
    language: props.language,
    readOnly: props.readOnly,
    minimap: { enabled: !isLargeText },
    // v1.4.4：关闭 Monaco 自带轮询，改用下方 ResizeObserver 事件驱动触发 layout
    automaticLayout: false,
    fontSize: 14,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    tabSize: 2,
    theme: 'mockhub-flat',
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    scrollbar: {
      verticalScrollbarSize: 6,
      horizontalScrollbarSize: 6
    }
  })

  // 挂 ResizeObserver：容器尺寸变化时 rAF 去抖触发 editor.layout()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      const entry = entries && entries[0]
      // 过滤尺寸为 0 的情况（display:none、折叠态切换瞬间等），避免 Monaco layout 异常
      if (!entry) return
      const { width, height } = entry.contentRect
      if (width === 0 || height === 0) return

      if (resizeRafId) cancelAnimationFrame(resizeRafId)
      resizeRafId = requestAnimationFrame(() => {
        if (editor) editor.layout()
        resizeRafId = null
      })
    })
    resizeObserver.observe(editorContainer.value)
  }

  // 仅同步到此文本模型，其他编辑器的团队变量互不影响
  setModelVariables(editor.getModel(), props.dynamicVariables)
  // 注册动态变量补全（模块级幂等）
  registerDynamicVariableCompletion()
  // 拦截 setModelMarkers，过滤掉落在 {{xxx}} 区间内的诊断标记（模块级幂等）
  patchSetModelMarkers()

  // 监听编辑器内容变化
  editor.onDidChangeModelContent(() => {
    const value = editor.getValue()
    if (!syncingExternalValue && !props.readOnly) emit('update:modelValue', value)

    // 动态切换 minimap
    const shouldDisableMinimap = value.length > LARGE_TEXT_THRESHOLD
    editor.updateOptions({ minimap: { enabled: !shouldDisableMinimap } })
  })
})

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  if (editor && newVal !== editor.getValue()) {
    syncingExternalValue = true
    try { editor.setValue(newVal || '') } finally { syncingExternalValue = false }
  }
})

// 监听语言切换
watch(() => props.language, (newLang) => {
  if (editor) {
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, newLang)
    }
  }
})

// 监听只读切换
watch(() => props.readOnly, (newVal) => {
  if (editor) {
    editor.updateOptions({ readOnly: newVal })
  }
})

// 监听动态变量列表变化（例如父组件切换了团队或拉取回了自定义变量），
// 写回模块级变量，provider 下次弹出补全时会读到新列表。
watch(() => props.dynamicVariables, (newVal) => {
  if (editor) setModelVariables(editor.getModel(), newVal)
}, { deep: false })

onBeforeUnmount(() => {
  // v1.4.4：清理 ResizeObserver 与 rAF，避免编辑器销毁后仍触发 layout
  if (resizeRafId) {
    cancelAnimationFrame(resizeRafId)
    resizeRafId = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (editor) {
    const model = editor.getModel()
    if (model) modelVariableLists.delete(model)
    editor.dispose()
    if (model && !model.isDisposed()) model.dispose()
    editor = null
  }
})

/**
 * 在当前光标位置插入文本
 *
 * 使用 executeEdits 写入，保留 undo 栈；插入后自动聚焦回编辑器。
 * 若存在选区，会先覆盖选区再插入。
 */
function insertAtCursor(text) {
  if (!editor || !text || props.readOnly) return
  const selection = editor.getSelection()
  const range = selection || {
    startLineNumber: 1,
    startColumn: 1,
    endLineNumber: 1,
    endColumn: 1
  }
  editor.pushUndoStop()
  editor.executeEdits('insert-dynamic-variable', [
    { range, text, forceMoveMarkers: true }
  ])
  editor.pushUndoStop()
  editor.focus()
}

defineExpose({ insertAtCursor })
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 400px;
  border-radius: 10px;
  overflow: hidden;
  border: 0;
  background: var(--app-bg-control);
}
.monaco-editor-container :deep(.monaco-hover),.monaco-editor-container :deep(.suggest-widget),.monaco-editor-container :deep(.parameter-hints-widget) { box-shadow: none; border: 0; }
</style>
