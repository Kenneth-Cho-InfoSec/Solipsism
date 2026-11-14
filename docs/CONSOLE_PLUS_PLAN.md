# Console+ Plan: On-device developer tools for Solipsism

**Goal:** Replace the in-app "Console" sheet with **Console+** — a tabbed bottom sheet combining the existing JavaScript console with an on-device element inspector, network panel, and storage inspector. Inspired by Eruda but native, Material 3, and engine-aware.

**Name:** Console+ (not "DevTools").

## Architecture

- Reuse the existing `ConsoleBottomSheet` / `ConsoleStore` as the **Console** tab.
- Add a new `ConsolePlusBottomSheet` (or extend `ConsoleBottomSheet`) with a tab bar: **Console | Elements | Network | Storage**.
- A resizable bottom sheet with snap points (peek / half / full) so the page stays visible while debugging.
- An overlay highlight layer on the WebView for the inspected element.
- A JS bridge (`window.solipsismDevtools`) injected into pages to support element inspection, network interception, and storage reads.

## Modules

| Module | Responsibility |
|---|---|
| `console/ConsolePlusBottomSheet.kt` | Sheet UI, tabs, snap control, MD3 styling |
| `console/ConsolePlusBridge.kt` | JS interface injected into WebView for inspection/network/storage |
| `console/ElementInspector.kt` | DOM query, computed styles, overlay highlight |
| `console/NetworkPanel.kt` | Request log, throttling, status/size/time |
| `console/StoragePanel.kt` | Cookies, localStorage, sessionStorage CRUD |
| `browser/tab/TabWebViewClient.kt` | Hook `shouldInterceptRequest` to feed NetworkPanel; inject bridge script |

## Feature breakdown (matches prototype)

### 1. Console (existing, enhanced)
- Level filters (log/warn/error/info), clear, run input with eval.
- Enhance: collapsible object tree, snippet library, auto-log failed network requests.

### 2. Elements (tap-to-inspect)
- Toggle "inspect mode"; tap page element → highlight overlay + computed styles card.
- DOM tree view with indentation; tap node to select.
- Computed styles: width/height/color/background/font-size/border-radius/display.
- Touch UX: bottom sheet, overlay highlight, toast "Inspected: <button>".

### 3. Network
- List of requests: URL, status, size, time, type.
- Throttle presets: No throttle / Slow 3G / Offline.
- Tap row → headers/details.
- Failed requests auto-logged to Console.

### 4. Storage
- Tabs: Cookies, localStorage, sessionStorage.
- Key-value list with delete; edit (v2).

## Implementation phases

**Phase A — Shell (Console+ bottom sheet with tabs)**
- Create `ConsolePlusBottomSheet` extending the existing console sheet.
- Tab bar, snap controls, panel switching.
- Wire into `BrowserActivity` replacing `ConsoleBottomSheet` usage (or add as new entry point).
- Effort: 2–3 days.

**Phase B — Elements (tap-to-inspect)**
- Inject JS bridge: `window.__solipsismInspect = { inspect(x,y), getStyles(selector) }`.
- Use `document.elementFromPoint` + `getComputedStyle`.
- Overlay highlight via `evaluateJavascript` returning bounding rect.
- DOM tree: serialize a simplified tree via JS, render in Kotlin RecyclerView.
- Effort: 3–4 days.

**Phase C — Network**
- Hook `shouldInterceptRequest` in `TabWebViewClient` to record request metadata + timing.
- Feed a `NetworkLog` (in-memory, per-tab) → NetworkPanel.
- Throttling: delay response in interceptor based on preset.
- Effort: 2–3 days.

**Phase D — Storage**
- Read cookies via `CookieManager`; localStorage/sessionStorage via JS eval.
- CRUD: delete via `CookieManager.removeSessionCookie` / JS `removeItem`.
- Effort: 1–2 days.

**Phase E — Polish**
- Object expansion in console, snippet library, performance panel (deferred).
- MD3 theming, accessibility, tablet layout.

## Engine constraints

- **WebView:** `evaluateJavascript`, `shouldInterceptRequest`, `CookieManager` all available. Bridge via `addJavascriptInterface`.
- **Antares (Servo):** Need to verify `evaluateJavascript`-equivalent and request interception. If unavailable, Console+ degrades gracefully (Console + limited Storage only) on Antares.
- **Threading:** network interceptor runs on a background thread; UI updates must be posted to main.

## Naming

- User-facing: "Console+" (menu item, sheet title).
- Internal classes: `ConsolePlus*`.
- No new localization strings beyond existing console strings where possible; reuse `R.string.console_*`.

## First build target

Phase A + Phase B (Console + Elements) as the first shippable increment, matching the prototype's recommended first feature.
