# Solipsism Browser — website revamp plan

**Status:** approved direction, implemented in this branch
**Base:** `website` branch at `4d30cde`
**Reference:** Valvet (composition and interaction only — not identity)
**Design record:** `website/DESIGN.md`
**Audit:** `website/WEBSITE_AUDIT.md`
**Verified facts:** `website/FEATURE_INVENTORY.md`

---

## 1. Objective

Turn the site from a competent brochure into a **demonstration**. A visitor should understand three things within ten seconds of landing:

1. Solipsism is an Android browser whose controls live in a rail at the screen edge.
2. That rail is real, configurable, and works with one hand.
3. WebView is the stable core; Antares is an optional experiment.

Everything else — docs, releases, comparison, community — must support that understanding without competing with it.

### Success criteria

| Criterion                                    | Target                                                                  |
| -------------------------------------------- | ----------------------------------------------------------------------- |
| Product idea is demonstrable without reading | A working rail fragment above the fold                                  |
| Visual identity                              | One charcoal/violet system, light + dark + AMOLED tier                  |
| Motion                                       | Purposeful, reduced-motion-safe, no autoplay that cannot be paused      |
| Docs                                         | Multi-page, searchable, sidebar navigation, code copy                   |
| Accessibility                                | WCAG 2.2 AA, keyboard-complete, visible focus, `aria-current`           |
| Performance                                  | No image over 200 KB on the critical path; fonts subset to two families |
| Deployment                                   | Unchanged GitHub Pages `/Solipsism/` base path                          |
| Honesty                                      | Every feature claim traceable to repo or release                        |

---

## 2. Design direction

Adopted from the `product-performs` draw (seed `1070271504`):

> Software is most convincing when it is doing its job in front of you.

### 2.1 Principles

1. **The interface is the imagery.** No stock photos, no device mockups, no dashboard collage. Fragments of the real UI, at true size, doing real things.
2. **Quiet column, then full-bleed bands.** The hero is a narrow left-aligned column with deliberate emptiness. Below it, sections may run edge to edge.
3. **One accent.** Violet is the only chromatic accent. Colour elsewhere comes from state (success/warning) and from the product fragment itself.
4. **Type does the hierarchy.** Archivo at regular and medium weight, tight tracking on display, generous leading on body.
5. **Motion explains state.** Nothing moves for decoration. The rail moves because the user moved it; the core story advances because the reader scrolled.

### 2.2 Palette

| Token         | Dark (default) | Light     | Role                   |
| ------------- | -------------- | --------- | ---------------------- |
| `--bg`        | `#171715`      | `#e9e7df` | page ground            |
| `--surface`   | `#1f1f1c`      | `#f4f2ec` | cards, panels          |
| `--surface-2` | `#262622`      | `#dedbd1` | inset fields           |
| `--ink`       | `#f2f1ec`      | `#1c1c1c` | primary text           |
| `--muted`     | `#a3a49c`      | `#5c5d57` | secondary text         |
| `--line`      | `#33332e`      | `#cfccc1` | hairlines              |
| `--accent`    | `#b7a4e6`      | `#6b4fa0` | accent, focus          |
| `--on-accent` | `#171715`      | `#f6f4ee` | text on accent         |
| `--amber`     | `#d9a566`      | `#8a5a1f` | experimental / warning |

AMOLED tier: `--bg: #000`, `--surface: #0a0a09`, keeping violet. Selected via the theme control (`system → light → dark → amoled`).

Contrast targets: body ≥ 7:1, muted ≥ 4.5:1, accent-on-ground ≥ 4.5:1, focus ring ≥ 3:1 against both adjacent colours.

### 2.3 Typography

- **Archivo Variable** (`@fontsource-variable/archivo`), weights 400/500/600. Self-hosted, no Google Fonts request.
- Display: 500 weight, tracking `-0.02em`, leading `1.05`.
- Body: 400 weight, leading `1.6`, measure capped at `68ch`.
- Scale: `--step-small` 13–14, `--step-body` 17–19, `--step-lead` 20–26, `--step-head` 32–64.
- Tabular numerals for version numbers and file sizes.

### 2.4 Motion

| Motion           | Where                | Implementation                                                                     |
| ---------------- | -------------------- | ---------------------------------------------------------------------------------- |
| Rail re-position | Rail playground      | `transform` on the rail, 420 ms spring-ish cubic-bezier                            |
| Panel reveal     | Rail playground tabs | opacity + 8 px translate, 220 ms                                                   |
| Section reveal   | Page sections        | IntersectionObserver adds `.is-visible`, 600 ms, once                              |
| Core story pin   | `/` how-it-works     | `position: sticky` + IntersectionObserver state, no scroll-jacking                 |
| Slow crossfade   | Core story stage     | 1.6 s opacity crossfade between WebView and Antares states                         |
| Band state       | Feature bands        | background/colour transition 320 ms                                                |
| Reduced motion   | All of the above     | `@media (prefers-reduced-motion: reduce)` disables transforms, keeps instant state |

No parallax, no cursor-follow, no autoplaying carousel that cannot be stopped.

---

## 3. Page architecture

### 3.1 Homepage — `features → showcase → how-it-works → proof → faq → cta → footer`

| Section      | Direction blueprint               | Implementation                                                                                                                                    |
| ------------ | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hero         | "Column on one line"              | Narrow left column at 27% on desktop; headline, one sentence, download + source links, version meta; a phone-sized 9:21 rail preview on the right |
| Features     | "Sentences with switches"         | Five full-bleed bands, each a sentence plus a real toggle; on-state reveals a consequence line with figures                                       |
| Showcase     | "Input half, output half"         | Filter lab: paste a rule on the left, see the parsed result on the right                                                                          |
| How it works | "Pinned fragment, changing state" | Core story: WebView (stable) vs Antares (experimental) with three statements                                                                      |
| Proof        | "Large marks on tiles"            | 9.4.0 · MPL 2.0 · Android 8.0+ · 0 trackers · one APK                                                                                             |
| FAQ          | "Question index, one answer"      | Six `<details>` questions, one open at a time                                                                                                     |
| CTA          | "First step, already open"        | Single download action plus docs link                                                                                                             |
| Footer       | "A single quiet line"             | One sentence, one row of links, licence                                                                                                           |

### 3.2 Features — `/features/`

Replaces the eight-card grid with grouped capability sections, each with a real state example and an explicit "limits" line. Anchored from the homepage bands.

### 3.3 Documentation — `/docs/` and children

Rebuilt as a documentation shell:

- persistent left sidebar with grouped topics
- right-hand "on this page" list generated from headings
- search (client-side, indexes titles and headings)
- code blocks with copy buttons
- previous/next footer
- callouts: `note`, `warning`, `experimental`
- breadcrumbs and `aria-current`

Pages:

| Route                    | Content                                                                   |
| ------------------------ | ------------------------------------------------------------------------- |
| `/docs/`                 | Hub: what Solipsism is, requirements, quick links                         |
| `/docs/installation/`    | Download, verify, install, update, uninstall                              |
| `/docs/privacy/`         | Incognito, cookies, headers, permissions, clearing data, limits           |
| `/docs/blocking/`        | Host lists, cosmetic filters, custom rules, element picker, filter syntax |
| `/docs/userscripts/`     | Metadata, lifecycle, import paths, security, limits                       |
| `/docs/antares/`         | Verified deep dive from `docs/ANTARES_ENGINE.md`                          |
| `/docs/appearance/`      | Themes, AMOLED, rail sizing, homepage customisation, accessibility        |
| `/docs/troubleshooting/` | Symptom → cause → fix                                                     |
| `/docs/development/`     | Build, test, contribute, release process                                  |

`/guides/getting-started/` stays as the friendly onboarding path and is linked from the docs hub.

### 3.4 Other routes

- `/compare/` — restyled; on mobile becomes stacked comparison cards instead of a scrolling table; dated and source-linked.
- `/community/` — restyled; four channels plus contribution guidance.
- `/releases/` — restyled archive; behaviour unchanged.

---

## 4. Component model

New reusable components under `src/components/`:

```
components/
  Header.astro            quiet bar, aria-current, theme control, download
  Footer.astro            one-line footer with link groups
  RailPlayground.astro    working rail fragment (hero)
  FeatureBands.astro      sentence + toggle bands
  FilterLab.astro         input/output filter demonstration
  CoreStory.astro         pinned WebView/Antares story
  ProofTiles.astro        large-mark proof tiles
  FaqList.astro           one-open-at-a-time accordion
  CallToAction.astro      closing download block
  ReleaseMount.astro      unchanged behaviour, restyled output
  docs/DocsShell.astro    sidebar + content + TOC + search
  docs/Callout.astro      note / warning / experimental
  ui/Button.astro         primary / secondary / ghost link
  ui/SectionHeading.astro eyebrow + heading + lead
  ui/Badge.astro          neutral / experimental / latest
```

Content that appears on more than one page moves to `src/data/`:

```
data/
  features.ts     capability groups, limits, anchors
  faqs.ts         homepage and docs FAQ
  facts.ts        version, min Android, licence, repository URLs
  compare.ts      comparison rows and sources
```

This removes the three-way duplication identified in the audit.

---

## 5. Content rules

1. **Every claim must be traceable.** Source: `README.md`, `CHANGELOG.md`, `docs/ANTARES_ENGINE.md`, `app/src/main/res/xml/preference_*.xml`, `strings.xml`, or a GitHub release. No invented benchmarks, user counts, or awards.
2. **Label the state of every feature**: stable, experimental, optional, or deprecated. Experimental features carry a visible badge and a one-line consequence.
3. **Antares is always paired with its fallback.** Any mention of Antares must say WebView remains the stable default.
4. **Limits belong next to claims**, not in a distant disclaimer section.
5. **Spelling:** en-GB throughout (`customise`, `behaviour`, `licence`, `colour`), matching `lang="en-GB"`.
6. **No marketing superlatives.** No "blazing", "revolutionary", "military-grade".
7. **Version numbers come from data**, not prose, where the page can read them.

---

## 6. Accessibility plan

- Keyboard-complete: every control reachable, Escape closes menus and dialogs, focus returns to the trigger.
- `aria-current="page"` on the active nav and sidebar item.
- `aria-pressed` on toggles; `aria-expanded` on disclosures; `aria-live="polite"` on the release region and the filter-lab result.
- Visible focus ring: 2 px `--accent` outline with 2 px offset, plus a light halo on dark surfaces.
- Minimum 44×44 px touch targets for all interactive controls.
- Headings form a single unbroken hierarchy per page.
- Tables have captions and scoped headers; mobile comparison uses definition-style cards instead.
- `prefers-reduced-motion` removes transforms and autoplay.
- `prefers-contrast: more` strengthens borders and removes translucency.
- Decorative interface fragments are `aria-hidden` where they duplicate text; interactive fragments are fully labelled.

---

## 7. Performance plan

- Replace the two eager screenshots with one optimised, responsive screenshot used only where it adds information (docs), `loading="lazy"`, explicit dimensions.
- Delete or compress unreferenced assets; keep the critical path under 200 KB of imagery.
- One variable font family (Archivo) instead of two.
- Inline critical tokens in `global.css`; defer non-critical scripts.
- Rail and filter demos are HTML/CSS/vanilla TS — no client framework, no hydration payload.
- Keep the existing release cache and manifest fallback.

---

## 8. Delivery sequence

1. **Foundations** — tokens, typography, reset, layout primitives, motion utilities.
2. **Shell** — header, footer, theme control, skip link, base layout metadata.
3. **Homepage** — hero, rail playground, bands, filter lab, core story, proof, FAQ, CTA.
4. **Content model** — `src/data/*`, remove duplicated arrays.
5. **Features** — grouped capability sections.
6. **Docs** — shell, search, callouts, code copy, nine pages.
7. **Secondary routes** — compare, community, releases, guide.
8. **Quality gates** — format, lint, typecheck, unit, e2e, build, manual 1440/768/390, keyboard pass, reduced-motion pass, contrast spot checks.
9. **Validation report** — `website/VALIDATION.md` with measured results and residual risks.

---

## 9. Out of scope

- Changes to the Android application.
- New runtime dependencies beyond the Archivo font package.
- Framework migration.
- Analytics, telemetry, or third-party embeds.
- Backend services; release data remains client-fetched from GitHub.
- Rewriting the release pipeline (it is already correct).
- Copying Valvet's visual identity.

---

## 10. Risks

| Risk                                                           | Mitigation                                                                              |
| -------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Documentation claims drift from the app                        | `FEATURE_INVENTORY.md` is the single source; every page cites a section                 |
| Interactive demos mislead users into thinking settings changed | Demos are labelled "preview" and state that they change the page only                   |
| Sticky core story breaks on short viewports                    | Falls back to a stacked, non-pinned layout below 900 px height                          |
| Large CSS rewrite regresses release rendering                  | Release markup classes are preserved and tested by existing e2e                         |
| GitHub Pages base path breaks new links                        | All internal links derive from `import.meta.env.BASE_URL`; e2e runs under `/Solipsism/` |
| Font package increases payload                                 | One family, variable, subset by `@fontsource`; measured in validation                   |
