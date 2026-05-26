import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:18080'

  return {
    plugins: [
      vue(),
      Components({
        resolvers: [NaiveUiResolver()]
      }),
      viteCompression({
        ext: '.gz',
        algorithm: 'gzip',
        deleteOriginFile: false
      })
    ],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
        '/ws': {
          target: apiTarget,
          ws: true,
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('error', (err) => {
              console.warn('[Vite WS Proxy Error]:', err.message)
            })
            proxy.on('proxyReqWs', (_proxyReq, _req, socket) => {
              socket.on('error', (err) => {
                console.info('[Vite WS Socket Info]: Connection closed smoothly.', err.message)
              })
            })
          },
        },
      },
    },
    preview: {
      allowedHosts: ['moodcopilot.dpdns.org'],
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'ui-vendor': ['naive-ui'],
            'editor-vendor': ['vditor', 'marked', 'dompurify'],
            'utils-vendor': ['axios', 'cropperjs', '@vicons/ionicons5']
          }
        }
      }
    }
  }
})
