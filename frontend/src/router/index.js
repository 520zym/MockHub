import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/apis'
      },
      {
        path: 'apis',
        name: 'ApiList',
        component: () => import('@/views/ApiList.vue'),
        meta: { title: '接口管理' }
      },
      {
        path: 'apis/new',
        name: 'ApiNew',
        component: () => import('@/views/ApiEdit.vue'),
        meta: { title: '新建接口' }
      },
      {
        path: 'apis/:id/edit',
        name: 'ApiEdit',
        component: () => import('@/views/ApiEdit.vue'),
        meta: { title: '编辑接口' }
      },
      {
        path: 'teams',
        name: 'TeamManage',
        component: () => import('@/views/TeamManage.vue'),
        meta: { title: '团队管理', requiresSuperAdmin: true }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/UserManage.vue'),
        meta: { title: '用户管理', requiresSuperAdmin: true }
      },
      {
        path: 'logs/operation',
        name: 'OperationLog',
        component: () => import('@/views/LogView.vue'),
        meta: { title: '操作日志' }
      },
      {
        path: 'logs/request',
        name: 'RequestLog',
        component: () => import('@/views/LogView.vue'),
        meta: { title: '请求日志' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '全局设置', requiresSuperAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  // 需要登录但未登录 → 跳转登录页
  if (to.meta.requiresAuth !== false && !to.matched.some(r => r.meta.requiresAuth === false)) {
    // 检查路由本身或其父路由是否需要认证
    const needsAuth = to.matched.some(r => r.meta.requiresAuth === true)
    if (needsAuth && !token) {
      return next('/login')
    }
  }

  // 已登录访问登录页 → 跳转首页
  if (to.path === '/login' && token) {
    return next('/')
  }

  // 超管页面权限校验
  if (to.meta.requiresSuperAdmin) {
    try {
      const userStr = localStorage.getItem('user')
      if (userStr) {
        const user = JSON.parse(userStr)
        if (user.globalRole !== 'SUPER_ADMIN') {
          return next('/')
        }
      }
    } catch (e) {
      // JSON 解析失败，忽略
    }
  }

  next()
})

export default router
