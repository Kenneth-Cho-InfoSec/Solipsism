import { copyFile } from 'node:fs/promises';
import { URL, fileURLToPath } from 'node:url';

const indexPath = fileURLToPath(
  new URL('../dist/sitemap-index.xml', import.meta.url),
);
const sitemapPath = fileURLToPath(
  new URL('../dist/sitemap.xml', import.meta.url),
);

await copyFile(indexPath, sitemapPath);
