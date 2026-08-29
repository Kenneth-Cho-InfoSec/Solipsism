# Antares experimental browser core

Solipsism 7 introduces a global browser-core boundary. Android WebView remains the complete,
production-compatible core. Antares is an experimental Servo continuation fork distributed as a
separate F-Droid package at `com.krystelligence.antares`.

## Why Antares is separate

Servo's native library is much larger than Solipsism. Keeping it in a separate package means a
WebView user does not store the engine. Android cannot remove native libraries from an installed
APK at runtime, so a first-run in-app deletion flow would only create the illusion of saving that
space.

The Antares package runs its engine in its own process. Solipsism connects through version 9 of a
small AIDL protocol and embeds the interactive page surface with `SurfaceControlViewHost`. Both
copies of the AIDL contract must remain byte-for-byte compatible.

## Trust and compatibility

Solipsism verifies all of the following before enabling Antares:

- Android 13 or newer
- a 64-bit ARM or x86 device
- the exact Antares package name
- the engine signing-certificate SHA-256 digest
- protocol version 9 after binding

Debug builds also trust an engine signed with the same debug certificate. Release builds require
one or more comma-separated certificate digests through the Gradle property
`antaresCertSha256`. Certificate digests are public metadata, not secrets.

## Global switching

The selected core applies to all tabs in a browser session. A switch is prepared before it is
committed. Solipsism reconstructs every replacement tab, keeps URL order, the active-tab index,
tab type, and incognito process separation, then swaps the pager as one commit. Existing tabs stay
in place if preparation fails.

Solipsism never changes the selected core just because a site or feature is opened. There are no
automatic compatibility warnings for Google, YouTube, or any other website. Core changes happen
only when the user explicitly selects one in Debug Settings.

History stacks, form state, media state, and cookies are engine-private and are not promised across
a core switch.

## Coordinate diagnostics

Debug Settings includes an optional **Antares coordinate bridge**. For each foreground Antares tab,
it loads the same HTTP or HTTPS address in an inaccessible Chromium WebView behind the Antares
surface. Before a page tap is sent to Antares, both engines describe the nearest interactive DOM
target. Matching semantic targets allow the native Antares tap; different targets, unavailable
probes, and timeouts block it and write both descriptions to Logcat.

This mode is disabled by default and doubles page requests while active. It does not copy a live
WebView DOM, cookies, storage, JavaScript state, or rendered pixels into Antares. The two engines
remain independent browsing contexts, so the diagnostic identifies reproducible compatibility
differences for a later Antares source fix instead of trying to repair a page at runtime.

## Local builds

Build Solipsism normally:

```bash
./gradlew assembleSolipsismBrowserDebug
```

Build the x86-64 Antares APK for the Android emulator from the Antares repository:

```bash
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/28.2.13676358"
./mach build --locked --target x86_64-linux-android --dev
```

Build ARM64 for a physical Android 13+ device by replacing the target with
`aarch64-linux-android`.

## Distribution

Solipsism and Antares use separate source builds. Solipsism never downloads or executes an engine
binary itself. Users download Antares from the
[Antares GitHub releases](https://github.com/Kenneth-Cho-InfoSec/Antares/releases), which keeps
installation, update, signature, and removal behaviour explicit and verifiable.
