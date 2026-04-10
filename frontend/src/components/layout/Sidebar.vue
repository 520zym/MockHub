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

    <!-- 团队筛选树（仅在接口管理页显示） -->
    <div
      v-if="showTeamTree && !appStore.sidebarCollapsed"
      class="sidebar__team-tree"
    >
      <div class="sidebar__divider" />
      <span class="sidebar__section-title">团队筛选</span>

      <!-- "所有接口"节点 -->
      <div
        class="tree-node"
        :class="{ 'tree-node--active': !appStore.currentTeamId }"
        @click="handleSelectAll"
      >
        <span class="tree-node__label">所有接口</span>
        <span class="tree-node__count">{{ totalApiCount }}</span>
      </div>

      <!-- 各团队节点 -->
      <div v-for="team in appStore.teams" :key="team.id">
        <div
          class="tree-node tree-node--team"
          :class="{ 'tree-node--active': appStore.currentTeamId === team.id }"
          @click="handleSelectTeam(team)"
        >
          <TeamTag :identifier="team.identifier" :color="team.color" />
          <span class="tree-node__label">{{ team.name }}</span>
          <span class="tree-node__count">{{ team.apiCount || 0 }}</span>
        </div>
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
/**
 * 侧边栏组件
 * 包含全局导航菜单和团队筛选树（仅接口管理页显示）
 * 团队筛选树支持：所有接口 → 团队 的二级树结构
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import TeamTag from '@/components/TeamTag.vue'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

// 导航菜单配置
const navItems = [
  { path: '/apis', label: '接口管理', icon: 'Connection', requiresSuperAdmin: false },
  { path: '/teams', label: '团队管理', icon: 'OfficeBuilding', requiresSuperAdmin: true },
  { path: '/users', label: '用户管理', icon: 'User', requiresSuperAdmin: true },
  { path: '/logs/operation', label: '日志', icon: 'Document', requiresSuperAdmin: false },
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

// --- 团队筛选树逻辑 ---

// 所有接口总数
const totalApiCount = computed(() => {
  return appStore.teams.reduce((sum, t) => sum + (t.apiCount || 0), 0)
})

// 选中"所有接口"
function handleSelectAll() {
  appStore.clearFilter()
}

// 选中团队节点
function handleSelectTeam(team) {
  appStore.setFilter(team.id)
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

// 树节点样式
.tree-node {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
  gap: 6px;
  user-select: none;

  &:hover {
    background: #F7F8FA;
  }

  // 选中态：主色浅背景 + 主色文字 + 左侧竖条
  &--active {
    background: #E0E7FF !important;
    color: #6366F1 !important;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 6px;
      bottom: 6px;
      width: 3px;
      background: #6366F1;
      border-radius: 2px;
    }

    .tree-node__label {
      color: #6366F1;
      font-weight: 600;
    }

    .tree-node__count {
      color: #6366F1;
    }
  }

  &__label {
    flex: 1;
    font-size: 13px;
    color: #4A5568;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &--muted {
      color: #A3AED0;
      font-style: italic;
    }
  }

  &__count {
    font-size: 12px;
    color: #A3AED0;
    flex-shrink: 0;
  }
}

</style>
