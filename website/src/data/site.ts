export const SITE = {
  name: 'Solipsism Browser',
  tagline: 'Android browsing within reach',
  description:
    'A privacy-focused Android browser built on WebView, with a rail-first interface that keeps tabs, search and controls within one-handed reach. Antares is an optional experimental core.',
  repository: 'https://github.com/Kenneth-Cho-InfoSec/Solipsism',
  releases: 'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases',
  latestRelease:
    'https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/latest',
  issues: 'https://github.com/Kenneth-Cho-InfoSec/Solipsism/issues',
  licence: 'https://github.com/Kenneth-Cho-InfoSec/Solipsism/blob/main/LICENSE',
  antaresNotes:
    'https://github.com/Kenneth-Cho-InfoSec/Solipsism/blob/main/docs/ANTARES_ENGINE.md',
  packageId: 'com.krystelligence.solipsism',
  minAndroid: 'Android 8.0 (API 26)',
  licenceName: 'MPL 2.0',
} as const;

export interface NavItem {
  label: string;
  path: string;
}

export const NAV: NavItem[] = [
  { label: 'Features', path: '/features/' },
  { label: 'Docs', path: '/docs/' },
  { label: 'Get started', path: '/guides/getting-started/' },
  { label: 'Compare', path: '/compare/' },
  { label: 'Releases', path: '/releases/' },
];

export const FOOTER_LINKS: NavItem[] = [
  { label: 'Features', path: '/features/' },
  { label: 'Documentation', path: '/docs/' },
  { label: 'Get started', path: '/guides/getting-started/' },
  { label: 'Compare browsers', path: '/compare/' },
  { label: 'Releases', path: '/releases/' },
  { label: 'Community', path: '/community/' },
];

/** Prefix an internal path with the deployed base path. */
export function withBase(base: string, path: string): string {
  const root = base.replace(/\/$/, '');
  if (path === '/') return `${root}/`;
  return `${root}${path}`;
}
