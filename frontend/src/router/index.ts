import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { isUsableToken, clearAuthStorage } from '../utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Square',
      component: () => import('../pages/SquarePage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/write',
      name: 'write',
      component: () => import('../pages/WritePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../pages/LoginPage.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../pages/RegisterPage.vue'),
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('../pages/ResetPasswordPage.vue'),
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('../pages/ChatPage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/report',
      name: 'report',
      component: () => import('../pages/ReportPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/following',
      name: 'Following',
      component: () => import('../pages/FollowingPage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/notifications',
      name: 'Notifications',
      component: () => import('../pages/NotificationPage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/diary/:id',
      name: 'diary-detail',
      component: () => import('../pages/DiaryDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/ai-memory',
      name: 'ai-memory',
      component: () => import('../pages/AiMemoryCenter.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/profile/:userId',
      name: 'profile',
      component: () => import('../pages/UserProfilePage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/search',
      redirect: () => {
        const auth = useAuthStore()
        return auth.userId ? `/profile/${auth.userId}` : '/'
      }
    },
    {
      path: '/task-center',
      name: 'task-center',
      component: () => import('../pages/TaskCenterPage.vue'),
      meta: { requiresAuth: true, keepAlive: true },
    },
    {
      path: '/support',
      name: 'support',
      component: () => import('../pages/SupportPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/collections/:id',
      name: 'collection-detail',
      component: () => import('../views/CollectionDetail.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('../pages/AdminUsersPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true, keepAlive: true },
    },
    {
      path: '/admin/reports',
      name: 'admin-reports',
      component: () => import('../pages/AdminReportsPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true, keepAlive: true },
    },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !isUsableToken(token)) {
    clearAuthStorage()
    next('/login')
  } else if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
    next('/')
  } else {
    next()
  }
})

router.onError((error) => {
  const msg = error?.message || ''
  const isChunkError = msg.includes('Failed to fetch dynamically imported module')
    || msg.includes('Importing a module script failed')
  if (!isChunkError) return

  const url = new URL(window.location.href)
  if (url.searchParams.has('t')) return

  url.searchParams.set('t', Date.now().toString())
  window.location.href = url.href
})

export default router
