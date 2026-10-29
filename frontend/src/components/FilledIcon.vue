<template>
  <svg
    class="filled-icon"
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : true"
    focusable="false"
    fill="currentColor"
    stroke="none"
  >
    <path :d="path" fill-rule="evenodd" clip-rule="evenodd" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

// Solid navigation glyphs from the approved flat shell. Import locally; no global registration.
// Usage: <FilledIcon name="api" :size="18" />. Add label only when the icon has no adjacent text.
const props = defineProps({
  name: { type: String, default: 'workspace' },
  size: { type: [Number, String], default: 20 },
  label: { type: String, default: '' }
})

const paths = {
  api: 'M8.5 3a1.2 1.2 0 0 1 1.2 1.2v2.5h4.6V4.2a1.2 1.2 0 1 1 2.4 0v2.5h.5A2.8 2.8 0 0 1 20 9.5v1.7a7.4 7.4 0 0 1-6.2 7.3v2.3a1.2 1.2 0 1 1-2.4 0v-2.3A7.4 7.4 0 0 1 4 11.2V9.5a2.8 2.8 0 0 1 2.8-2.8h.5V4.2A1.2 1.2 0 0 1 8.5 3Z',
  files: 'M3 7.2A3.2 3.2 0 0 1 6.2 4h3.1c.8 0 1.5.3 2 .9l.9 1.1h5.6A3.2 3.2 0 0 1 21 9.2v7.6a3.2 3.2 0 0 1-3.2 3.2H6.2A3.2 3.2 0 0 1 3 16.8Z',
  variables: 'M3 6.2A3.2 3.2 0 0 1 6.2 3h6.1c.9 0 1.7.3 2.3.9l5.5 5.5a3.2 3.2 0 0 1 0 4.5l-5.4 5.4a3.2 3.2 0 0 1-4.5 0l-5.5-5.5A3.2 3.2 0 0 1 3 11.5Zm5.2 3.7a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4Z',
  stats: 'M3 14.5A1.5 1.5 0 0 1 4.5 13h1.2a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 5.7 20H4.5A1.5 1.5 0 0 1 3 18.5Zm6.9-5A1.5 1.5 0 0 1 11.4 8h1.2a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5h-1.2a1.5 1.5 0 0 1-1.5-1.5Zm6.9-5A1.5 1.5 0 0 1 18.3 3h1.2A1.5 1.5 0 0 1 21 4.5v14a1.5 1.5 0 0 1-1.5 1.5h-1.2a1.5 1.5 0 0 1-1.5-1.5Z',
  team: 'M11.5 7.7a3.2 3.2 0 1 1-6.4 0 3.2 3.2 0 0 1 6.4 0ZM2.4 18.2c0-3 2.5-5 5.9-5s5.9 2 5.9 5c0 .8-.6 1.4-1.4 1.4H3.8c-.8 0-1.4-.6-1.4-1.4ZM19.2 8.8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0Zm-3.9 5.4c2.9-.2 5.4 1.6 5.4 4.2 0 .7-.5 1.2-1.2 1.2h-3c.1-.4.2-.9.2-1.4 0-1.6-.5-3-1.4-4Z',
  user: 'M15.8 8a3.8 3.8 0 1 1-7.6 0 3.8 3.8 0 0 1 7.6 0ZM4.2 20c0-3.9 3.4-6.3 7.8-6.3s7.8 2.4 7.8 6.3c0 .7-.6 1.2-1.3 1.2H5.5c-.7 0-1.3-.5-1.3-1.2Z',
  logs: 'M9 6.2A1.2 1.2 0 0 1 10.2 5h9.6a1.2 1.2 0 0 1 0 2.4h-9.6A1.2 1.2 0 0 1 9 6.2Zm0 5.8a1.2 1.2 0 0 1 1.2-1.2h9.6a1.2 1.2 0 0 1 0 2.4h-9.6A1.2 1.2 0 0 1 9 12Zm0 5.8a1.2 1.2 0 0 1 1.2-1.2h9.6a1.2 1.2 0 0 1 0 2.4h-9.6A1.2 1.2 0 0 1 9 17.8ZM6.1 6.2a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm0 5.8a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm0 5.8a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Z',
  settings: 'M3 5.8h8.2a3.8 3.8 0 0 1 7.2 0H21v2.4h-2.6a3.8 3.8 0 0 1-7.2 0H3Zm10.3 1.2a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0ZM3 15.8h2a3.8 3.8 0 0 1 7.2 0H21v2.4h-8.8a3.8 3.8 0 0 1-7.2 0H3Zm4.1 1.2a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0Z',
  workspace: 'M3 5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm10 0a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2ZM3 15a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm10 0a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2Z',
  shield: 'M12 2 3.5 5v6c0 5.2 3.4 9.1 8.5 11 5.1-1.9 8.5-5.8 8.5-11V5Zm4.7 7.3a1 1 0 0 0-1.4-1.4L10.5 13l-1.8-1.8a1 1 0 0 0-1.4 1.4l2.5 2.5a1 1 0 0 0 1.4 0Z',
  key: 'M15.5 2a6.5 6.5 0 0 0-6.1 8.8L2 18.2V22h3.8l1.4-1.4V18h2.6l1.4-1.4V15l2-2a6.5 6.5 0 1 0 2.3-11Zm2.7 4.2a1.7 1.7 0 1 1-2.4 2.4 1.7 1.7 0 0 1 2.4-2.4Z',
  history: 'M12 3a9 9 0 1 1-8.7 11.3 1 1 0 0 1 1.9-.6A7 7 0 1 0 6.4 7.8H9a1 1 0 0 1 0 2H3V3.9a1 1 0 1 1 2 0v2.2A9 9 0 0 1 12 3Zm-1 4h2v5.4l3.1 1.8-1 1.7-4.1-2.3Z',
  trash: 'M9 2h6l1 2h4a1 1 0 1 1 0 2H4a1 1 0 1 1 0-2h4ZM5 8h14l-1 12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2Zm4 3v7h2v-7Zm4 0v7h2v-7Z',
  assistant: 'M12 2a1 1 0 0 1 1 1v2h4a4 4 0 0 1 4 4v7a4 4 0 0 1-4 4H7l-4 2V9a4 4 0 0 1 4-4h4V3a1 1 0 0 1 1-1ZM7 10v3h3v-3Zm7 0v3h3v-3Zm-6 6v2h8v-2Z'
}
const aliases = { folder: 'files', chart: 'stats', teams: 'team', users: 'user', log: 'logs', account: 'user', tokens: 'key', system: 'shield' }
const path = computed(() => paths[aliases[props.name] || props.name] || paths.workspace)
</script>

<style scoped>
.filled-icon {
  display: inline-block;
  flex: 0 0 auto;
  vertical-align: -0.125em;
  color: inherit;
}
</style>
