import fs from 'node:fs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// Single source of truth: the Maven project version in the repository root pom.xml.
const pom = fs.readFileSync(path.resolve(__dirname, '../pom.xml'), 'utf8')
const versionMatch = pom.match(/<artifactId>mockhub<\/artifactId>\s*<version>([^<]+)<\/version>/)
const appVersion = versionMatch ? versionMatch[1].trim() : 'dev'

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(appVersion) },
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
