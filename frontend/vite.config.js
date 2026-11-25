import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// npm run dev   -> http://localhost:5173, запросы /api проксируются на backend :8080
// npm run build -> сборка кладётся в static backend и отдаётся самим Spring Boot
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
