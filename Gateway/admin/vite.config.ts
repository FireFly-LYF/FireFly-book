import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // 开发时代理到本地网关，避免浏览器跨域
    proxy: {
      '/gateway': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
})
