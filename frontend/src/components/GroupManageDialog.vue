<template><TeamMetadataDialog :model-value="modelValue" :team-id="teamId" kind="groups" @update:model-value="$emit('update:modelValue', $event)" @changed="$emit('changed', $event)" /></template>
<script setup>
import TeamMetadataDialog from '@/components/TeamMetadataDialog.vue'
defineProps({ modelValue: Boolean, teamId: { type: String, default: '' } })
defineEmits(['update:modelValue', 'changed'])
</script>
