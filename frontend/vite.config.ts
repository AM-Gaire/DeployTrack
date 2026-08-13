import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Proxy /api to the Spring Boot backend so the browser sees a single
    // origin during development. Without this the frontend on :5173 calling
    // :8080 is a cross-origin request, which would mean configuring CORS on
    // the backend purely to work around a dev-only split.
    //
    // It also mirrors production, where Nginx serves the built assets and
    // forwards /api to the API — same-origin in both, so no code changes
    // between environments.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
