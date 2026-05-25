import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { logError } from './utils/logger'
import './styles.css'

cleanupLegacyPwa()

const pinia = createPinia()
const app = createApp(App)

// 全局错误处理器
app.config.errorHandler = (err, instance, info) => {
  logError('vue', `组件异常 [${info}]`, err)
}
window.onerror = (msg, _src, _line, _col, err) => {
  logError('window', String(msg), err ?? undefined)
}
window.addEventListener('unhandledrejection', (e) => {
  logError('promise', '未处理的 Promise rejection', e.reason)
})

app.use(pinia).use(router)

const auth = useAuthStore(pinia)
if (auth.isAuthenticated) {
  void auth.fetchProfile()
}

app.mount('#app')

// 移动端调试台（仅开发环境）
if (import.meta.env.DEV) {
  import('vconsole').then(({ default: VConsole }) => {
    new VConsole({ theme: 'light' })
  })
}

function cleanupLegacyPwa() {
  if (!('serviceWorker' in navigator)) return

  window.addEventListener('load', () => {
    void (async () => {
      try {
        const registrations = await navigator.serviceWorker.getRegistrations()
        await Promise.all(registrations.map((registration) => registration.unregister()))

        if ('caches' in window) {
          const cacheNames = await caches.keys()
          await Promise.all(cacheNames.map((name) => caches.delete(name)))
        }
      } catch {
        // Ignore cleanup failures; the app should still load normally.
      }
    })()
  }, { once: true })
}
