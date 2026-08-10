import { z } from 'zod';

const REPOSITORY = 'Kenneth-Cho-InfoSec/Solipsism';
export const GITHUB_API = `https://api.github.com/repos/${REPOSITORY}`;
const CACHE_TTL = 5 * 60 * 1000;

export interface ReleaseAuthor {
  login: string;
  avatar_url: string;
  html_url: string;
}

export interface GitHubReleaseAsset {
  id: number;
  name: string;
  size: number;
  download_count: number;
  browser_download_url: string;
  content_type: string;
  updated_at: string;
}

export interface GitHubRelease {
  id: number;
  name: string | null;
  tag_name: string;
  body: string | null;
  html_url: string;
  published_at: string | null;
  created_at: string;
  draft: boolean;
  prerelease: boolean;
  author: ReleaseAuthor;
  assets: GitHubReleaseAsset[];
}

export interface PaginationMetadata {
  page: number;
  perPage: number;
  hasNextPage: boolean;
  nextPage: number | null;
}

export interface PaginatedReleases {
  releases: GitHubRelease[];
  pagination: PaginationMetadata;
}

export interface ReleaseManifest {
  schemaVersion: number;
  generatedAt: string;
  repository: string;
  releases: GitHubRelease[];
}

const authorSchema = z.object({
  login: z.string(),
  avatar_url: z.url(),
  html_url: z.url(),
});

const assetSchema = z.object({
  id: z.number(),
  name: z.string(),
  size: z.number().nonnegative(),
  download_count: z.number().nonnegative(),
  browser_download_url: z
    .url()
    .refine((url) => isTrustedDownloadUrl(url), 'Untrusted asset host'),
  content_type: z.string(),
  updated_at: z.string(),
});

export const releaseSchema = z.object({
  id: z.number(),
  name: z.string().nullable(),
  tag_name: z.string(),
  body: z.string().nullable(),
  html_url: z.url(),
  published_at: z.string().nullable(),
  created_at: z.string(),
  draft: z.boolean(),
  prerelease: z.boolean(),
  author: authorSchema,
  assets: z.array(assetSchema),
});

export function isTrustedDownloadUrl(value: string): boolean {
  try {
    const host = new URL(value).hostname;
    return host === 'github.com' || host === 'objects.githubusercontent.com';
  } catch {
    return false;
  }
}

export function findApkAssets(release: GitHubRelease): GitHubReleaseAsset[] {
  return release.assets.filter(
    (asset) =>
      /\.apk$/i.test(asset.name) &&
      isTrustedDownloadUrl(asset.browser_download_url),
  );
}

export function getPreferredApkAsset(
  release: GitHubRelease,
): GitHubReleaseAsset | null {
  const apks = findApkAssets(release);
  return (
    apks.find((asset) =>
      /(?:universal|release)(?:[-_.]|$)/i.test(asset.name),
    ) ??
    apks[0] ??
    null
  );
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = units[0];
  for (let index = 1; value >= 1024 && index < units.length; index += 1) {
    value /= 1024;
    unit = units[index];
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${unit}`;
}

export function formatReleaseDate(date: string | null): string {
  if (!date) return 'Date unavailable';
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(date));
}

function requestHeaders(): HeadersInit {
  return {
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };
}

async function githubFetch(path: string): Promise<Response> {
  const response = await fetch(`${GITHUB_API}${path}`, {
    headers: requestHeaders(),
  });
  if (!response.ok) {
    const remaining = response.headers.get('x-ratelimit-remaining');
    throw new Error(
      response.status === 403 && remaining === '0'
        ? 'GitHub’s public API rate limit has been reached. Please try again shortly.'
        : `GitHub API request failed (${response.status}).`,
    );
  }
  return response;
}

function parseLinkHeader(header: string | null): boolean {
  return header?.split(',').some((part) => /rel="next"/.test(part)) ?? false;
}

export async function fetchLatestRelease(): Promise<GitHubRelease> {
  const response = await githubFetch('/releases/latest');
  const release = releaseSchema.parse(await response.json());
  if (release.draft || release.prerelease)
    throw new Error('No stable public release is available.');
  return release;
}

export async function fetchReleaseManifest(): Promise<ReleaseManifest> {
  const response = await fetch(
    `${import.meta.env.BASE_URL}release-manifest.json`,
    { headers: requestHeaders() },
  );
  if (!response.ok)
    throw new Error(`Release manifest request failed (${response.status}).`);
  const value = await response.json();
  return z
    .object({
      schemaVersion: z.number(),
      generatedAt: z.string(),
      repository: z.string(),
      releases: z.array(releaseSchema),
    })
    .parse(value);
}

export function findReleaseByTag(
  releases: GitHubRelease[],
  tagName: string,
): GitHubRelease | null {
  return releases.find((release) => release.tag_name === tagName) ?? null;
}

export async function fetchReleasePage(
  page = 1,
  perPage = 100,
): Promise<PaginatedReleases> {
  const response = await githubFetch(
    `/releases?per_page=${perPage}&page=${page}`,
  );
  const releases = z
    .array(releaseSchema)
    .parse(await response.json())
    .filter((item) => !item.draft);
  const hasNextPage = parseLinkHeader(response.headers.get('link'));
  return {
    releases,
    pagination: {
      page,
      perPage,
      hasNextPage,
      nextPage: hasNextPage ? page + 1 : null,
    },
  };
}

export async function fetchAllReleases(): Promise<GitHubRelease[]> {
  const all: GitHubRelease[] = [];
  for (let page = 1; ; page += 1) {
    const result = await fetchReleasePage(page);
    all.push(...result.releases);
    if (!result.pagination.hasNextPage) return all;
  }
}

interface CachedValue<T> {
  savedAt: number;
  data: T;
}

export async function withLocalCache<T>(
  key: string,
  loader: () => Promise<T>,
): Promise<T> {
  const storageKey = `solipsism:${key}`;
  const cached = localStorage.getItem(storageKey);
  if (cached) {
    try {
      const value = JSON.parse(cached) as CachedValue<T>;
      if (Date.now() - value.savedAt < CACHE_TTL) return value.data;
    } catch {
      localStorage.removeItem(storageKey);
    }
  }
  try {
    const data = await loader();
    localStorage.setItem(
      storageKey,
      JSON.stringify({ savedAt: Date.now(), data }),
    );
    return data;
  } catch (error) {
    if (cached) return (JSON.parse(cached) as CachedValue<T>).data;
    throw error;
  }
}
