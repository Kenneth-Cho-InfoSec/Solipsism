# iOS KMP CI Spike (isolated experiment)

Minimal proof that a **pure Kotlin Multiplatform** slice runs on iOS and can be verified in CI (no iPhone required). This experiment is isolated under `experiments/ios-kmp-ci-spike/` and does **not** modify Solipsism’s production `:app`.

## Shared contract
- Kotlin API: `com.solipsism.spike.spikeMessage(): String`
- Deterministic expected output (must be visible in the iOS app / CI proof): **`KMP on iOS: OK`**

## Layout
- `shared/` – KMP module (`commonMain/commonTest`, `jvm` for host tests, iOS targets with `Shared` framework).
- `iosApp/` – minimal SwiftUI app that imports `Shared` and displays `SpikeKt.spikeMessage()`.

## Local (host-side, Linux/macOS with JDK)
```bash
cd experiments/ios-kmp-ci-spike
JAVA_HOME=${JAVA_HOME:-/path/to/jdk21} ./gradlew :shared:jvmTest
```

## iOS build/run (requires macOS + Xcode)
Build the framework for the desired simulator, then build/run the app. Example (Apple Silicon simulator):
```bash
cd experiments/ios-kmp-ci-spike
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath build/DerivedData \
  FRAMEWORK_SEARCH_PATHS="$(pwd)/shared/build/bin/iosSimulatorArm64/debugFramework" \
  build
```
For Intel simulators use `:shared:linkDebugFrameworkIosX64` and the corresponding `.../iosX64/debugFramework` path. CI should select an available destination (e.g. from `xcrun simctl list devices available`).

To capture a screenshot in CI after install/launch (illustrative):
```bash
xcrun simctl boot "<SIM_UDID>" || true
xcrun simctl bootstatus "<SIM_UDID>" -b
xcrun simctl install booted build/DerivedData/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.solipsism.spike.iosApp
mkdir -p artifacts
xcrun simctl io booted screenshot artifacts/ios-spike.png
```

## GitLab CI
Job: `ios-kmp-spike` (root `.gitlab-ci.yml`). Runs on a **macOS runner with Xcode**.
- Runner tag: set `KMP_IOS_RUNNER_TAG` CI variable to your macOS runner tag (default in YAML: `macos`). If GitLab SaaS macOS runners are unavailable for your plan/tier, register/use a **self-managed macOS runner** with Xcode and matching tag.
- Artifacts: `experiments/ios-kmp-ci-spike/artifacts/` (screenshots/logs, `expire_in: 1 week`, uploaded `when: always`).

The job fails clearly if not running on macOS or if Xcode/simulator tooling is missing.
