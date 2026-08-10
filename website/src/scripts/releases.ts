import {
  fetchAllReleases,
  fetchLatestRelease,
  fetchReleaseManifest,
  findReleaseByTag,
  findApkAssets,
  formatFileSize,
  formatReleaseDate,
  getPreferredApkAsset,
  type GitHubRelease,
  type GitHubReleaseAsset,
  withLocalCache,
} from '../lib/releases';
import { renderReleaseMarkdown } from '../lib/markdown';

const external = ' target="_blank" rel="noopener noreferrer"';

function escapeHtml(value: string): string {
  return value.replace(
    /[&<>'"]/g,
    (character) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[
        character
      ] ?? character,
  );
}

function assetMarkup(asset: GitHubReleaseAsset): string {
  const apk = /\.apk$/i.test(asset.name);
  return `<li class="asset-row"><div><strong>${escapeHtml(asset.name)}</strong><span>${formatFileSize(asset.size)} · ${asset.download_count.toLocaleString('en-GB')} downloads</span></div><a class="button ${apk ? '' : 'button-secondary'} button-small" href="${escapeHtml(asset.browser_download_url)}"${external} download>${apk ? 'Download APK' : 'Download asset'}</a></li>`;
}

async function releaseCard(
  release: GitHubRelease,
  latest = false,
): Promise<string> {
  const apks = findApkAssets(release);
  const body = await renderReleaseMarkdown(release.body);
  const anchor = `release-${release.tag_name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
  return `<article class="release-card" id="${anchor}">
    <header class="release-card-header"><div><div class="badges">${latest ? '<span class="badge">Latest release</span>' : ''}${release.prerelease ? '<span class="badge badge-warn">Prerelease</span>' : ''}</div><h3><a href="#${anchor}">${escapeHtml(release.name || release.tag_name)}</a></h3><p><code>${escapeHtml(release.tag_name)}</code> · <time datetime="${escapeHtml(release.published_at || release.created_at)}">${formatReleaseDate(release.published_at || release.created_at)}</time></p></div><a class="text-link" href="${escapeHtml(release.html_url)}"${external}>View on GitHub ↗</a></header>
    <div class="release-notes">${body}</div>
    <div class="assets"><h4>Release assets</h4>${release.assets.length ? `<ul>${release.assets.map(assetMarkup).join('')}</ul>` : '<p class="notice">No assets are attached to this release.</p>'}${apks.length ? '' : '<p class="no-apk">No APK attached to this release</p>'}</div>
  </article>`;
}

function updateGlobalDownload(release: GitHubRelease): void {
  const apks = findApkAssets(release);
  const preferred = getPreferredApkAsset(release);
  document
    .querySelectorAll<HTMLAnchorElement>('[data-latest-download]')
    .forEach((link) => {
      if (preferred) {
        link.href = preferred.browser_download_url;
        link.removeAttribute('aria-disabled');
        link.textContent = 'Download latest APK';
      } else {
        link.href = release.html_url;
        link.setAttribute('aria-disabled', 'true');
        link.textContent = 'APK unavailable';
      }
    });
  document
    .querySelectorAll<HTMLElement>('[data-latest-version]')
    .forEach((element) => {
      element.textContent = release.tag_name;
    });
  document
    .querySelectorAll<HTMLElement>('[data-latest-meta]')
    .forEach((element) => {
      element.textContent = preferred
        ? `${release.tag_name} · ${formatFileSize(preferred.size)}`
        : `${release.tag_name} · No APK attached`;
    });
  const chooser = document.querySelector<HTMLElement>(
    '[data-architecture-chooser]',
  );
  if (chooser && apks.length > 1) {
    chooser.hidden = false;
    chooser.innerHTML = `<span>Choose an APK</span>${apks.map((asset) => `<a href="${escapeHtml(asset.browser_download_url)}"${external}>${escapeHtml(asset.name)} <small>${formatFileSize(asset.size)}</small></a>`).join('')}`;
  }
}

async function loadLatest(root: HTMLElement): Promise<void> {
  const release = await withLocalCache('latest-v2', async () => {
    try {
      const manifest = await fetchReleaseManifest();
      return (
        manifest.releases.find((item) => !item.prerelease && !item.draft) ??
        (await fetchLatestRelease())
      );
    } catch {
      return fetchLatestRelease();
    }
  });
  updateGlobalDownload(release);
  const list = root.querySelector<HTMLElement>('[data-release-list]');
  if (list) list.innerHTML = await releaseCard(release, true);
}

async function loadArchive(root: HTMLElement): Promise<void> {
  const all = await withLocalCache('all-releases-v2', async () => {
    try {
      return (await fetchReleaseManifest()).releases;
    } catch {
      return fetchAllReleases();
    }
  });
  const list = root.querySelector<HTMLElement>('[data-release-list]');
  const prerelease = root.querySelector<HTMLInputElement>('[data-prerelease]');
  const sort = root.querySelector<HTMLSelectElement>('[data-sort]');
  const render = async () => {
    const releases = all
      .filter((release) => prerelease?.checked || !release.prerelease)
      .sort((a, b) => {
        const difference =
          new Date(b.published_at || b.created_at).getTime() -
          new Date(a.published_at || a.created_at).getTime();
        return sort?.value === 'oldest' ? -difference : difference;
      });
    if (list)
      list.innerHTML = releases.length
        ? (
            await Promise.all(
              releases.map((release, index) =>
                releaseCard(release, index === 0 && !release.prerelease),
              ),
            )
          ).join('')
        : '<p class="notice">No releases match these filters.</p>';
    const requestedTag = new URLSearchParams(location.search).get('release');
    const requestedAnchor = location.hash.slice(1);
    const requested = requestedTag
      ? findReleaseByTag(releases, requestedTag)
      : null;
    const target = requested
      ? `release-${requested.tag_name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
      : requestedAnchor;
    if (target) {
      document.getElementById(target)?.scrollIntoView({ block: 'start' });
    }
    const stable = all.find((release) => !release.prerelease && !release.draft);
    if (stable) updateGlobalDownload(stable);
  };
  prerelease?.addEventListener('change', render);
  sort?.addEventListener('change', render);
  await render();
}

async function initialise(root: HTMLElement): Promise<void> {
  const status = root.querySelector<HTMLElement>('[data-release-status]');
  try {
    await (root.dataset.releaseApp === 'latest'
      ? loadLatest(root)
      : loadArchive(root));
    status?.remove();
  } catch (error) {
    if (status)
      status.innerHTML = `<div class="notice"><strong>Live release data is temporarily unavailable.</strong><p>${escapeHtml(error instanceof Error ? error.message : 'Please try again shortly.')}</p><a href="https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases"${external}>Browse releases on GitHub ↗</a></div>`;
  }
}

document
  .querySelectorAll<HTMLElement>('[data-release-app]')
  .forEach((root) => void initialise(root));
