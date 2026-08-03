import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '../api/gateway'

// 路由表：登录页公开，管理页需先登录
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/services' },
      {
        path: 'services',
        name: 'Services',
        component: () => import('../views/Services.vue'),
        meta: { title: '服务管理' },
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '网关状态' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫：未登录访问管理页时跳转登录
router.beforeEach((to, _from, next) => {
  const isPublic = to.matched.some((record) => record.meta.public)
  const token = getToken()

  if (!isPublic && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 已登录用户访问登录页，直接进入服务管理
  if (to.name === 'Login' && token) {
    next({ name: 'Services' })
    return
  }

  next()
})

export default router
