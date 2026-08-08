import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e',
  use: { baseURL: 'http://127.0.0.1:4321', trace: 'retain-on-failure' },
  webServer: {
    command:
      'npm run build && mkdir -p .e2e-root && ln -sfn ../dist .e2e-root/Solipsism && http-server .e2e-root -p 4321 -c-1',
    url: 'http://127.0.0.1:4321/Solipsism/',
    reuseExistingServer: true,
  },
});
