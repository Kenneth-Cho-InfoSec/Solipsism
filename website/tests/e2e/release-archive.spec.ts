import { expect, test } from '@playwright/test';

test('release archive loads its project-base manifest and stays within a phone viewport', async ({
  page,
}) => {
  let manifestRequested = false;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/Solipsism/release-manifest.json', (route) => {
    manifestRequested = true;
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        schemaVersion: 1,
        generatedAt: '2026-10-31T00:00:00.000Z',
        repository: 'Kenneth-Cho-InfoSec/Solipsism',
        releases: [
          {
            id: 94,
            name: 'Solipsism Browser 9.4.0',
            tag_name: 'v9.4.0',
            body: `Release signature: ${'a'.repeat(64)}`,
            html_url:
              'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/tag/v9.4.0',
            published_at: '2026-10-24T00:00:00.000Z',
            created_at: '2026-10-24T00:00:00.000Z',
            draft: false,
            prerelease: false,
            author: {
              login: 'Kenneth-Cho-InfoSec',
              avatar_url: 'https://avatars.githubusercontent.com/u/1',
              html_url: 'https://github.com/Kenneth-Cho-InfoSec',
            },
            assets: [],
          },
        ],
      }),
    });
  });
  await page.route('https://api.github.com/**', (route) => route.abort());

  await page.goto('/Solipsism/releases/');
  await expect(page.locator('.release-card h3')).toContainText(
    'Solipsism Browser 9.4.0',
  );
  expect(manifestRequested).toBe(true);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
});
