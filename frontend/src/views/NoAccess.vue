<template>
  <section class="no-access app-surface">
    <div class="no-access__mark"><FilledIcon name="shield" :size="28" /></div>
    <h1>当前账号没有此页面的访问权限</h1>
    <p>你可能已被移出团队、角色被调整，或打开了其他团队的链接。链接本身不会授予资源访问权。</p>
    <div class="no-access__actions"><el-button type="primary" @click="router.push('/apis')">返回我的工作区</el-button><el-button @click="router.push('/account?tab=teams')">查看我的团队</el-button></div>
    <div class="no-access__identity"><span>当前账号</span><b>{{ userStore.user?.username }}</b><span>全局身份</span><b>{{ userStore.isSuperAdmin ? '超级管理员' : '普通账号' }}</b></div>
    <p class="no-access__hint">需要访问？请联系目标团队管理员添加成员；平台账号或团队设置问题请联系系统管理员。</p>
  </section>
</template>
<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import FilledIcon from '@/components/FilledIcon.vue'
const router = useRouter(), userStore = useUserStore()
</script>
<style scoped>
.no-access { margin: 40px auto; max-width: 680px; padding: 42px 32px; text-align: center; }
.no-access__mark { width: 54px; height: 54px; display: grid; place-items: center; background: var(--app-bg-control); border-radius: 16px; color: var(--app-text-secondary); margin: 0 auto 20px; }
.no-access h1 { font-size: 22px; font-weight: 600; margin: 0 0 12px; } .no-access p { max-width: 480px; margin: 0 auto 20px; color: var(--app-text-secondary); font-size: 13px; line-height: 1.8; }
.no-access__actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-bottom: 24px; } .no-access__actions .el-button { margin: 0; }
.no-access__identity { display: grid; grid-template-columns: auto 1fr; gap: 10px 22px; padding: 16px; border-radius: 12px; background: var(--app-bg-control); text-align: left; font-size: 12px; max-width: 400px; margin: auto; } .no-access__identity span { color: var(--app-text-secondary); } .no-access__identity b { font-weight: 500; } .no-access .no-access__hint { font-size: 11px; margin-top: 20px; margin-bottom: 0; }
@media(max-width: 600px) { .no-access { margin: 0; padding: 28px 18px; } .no-access h1 { font-size: 18px; } }
</style>
