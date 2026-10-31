<template>
  <!-- Searchable single-account picker built on el-select so the dropdown renders in a body-level popper. -->
  <el-select
    class="user-picker"
    :model-value="selectedId"
    :disabled="disabled"
    :loading="loading"
    :placeholder="loading ? '正在加载账号…' : placeholder"
    :no-data-text="loading ? '正在加载账号…' : '没有匹配的账号'"
    :aria-label="ariaLabel"
    filterable
    clearable
    :reserve-keyword="false"
    popper-class="user-picker-popper"
    :fit-input-width="true"
    @update:model-value="onPick"
  >
    <el-option
      v-for="user in users"
      :key="user.id"
      :value="user.id"
      :label="user.displayName ? user.displayName + ' ' + user.username : user.username"
      :disabled="user.status !== 'ACTIVE'"
    >
      <span class="user-picker-row">
        <span class="user-picker-row__monogram" aria-hidden="true">{{ monogram(user) }}</span>
        <span class="user-picker-row__identity">
          <b>{{ user.displayName || user.username }}</b>
          <small>{{ user.username }}</small>
        </span>
        <span v-if="user.status !== 'ACTIVE'" class="user-picker-row__badge muted">已停用</span>
        <span v-else-if="user.globalRole === 'SUPER_ADMIN'" class="user-picker-row__badge">超管</span>
      </span>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Object, default: null },
  users: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  placeholder: { type: String, default: '搜索显示名或用户名' },
  ariaLabel: { type: String, default: '选择账号' }
})
const emit = defineEmits(['update:modelValue'])

const selectedId = computed(() => (props.modelValue && props.modelValue.id) || null)

function monogram(user) {
  const source = (user && (user.displayName || user.username)) || ''
  return source.trim().slice(0, 1).toUpperCase() || '?'
}

function onPick(id) {
  if (id === undefined || id === null || id === '') { emit('update:modelValue', null); return }
  const user = props.users.find(item => item.id === id) || null
  emit('update:modelValue', user)
}
</script>

<style lang="scss" scoped>
.user-picker { width: 100%; }
.user-picker-row { display: flex; align-items: center; gap: 10px; min-width: 0; }
.user-picker-row__monogram { width: 26px; height: 26px; flex: 0 0 26px; display: grid; place-items: center; border-radius: 8px; background: var(--app-bg-hover); color: var(--app-text-regular); font-size: var(--app-font-micro); font-weight: 600; }
.user-picker-row__identity { display: grid; min-width: 0; line-height: 1.35; margin-right: auto; }
.user-picker-row__identity b { font-size: 13px; font-weight: 500; color: var(--app-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-picker-row__identity small { font-size: var(--app-font-caption); color: var(--app-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-picker-row__badge { flex: 0 0 auto; font-size: var(--app-font-micro); color: var(--app-text-accent); background: var(--app-bg-selected); border-radius: 6px; padding: 2px 6px; }
.user-picker-row__badge.muted { color: var(--app-text-disabled); background: var(--app-bg-disabled); }
</style>

<style lang="scss">
/* Popper lives under <body>, so its sizing lives outside the scoped block. */
.user-picker-popper .el-select-dropdown__item { height: auto; padding: 6px 12px; line-height: 1.4; }
.user-picker-popper .el-select-dropdown__item.is-disabled { opacity: .62; }
.user-picker-popper .el-select-dropdown__wrap { max-height: 264px; }
</style>
