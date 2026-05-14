import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './styles.css'

cleanupLegacyPwa()

const pinia = createPinia()
const app = createApp(App)

app.use(pinia).use(router).use(naive)

const auth = useAuthStore(pinia)
if (auth.isAuthenticated) {
  void auth.fetchProfile()
}

app.mount('#app')

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
