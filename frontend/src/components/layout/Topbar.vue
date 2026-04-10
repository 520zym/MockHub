<template>
  <div class="topbar">
    <div class="topbar__left">
      <h1 class="topbar__title">{{ pageTitle }}</h1>
    </div>
    <div class="topbar__right">
      <div class="topbar__user">
        <el-avatar :size="32" class="topbar__avatar">
          {{ userInitial }}
        </el-avatar>
        <span class="topbar__username">{{ userStore.user?.displayName || userStore.user?.username }}</span>
      </div>
      <el-button text class="topbar__logout" @click="handleLogout">
        <el-icon><SwitchButton /></el-icon>
        <span>退出</span>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitle = computed(() => {
  return route.meta.title || 'MockHub'
})

const userInitial = computed(() => {
  const name = userStore.user?.displayName || userStore.user?.username || ''
  return name.charAt(0).toUpperCase()
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.topbar {
  height: 60px;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.03);
  z-index: 50;

  &__left {
    display: flex;
    align-items: center;
  }

  &__title {
    font-size: 20px;
    font-weight: 700;
    color: #1B2559;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__avatar {
    background: linear-gradient(135deg, #6366F1, #4F46E5);
    color: #FFFFFF;
    font-weight: 600;
  }

  &__username {
    font-size: 14px;
    font-weight: 500;
    color: #1B2559;
  }

  &__logout {
    color: #A3AED0;

    &:hover {
      color: #EF4444;
    }
  }
}
</style>
