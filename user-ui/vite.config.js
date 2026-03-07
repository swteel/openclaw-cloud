import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: path.resolve(__dirname, '../portal/src/main/resources/static'),
    emptyOutDir: false, // don't wipe admin/ subfolder
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8081',
      '/portal': 'http://localhost:8081',
      '/app': 'http://localhost:8081',
    }
  }
})
