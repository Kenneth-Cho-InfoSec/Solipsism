# iOS KMP CI Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an isolated `experiments/ios-kmp-ci-spike/` KMP experiment (pure-Kotlin shared slice + minimal SwiftUI iOS app shell) and a GitLab CI job that builds/runs it on an iOS Simulator and publishes screenshot/test artifacts, without touching Solipsism’s production `:app`.

**Architecture:** A standalone Gradle project under `experiments/ios-kmp-ci-spike/` defines a Kotlin Multiplatform `:shared` module with a deterministic pure function in `commonMain` (consumed by Android/iOS targets and covered by `commonTest`). A minimal Xcode `iosApp` displays that shared output so CI can prove execution in Simulator. GitLab CI (`.gitlab-ci.yml`) runs on a tagged macOS/Xcode runner to build the framework/app, execute in simulator, and upload artifacts.

**Tech Stack:** Kotlin Multiplatform (Kotlin Gradle plugin), Gradle (wrapper in experiment), Swift/SwiftUI (Xcode project), `xcodebuild` + `xcrun simctl`, GitLab CI YAML.

**Spec:** `docs/superpowers/specs/2026-11-14-ios-kmp-ci-spike-design.md`

## Global Constraints

- Keep the spike **isolated** in `experiments/ios-kmp-ci-spike/`; do **not** modify Solipsism’s production `:app`, root Android build logic, or app behavior.
- Shared slice must be **pure common Kotlin** (stdlib/common APIs only, no Android/iOS framework types).
- CI must target **GitLab CI** on a **macOS runner with Xcode** (iOS Simulator); runner tag/image is environment-dependent and must be configurable/documented (SaaS macOS or self-managed).
- Automated verification only: build + simulator launch/test + screenshot or test result artifacts (no physical device, no interactive/manual flow).
- Pin/document tool versions as required by the chosen runner; prefer explicit, reproducible commands.

## Review Focus

1. **No production impact:** changes must not alter `:app` sources/resources/build outputs or root Android app behavior; only additive experiment/CI/docs files.
2. **Shared API is pure/common:** `commonMain` must not import `android.*`, `platform.*`, or other platform frameworks; function deterministic and callable from Swift.
3. **CI runner portability/failure clarity:** job uses a documented/configurable macOS tag and fails with actionable message when macOS/Xcode/simulator unavailable (not a silent skip that reports success).
4. **Simulator execution proves shared output:** artifact/test must demonstrate the app ran and surfaced the shared Kotlin value (not just that compilation succeeded).
5. **Artifact collection robustness:** screenshots/logs/xcresult (as applicable) are uploaded on success/failure where possible with sensible expiry.

---

### Task 1: Record approved spec in version control

**Files:**
- Add: `docs/superpowers/specs/2026-11-14-ios-kmp-ci-spike-design.md`
- Add: `docs/superpowers/plans/2026-11-14-ios-kmp-ci-spike.md` (this plan)

**Interfaces:**
- Consumes: approved design content (already written).
- Produces: committed spec/plan docs for traceability; no runtime code.

- [ ] **Step 1: Verify docs exist and match approved scope**

Run: `sed -n '1,40p' docs/superpowers/specs/2026-11-14-ios-kmp-ci-spike-design.md && echo "---" && sed -n '1,20p' docs/superpowers/plans/2026-11-14-ios-kmp-ci-spike.md`
Expected: spec shows “Isolated Experiment”, approach (2) chosen, GitLab CI automated simulator flow; plan header present.

- [ ] **Step 2: Stage and commit docs**

```bash
git add docs/superpowers/specs/2026-11-14-ios-kmp-ci-spike-design.md docs/superpowers/plans/2026-11-14-ios-kmp-ci-spike.md
git commit -m "docs: add iOS KMP CI spike spec and plan"
```
Expected: commit created containing only the two doc files (plus any intentional doc dir scaffolding).

---

### Task 2: Scaffold isolated Gradle KMP experiment and shared pure function (TDD)

**Files:**
- Create: `experiments/ios-kmp-ci-spike/settings.gradle.kts`
- Create: `experiments/ios-kmp-ci-spike/build.gradle.kts`
- Create: `experiments/ios-kmp-ci-spike/gradle.properties`
- Create: `experiments/ios-kmp-ci-spike/shared/build.gradle.kts`
- Create: `experiments/ios-kmp-ci-spike/shared/src/commonMain/kotlin/com/solipsism/spike/Spike.kt`
- Create: `experiments/ios-kmp-ci-spike/shared/src/commonTest/kotlin/com/solipsism/spike/SpikeTest.kt`
- Create: `experiments/ios-kmp-ci-spike/gradlew`, `experiments/ios-kmp-ci-spike/gradle/wrapper/*` (or document/use a pinned wrapper setup)

**Interfaces:**
- Consumes: none (new isolated project).
- Produces: `com.solipsism.spike.spikeMessage(): String` in commonMain returning deterministic `"KMP on iOS: OK"`; iOS framework baseName `Shared` for simulator targets (e.g. `iosSimulatorArm64`, `iosX64`, and/or `iosArm64` as needed) so the iOS app/CI can consume it.

- [ ] **Step 1: Write the failing shared test**

Create `experiments/ios-kmp-ci-spike/shared/src/commonTest/kotlin/com/solipsism/spike/SpikeTest.kt`:
```kotlin
package com.solipsism.spike

import kotlin.test.Test
import kotlin.test.assertEquals

class SpikeTest {
    @Test
    fun spikeMessage_isDeterministic() {
        assertEquals("KMP on iOS: OK", spikeMessage())
    }
}
```

- [ ] **Step 2: Run test to verify it fails (or project not yet buildable)**

Run: `cd experiments/ios-kmp-ci-spike && ./gradlew :shared:allTests --console=plain 2>&1 | tail -40` (or `:shared:jvmTest` / appropriate task once scaffolded; if wrapper/DSL absent expect failure indicating missing build/function)
Expected: FAIL (missing `spikeMessage`, missing Gradle config, or test failure) — not a passing green build.

- [ ] **Step 3: Implement minimal isolated KMP scaffold and `spikeMessage(): String`**

- `settings.gradle.kts`: `rootProject.name = "ios-kmp-ci-spike"` and `include(":shared")` with plugin repositories (mavenCentral, google as needed) and pluginManagement; keep isolated from root repo build.
- Root `build.gradle.kts`: apply Kotlin Multiplatform plugin version (pin a stable version compatible with runner JDK/Xcode; document in README/gradle.properties) as needed for subproject.
- `gradle.properties`: enable defaults suitable for KMP (e.g. `kotlin.code.style=official`) and any required flags.
- `shared/build.gradle.kts`: `kotlin {` targets including JVM/Android not required but at least iOS simulator targets for framework and a host-testable target if convenient; define `iosSimulatorArm64()` and `iosX64()` (and `iosArm64()` if desired) with `binaries.framework { baseName = "Shared"; isStatic = true }`; `sourceSets { val commonMain/commonTest ... }` with `kotlin("test")` for commonTest.
- `shared/src/commonMain/kotlin/com/solipsism/spike/Spike.kt`:
```kotlin
package com.solipsism.spike

fun spikeMessage(): String = "KMP on iOS: OK"
```
Add Gradle wrapper files so `./gradlew` works inside the experiment (copy/init consistent pinned version).

- [ ] **Step 4: Run tests to verify pass**

Run: `cd experiments/ios-kmp-ci-spike && ./gradlew :shared:allTests --console=plain 2>&1 | tail -60` (or the project’s host/common test task that runs without macOS, e.g. jvm/linux test target if configured)
Expected: PASS for `SpikeTest.spikeMessage_isDeterministic` (exit 0 for the executed test task).

- [ ] **Step 5: Commit**

```bash
git add experiments/ios-kmp-ci-spike
git commit -m "feat: add isolated KMP shared spike module and tests"
```

---

### Task 3: Minimal SwiftUI iOS app shell consuming the shared framework

**Files:**
- Create: `experiments/ios-kmp-ci-spike/iosApp/iosApp.xcodeproj/project.pbxproj` (and required xcodeproj package files)
- Create: `experiments/ios-kmp-ci-spike/iosApp/iosApp/iosAppApp.swift`
- Create: `experiments/ios-kmp-ci-spike/iosApp/iosApp/ContentView.swift`
- Create: `experiments/ios-kmp-ci-spike/iosApp/iosApp/Info.plist` (if needed by project)
- Create/Modify: `experiments/ios-kmp-ci-spike/iosApp/iosApp/Assets.xcassets/*` (minimal, if required)
- (Optional) Create: `experiments/ios-kmp-ci-spike/iosApp/iosAppUITests/*` if choosing XCUITest-based proof

**Interfaces:**
- Consumes: `Shared` framework produced by `:shared` for simulator (`import Shared` / Kotlin-generated `SpikeKt.spikeMessage()` or platform-accessible name per Kotlin/Native export).
- Produces: a launchable iOS Simulator app that renders the string returned by `spikeMessage()` visibly (e.g. `Text(SpikeKt.spikeMessage())` or equivalent), enabling screenshot/test verification.

- [ ] **Step 1: Define the display contract (failing/unchecked before wiring)**

Create a short check script or document the expected UI contract in `experiments/ios-kmp-ci-spike/README.md` stub: app screen must show text `KMP on iOS: OK`. (Automated iOS assertion runs in CI; local macOS verification is environment-dependent.)
Run: `grep -n "KMP on iOS: OK" experiments/ios-kmp-ci-spike/README.md`
Expected: line found documenting the expected visible output.

- [ ] **Step 2: Implement minimal Xcode project and SwiftUI UI**

- Create a minimal iOS app target (SwiftUI lifecycle) named `iosApp` with deployment target compatible with CI simulator runtimes.
- `iosAppApp.swift`: standard `@main` App launching `ContentView`.
- `ContentView.swift`: display shared message, e.g.
```swift
import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        Text(SpikeKt.spikeMessage())
            .padding()
    }
}
```
(adjust call to the actual Kotlin/Native exported symbol; keep it the only shared dependency).
- Wire framework linkage: either a Gradle task/run-script phase invoking `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (with correct env vars when built from Xcode) or a CI-friendly pre-build step (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`/appropriate task) plus `FRAMEWORK_SEARCH_PATHS`/embed settings in the Xcode project. Choose the simpler, CI-reproducible path and make paths relative to `experiments/ios-kmp-ci-spike`.
- Ensure the scheme is shared/buildable via `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build` (command documented).

- [ ] **Step 3: Add a lightweight verification hook for CI**

Add either (a) a minimal XCUITest that asserts static text `KMP on iOS: OK` exists, or (b) document/implement a `simctl` launch + `xcrun simctl io booted screenshot` flow to be used by CI. Record the chosen command(s) in README and ensure project/scheme supports it (`xcodebuild test ...` or build+install+launch steps).
Run: `test -f experiments/ios-kmp-ci-spike/iosApp/iosApp.xcodeproj/project.pbxproj && echo OK`
Expected: `OK`.

- [ ] **Step 4: Sanity-check Gradle/framework build commands locally where possible**

Run: `cd experiments/ios-kmp-ci-spike && ./gradlew :shared:tasks --all --console=plain 2>&1 | grep -i framework | head -20`
Expected: framework-related tasks listed for iOS targets (confirms wiring). Full `xcodebuild` simulator run may require macOS; if unavailable, note in commit/README and rely on CI job.

- [ ] **Step 5: Commit**

```bash
git add experiments/ios-kmp-ci-spike/iosApp experiments/ios-kmp-ci-spike/README.md
git commit -m "feat: add minimal SwiftUI iosApp shell for KMP spike"
```

---

### Task 4: GitLab CI pipeline and runner/artifact documentation

**Files:**
- Create: `.gitlab-ci.yml` (root) — new file; define `ios-kmp-spike` job scoped to experiment (and manual/path rules as appropriate)
- Create/Modify: `experiments/ios-kmp-ci-spike/README.md` — complete usage, runner requirements, and artifact description

**Interfaces:**
- Consumes: experiment build commands from Tasks 2–3 (`./gradlew ...`, `xcodebuild ...`, `xcrun simctl ...`).
- Produces: CI job `ios-kmp-spike` running on macOS/Xcode-tagged runner that builds, runs in iOS Simulator, and uploads screenshot/test artifacts (`.png`, logs, and/or `.xcresult`) proving shared output; clear failure when environment missing.

- [ ] **Step 1: Write README requirements (acts as executable spec for CI expectations)**

In `experiments/ios-kmp-ci-spike/README.md` document: purpose, required macOS runner + Xcode, configurable tag variable (e.g. `KMP_IOS_RUNNER_TAG` default suggestion), exact local/CI commands, expected visible output `KMP on iOS: OK`, and artifact paths.
Run: `grep -n "KMP_IOS_RUNNER_TAG\|KMP on iOS: OK\|artifacts" experiments/ios-kmp-ci-spike/README.md`
Expected: matching lines present.

- [ ] **Step 2: Add `.gitlab-ci.yml` job (initial version)**

Define:
```yaml
stages: [test]

ios-kmp-spike:
  stage: test
  tags:
    - ${KMP_IOS_RUNNER_TAG:-macos}
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      changes:
        - experiments/ios-kmp-ci-spike/**/*
        - .gitlab-ci.yml
    - if: '$CI_COMMIT_BRANCH'
      changes:
        - experiments/ios-kmp-ci-spike/**/*
        - .gitlab-ci.yml
    - when: manual
      allow_failure: true
  script:
    - set -euo pipefail
    - uname -a
    - xcodebuild -version
    - cd experiments/ios-kmp-ci-spike
    - ./gradlew :shared:allTests --console=plain
    - ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --console=plain # or task matching chosen target
    - # build + run/test on simulator; capture screenshot to ../artifacts/ (commands per Task 3 choice)
  artifacts:
    when: always
    paths:
      - experiments/ios-kmp-ci-spike/artifacts/
    expire_in: 1 week
```
Adjust simulator destination to an available runtime (discover via `xcrun simctl list devices available` or `xcodebuild -showdestinations`) and ensure artifact directory is created. Prefer failing fast with clear `echo` if not on Darwin/Xcode.

- [ ] **Step 3: Validate YAML syntax locally**

Run: `ruby -e "require 'yaml'; YAML.load_file('.gitlab-ci.yml'); puts 'YAML_OK'" 2>&1 || python3 -c "import yaml,sys; yaml.safe_load(open('.gitlab-ci.yml')); print('YAML_OK')" 2>&1 | tail -5`
Expected: `YAML_OK` (if a YAML parser is available); otherwise manually verify structure and run `git diff --check`.

- [ ] **Step 4: Verify no production app files changed**

Run: `git status --short && echo "---" && git diff --name-only -- app/ 2>&1 | wc -l`
Expected: changed files limited to docs/experiments/.gitlab-ci.yml as intended; count for `app/` is `0`.

- [ ] **Step 5: Commit**

```bash
git add .gitlab-ci.yml experiments/ios-kmp-ci-spike/README.md
git commit -m "ci: add GitLab job for isolated iOS KMP simulator spike"
```

---

### Task 5: Final verification and handoff notes

**Files:**
- Modify (if needed): `experiments/ios-kmp-ci-spike/README.md`
- Verify: all files from Tasks 1–4

**Interfaces:**
- Consumes: completed experiment and CI config.
- Produces: documented, reviewable spike ready for GitLab macOS runner execution; explicit notes on runner tag/simulator selection.

- [ ] **Step 1: Run local host-side checks available in this environment**

Run: `cd experiments/ios-kmp-ci-spike && ./gradlew :shared:allTests --console=plain 2>&1 | tail -30`
Expected: PASS.

Run: `python3 -c "import pathlib; p=pathlib.Path('.gitlab-ci.yml'); print('exists', p.exists())"`
Expected: `exists True`.

- [ ] **Step 2: Confirm isolation and spec alignment**

Run: `git diff --name-only HEAD~3..HEAD 2>&1 | sort` (adjust range to cover spike commits) and visually confirm no `app/` paths.
Expected: only `docs/superpowers/...`, `experiments/ios-kmp-ci-spike/...`, `.gitlab-ci.yml`.

- [ ] **Step 3: Document environment caveat**

Ensure README states: iOS Simulator build/run requires macOS+Xcode runner; if GitLab SaaS macOS is unavailable set `KMP_IOS_RUNNER_TAG` to a self-managed macOS runner tag. No code change beyond docs if already clear.
Run: `grep -n "self-managed\|SaaS" experiments/ios-kmp-ci-spike/README.md`
Expected: caveat present.

- [ ] **Step 4: Final commit (if any doc tweaks)**

```bash
git add -A docs/superpowers experiments/ios-kmp-ci-spike .gitlab-ci.yml
git commit -m "docs: finalize iOS KMP CI spike notes" || echo "nothing to commit"
```

- [ ] **Step 5: Report execution readiness**

Summarize: created paths, how to trigger `ios-kmp-spike` in GitLab, required runner tag/Xcode, and that real simulator execution proof occurs on macOS CI (not verifiable on this Linux host). 
