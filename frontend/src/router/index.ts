import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../pages/HomePage.vue'),
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
      path: '/summaries',
      name: 'summaries',
      component: () => import('../pages/SummaryLibraryPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/following',
      name: 'following',
      component: () => import('../pages/FollowingPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/weekly-report',
      name: 'weekly-report',
      component: () => import('../pages/WeeklyReportPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/diary/:id',
      name: 'diary-detail',
      component: () => import('../pages/DiaryDetailPage.vue'),
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
