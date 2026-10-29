import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // API на том же origin, что и интерфейс: cookie сессии SameSite=Lax и CSRF-cookie
    // доступны фронтенду так же, как за nginx в Docker.
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY ?? 'http://127.0.0.1:8081', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // Не создаём worker на каждый файл: на Windows это приводит к тайм-аутам.
    maxWorkers: 4,
    // Полные пользовательские сценарии MUI + userEvent могут занимать больше 5 секунд.
    testTimeout: 10_000,
  },
})
