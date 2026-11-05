# Solipsism Browser — website audit

**Audited:** 4 November 2026
**Subject:** `website` branch at `4d30cde` (the branch GitHub Pages deploys), cross-checked against the live site at <https://kenneth-cho-infosec.github.io/Solipsism/>.
**Method:** full read of `website/src` (1,045-line `global.css`, 7 routes, 4 components, release client), live inspection at 1440 / 768 / 390 px, comparison with the Valvet reference site, and a code-only Graphify pass over the website source (146 nodes, 234 edges, 17 communities).

This audit records what exists and what is wrong with it. It does not propose the fix — that is `WEBSITE_REVAMP_PLAN.md`.

---

## 1. Summary judgement

The current site is **competent and honest but visually anonymous**. Its information architecture is sound, its release pipeline is genuinely good, and its copy is unusually careful about limits. What it lacks is a design point of view: it uses a generic lavender-and-violet SaaS palette, the same card grid on every page, and almost no motion or interactive proof. A visitor cannot _see_ the product's defining idea — a rail-first, one-handed browser — anywhere on the page except inside two shrunken screenshots.

| Area                     | Verdict                                                      |
| ------------------------ | ------------------------------------------------------------ |
| Information architecture | Good — routes cover the real jobs                            |
| Copy accuracy            | Good — limits are stated, experimental features are labelled |
| Release integration      | Strong — validated, cached, sanitised, tested                |
| Visual identity          | Weak — generic palette and type, no product metaphor         |
| Product demonstration    | Weak — screenshots only, no working interface                |
| Documentation depth      | Weak — one long page, six sections, no search or navigation  |
| Motion                   | Minimal — hover transitions and smooth scroll only           |
| Accessibility            | Fair — good foundations, several concrete gaps               |
| Performance              | Fair — oversized hero images, render-blocking font imports   |
| Maintainability          | Fair — duplicated content and one monolithic stylesheet      |

---

## 2. Visual design findings

### 2.1 Palette

`global.css` defines a light-first theme:

```
--bg: #f7f6fb;  --surface: #fff;  --accent: #5746a7;  --accent-2: #e4ddff;
```

- **Lavender ground + violet accent** is the default template palette of 2023–2025 developer SaaS. It carries no relationship to privacy, Android, or browsing.
- Dark mode (`#070709`) is near-black with a lilac accent (`#c9bcff`), which reads as a different product rather than the same one at night.
- There is **no AMOLED tier**, even though true-black AMOLED is one of the app's advertised appearance modes. The site cannot demonstrate the feature it sells.
- Accent usage is inconsistent: `.badge`, `.text-link`, `.release-notes a`, and focus rings all derive from the same violet, so "interactive", "informational", and "warning" states are not visually distinct.

### 2.2 Typography

- Two variable fonts ship: **Manrope** (body) and **Space Grotesk** (headings). Both are common defaults; neither is distinctive.
- Hierarchy is carried almost entirely by size. There is no weight, tracking, or case system, so the page reads as one texture.
- Heading scale is large and uniform: every route opens with the same `.archive-hero` block at the same size. The homepage, the feature index, the comparison, and the release archive all begin identically.
- Line length is uncontrolled in places. `.privacy-copy` and `.release-notes` use `max-width: 850px`, which at 18px produces roughly 95 characters per line — well past a comfortable measure.
- `lang="en-GB"` is declared, but the copy mixes en-US and en-GB spelling ("customization", "color", "behavior" alongside "licence", "customise"). Screen readers and search engines receive contradictory signals.

### 2.3 Layout

- Every page follows `archive-hero → section → grid of cards → privacy band → footer`. The rhythm never changes, so the eye has no landmarks.
- `.feature-grid` renders eight identical cards on `/features/` and eight near-identical cards on `/`. Card content lengths vary by 2×, producing ragged rows.
- `/compare/` is a five-column table with long prose in every cell. On a 390 px viewport it becomes a horizontal scroll region; the row header scrolls out of view, so the reader loses the browser name.
- The homepage hero is a 50/50 split of text and a phone screenshot. The screenshot is 921×2048 displayed at roughly 300 px wide — every label inside it is illegible. The product's best idea is reduced to decoration.
- There is no editorial use of whitespace, no full-bleed band, no asymmetry. Everything is a centred container of equal cards.

### 2.4 Imagery

- `public/assets/` contains 3.0 MB of images, including `homepage-light.jpg` at **1.6 MB** and `homepage-dark.jpg` at **637 KB**, neither of which is referenced by any page.
- The two referenced screenshots are 72 KB and 79 KB — acceptable — but both are loaded `eager` with `fetchpriority="high"` on the homepage, competing with the LCP text.
- No `srcset`, no AVIF/WebP, no width/height discipline beyond the two screenshots.
- The logo is shipped at 604 KB (`solipsism-logo.png`) for social cards and at 9.5 KB for the favicon; the 604 KB file is used as the Open Graph image.

### 2.5 Motion

- The only motion is `transition` on buttons/cards, a `spin` keyframe for the release spinner, and `html { scroll-behavior: smooth }`.
- There is **no scroll-triggered reveal, no state transition, no demonstration motion**. The site never shows the product moving, despite the app being defined by physical rail interaction.
- `prefers-reduced-motion` is handled correctly at the bottom of the stylesheet (animations off, transitions reduced to 160 ms) — the one motion decision that is right.

---

## 3. Content and information architecture

### 3.1 Routes

| Route                      | Purpose                                                   | State                        |
| -------------------------- | --------------------------------------------------------- | ---------------------------- |
| `/`                        | Product overview, download, latest release                | Present, thin                |
| `/features/`               | Feature index                                             | Present, duplicates homepage |
| `/docs/`                   | Install, cores, permissions, development, troubleshooting | Present, shallow             |
| `/guides/getting-started/` | Five-step onboarding                                      | Present, good                |
| `/compare/`                | Five-browser comparison                                   | Present, dense table         |
| `/community/`              | Source, issues, releases, licence                         | Present, four cards          |
| `/releases/`               | Live release archive                                      | Present, strong              |

### 3.2 Duplication

Three separate places describe the same eight capabilities with different wording:

1. the `features` array in `src/pages/index.astro`,
2. the `features` array in `src/pages/features.astro`,
3. the "why" benefit grid on the homepage.

The three "limits" sections (homepage `#privacy`, `/features/` limits band, `/docs/` privacy section) repeat the same three caveats — WebView dependence, experimental permissions, subset filtering — in slightly different words. There is no single source of truth, so a factual correction must be made in three places.

### 3.3 Documentation depth

`/docs/` covers six topics in roughly 1,900 words:

- requirements and releases
- WebView vs Antares
- permissions
- privacy and downloads
- build commands
- troubleshooting

Missing for a browser with this feature surface:

- installation and update procedure beyond "download the APK"
- the full settings map (privacy, adblocker, userscripts, malware scanner, appearance, accessibility, audio)
- filter-language reference
- userscript metadata reference
- homepage customisation and its sandbox rules
- keyboard/accessibility behaviour
- a glossary of project terms (rail, Rail & Menu Studio, Decoy Mode, Antares)
- per-topic deep dives for Antares, which is the project's most distinctive engineering claim

There is **no search, no sidebar, no previous/next navigation, no copy-code button, and no per-section anchors beyond six `id`s**.

### 3.4 Accuracy and freshness

- The repository `README.md` still advertises **v6.1.0**, while GitHub Releases publishes **v9.4.0**. The website hardcodes 9.4.0 in two places, so it is correct today but will rot at the next release.
- `app/build.gradle.kts` on this branch reports `versionName = "6.1.6"`, which conflicts with the release tag. This is an Android-repo issue, but the site should not depend on a hand-maintained version string.
- Release name, notes, date, assets, and download URLs are fetched at runtime — good — but the _requirements_ text ("Android 8.0", "one APK asset") is hardcoded prose that the release data cannot correct.
- `/compare/` is dated "31 October 2026" and is manually maintained. It will silently age.

### 3.5 Tone

The copy is careful and specific, which is a genuine asset. It is also uniformly defensive: nearly every paragraph ends in a caveat. The homepage never states, in one confident sentence, what the product is _for_. A reader finishes the hero knowing the limits before knowing the benefit.

---

## 4. Accessibility findings

Verified strengths:

- Skip link to `#main` on every page.
- `aria-expanded` / `aria-controls` on the mobile nav toggle.
- `aria-label` on icon-only theme and nav buttons.
- `aria-live="polite"` on the release region.
- Table scroll regions are focusable and labelled (`role="region"`, `tabindex="0"`, `aria-label`).
- `:focus-visible` outline exists.
- Reduced-motion media query exists.
- Semantic landmarks (`header`, `nav`, `main`, `footer`, `article`, `aside`).

Concrete gaps:

| Issue                                                             | Evidence                                        | Impact                                                                                      |
| ----------------------------------------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Theme button is a glyph                                           | `◐` / `●` / `○` with `aria-label` updated in JS | If JS fails, the control shows a meaningless glyph; state is not exposed via `aria-pressed` |
| Mobile nav has no Escape handler                                  | `data-nav-toggle` script                        | Keyboard users cannot dismiss the open menu without tabbing through it                      |
| No visible focus on dark surfaces                                 | Single `outline` colour from `--accent`         | Violet outline on near-black is low contrast                                                |
| Comparison table loses row context                                | Horizontal scroll on mobile                     | Reader cannot tell which browser a cell belongs to                                          |
| No `aria-current` on active nav item                              | Header markup                                   | Screen-reader users get no "you are here" signal                                            |
| Decorative screenshots have alt text but no `figure`/`figcaption` | Homepage                                        | Alt text is doing caption work                                                              |
| Release notes are injected HTML with headings                     | `renderReleaseMarkdown`                         | Injected `h1`/`h2` can break the document outline                                           |
| No `prefers-contrast` support                                     | `global.css`                                    | Users who request higher contrast get the default palette                                   |
| Smooth scroll is global                                           | `html { scroll-behavior: smooth }`              | Vestibular trigger; only partially mitigated by the reduced-motion block                    |

---

## 5. Performance findings

Source-level observations (not a Lighthouse run):

1. **Render-blocking font imports.** `BaseLayout.astro` imports two `@fontsource-variable` packages at the top of the layout, which emits CSS `@import`-style bundling. Two variable font families is roughly 200–400 KB of WOFF2 across the weight ranges.
2. **Oversized hero images.** 1.6 MB + 637 KB unreferenced assets ship in `public/`; they are copied verbatim into `dist/`.
3. **Eager, high-priority screenshots.** Both homepage screenshots use `loading="eager"` and `fetchpriority="high"`, so two 2048 px-tall images compete with the hero text for bandwidth.
4. **No image formats beyond JPEG/PNG.** No AVIF or WebP fallbacks.
5. **Release client is small and well-cached** (5-minute `localStorage` TTL, stale fallback, pagination) — this part is efficient.
6. **No third-party scripts and no analytics** — an explicit project value, correctly upheld.

---

## 6. Engineering findings

### 6.1 What is good

- Strict TypeScript (`astro/tsconfigs/strict`, `noUncheckedIndexedAccess`).
- Zod validation of every GitHub API field, including a host allow-list for download URLs.
- Markdown sanitised with DOMPurify; release bodies treated as untrusted.
- Unit tests for APK selection and size formatting; Playwright tests for the download CTA and the archive.
- Build gate runs `astro check` before `astro build`.
- Sitemap, canonical URLs, `robots.txt`, JSON-LD (`SoftwareApplication`, `TechArticle`, `BreadcrumbList`).
- Base path is environment-driven (`PUBLIC_BASE_PATH`), so `/Solipsism/` deployment is correct.

### 6.2 What is weak

- **One 1,045-line stylesheet** with no layering, no component scoping, and selectors that reach across pages. Any redesign risks breaking unrelated routes.
- **Content lives inside page frontmatter.** Feature lists, comparison rows, and permission tables are inline arrays/JSX. There is no content collection, so reuse requires copy-paste.
- **Components are thin.** Only `Header`, `Footer`, and `ReleaseMount` exist; every page re-implements its hero and section wrapper.
- **No `src/components/ui/` primitives.** Buttons, badges, callouts, and section headings are CSS classes applied ad hoc.
- **No visual regression or accessibility test.** Playwright covers two behaviours; nothing checks layout, contrast, or focus order.
- **`scripts/create-sitemap-alias.mjs`** exists to work around the sitemap alias; it is undocumented in the README.

---

## 7. Comparison with the Valvet reference

Valvet's site earns its credibility by _showing the mechanism_: editorial type, a restrained palette, real interface fragments, and motion that explains state. Solipsism's site describes the mechanism and shows a screenshot.

Adopt from Valvet:

- editorial composition and generous negative space
- one accent colour used with discipline
- interface fragments at true size as the primary imagery
- a pinned/state-changing section that explains a technical choice
- quiet, purposeful motion with a reduced-motion path

Do **not** adopt: Valvet's violet identity, its glass-and-glow treatment, its copy voice, or its page structure wholesale.

---

## 8. Prioritised problem list

| #   | Problem                                                     | Severity | Owner section   |
| --- | ----------------------------------------------------------- | -------- | --------------- |
| 1   | No product demonstration — the rail idea is invisible       | Critical | Hero + showcase |
| 2   | Generic lavender/violet identity                            | Critical | Design tokens   |
| 3   | Documentation is too shallow for the feature surface        | High     | Docs rebuild    |
| 4   | Three competing feature lists and three limits sections     | High     | Content model   |
| 5   | Comparison table fails on mobile                            | High     | Compare         |
| 6   | Hero images oversized and over-prioritised                  | High     | Assets          |
| 7   | No motion language; no reduced-motion-aware reveals         | Medium   | Motion          |
| 8   | Accessibility gaps (Escape, `aria-current`, focus contrast) | Medium   | Components      |
| 9   | Monolithic CSS, no primitives, duplicated markup            | Medium   | Architecture    |
| 10  | en-GB/en-US spelling drift                                  | Low      | Copy            |
| 11  | Version strings hardcoded in prose                          | Low      | Release data    |
| 12  | No `prefers-contrast` tier                                  | Low      | Tokens          |

---

## 9. Evidence index

- `website/src/styles/global.css` — tokens, layout, motion, reduced-motion block.
- `website/src/pages/*.astro` — route content and duplicated feature arrays.
- `website/src/layouts/BaseLayout.astro` — metadata, theme bootstrap, nav script.
- `website/src/components/*.astro` — header, footer, release mount.
- `website/src/lib/releases.ts`, `website/src/scripts/releases.ts` — release pipeline.
- `website/tests/` — existing coverage.
- `README.md`, `CHANGELOG.md`, `docs/ANTARES_ENGINE.md`, `app/src/main/res/xml/preference_*.xml` — product facts.
- Live GitHub Releases API — `v9.4.0`, single asset `Solipsism-Browser-v9.4.0.apk`.
- Graphify report — `website/graphify-out/GRAPH_REPORT.md`.
