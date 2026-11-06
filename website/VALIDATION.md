# Solipsism Browser — validation report

**Branch:** `website` (base `4d30cde`)
**Build:** Astro 7, static output, GitHub Pages base `/Solipsism`
**Date:** 6 November 2026
**Revision:** violet accent, 9:21 phone-sized rail stage, frame-aligned filter lab
**Verdict:** ships. All gates pass; residuals are documented below rather than
hidden.

This report records what was measured, how, and what is still imperfect. The
numbers are from the local build and the rendered pages, not estimates.

---

## 1. Gate summary

| Gate                         | Command                            | Result                                      |
| ---------------------------- | ---------------------------------- | ------------------------------------------- |
| Types and Astro templates    | `npx astro check`                  | 0 errors, 0 warnings                        |
| Production build             | `npm run build`                    | 15 pages, sitemap created                   |
| JavaScript lint              | `npm run lint`                     | pass                                        |
| Unit tests                   | `npm test`                         | 3/3 pass                                    |
| End-to-end tests             | `npx playwright test`              | 2/2 pass                                    |
| Formatting                   | `npm run format:check`             | pass                                        |
| House-style source lint      | `lint.mjs src`                     | 0 FAIL, 0 WARN                              |
| Rendered homepage inspection | `inspect.mjs /Solipsism/`          | 0 FAIL, 2 WARN (`no-footage`, `off-centre`) |
| Rendered features inspection | `inspect.mjs /Solipsism/features/` | 0 FAIL, 1 WARN (`no-footage`)               |

The two e2e tests are the ones that existed on the branch and still describe
real contracts:

- the hero download button uses the asset URL returned by the GitHub API;
- the release archive loads its project-base manifest and stays within a
  390 px viewport (no horizontal overflow).

**Deployment:** GitHub Actions run `42216775992` completed successfully
(verify-and-build and deploy jobs). After deployment, all 15 routes,
`/robots.txt` and `/sitemap.xml` returned HTTP 200 at
`https://kenneth-cho-infosec.github.io/Solipsism/`.

## 2. Build output

- 15 routes: `/`, `/features/`, `/docs/`, eight documentation articles,
  `/guides/getting-started/`, `/compare/`, `/community/`, `/releases/`.
- `dist/sitemap-0.xml` lists all 15 routes under
  `https://kenneth-cho-infosec.github.io/Solipsism/`.
- `dist/sitemap.xml` is a stable alias for `sitemap-index.xml`, produced by
  `scripts/create-sitemap-alias.mjs` after the build.
- `dist/robots.txt` allows search engines and disallows 41 named AI crawlers,
  then points at the sitemap.
- Total `dist/` is about 3.7 MB, dominated by the APK-free docs and six WebP
  screenshots (9–43 KB each).
- The filter lab is now a 1280 px band inside the page frame, not a viewport-
  wide split, so its first line of copy starts on the same 80 px margin as the
  header and hero at 1440 px.

## 3. Measured homepage

From `inspect.mjs` at 1440 px, with tablet and phone re-measurement:

| Measure             | Result                                                         |
| ------------------- | -------------------------------------------------------------- |
| Hero headline       | “Android browsing within reach.”, 4 words, 2 lines             |
| Hero at 768 px      | 2 lines, 46.8 px                                               |
| Hero at 390 px      | 2 lines, 36 px                                                 |
| Typefaces           | Archivo Variable, 100%                                         |
| Visible sizes       | 12 / 13 / 14 / 16 / 17 / 20 / 32 / 40 / 56 px                  |
| Body                | 17 px, leading 1.62                                            |
| Page length         | 8.3 screens, 838 words, 5 images                               |
| Headings            | 17, longest 2 lines, median 5 words                            |
| Colour              | page `#e6e6e1`, sections `#dcdcd6` `#dfdfd9`, accent `#6b4fa0` |
| Horizontal overflow | none at 1440, 768 or 390                                       |

The 12 px entries are inside `data-ui` fragments and code blocks; page copy does
not go below 13 px.

## 4. Accessibility

Checked by review and by keyboard walkthrough, not by an automated audit only.

- **Landmarks:** one `<main id="main">` per page, banner and contentinfo around
  it. Skip link is the first focusable element and becomes visible on focus.
- **Headings:** one `<h1>` per route; sections use `<h2>`/`<h3>` in order. The
  homepage has 17 headings and no level skips.
- **Focus:** `:focus-visible` uses a 2 px violet ring with a 2 px offset, on both
  light and dark grounds. Buttons, links, summary rows, the rail controls, the
  theme control and the filter textarea all take it.
- **Keyboard:** the rail playground is buttons, not divs. The tab panel uses
  `aria-current`; side/size controls use `aria-pressed`. FAQ uses native
  `<details>`/`<summary>`. The mobile nav button manages `aria-expanded` and
  closes on Escape. Docs search is a labelled input with a live result count.
- **Live regions:** filter output is `aria-live="polite"`; release loading
  states announce through text, not colour alone.
- **Colour:** body text is `--ink` on `--bg` (about 14:1 dark, 14:1 light);
  `--muted` is above 4.5:1 on every surface it is used on; the violet accent is
  above 4.5:1 on every ground it is used on; focus ring is above 3:1 against both
  adjacent colours.
- **Motion:** `prefers-reduced-motion: reduce` removes the rail transform
  transition, panel translate, section reveal offset and smooth scrolling. The
  core story still changes state, instantly.
- **Images:** every screenshot has a caption and alt text describing the screen,
  not the filename. Decorative rail tiles are `aria-hidden`.
- **Zoom:** layout holds at 200% text zoom at 1440 px; no fixed-height text
  containers clip.
- **Targets:** interactive controls are at least 44×44 px on touch, including
  the rail buttons and theme control.

Not claimed: a full screen-reader session on Android TalkBack or NVDA. The
markup is standard and the interactions are native controls, but that is a
review, not a test result.

## 5. Honesty review

Every product claim on the site was checked against the repository or the
published release:

- Latest release `v9.4.0`, 24 October 2026, single asset
  `Solipsism-Browser-v9.4.0.apk`. The site reads this from the GitHub API and
  falls back to the releases page, so it does not hard-code a version.
- `minSdk 26` (Android 8.0), `targetSdk 36`, package
  `com.krystelligence.solipsism`, MPL 2.0.
- Antares 0.5.0 is described as **experimental and optional** everywhere it
  appears. Android WebView is named as the stable core and the default.
- The filter lab is labelled a parser preview that does not install a rule.
- The rail playground is labelled a preview; it does not claim to be the app.
- No invented benchmarks, user counts, awards or logos. No “trusted by”.
- `FEATURE_INVENTORY.md` carries the per-feature source line.

Stale strings outside the website were left alone: `README.md` still advertises
v6.1.0 and `build.gradle.kts` still says `6.1.6`. The site does not read either.

## 6. Residual warnings and why they stand

### `no-footage` (homepage, features)

The inspection asks for one honest video clip. There is no honest clip to use:
the product is an Android app, and this environment has no device or emulator
capture. A stock clip or a synthetic mockup would be less truthful than the real
screenshots already on the page. The screenshots come from `docs/screenshots/`
and show the shipped 9.4.0 interface. When a real device capture exists, the
right move is a muted loop with a poster frame in the showcase section — not a
fabricated one now.

### `off-centre` (homepage)

The structural frame is symmetric: at 1440 px the header inner, the hero grid,
the filter lab and the footer all run from 80 px to 1360 px, so the shell has
80 px on each side. The inspection derives its margins from the 2nd and 98th
percentiles of text boxes, and the rightmost text sits inside controls and
panels that have their own padding: the CTA button's label ends 16 px inside
the frame, and the story panel's rows end 40 px inside it. The 98th percentile
therefore lands on the button at 1344 px and reports a 96 px right margin.

Removing the button's padding would make the metric agree with the frame, but it
would also break the control. The frame, not the text inside padded controls, is
the thing the rule is meant to protect, so this residual is kept and measured
rather than hidden. The rule does not trigger at 768 px or 390 px.

### `still-page` (features, resolved)

The features page used to reveal each feature group as one block, which gave the
probe 13 moving elements. Each feature heading and item now has its own reveal,
with the existing 70/140/200 ms stagger, so the page enters by part rather than
as a single fade. That clears the warning without adding decoration-only motion.

### Subpage heuristics

`few-pictures`, `focal-point` and similar warnings on documentation routes are
reading-page heuristics. Documentation is text, code and tables; adding imagery
would slow the reader down. The docs shell uses typography, spacing and a
sidebar instead.

## 7. Known limitations

- The release archive depends on the GitHub API. Unauthenticated requests are
  rate-limited; the page shows a fallback link and does not fail closed.
- Client-side docs search indexes titles and headings, not full body text. It is
  a navigator, not a full-text engine.
- The light theme is a deliberate neutral bone, not a warm paper. It was changed
  after the first pass because the warm version read as a generic assistant
  palette.
- The screenshots are static. The rail playground is the only moving product
  fragment, and it is a reconstruction of the interaction, not a capture.
- No analytics means no field data on which sections are read. Decisions here
  are from review and inspection, not telemetry.

## 8. How to reproduce

```bash
cd website
npm ci
npx astro check
npm run build
npm run lint
npm test
npx playwright test
npm run format:check

# house style
node /path/to/design-taste-frontend/scripts/lint.mjs src
CHROME_PATH=/path/to/chrome node /path/to/design-taste-frontend/scripts/inspect.mjs \
  http://127.0.0.1:4321/Solipsism/

# serve the built site at the deployed base path
npx astro preview --port 4321 --host 127.0.0.1
```

The preview serves `dist/` at `http://127.0.0.1:4321/Solipsism/`, which matches
the GitHub Pages path.
