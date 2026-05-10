import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import './styles.css'

// 注册 Service Worker（PWA 离线支持）
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {})
}

createApp(App).use(createPinia()).use(router).use(naive).mount('#app')
