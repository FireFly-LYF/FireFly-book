import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api/user': { target: 'http://127.0.0.1:9001', changeOrigin: true },
      '/api/note': { target: 'http://127.0.0.1:9002', changeOrigin: true },
      '/api/media': { target: 'http://127.0.0.1:9003', changeOrigin: true },
      '/files': { target: 'http://127.0.0.1:9003', changeOrigin: true },
    },
  },
})
