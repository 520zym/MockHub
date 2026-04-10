<template>
  <span class="http-method-tag" :style="tagStyle">
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

const methodColors = {
  GET: { bg: 'rgba(16, 185, 129, 0.12)', color: '#059669' },
  POST: { bg: 'rgba(99, 102, 241, 0.12)', color: '#4F46E5' },
  PUT: { bg: 'rgba(245, 158, 11, 0.12)', color: '#D97706' },
  DELETE: { bg: 'rgba(239, 68, 68, 0.12)', color: '#DC2626' },
  PATCH: { bg: 'rgba(139, 92, 246, 0.12)', color: '#7C3AED' }
}

const tagStyle = computed(() => {
  const scheme = methodColors[props.method] || methodColors.GET
  return {
    backgroundColor: scheme.bg,
    color: scheme.color
  }
})
</script>

<style scoped>
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
</style>
