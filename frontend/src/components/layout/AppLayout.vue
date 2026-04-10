<template>
  <div class="app-layout">
    <Sidebar class="app-layout__sidebar" />
    <div class="app-layout__main" :class="{ 'sidebar-collapsed': appStore.sidebarCollapsed }">
      <Topbar class="app-layout__topbar" />
      <div class="app-layout__content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useAppStore } from '@/stores/app'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const appStore = useAppStore()
</script>

<style lang="scss" scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;

  &__sidebar {
    flex-shrink: 0;
  }

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    margin-left: 220px;
    transition: margin-left 0.3s ease;

    &.sidebar-collapsed {
      margin-left: 64px;
    }
  }

  &__topbar {
    flex-shrink: 0;
  }

  &__content {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf4 50%, #e0e7f1 100%);
  }
}
</style>
