import { mkdir, writeFile } from 'node:fs/promises';

const api = 'https://api.github.com/repos/Kenneth-Cho-InfoSec/Solipsism';
const headers = {
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'solipsism-website-release-sync',
};

const releases = [];
for (let page = 1; ; page += 1) {
  const response = await fetch(
    `${api}/releases?per_page=100&page=${page}`,
    { headers },
  );
  if (!response.ok) throw new Error(`GitHub release sync failed: ${response.status}`);
  const pageReleases = await response.json();
  releases.push(...pageReleases.filter((release) => !release.draft));
  const link = response.headers.get('link') ?? '';
  if (!/rel="next"/.test(link)) break;
}

await mkdir('public', { recursive: true });
await writeFile(
  'public/release-manifest.json',
  JSON.stringify(
    {
      schemaVersion: 1,
      generatedAt: new Date().toISOString(),
      repository: 'Kenneth-Cho-InfoSec/Solipsism',
      releases,
    },
    null,
    2,
  ) + '\n',
);
