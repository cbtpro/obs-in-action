import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Vite 配置：
// - HTTPS dev server：页面在 https 下加载，避免七牛 https 图片被浏览器 mixed-content 拦截
// - dev 代理 /api 到后端 8080，host:true 便于真机测试（iOS Safari）
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    https: {
      key: readFileSync(resolve(__dirname, '.ssl/key.pem')),
      cert: readFileSync(resolve(__dirname, '.ssl/cert.pem')),
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/task-api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/task-api/, ''),
      },
    },
  },
});
