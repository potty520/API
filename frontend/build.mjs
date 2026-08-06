import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { build } from 'vite'

await build({
  configFile: false,
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    sourcemap: false
  }
})
