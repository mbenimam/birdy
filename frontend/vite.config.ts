import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Where the dev server forwards /api and /ws; override with BIRDY_BACKEND_URL.
const backendUrl = process.env.BIRDY_BACKEND_URL ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
      '/ws': { target: backendUrl, changeOrigin: true, ws: true },
    },
  },
  build: {
    // Never inline assets as data: URIs, so the production CSP can stay at 'self' for fonts and images.
    assetsInlineLimit: 0,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
