import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 统一经 Gateway :8080 按路径转发到各 Java 服务
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/files': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
})
