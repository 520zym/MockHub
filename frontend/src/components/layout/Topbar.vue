<template>
  <header class="topbar">
    <div class="topbar__left">
      <button class="topbar__toggle" type="button" @click="appStore.toggleSidebar" :aria-label="appStore.sidebarCollapsed ? '展开侧栏' : '收起侧栏'"><el-icon><Expand v-if="appStore.sidebarCollapsed" /><Fold v-else /></el-icon></button>
      <div class="topbar__breadcrumb"><span>{{ context }}</span><span aria-hidden="true">/</span><b>{{ route.meta.title || 'MockHub' }}</b></div>
    </div>
    <div class="topbar__right">
      <span class="topbar__scope">{{ route.meta.requiresSuperAdmin ? '系统管理' : route.path.startsWith('/account') ? '个人范围' : '团队工作区' }}</span>
      <form class="topbar__search" @submit.prevent="search"><el-icon><Search /></el-icon><input v-model="query" type="search" placeholder="搜索当前团队接口" aria-label="搜索当前团队接口" /></form>
      <button class="topbar__toggle" type="button" aria-label="刷新当前身份" title="刷新当前身份" @click="refresh"><el-icon><Refresh /></el-icon></button>
    </div>
  </header>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
const appStore = useAppStore(), userStore = useUserStore(), route = useRoute(), router = useRouter(), query = ref('')
const context = computed(() => route.meta.requiresSuperAdmin ? '系统管理' : route.path.startsWith('/account') ? '个人空间' : appStore.currentTeam?.name || 'MockHub')
function search() { if (appStore.currentTeamId) router.push({ path: '/apis', query: query.value.trim() ? { keyword: query.value.trim() } : {} }) }
function refresh() { window.dispatchEvent(new CustomEvent('mockhub:permissions-changed')) }
</script>
<style lang="scss" scoped>
.topbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 56px; min-height: 56px; padding: 0 16px; background: var(--app-bg-surface); border-radius: 16px; min-width: 0; }
.topbar__left,.topbar__right { display: flex; align-items: center; gap: 10px; min-width: 0; }
.topbar__toggle { width: 30px; height: 30px; border: 0; background: var(--app-bg-control); border-radius: 9px; color: var(--app-text-secondary); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; }
.topbar__breadcrumb { display: flex; align-items: center; gap: 9px; min-width: 0; font-size: 12px; } .topbar__breadcrumb span { color: var(--app-text-secondary); } .topbar__breadcrumb b { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.topbar__scope { padding: 4px 8px; background: var(--app-bg-control); border-radius: 7px; font-size: 12px; white-space: nowrap; color: var(--app-text-secondary); }
.topbar__search { display: flex; gap: 8px; align-items: center; background: var(--app-bg-control); border-radius: 9px; padding: 7px 10px; color: var(--app-text-secondary); }
.topbar__search input { border: 0; background: transparent; outline: none; width: 180px; min-width: 0; font: inherit; font-size: 12px; color: var(--app-text-primary); }
@media(max-width: 1000px) { .topbar__scope { display: none; } .topbar__search input { width: 130px; } }
@media(max-width: 650px) { .topbar { padding: 0 10px; } .topbar__search { display: none; } .topbar__breadcrumb > span:first-child { display: none; } }
</style>
