import { defineConfig } from 'vitest/config'

// 独立于 vite.config.ts：那里挂着 uni 插件，会让 vitest 去解析 vue/compiler-sfc 而失败。
// 单测只覆盖不依赖 uni 运行时的纯模块。
export default defineConfig({
  test: {
    include: ['src/**/__tests__/**/*.test.ts'],
    environment: 'node',
  },
})
