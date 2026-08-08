import { describe, expect, it } from 'vitest';
import {
  findApkAssets,
  formatFileSize,
  getPreferredApkAsset,
  type GitHubRelease,
} from '../../src/lib/releases';

function release(assetNames: string[]): GitHubRelease {
  return {
    id: 1,
    name: 'Test',
    tag_name: 'v1',
    body: '',
    html_url:
      'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/tag/v1',
    published_at: '2026-01-01T00:00:00Z',
    created_at: '2026-01-01T00:00:00Z',
    draft: false,
    prerelease: false,
    author: {
      login: 'owner',
      avatar_url: 'https://avatars.githubusercontent.com/u/1',
      html_url: 'https://github.com/owner',
    },
    assets: assetNames.map((name, id) => ({
      id,
      name,
      size: 1_500_000,
      download_count: 2,
      browser_download_url: `https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/download/v1/${name}`,
      content_type: 'application/octet-stream',
      updated_at: '2026-01-01T00:00:00Z',
    })),
  };
}

describe('release utilities', () => {
  it('selects APK files case-insensitively and rejects source archives', () => {
    expect(
      findApkAssets(release(['source.zip', 'arm64.APK', 'notes.txt'])).map(
        ({ name }) => name,
      ),
    ).toEqual(['arm64.APK']);
  });
  it('prefers a universal or release APK', () => {
    expect(
      getPreferredApkAsset(release(['app-arm64.apk', 'app-universal.apk']))
        ?.name,
    ).toBe('app-universal.apk');
    expect(getPreferredApkAsset(release(['source.zip']))).toBeNull();
  });
  it('formats file sizes for people', () => {
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(1_048_576)).toBe('1.00 MB');
  });
});
