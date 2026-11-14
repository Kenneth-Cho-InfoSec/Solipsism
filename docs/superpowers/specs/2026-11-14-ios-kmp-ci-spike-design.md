# iOS KMP CI Spike (Isolated Experiment) — Design

**Date:** 2026-11-14  
**Status:** Proposed (awaiting approval)  
**Scope:** Small, self-contained experiment. No changes to Solipsism’s production `:app` build.

## Intent & why
Validate, with minimal effort, that **Kotlin Multiplatform (KMP)** code can be built for iOS and exercised in CI without an iPhone. The experiment proves a **small shared-Kotlin slice runs on iOS** via automated **build + launch in iOS Simulator + screenshot/test** on **GitLab CI**. Solipsism itself is an Android WebView/browser app (XML/View UI, WebView/Antares engine, Android-specific APIs), so a full iOS port is out of scope for this spike.

## Goals
- Add an isolated KMP spike under `experiments/ios-kmp-ci-spike/` (own Gradle build, independent of `:app`).
- Implement a tiny **pure-Kotlin** function in `commonMain` (no Android/iOS framework deps) shared to Android and iOS targets (e.g. `sharedGreeting()`/`formatMessage()` returning a deterministic string).
- Provide a **minimal iOS app shell** (`iosApp/`, SwiftUI) that calls the shared function and displays its result on screen (so a simulator screenshot/test can assert it ran).
- Provide a **GitLab CI** pipeline (`.gitlab-ci.yml` at repo root or experiment-scoped include/job) that, on a **macOS runner with Xcode**:
  - builds the shared framework and the iOS app for an iOS Simulator destination,
  - boots/runs the app (or runs an XCUITest/UI test) in the simulator,
  - captures a **screenshot** and/or test results/logs and publishes them as **CI artifacts**.
- Keep the spike reproducible from CI with pinned/documented tool versions where practical.

## Non-goals
- Porting Solipsism’s browser UI, navigation, WebView/WKWebView parity, Antares/Servo engine, downloads, permissions, or any Android-specific feature to iOS.
- Modifying or depending on Solipsism’s production `:app` module.
- Interactive/manual iOS testing flows, device-farm/browser streaming services (Appetize/BrowserStack), or requiring a physical iPhone.
- App Store signing/distribution.

## Anti-goals
- “Making Solipsism run on iOS” in any user-facing sense.
- Coupling the experiment to production release/versioning or risking the main app build.

## Constraints
- iOS Simulator execution requires **macOS + Xcode** (Apple licensing). CI must run on a macOS environment.
- **GitLab CI** macOS runners: SaaS-hosted macOS availability/tags depend on the project’s GitLab plan/tier; if unavailable, the job must target a **registered self-managed macOS runner** with Xcode installed. The pipeline will use a configurable runner tag (e.g. `macos`, `saas-macos-medium-m1`) and document the requirement.
- The shared slice must be **pure common Kotlin** (stdlib/common APIs only) to keep the spike minimal and platform-neutral.
- This repo currently has an Android-only Gradle setup; the spike lives in its own directory/build to avoid impacting it.

## Approaches considered
1. **Integrated `:shared` KMP module in this repo (not chosen):** add shared module + `iosApp` wired into the main build and `:app`. *Trade-off:* proves sharing in the real codebase but adds integration risk/config churn to Solipsism’s build.
2. **Isolated spike subproject in repo (chosen):** `experiments/ios-kmp-ci-spike/` with its own Gradle + KMP + minimal iOS app; CI runs only this spike. *Trade-off:* safest/fastest proof of KMP+CI with no production impact, but integration into Solipsism’s main build is deferred.
3. **Compose Multiplatform shared UI spike (rejected):** overkill for a minimal proof and mismatched with Solipsism’s XML/View stack.

**Decision:** Approach (2), per request.

## Design (chosen parts)

### Project layout (proposed)
- `experiments/ios-kmp-ci-spike/`
  - `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, Gradle wrapper (or documented Gradle version)
  - `shared/` (KMP): `src/commonMain/kotlin/...` with the pure function; `androidMain`/`iosMain` as needed by targets; produces an iOS framework (e.g. via `binaries.framework` for simulator targets).
  - `iosApp/` (Xcode project, SwiftUI): minimal app that imports/displays the shared function output.
  - `README.md`: how to run locally (on macOS) and CI notes/runner tag.

### Shared slice
- One deterministic pure-Kotlin API, e.g. `fun spikeMessage(): String = "KMP on iOS: OK"` (exact name/content flexible), callable from Swift. Keep free of platform/Android types.

### CI (GitLab)
- A job (e.g. `ios-kmp-spike`) triggered for changes under `experiments/ios-kmp-ci-spike/**` and/or manually:
  - runs on a macOS/Xcode runner (documented tag),
  - steps (illustrative): assemble shared/framework → `xcodebuild` build/test for a simulator (e.g. `iPhone 16`/available runtime) → `xcrun simctl` boot/install/launch & `xcrun simctl io booted screenshot` if not covered by tests → collect `.xcresult`/screenshot/logs,
  - `artifacts`: screenshot(s), test results/logs (with reasonable expiry).
- Pipeline must fail clearly if no suitable macOS runner/Xcode is available, with README guidance.

### Verification (automated)
- CI green when: shared code compiles for iOS target, app builds for simulator, app launches/runs and produces a screenshot or passing UI/unit test that demonstrates the shared Kotlin output is present.

## Left to the builder (planning/implementation details)
- Exact Kotlin/Gradle/AGP/Xcode versions compatible with the chosen GitLab macOS runner image.
- Exact runner `tags`/image and simulator device/runtime selection available on that runner.
- Whether to assert via XCUITest, a unit test, or `simctl` launch + screenshot OCR/text check kept simple.
- Root `.gitlab-ci.yml` structure (new file or extend existing) and job rules/paths.
- Whether to include an Android target/app in the spike to demonstrate “shared” in-run (nice-to-have, minimal).

## Risks / notes
- Hosted GitLab macOS runners may be unavailable or quota-limited on the project’s plan; a self-managed macOS runner may be required (user confirmed GitHub Actions minutes exhausted, chose GitLab).
- Xcode/simulator/runtime availability varies by runner image; the job should select an available destination dynamically or document a fixed one.

## Approval
Implementation/planning proceeds only after this design is approved. After approval, the next step is **writing-plans** (no other implementation skill). 
