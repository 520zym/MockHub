<template>
  <el-dialog
    v-model="visible"
    title="返回体怎么选"
    width="min(740px, calc(100vw - 24px))"
    top="24px"
    class="match-guide-dialog"
    append-to-body
    :show-close="false"
  >
    <template #header="{ titleId }">
      <div class="guide-header">
        <div>
          <h2 :id="titleId">返回体怎么选</h2>
          <p>条件决定返回哪份内容。</p>
        </div>
        <button type="button" class="guide-close" @click="visible = false">关闭</button>
      </div>
    </template>

    <div class="match-guide">
      <div class="guide-tabs" role="tablist" aria-label="返回体选择方式" @keydown="onTabKeydown">
        <button
          v-for="tab in tabs"
          :id="tabId(tab.value)"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="activeMode === tab.value"
          :aria-controls="panelId(tab.value)"
          :tabindex="activeMode === tab.value ? 0 : -1"
          :class="{ active: activeMode === tab.value }"
          @click="activeMode = tab.value"
        >{{ tab.label }}</button>
      </div>

      <section
        v-show="activeMode === 'CONDITION'"
        :id="panelId('CONDITION')"
        role="tabpanel"
        :aria-labelledby="tabId('CONDITION')"
        tabindex="0"
      >
        <figure class="guide-figure">
          <figcaption>一个请求 → 选一份返回体 → 返回它的内容</figcaption>
          <div class="condition-flow">
            <div class="request-card">
              <span class="card-label">请求 · Query</span>
              <code>?status=paid</code>
            </div>
            <svg class="flow-arrow" viewBox="0 0 28 28" aria-hidden="true"><path d="M3 14h21m-7-7 7 7-7 7" /></svg>
            <div class="response-choices" aria-label="示例返回体，按顺序判断">
              <div class="response-choice selected">
                <div class="choice-heading"><strong>成功返回体</strong><span class="selected-badge">✓ 命中</span></div>
                <div class="condition-tokens"><span>Query</span><code>status</code><span>等于</span><code>paid</code></div>
              </div>
              <div class="response-choice fallback">
                <strong>默认返回体</strong>
                <span>无条件 · 未命中时兜底</span>
              </div>
            </div>
            <svg class="flow-arrow" viewBox="0 0 28 28" aria-hidden="true"><path d="M3 14h21m-7-7 7 7-7 7" /></svg>
            <div class="result-card">
              <span class="card-label">返回成功 JSON</span>
              <pre><code>{{ successJson }}</code></pre>
              <span class="result-status">200 · 成功</span>
            </div>
          </div>
        </figure>

        <div class="selection-rules">
          <p><span class="rule-mark" aria-hidden="true">①</span><strong>按顺序，第一个命中就返回</strong></p>
          <p><span class="rule-mark" aria-hidden="true">↳</span>都未命中 → 唯一的无条件兜底</p>
        </div>

        <ol class="guide-steps" aria-label="配置三步">
          <li><span class="step-number" aria-hidden="true">1</span><div><strong>选返回体</strong><span>选「成功返回体」</span></div></li>
          <li><span class="step-number" aria-hidden="true">2</span><div><strong>加条件</strong><span>Query · status 等于 paid</span></div></li>
          <li><span class="step-number" aria-hidden="true">3</span><div><strong>填内容</strong><span>填写这份返回体的 JSON</span></div></li>
        </ol>
        <div class="guide-facts">
          <p>同一返回体：全部条件都满足（AND）。</p>
          <p>也可匹配 Body {{ isXml ? 'XML' : 'JSON' }} / Header。</p>
        </div>
      </section>

      <section
        v-show="activeMode === 'RANDOM'"
        :id="panelId('RANDOM')"
        role="tabpanel"
        :aria-labelledby="tabId('RANDOM')"
        tabindex="0"
      >
        <figure class="guide-figure random-figure">
          <figcaption>不看条件，不看顺序，机会一样</figcaption>
          <div class="random-request"><span class="card-label">每次请求</span><code>?status=paid</code></div>
          <svg class="random-branches" viewBox="0 0 320 40" aria-hidden="true"><path d="M160 2v13H80v20m80-20h80v20M74 29l6 6 6-6m148 0 6 6 6-6" /></svg>
          <div class="random-candidates" aria-label="两份启用返回体，各有百分之五十的概率">
            <div class="random-choice"><span class="probability">50<span>%</span></span><strong>成功返回体</strong><span>返回成功 JSON</span></div>
            <div class="random-choice"><span class="probability">50<span>%</span></span><strong>默认返回体</strong><span>返回默认 JSON</span></div>
          </div>
          <p class="random-caption">示例：2 份启用返回体，每次随机选 1 份。</p>
        </figure>
        <div class="selection-rules random-rules">
          <p><strong>仅从启用的返回体中等概率选择</strong></p>
          <p>条件会保留；切回条件匹配后继续生效。</p>
        </div>
      </section>

      <p class="single-note">只启用 1 份？直接返回它。</p>
      <p class="guide-only-note">切换上方说明，不会修改接口配置。</p>
    </div>

    <template #footer>
      <button type="button" class="guide-done" @click="visible = false">我明白了</button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, useId, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'CONDITION' },
  bodySampleKind: { type: String, default: 'json' }
})
const emit = defineEmits(['update:modelValue'])
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const normalizeMode = value => value === 'RANDOM' ? 'RANDOM' : 'CONDITION'
const activeMode = ref(normalizeMode(props.mode))
const isXml = computed(() => String(props.bodySampleKind || 'json').toLowerCase() === 'xml')
const uid = useId()
const tabId = value => uid + '-guide-tab-' + value
const panelId = value => uid + '-guide-panel-' + value
const tabs = [{ value: 'CONDITION', label: '条件匹配' }, { value: 'RANDOM', label: '随机返回' }]
const successJson = JSON.stringify({ ok: true }, null, 2)

// Reopening follows the actual API mode; switching a guide tab never changes it.
watch([() => props.modelValue, () => props.mode], ([open]) => {
  if (open) activeMode.value = normalizeMode(props.mode)
})

function onTabKeydown(event) {
  const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End']
  if (!keys.includes(event.key)) return
  event.preventDefault()
  const index = tabs.findIndex(tab => tab.value === activeMode.value)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length
  activeMode.value = tabs[next].value
  event.currentTarget.querySelectorAll('[role="tab"]')[next]?.focus()
}
</script>

<style scoped>
:global(.match-guide-dialog.el-dialog) {
  --el-dialog-padding-primary: 24px;
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 24px);
  padding: 24px;
  color: var(--app-text-primary);
  background: var(--app-bg-surface);
  border-radius: var(--app-radius-panel);
  box-shadow: none;
  background-image: none;
}
:global(.match-guide-dialog.el-dialog:not(.is-fullscreen)) { margin: 24px auto; max-height: calc(100dvh - 48px); }
:global(.match-guide-dialog .el-dialog__header) { flex-shrink: 0; padding: 0 0 20px; margin: 0; }
:global(.match-guide-dialog .el-dialog__body) { min-width: 0; min-height: 0; padding: 0; overflow: auto; overscroll-behavior: contain; }
:global(.match-guide-dialog .el-dialog__footer) { flex-shrink: 0; padding: 18px 0 0; }
.guide-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.guide-header h2 { margin: 0; color: var(--app-text-primary); font-size: 20px; line-height: 1.4; font-weight: 650; }
.guide-header p { margin: 6px 0 0; color: var(--app-text-secondary); font-size: 14px; line-height: 1.5; }
.match-guide { color: var(--app-text-primary); font-size: 14px; line-height: 1.5; }
.match-guide *, .guide-header *, .guide-done { box-sizing: border-box; }
.match-guide p { margin: 0; }
.match-guide code { font-family: var(--app-font-mono); }
.guide-tabs { display: flex; gap: 6px; width: fit-content; max-width: 100%; padding: 4px; margin-bottom: 18px; background: var(--app-bg-control); border-radius: var(--app-radius-control); }
.guide-tabs button, .guide-close, .guide-done { min-height: 44px; border: 0; border-radius: var(--app-radius-small); font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: none; background-image: none; }
.guide-tabs button { min-width: 108px; padding: 10px 16px; color: var(--app-text-secondary); background: transparent; }
.guide-tabs button.active { color: var(--app-text-inverse); background: var(--app-primary); }
.guide-tabs button:not(.active):hover, .guide-close:hover { background: var(--app-bg-hover); color: var(--app-text-primary); }
.guide-close { flex-shrink: 0; min-width: 60px; padding: 10px 14px; color: var(--app-text-regular); background: var(--app-bg-control); }
.guide-done { min-width: 120px; padding: 11px 22px; color: var(--app-text-inverse); background: var(--app-primary); }
.guide-done:hover { background: var(--app-primary-hover); }
.guide-tabs button:focus-visible, .guide-close:focus-visible, .guide-done:focus-visible, [role="tabpanel"]:focus-visible { outline: 2px solid var(--app-focus-color); outline-offset: 3px; }
.guide-figure { margin: 0; padding: 18px 14px; border-radius: var(--app-radius-control); background: var(--app-bg-control); }
.guide-figure figcaption { margin-bottom: 18px; color: var(--app-text-primary); font-size: 14px; font-weight: 600; }
.condition-flow { display: grid; grid-template-columns: minmax(0, 1fr) 24px minmax(0, 1.65fr) 24px minmax(0, 1fr); align-items: center; gap: 8px; }
.request-card, .result-card { min-width: 0; }
.card-label { display: block; margin-bottom: 8px; color: var(--app-text-secondary); font-size: 12px; font-weight: 600; }
.request-card > code { display: block; color: var(--app-text-accent); font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.flow-arrow, .random-branches { fill: none; stroke: var(--app-text-accent); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.flow-arrow { width: 24px; height: 28px; }
.response-choices { display: grid; min-width: 0; gap: 10px; }
.response-choice { min-width: 0; padding: 12px; border: 1px solid var(--app-divider); border-radius: var(--app-radius-small); background: var(--app-bg-surface); }
.response-choice.selected { border: 2px solid var(--app-accent); background: var(--app-bg-selected); }
.choice-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 4px 8px; }
.response-choice strong { font-size: 14px; font-weight: 650; }
.selected-badge { color: var(--app-text-accent); font-size: 12px; font-weight: 600; white-space: nowrap; }
.condition-tokens { display: flex; flex-wrap: wrap; gap: 4px 6px; margin-top: 8px; color: var(--app-text-regular); font-size: 12px; }
.condition-tokens code { color: var(--app-text-primary); font-weight: 600; }
.fallback { display: grid; gap: 4px; border-style: dashed; }
.fallback > span { color: var(--app-text-secondary); font-size: 12px; }
.result-card pre { margin: 0 0 8px; color: var(--app-text-primary); font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.result-status { color: var(--app-text-success); font-size: 12px; font-weight: 600; }
.selection-rules { display: grid; gap: 6px; margin: 14px 0 18px; color: var(--app-text-regular); font-size: 13px; }
.selection-rules p { display: flex; align-items: baseline; gap: 8px; }
.rule-mark { width: 18px; color: var(--app-text-accent); font-weight: 650; text-align: center; }
.selection-rules strong { color: var(--app-text-primary); font-weight: 600; }
.guide-steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 0; padding: 0; list-style: none; }
.guide-steps li { display: flex; align-items: flex-start; gap: 10px; padding: 12px 10px; border-radius: var(--app-radius-small); background: var(--app-bg-control); }
.step-number { display: grid; place-items: center; flex-shrink: 0; width: 24px; height: 24px; border-radius: 50%; color: var(--app-text-inverse); background: var(--app-primary); font-size: 12px; font-weight: 600; }
.guide-steps li > div { display: grid; min-width: 0; gap: 4px; }
.guide-steps strong { font-size: 14px; font-weight: 600; }
.guide-steps li > div > span { color: var(--app-text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.guide-facts { display: grid; gap: 4px; margin-top: 14px; color: var(--app-text-secondary); font-size: 12px; }
.single-note { margin-top: 14px !important; color: var(--app-text-regular); font-size: 13px; }
.guide-only-note { margin-top: 6px !important; color: var(--app-text-secondary); font-size: 12px; }
.random-request { width: fit-content; margin: 0 auto; padding: 10px 18px; border-radius: var(--app-radius-small); background: var(--app-bg-surface); text-align: center; }
.random-request .card-label { margin-bottom: 3px; }
.random-request code { color: var(--app-text-accent); font-size: 13px; }
.random-branches { display: block; width: 100%; height: 40px; }
.random-candidates { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.random-choice { display: grid; justify-items: center; gap: 5px; min-width: 0; padding: 16px 8px; border: 1px solid var(--app-accent); border-radius: var(--app-radius-small); background: var(--app-bg-selected); text-align: center; }
.random-choice .probability { color: var(--app-text-accent); font-family: var(--app-font-mono); font-size: 32px; font-weight: 650; line-height: 1.2; }
.probability > span { font-size: 18px; }
.random-choice > strong { font-size: 14px; }
.random-choice > span:last-child, .random-caption { color: var(--app-text-secondary); font-size: 12px; }
.random-caption { margin-top: 14px !important; text-align: center; }
.random-rules { margin-bottom: 0; }
@media (max-width: 600px) {
  :global(.match-guide-dialog.el-dialog) { padding: 18px 16px; }
  :global(.match-guide-dialog.el-dialog:not(.is-fullscreen)) { margin: 12px auto; max-height: calc(100dvh - 24px); }
  :global(.match-guide-dialog .el-dialog__header) { padding-bottom: 16px; }
  :global(.match-guide-dialog .el-dialog__footer) { padding-top: 14px; }
  .guide-header h2 { font-size: 18px; }
  .guide-header p { font-size: 13px; }
  .guide-tabs { margin-bottom: 14px; }
  .guide-figure { padding: 14px; }
  .guide-figure figcaption { margin-bottom: 14px; font-size: 13px; }
  .condition-flow { grid-template-columns: minmax(0, 1fr); justify-items: stretch; gap: 4px; }
  .request-card { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 4px 10px; }
  .request-card .card-label { margin: 0; }
  .flow-arrow { justify-self: center; height: 20px; transform: rotate(90deg); }
  .response-choice { padding: 10px 12px; }
  .result-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 4px 10px; }
  .result-card .card-label { margin: 0; }
  .result-card pre { grid-column: 2; grid-row: 1 / 3; margin: 0; font-size: 13px; }
  .result-status { grid-column: 1; }
  .guide-steps { grid-template-columns: minmax(0, 1fr); gap: 6px; }
  .guide-steps li { align-items: center; padding: 8px 12px; }
  .guide-steps li > div { grid-template-columns: 62px minmax(0, 1fr); align-items: baseline; gap: 6px; }
  .selection-rules { margin: 12px 0 14px; }
  .guide-done { width: 100%; }
}
</style>
