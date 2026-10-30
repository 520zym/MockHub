<template>
  <span class="http-method-tag" :class="'is-' + method.toLowerCase()">
    {{ method }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  method: {
    type: String,
    required: true,
    validator: (val) => ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(val)
  }
})

// Colour comes from the approved tokens: the old Tailwind values sat at 1.3–1.5:1
// on their own 12% tint, which is unreadable for a label this small.
const methodClass = computed(() => 'is-' + (props.method || 'GET').toLowerCase())
</script>

<style>
.http-method-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  line-height: 20px;
  white-space: nowrap;
}

.http-method-tag.is-get {
  background: var(--el-color-success-light-9);
  color: var(--app-text-success);
}

.http-method-tag.is-post {
  background: var(--app-bg-selected);
  color: var(--app-text-accent);
}

.http-method-tag.is-put {
  background: var(--el-color-warning-light-9);
  color: var(--app-text-warning);
}

.http-method-tag.is-delete {
  background: var(--el-color-danger-light-9);
  color: var(--app-text-danger);
}

.http-method-tag.is-patch {
  background: var(--app-bg-violet);
  color: var(--app-text-violet);
}
</style>
