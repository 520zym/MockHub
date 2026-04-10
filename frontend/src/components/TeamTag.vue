<template>
  <span class="team-tag" :style="tagStyle">
    {{ identifier }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  identifier: {
    type: String,
    required: true
  },
  color: {
    type: String,
    default: '#6366F1'
  }
})

const tagStyle = computed(() => {
  return {
    backgroundColor: hexToRgba(props.color, 0.15),
    color: props.color
  }
})

function hexToRgba(hex, alpha) {
  // 去掉 # 号
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<style scoped>
.team-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}
</style>
