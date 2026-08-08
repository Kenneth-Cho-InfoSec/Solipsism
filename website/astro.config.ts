import sitemap from '@astrojs/sitemap';
import { defineConfig } from 'astro/config';

export default defineConfig({
  site:
    process.env.PUBLIC_SITE_URL ??
    'https://kenneth-cho-infosec.github.io/Solipsism/',
  base: process.env.PUBLIC_BASE_PATH ?? '/Solipsism',
  integrations: [sitemap()],
  build: { format: 'directory' },
});
