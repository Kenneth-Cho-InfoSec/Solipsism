import { expect, test } from '@playwright/test';

test('latest APK CTA uses the API-provided asset URL', async ({ page }) => {
  const apkUrl =
    'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/download/v9.9.9/solipsism-universal.apk';
  await page.route(
    'https://api.github.com/repos/Kenneth-Cho-InfoSec/Solipsism/releases/latest',
    (route) =>
      route.fulfill({
        json: {
          id: 99,
          name: 'Solipsism 9.9.9',
          tag_name: 'v9.9.9',
          body: '## Safe notes',
          html_url:
            'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/tag/v9.9.9',
          published_at: '2026-08-01T00:00:00Z',
          created_at: '2026-08-01T00:00:00Z',
          draft: false,
          prerelease: false,
          author: {
            login: 'Kenneth-Cho-InfoSec',
            avatar_url: 'https://avatars.githubusercontent.com/u/1',
            html_url: 'https://github.com/Kenneth-Cho-InfoSec',
          },
          assets: [
            {
              id: 1,
              name: 'solipsism-universal.apk',
              size: 12345678,
              download_count: 10,
              browser_download_url: apkUrl,
              content_type: 'application/vnd.android.package-archive',
              updated_at: '2026-08-01T00:00:00Z',
            },
          ],
        },
      }),
  );
  await page.goto('/Solipsism/');
  const cta = page.locator('.hero [data-latest-download]');
  await expect(cta).toHaveAttribute('href', apkUrl);
  await expect(page.locator('[data-latest-version]')).toHaveText('v9.9.9');
});
