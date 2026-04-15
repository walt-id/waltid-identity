import { defineConfig } from 'vite';

const allowedHosts = (process.env.ALLOWED_HOSTS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);

export default defineConfig({
  server: {
    allowedHosts: [...allowedHosts, 'digital-credentials.walt.id'],
    proxy: {
      /*
      '/api.json': {
        target: 'http://192.168.x.y:7003',
      },
      '/verification-session/': {
        target: 'http://192.168.x.y:7003',
      },
      */
      '/verifier-api': {
        target: 'https://waltid.enterprise.test.waltid.cloud',
        changeOrigin: true,
        secure: true,
        rewrite: (path) => path.replace(/^\/verifier-api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
            proxyReq.removeHeader('referer');
          });
        }
      }
    }
  }
});
