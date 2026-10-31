<template>
  <!-- Searchable single-account picker: type to filter, click or Enter to choose. -->
  <div class="user-picker" :class="{ 'is-open': open, 'is-disabled': disabled }" @keydown.esc.stop.prevent="close">
    <div class="user-picker__control">
      <span v-if="selected" class="user-picker__selected">
        <span class="user-picker__monogram" aria-hidden="true">{{ monogram(selected) }}</span>
        <span class="user-picker__identity">
          <b :title="selected.displayName">{{ selected.displayName }}</b>
          <small class="mono" :title="selected.username">{{ selected.username }}</small>
        </span>
      </span>
      <input
        :id="inputId"
        ref="input"
        v-model="query"
        class="user-picker__input"
        type="text"
        role="combobox"
        autocomplete="off"
        :aria-expanded="open ? 'true' : 'false'"
        aria-controls="user-picker-list"
        :aria-activedescendant="open && active >= 0 ? 'user-picker-option-' + active : undefined"
        :aria-label="ariaLabel"
        :placeholder="selected ? '重新搜索并更换' : placeholder"
        :disabled="disabled"
        @focus="onFocus"
        @input="onInput"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.enter.prevent="choose(active)"
        @keydown.tab="close"
      />
      <button v-if="selected" type="button" class="user-picker__clear" :disabled="disabled" aria-label="清除已选账号" @click="clear">×</button>
      <button v-else type="button" class="user-picker__toggle" :disabled="disabled" tabindex="-1" aria-hidden="true" @click="toggle">{{ open ? '▴' : '▾' }}</button>
    </div>

    <ul v-if="open" id="user-picker-list" class="user-picker__list" :class="{ 'is-up': placement === 'up' }" :style="{ maxHeight: listHeight + 'px' }" role="listbox" :aria-label="ariaLabel">
      <li v-if="loading" class="user-picker__empty">正在加载账号…</li>
      <li v-else-if="!options.length" class="user-picker__empty">{{ query.trim() ? '没有匹配的账号' : '还没有可指派的账号' }}</li>
      <template v-else>
        <li
          v-for="(user, index) in options"
          :id="'user-picker-option-' + index"
          :key="user.id"
          class="user-picker__option"
          :class="{ 'is-active': index === active, 'is-current': selected && selected.id === user.id, 'is-inactive': user.status !== 'ACTIVE' }"
          role="option"
          :aria-selected="selected && selected.id === user.id ? 'true' : 'false'"
          :aria-disabled="user.status !== 'ACTIVE' ? 'true' : 'false'"
          @mousedown.prevent="choose(index)"
          @mouseenter="active = index"
        >
          <span class="user-picker__monogram" aria-hidden="true">{{ monogram(user) }}</span>
          <span class="user-picker__identity">
            <b :title="user.displayName">{{ user.displayName }}</b>
            <small class="mono" :title="user.username">{{ user.username }}</small>
          </span>
          <span v-if="user.status !== 'ACTIVE'" class="user-picker__badge muted">已停用</span>
          <span v-else-if="user.globalRole === 'SUPER_ADMIN'" class="user-picker__badge">超管</span>
        </li>
      </template>
    </ul>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Object, default: null },
  users: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  placeholder: { type: String, default: '搜索显示名或用户名' },
  ariaLabel: { type: String, default: '选择账号' }
})
const emit = defineEmits(['update:modelValue'])

const inputId = 'user-picker-' + Math.random().toString(36).slice(2, 8)
const input = ref(), query = ref(''), open = ref(false), active = ref(-1)
// The list is taller than the space left in a phone-sized dialog, so it flips upward
// and shrinks to whatever room is actually available.
const placement = ref('down'), listHeight = ref(264)
const selected = computed(() => props.modelValue || null)

const options = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  const list = props.users.filter(user => !keyword || user.username.toLowerCase().includes(keyword) || (user.displayName || '').toLowerCase().includes(keyword))
  // Active accounts first, then by display name so the list stays predictable.
  return list.slice().sort((a, b) => {
    const rank = (a.status === 'ACTIVE' ? 0 : 1) - (b.status === 'ACTIVE' ? 0 : 1)
    return rank || (a.displayName || a.username).localeCompare(b.displayName || b.username, 'zh')
  })
})

function monogram(user) {
  const source = (user && (user.displayName || user.username)) || ''
  return source.trim().slice(0, 1).toUpperCase() || '?'
}

function place() {
  const control = input.value && input.value.closest('.user-picker__control')
  if (!control) return
  const rect = control.getBoundingClientRect()
  const below = window.innerHeight - rect.bottom - 12
  const above = rect.top - 12
  const wantsUp = below < 220 && above > below
  placement.value = wantsUp ? 'up' : 'down'
  listHeight.value = Math.max(120, Math.min(264, wantsUp ? above : below))
}

function onFocus() {
  if (props.disabled) return
  open.value = true
  active.value = options.value.findIndex(user => user.status === 'ACTIVE')
  place()
}

function onInput() {
  open.value = true
  active.value = options.value.findIndex(user => user.status === 'ACTIVE')
  place()
}

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    place()
    nextTick(() => input.value && input.value.focus())
  }
}

function close() {
  open.value = false
  active.value = -1
}

function move(step) {
  if (!open.value) { onFocus(); return }
  if (!options.value.length) return
  let index = active.value
  for (let attempt = 0; attempt < options.value.length; attempt++) {
    index = (index + step + options.value.length) % options.value.length
    if (options.value[index].status === 'ACTIVE') break
  }
  active.value = index
}

function choose(index) {
  const user = options.value[index]
  if (!user || user.status !== 'ACTIVE' || props.disabled) return
  emit('update:modelValue', user)
  query.value = ''
  close()
}

function clear() {
  if (props.disabled) return
  emit('update:modelValue', null)
  query.value = ''
  nextTick(() => input.value && input.value.focus())
}

function onViewportChange() {
  if (open.value) place()
}

window.addEventListener('resize', onViewportChange)
document.addEventListener('scroll', onViewportChange, true)

function onDocumentPointer(event) {
  if (!input.value || !open.value) return
  const root = input.value.closest('.user-picker')
  if (root && !root.contains(event.target)) close()
}

document.addEventListener('mousedown', onDocumentPointer)
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentPointer)
  window.removeEventListener('resize', onViewportChange)
  document.removeEventListener('scroll', onViewportChange, true)
})

watch(() => props.disabled, disabled => { if (disabled) close() })
watch(() => props.users.length, () => { if (open.value) active.value = options.value.findIndex(user => user.status === 'ACTIVE') })
</script>

<style lang="scss" scoped>
.user-picker {
  position: relative;
  width: 100%;
}

.user-picker__control {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 8px 0 10px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-divider);
  border-radius: var(--app-radius-control);
}

.user-picker__control:focus-within {
  border-color: var(--app-accent);
}

.is-open .user-picker__control {
  border-color: var(--app-accent);
}

.is-disabled .user-picker__control {
  background: var(--app-bg-disabled);
}

.user-picker__selected {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 8px 3px 4px;
  border-radius: var(--app-radius-small);
  background: var(--app-bg-control);
  max-width: 100%;
}

.user-picker__monogram {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--app-bg-hover);
  color: var(--app-text-regular);
  font-size: var(--app-font-micro);
  font-weight: 600;
}

.user-picker__identity {
  display: grid;
  min-width: 0;
  line-height: 1.35;
}

.user-picker__identity b {
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-picker__identity small {
  font-size: var(--app-font-caption);
  color: var(--app-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-picker__input {
  flex: 1;
  min-width: 60px;
  height: 34px;
  border: 0;
  outline: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--app-text-primary);
  padding: 0;
}

.user-picker__input::placeholder {
  color: var(--app-text-muted);
}

.user-picker__clear,
.user-picker__toggle {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.user-picker__clear:hover,
.user-picker__toggle:hover {
  background: var(--app-bg-hover);
  color: var(--app-text-primary);
}

.user-picker__list {
  position: absolute;
  z-index: 3000;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  margin: 0;
  padding: 4px;
  list-style: none;
  max-height: 264px;
  overflow-y: auto;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-divider);
  border-radius: var(--app-radius-panel);
}

.user-picker__list.is-up {
  top: auto;
  bottom: calc(100% + 6px);
}

.user-picker__option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 4px 8px;
  border-radius: var(--app-radius-small);
  cursor: pointer;
}

.user-picker__option + .user-picker__option {
  margin-top: 2px;
}

.user-picker__option.is-active {
  background: var(--app-bg-control);
}

.user-picker__option.is-current {
  background: var(--app-bg-selected);
}

.user-picker__option.is-inactive {
  cursor: not-allowed;
  opacity: .62;
}

.user-picker__badge {
  margin-left: auto;
  font-size: var(--app-font-micro);
  color: var(--app-text-accent);
  background: var(--app-bg-selected);
  border-radius: 6px;
  padding: 2px 6px;
}

.user-picker__badge.muted {
  color: var(--app-text-disabled);
  background: var(--app-bg-disabled);
}

.user-picker__empty {
  padding: 12px 10px;
  font-size: var(--app-font-caption);
  color: var(--app-text-secondary);
}
</style>
