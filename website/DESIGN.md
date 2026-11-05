# Solipsism Browser — design record

**Direction:** The product performs
**Seed:** `1070271504` (`design-taste-frontend`, `product-performs`)
**Reference:** Valvet — composition, pacing and technical credibility only
**Implementation:** `src/styles/global.css`, `src/components/*`, `src/pages/*`

This file records what the site is trying to be, the decisions that got it
there, and the rules that keep future edits inside the same system. It is a
working document, not a brand book.

---

## 1. The idea in one sentence

> A privacy browser is easiest to believe when you can move the rail yourself,
> then read the rule it just parsed.

Everything on the homepage serves that sentence. The hero is not a screenshot of
a phone; it is a working fragment of the interface. The filter section is not a
feature card; it is a parser you can type into. Claims appear next to the switch,
the state, or the release that proves them.

## 2. What was drawn, and what was redrawn

The direction came from a seeded draw, then was redrawn against the actual
product and the house rules. The redraws matter more than the original draw.

| Drawn                                           | Redrawn to                                                   | Why                                                                                                          |
| ----------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Full-width editorial hero with a large image    | 5/7 split: quiet copy column, live rail fragment             | An empty right half fails the house rule; a working fragment earns the space.                                |
| Warm cream light theme (`#e9e7df`)              | Neutral bone (`#e6e6e1`)                                     | Cream reads as a generic assistant palette; the product is grey-green, not yellow.                           |
| Rounded cards, soft shadows, glass header       | Square page surfaces, hairline borders, solid header         | Shadows and glass at rest were flagged and read as decoration; the product UI is flat and precise.           |
| Six-step type scale with micro uppercase labels | Five visible sizes, sentence case throughout                 | Tracking-heavy eyebrows and 11 px labels were doing hierarchy that type weight should do.                    |
| Gradient wash behind the hero                   | Flat charcoal ground, one sage accent                        | The gradient added atmosphere but no information, and failed the lint gate.                                  |
| Stock photography / device mockups              | Real screenshots from `docs/screenshots/`, converted to WebP | Only the shipped interface is honest imagery for a browser.                                                  |
| Animated counters and numbered sections         | Large static marks on proof tiles                            | Numbers counting up implied measurement theatre; the figures are facts, not a performance.                   |
| Scroll-jacked "how it works"                    | Sticky stage + IntersectionObserver state                    | The reader keeps control of the scroll; the panel changes because they arrived, not because they were moved. |

The net effect: less atmosphere, more evidence. Valvet's contribution is the
confidence to let a narrow column and a full-bleed band share a page, and to
make the product itself the demonstration. Its palette, type and voice are not
copied.

## 3. Design tokens

### 3.1 Colour

| Token         | Dark (default) | Light     | AMOLED    | Role                   |
| ------------- | -------------- | --------- | --------- | ---------------------- |
| `--bg`        | `#171715`      | `#e6e6e1` | `#000000` | page ground            |
| `--bg-deep`   | `#111110`      | `#dcdcd6` | `#000000` | inset ground           |
| `--surface`   | `#1f1f1c`      | `#f2f2ee` | `#0a0a09` | cards, panels          |
| `--surface-2` | `#262622`      | `#dfdfd9` | `#141412` | fields, table stripes  |
| `--ink`       | `#f2f1ec`      | `#1a1a19` | `#f2f1ec` | primary text           |
| `--ink-soft`  | `#d8d7cf`      | `#30302e` | `#d8d7cf` | strong secondary text  |
| `--muted`     | `#a3a49c`      | `#4c4d48` | `#9b9c94` | secondary text         |
| `--line`      | `#34342f`      | `#cbcbc4` | `#242422` | borders                |
| `--sage`      | `#9db39b`      | `#4a664a` | `#9db39b` | accent, focus          |
| `--on-sage`   | `#14150f`      | `#edefea` | `#000000` | text on accent         |
| `--amber`     | `#d9a566`      | `#7d5019` | `#d9a566` | experimental / warning |
| `--danger`    | `#d98a7a`      | `#8d3b2c` | `#d98a7a` | destructive states     |

Sage is the only chromatic accent on the page. Amber appears only where the
product is experimental or a release needs a warning. Everything else is ink,
surface or line.

Themes are selected with `html[data-theme]` (`light`, `dark`, `amoled`) and the
control cycles `system → light → dark → amoled`. The choice persists in
`localStorage`; with no choice the system preference wins.

### 3.2 Type

- **Archivo Variable**, self-hosted through `@fontsource-variable/archivo`.
  One family for the whole site. No second display face, no monospace outside
  code blocks.
- Weights: 400 body, 500 display, 600 only for strong inline emphasis.
- Display: `clamp(2.25rem, 1.2rem + 3.6vw, 3.5rem)`, tracking `-0.035em`,
  leading `1.02`.
- Headings: `clamp(2rem, 1.35rem + 1.9vw, 2.5rem)`, tracking `-0.02em`.
- Body: `1.0625rem` (17 px), leading `1.62`, measure capped at `66ch`.
- Page copy scale: 13 / 17 / 20 / 24 / 40 / 56 px. Nothing in page copy goes
  below 13 px.
- Exception: inside `data-ui` fragments (rail tiles, tab strip, stage chrome),
  type drops to 10–12 px because it is imitating app controls at true scale.
  Those fragments are marked as previews and are not page copy.
- Sentence case everywhere. No eyebrows, no tracked-out micro labels.
- Tabular numerals on version numbers, file sizes and the compare table.

### 3.3 Space and surface

- `--gutter: clamp(20px, 4.6vw, 64px)`; `--shell: min(1280px, 100% - 2×gutter)`.
  Outer margins stay equal at every width.
- Page surfaces are square. `--radius-ui: 8px` is used only inside
  `data-ui` fragments (the rail playground, the filter lab, screenshot frames),
  because the app itself has rounded controls.
- Borders are 1 px `--line`. No thin rules used as decoration; a rule must
  separate two things.
- No shadows at rest. Elevation is expressed with surface steps, not blur.

### 3.4 Motion

| Motion           | Where                | Implementation                                                      |
| ---------------- | -------------------- | ------------------------------------------------------------------- |
| Rail re-position | Rail playground      | `transform`, `--dur-panel` 420 ms, `--ease-drawer`                  |
| Panel swap       | Rail playground tabs | opacity + 8 px translate, `--dur-ui` 220 ms                         |
| Section reveal   | Long pages           | IntersectionObserver adds `.is-visible`, 420 ms, once               |
| Core story state | Homepage             | Sticky stage + observer state, 1.6 s crossfade                      |
| Band state       | Feature bands        | background/colour transition 320 ms                                 |
| Reduced motion   | All                  | `prefers-reduced-motion: reduce` removes transforms and transitions |

Rules:

1. Motion explains a state change the user caused, or announces content that
   arrived. It never loops for atmosphere.
2. Nothing autoplays that cannot be stopped. There is no carousel.
3. No parallax, cursor-follow, scroll-jacking or counter animation.
4. Every animated property has a reduced-motion branch.

## 4. Composition

### 4.1 Homepage

`hero → features → showcase → how-it-works → proof → faq → cta → footer`

| Section      | Composition                                                                                                                                                  |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Hero         | Narrow copy column (headline, one paragraph, two actions, version meta, four links) beside the rail fragment. Headline stays two lines at 1440, 768 and 390. |
| Features     | Five full-bleed bands. Each is a sentence plus a real switch; the on state reveals a consequence line with a figure.                                         |
| Showcase     | Four real screenshots at true aspect ratio, captioned by what they show, not by marketing names.                                                             |
| Filter lab   | Input left, parsed output right. Typing changes the parse; the copy says it is a preview, not an install.                                                    |
| How it works | WebView (stable) and Antares (experimental) panels with three statements each. The stable core is the default reading.                                       |
| Proof        | Four large marks: 9.4.0 · MPL 2.0 · Android 8.0+ · one APK. No counters.                                                                                     |
| FAQ          | Six `<details>` questions; opening one closes the others.                                                                                                    |
| CTA          | One download action, one documentation link.                                                                                                                 |
| Footer       | One sentence, one row of links, licence and source.                                                                                                          |

### 4.2 Interior pages

- `/features/` — split hero with the rail playground, a four-up screen strip,
  then a two-column feature list with limits stated per item.
- `/docs/` and `/docs/[slug]/` — persistent grouped sidebar, client-side search,
  right-hand "on this page", code copy buttons, previous/next pager.
- `/guides/getting-started/` — ordered steps with commands and expected output.
- `/compare/` — table on desktop, stacked cards on mobile; cells describe
  behaviour, not scores.
- `/releases/` — GitHub API-backed archive, markdown notes, APK asset rows,
  graceful fallback when the API is unavailable.
- `/community/` — contribution paths and conduct expectations.

## 5. The live things

Two components carry the "product performs" idea. Both are client-side and both
degrade to readable static markup without JavaScript.

**`RailPlayground.astro`** — a working rail: choose a side, choose a size, open
the tab panel, switch tabs. The stage re-themes to dark or light inside its own
frame so the fragment reads as an app, not as page chrome. All controls are
buttons with `aria-pressed` / `aria-current`; the frame is `data-ui`, so it keeps
the app's rounded controls without loosening the page's square system.

**`FilterLab.astro`** — a small parser for the cosmetic filter syntax the app
documents. It classifies each line as hide, allow, network or skip, names the
scope, and updates an `aria-live` result list. The hint text states plainly that
this is a preview and does not change the app.

Neither demo claims to be the app. Both are labelled as previews, and the copy
next to them points to the documentation for the real behaviour.

## 6. What was deliberately not done

- No analytics, tag manager, heatmap or cookie banner.
- No framework migration for visual effects. Astro stays; interactivity is
  small vanilla scripts.
- No stock imagery, AI-generated product shots or device mockups.
- No second font, icon library or animation library.
- No changes to the Android application to support a website effect.
- No invented benchmarks, user counts, awards or "trusted by" logos.

## 7. Editing rules

1. New sections need a heading and a reason to exist. If it cannot be tied to a
   repository fact or a shipped release, it does not ship.
2. Keep the page copy scale (13 / 17 / 20 / 24 / 40 / 56). If a new size is
   needed, replace one.
3. Sage is the only accent. Amber is for experimental or warning states.
4. Page surfaces stay square; rounded controls only inside `data-ui`.
5. No shadows, gradients, glass or hairline decoration at rest.
6. Any new motion gets a reduced-motion branch and must explain a state change.
7. Any new claim gets a line in `FEATURE_INVENTORY.md` with its source.
8. Run `npm run build`, `npm run lint`, `npm test`, `npm run test:e2e`,
   `lint.mjs src` and `inspect.mjs` before calling a change finished.
