import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:3100',
      '/demo-api': 'http://127.0.0.1:3100'
    }
  }
})
