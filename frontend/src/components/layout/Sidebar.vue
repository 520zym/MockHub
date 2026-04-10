<template>
  <div class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
    <!-- Logo -->
    <div class="sidebar__logo">
      <div class="sidebar__logo-icon">M</div>
      <span v-show="!appStore.sidebarCollapsed" class="sidebar__logo-text">MockHub</span>
    </div>

    <!-- 导航菜单 -->
    <nav class="sidebar__nav">
      <router-link
        v-for="item in visibleNavItems"
        :key="item.path"
        :to="item.path"
        class="sidebar__nav-item"
        :class="{ 'sidebar__nav-item--active': isActive(item.path) }"
      >
        <el-icon class="sidebar__nav-icon"><component :is="item.icon" /></el-icon>
        <span v-show="!appStore.sidebarCollapsed" class="sidebar__nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <!-- 团队筛选树占位（仅在接口管理页显示） -->
    <div
      v-if="showTeamTree && !appStore.sidebarCollapsed"
      class="sidebar__team-tree"
    >
      <div class="sidebar__divider" />
      <div class="sidebar__team-tree-placeholder">
        <span class="sidebar__section-title">团队筛选</span>
        <!-- Wave 2: ApiList subagent 完善团队筛选树 -->
        <p class="sidebar__placeholder-text">待实现</p>
      </div>
    </div>

    <!-- 收起/展开按钮 -->
    <div class="sidebar__footer">
      <div class="sidebar__toggle" @click="appStore.toggleSidebar">
        <el-icon>
          <Fold v-if="!appStore.sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const navItems = [
  { path: '/apis', label: '接口管理', icon: 'Connection', requiresSuperAdmin: false },
  { path: '/teams', label: '团队管理', icon: 'OfficeBuilding', requiresSuperAdmin: true },
  { path: '/users', label: '用户管理', icon: 'User', requiresSuperAdmin: true },
  { path: '/logs/operation', label: '操作日志', icon: 'Document', requiresSuperAdmin: false },
  { path: '/logs/request', label: '请求日志', icon: 'Tickets', requiresSuperAdmin: false },
  { path: '/settings', label: '全局设置', icon: 'Setting', requiresSuperAdmin: true }
]

const visibleNavItems = computed(() => {
  return navItems.filter(item => {
    if (item.requiresSuperAdmin) {
      return userStore.isSuperAdmin
    }
    return true
  })
})

const showTeamTree = computed(() => {
  return route.path === '/apis' || route.path.startsWith('/apis')
})

function isActive(path) {
  if (path === '/apis') {
    return route.path === '/apis' || route.path.startsWith('/apis/')
  }
  return route.path === path
}
</script>

<style lang="scss" scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  width: 220px;
  background: #FFFFFF;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  z-index: 100;
  overflow-y: auto;
  overflow-x: hidden;

  &--collapsed {
    width: 64px;
  }

  // Logo
  &__logo {
    display: flex;
    align-items: center;
    padding: 20px 16px;
    gap: 10px;
  }

  &__logo-icon {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, #6366F1, #4F46E5);
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #FFFFFF;
    font-size: 18px;
    font-weight: 700;
    flex-shrink: 0;
  }

  &__logo-text {
    font-size: 20px;
    font-weight: 700;
    color: #1B2559;
    white-space: nowrap;
  }

  // 导航
  &__nav {
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__nav-item {
    display: flex;
    align-items: center;
    height: 44px;
    padding: 0 12px;
    border-radius: 12px;
    color: #4A5568;
    text-decoration: none;
    transition: all 0.2s ease;
    gap: 10px;
    white-space: nowrap;

    &:hover {
      background: #F7F8FA;
      color: #1B2559;
    }

    &--active {
      background: #6366F1 !important;
      color: #FFFFFF !important;

      .sidebar__nav-icon {
        color: #FFFFFF;
      }
    }
  }

  &__nav-icon {
    font-size: 18px;
    flex-shrink: 0;
  }

  &__nav-label {
    font-size: 14px;
    font-weight: 500;
  }

  // 团队筛选树
  &__team-tree {
    flex: 1;
    padding: 0 12px;
    overflow-y: auto;
  }

  &__divider {
    height: 1px;
    background: #F1F5F9;
    margin: 8px 0 12px;
  }

  &__section-title {
    font-size: 12px;
    font-weight: 600;
    color: #A3AED0;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 0 12px;
    margin-bottom: 8px;
    display: block;
  }

  &__placeholder-text {
    font-size: 12px;
    color: #A3AED0;
    padding: 8px 12px;
  }

  // 底部收起按钮
  &__footer {
    padding: 12px;
    margin-top: auto;
  }

  &__toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 36px;
    border-radius: 10px;
    cursor: pointer;
    color: #A3AED0;
    transition: all 0.2s ease;

    &:hover {
      background: #F7F8FA;
      color: #6366F1;
    }
  }
}
</style>
