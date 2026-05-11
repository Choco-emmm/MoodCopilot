import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'square',
      component: () => import('../pages/SquarePage.vue'),
      meta: { requiresAuth: true },
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
      path: '/chat',
      name: 'chat',
      component: () => import('../pages/ChatPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/report',
      name: 'report',
      component: () => import('../pages/ReportPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/following',
      name: 'following',
      component: () => import('../pages/FollowingPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/diary/:id',
      name: 'diary-detail',
      component: () => import('../pages/DiaryDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../pages/SettingsPage.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
