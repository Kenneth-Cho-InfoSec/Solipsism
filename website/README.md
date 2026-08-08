# Solipsism Browser website

The official product site is a static Astro application. GitHub Releases remains the sole source of truth: release names, notes, dates, assets and download URLs are fetched in the visitor's browser and validated before display.

## Local development

Requires Node.js 22 or newer.

```bash
cd website
npm ci
npm run dev
```

Quality checks:

```bash
npm run format:check
npm run lint
npm run typecheck
npm test
npm run test:e2e
npm run build
```

Install Playwright's Chromium browser once before the end-to-end test with `npx playwright install chromium`.

## Release refresh strategy

The site requests GitHub's unauthenticated Releases API at runtime. Valid responses are cached in `localStorage` for five minutes; an expired cached response is retained as a stale fallback if GitHub is unavailable or rate-limited. The latest endpoint supplies the primary download. The archive follows GitHub pagination links until every page has loaded, discards drafts, and lets visitors include prereleases.

No token is shipped to the browser. Release Markdown is treated as untrusted, rendered with GitHub-Flavoured Markdown and sanitised with DOMPurify. Asset download hosts are restricted to `github.com` and `objects.githubusercontent.com`.

The root `release-site.yml` workflow redeploys GitHub Pages after relevant release lifecycle events, as well as changes to the website. Because data is loaded at runtime, edited notes and replaced assets can appear within the five-minute browser cache window even before a deployment finishes.

## Production and deployment

`npm run build` writes the static site to `website/dist`. The included workflow publishes that directory to GitHub Pages. In repository settings, set **Pages → Source** to **GitHub Actions**.

For a custom host, deploy `dist` to any static host and set:

- `PUBLIC_SITE_URL`: canonical public URL, ending in `/`.
- `PUBLIC_BASE_PATH`: pathname prefix (`/` on a custom domain; `/Solipsism` on project Pages).

See `.env.example`. `GITHUB_TOKEN` is documented only as a possible future build/server secret and is not read by the current browser application. Never prefix secrets with `PUBLIC_`.

The site includes canonical and social metadata, `SoftwareApplication` structured data, a sitemap, and `robots.txt`. It does not include analytics or third-party scripts.
