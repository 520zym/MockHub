<template>
  <!-- 接口类型 Tag，REST 蓝色，SOAP 紫色。样式复用 HttpMethodTag 的小胶囊风格。 -->
  <span class="api-type-tag" :style="tagStyle">
    {{ type }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 接口类型：REST 或 SOAP */
  type: {
    type: String,
    required: true,
    validator: (val) => ['REST', 'SOAP'].includes(val)
  }
})

/**
 * REST 用蓝色调（和后端 JSON 类 API 呼应），SOAP 用紫色调（区分度高）。
 * 透明度 0.12 背景 + 深色前景，Soft UI 风格与 HttpMethodTag 保持一致。
 */
const typeColors = {
  REST: { bg: 'rgba(59, 130, 246, 0.12)', color: '#2563EB' },
  SOAP: { bg: 'rgba(139, 92, 246, 0.12)', color: '#7C3AED' }
}

const tagStyle = computed(() => {
  const scheme = typeColors[props.type] || typeColors.REST
  return {
    backgroundColor: scheme.bg,
    color: scheme.color
  }
})
</script>

<style scoped>
.api-type-tag {
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
