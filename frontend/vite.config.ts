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
      // 不再按库做 manualChunks：按库分包会把「任意页面用到的同库模块」塞进同一个
      // chunk，入口只要碰其中一个，就会把整包（编辑器、日期选择器、数据表格…）预加载。
      // 交给 Rollup 按动态 import 的自然边界切分，编辑器只随 /write 下载。
      chunkSizeWarningLimit: 900
    }
  }
})
