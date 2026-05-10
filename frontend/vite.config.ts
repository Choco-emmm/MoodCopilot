import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:18080'

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      allowedHosts: ['moodcopilot.dpdns.org', '.dpdns.org'],
      proxy: {
        '/api': apiTarget,
      },
    },
    build: {
      chunkSizeWarningLimit: 500,
      rollupOptions: {
        output: {
          manualChunks: {
            'naive-ui': ['naive-ui'],
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
          },
        },
      },
    },
  }
})
