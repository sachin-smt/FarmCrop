import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  // DEV_API_PROXY_TARGET has no VITE_ prefix, so it is never bundled into client code.
  const env = loadEnv(mode, process.cwd(), '');
  const proxyTarget = env.DEV_API_PROXY_TARGET || 'http://localhost:8080';

  return {
    plugins: [react(), tailwindcss()],
    server: {
      // Same-origin /api calls in development: no CORS preflights and first-party refresh cookies.
      proxy: {
        '/api': { target: proxyTarget, changeOrigin: false },
      },
    },
  };
});
