# Solipsism Browser — verified feature inventory

**Purpose:** the single source of truth for every feature claim the website makes.
**Compiled:** 4 November 2026 from the `website` branch (`4d30cde`), `main`, and the public release feed.
**Rule:** if a claim is not in this file with evidence, it does not go on the website.

## Evidence key

| Code | Source                                  |
| ---- | --------------------------------------- |
| `R`  | `README.md`                             |
| `C`  | `CHANGELOG.md`                          |
| `P`  | `app/src/main/res/xml/preference_*.xml` |
| `S`  | `app/src/main/res/values/strings.xml`   |
| `A`  | `docs/ANTARES_ENGINE.md` (on `main`)    |
| `G`  | GitHub release `v9.4.0`                 |
| `B`  | `app/build.gradle.kts`                  |

## Product facts

| Fact                 | Value                                                                                             | Evidence                  |
| -------------------- | ------------------------------------------------------------------------------------------------- | ------------------------- |
| Name                 | Solipsism Browser                                                                                 | `R`                       |
| Application ID       | `com.krystelligence.solipsism`                                                                    | `R`, `B`                  |
| Developer            | Kenneth-Cho-InfoSec                                                                               | `R`                       |
| Licence              | Mozilla Public License 2.0                                                                        | `R`, repository `LICENSE` |
| Latest release       | 9.4.0, published 24 October 2026                                                                  | `G`                       |
| Release asset        | `Solipsism-Browser-v9.4.0.apk` (single APK)                                                       | `G`                       |
| Minimum Android      | 8.0 (API 26)                                                                                      | `B` (`minSdk = 26`)       |
| Target Android       | 36                                                                                                | `B` (`targetSdk = 36`)    |
| Stable browser core  | Android System WebView                                                                            | `R`, `A`, `G`             |
| Experimental core    | Antares 0.5.0, separate companion package `com.krystelligence.antares`                            | `A`, `G`                  |
| Antares requirements | Android 13+, 64-bit ARM or x86, matching package name, signing digest and AIDL protocol version 9 | `A`                       |
| Heritage             | Continuation fork of Lightning Browser                                                            | `R`                       |
| Distribution         | GitHub Releases; F-Droid submission in progress                                                   | `R`                       |

> The repository `README.md` still advertises v6.1.0 and `build.gradle.kts` reports `versionName = "6.1.6"`. Both are stale relative to the published 9.4.0 release. The website reads the version from the GitHub API and must not repeat either string as fact.

## Navigation and browsing

| Feature                  | State         | Detail                                                                                                                                                | Evidence                        |
| ------------------------ | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| Rail-first navigation    | Stable        | Compact rail on the left or right holds tabs, search, refresh and tools                                                                               | `R`                             |
| Rail sizes               | Stable        | Small, Medium, Large, Super Compact                                                                                                                   | `R`, `P` (`settings_rail_size`) |
| Rail side                | Stable        | Left or right                                                                                                                                         | `R`, `P`                        |
| Top/bottom rail layouts  | Experimental  | Behind Debug settings                                                                                                                                 | `R`                             |
| Rail & Menu Studio       | Stable        | Move actions between rail and overflow, drag to reorder, up to eight movable rail actions, five quick actions, duplicate prevention, restore defaults | `R`                             |
| Tabs and tab overview    | Stable        | Multiple tabs, overview switcher                                                                                                                      | `R`                             |
| URL/search rail          | Stable        | Vertical bar with expanded URL editor                                                                                                                 | `R`                             |
| QR scanning              | Stable        | Optional downloadable scanner module                                                                                                                  | `R`                             |
| Find in page             | Stable        | —                                                                                                                                                     | `R`                             |
| Long-press refresh       | Stable        | Reload with JavaScript disabled                                                                                                                       | `R`                             |
| Fullscreen video         | Stable        | Rail and system-bar handling; landscape orientation                                                                                                   | `R`, `C`                        |
| File upload              | Stable        | Android system picker                                                                                                                                 | `R`                             |
| Install website as app   | Stable        | Android shortcut                                                                                                                                      | `R`                             |
| Folding-phone user agent | Optional      | Selectable user agent                                                                                                                                 | `R`                             |
| Text-to-speech           | Accessibility | Page reading                                                                                                                                          | `R`                             |

## Privacy and security

| Feature                         | State                        | Detail                                                                                                                                          | Evidence                           |
| ------------------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| Incognito browsing              | Stable                       | Separate session                                                                                                                                | `R`                                |
| Cookie Manager                  | Stable                       | View, edit, remove per-site cookies                                                                                                             | `R`                                |
| Third-party cookie control      | Stable                       | Toggle                                                                                                                                          | `P` (`third_party`)                |
| Do Not Track                    | Stable                       | Sends DNT header                                                                                                                                | `P` (`do_not_track`)               |
| Remove identifying headers      | Stable                       | Header stripping                                                                                                                                | `P` (`remove_identifying_headers`) |
| WebRTC configuration            | Stable                       | Enable/disable                                                                                                                                  | `P` (`webrtc_support`)             |
| Clear on exit                   | Stable                       | Cache, history, cookies, web storage, individually                                                                                              | `P`                                |
| Safe Browsing                   | Conditional                  | Available when the installed WebView provider supports it                                                                                       | `R`                                |
| URL hardening                   | Stable                       | Checks for `intent://`, `file://`, `content://`, `data:`, `javascript:`                                                                         | `R`                                |
| Canonical-path checks           | Stable                       | For app-generated internal HTML pages                                                                                                           | `R`                                |
| Decoy Mode                      | Stable                       | Replaces recent history with randomised coherent sessions; opened by long-pressing Clear All History                                            | `R`, `C`                           |
| Site permissions (standard)     | Stable where WebView permits | Location, camera, microphone, notifications, clipboard, motion sensors, protected content, embedded content, local network, automatic downloads | `R`                                |
| Site permissions (experimental) | Experimental                 | NFC, USB, serial, file editing, VR, AR, device use, apps on device, JavaScript JIT — best-effort policy signals                                 | `R`                                |

## Content blocking

| Feature                   | State             | Detail                                                                        | Evidence |
| ------------------------- | ----------------- | ----------------------------------------------------------------------------- | -------- |
| Host-based blocking       | Stable            | Host lists block ad and tracker domains                                       | `R`      |
| Cosmetic filtering        | Subset            | uBlock-style `##` hide rules and `#@#` exceptions where WebView supports them | `R`      |
| Custom filter rules       | Stable            | User-authored rules with in-app filter-language help                          | `R`      |
| Element picker            | Stable            | Create a rule from a page element                                             | `R`      |
| Animated GIF blocking     | Optional          | —                                                                             | `R`      |
| Host-list sources         | Stable            | Selectable sources and manual refresh                                         | `R`      |
| Full uBlock Origin parity | **Not supported** | WebView-safe subset only                                                      | `R`      |

## Userscripts

| Feature                 | State             | Detail                                                               | Evidence |
| ----------------------- | ----------------- | -------------------------------------------------------------------- | -------- |
| Userscript manager      | Experimental      | Global enable/disable, list, manage                                  | `R`      |
| Import from local `.js` | Experimental      | File picker                                                          | `R`      |
| Import from HTTPS URL   | Experimental      | —                                                                    | `R`      |
| Metadata                | Experimental      | `@match`, `@include`, `@exclude`, `@run-at`, `@name`, `@description` | `R`      |
| Lifecycle injection     | Experimental      | Supported document lifecycle points                                  | `R`      |
| Tampermonkey API parity | **Not supported** | Privileged APIs are not emulated                                     | `R`      |

## Download safety

| Feature                    | State              | Detail                                                              | Evidence |
| -------------------------- | ------------------ | ------------------------------------------------------------------- | -------- |
| Local definitions scanning | Stable             | Works without an API key                                            | `R`      |
| Definition refresh         | Stable             | Configurable in settings                                            | `R`      |
| Image and video scanning   | Optional           | Enabled separately                                                  | `R`      |
| VirusTotal scanning        | Optional           | Requires the user's own API key                                     | `R`      |
| Download choices           | Stable             | Scan, download without scanning, or cancel                          | `R`      |
| Suspicious-file blocking   | Stable             | Detection prompt                                                    | `R`      |
| Detection accuracy         | **Not guaranteed** | App states the scanner is an aid, not a complete security programme | `R`      |

## Homepage and appearance

| Feature                        | State    | Detail                                                                     | Evidence |
| ------------------------------ | -------- | -------------------------------------------------------------------------- | -------- |
| Built-in homepage              | Stable   | Wallpaper, bookmarks, motto, clock                                         | `R`      |
| Wallpaper presentation         | Stable   | Light, dark, non-AMOLED, AMOLED-black                                      | `R`      |
| Wallpaper position and opacity | Stable   | —                                                                          | `R`      |
| Bookmark shortcuts             | Stable   | Visibility and column count                                                | `R`      |
| Motto                          | Stable   | Text, size, opacity                                                        | `R`      |
| Date and time                  | Stable   | Format and opacity                                                         | `R`, `P` |
| Static HTML/CSS import         | Stable   | Sanitised, app-private, no scripts/forms/frames/downloads/remote resources | `R`      |
| In-app HTML/CSS editor         | Stable   | —                                                                          | `R`      |
| HTTPS homepage                 | Optional | Restricted safe mode                                                       | `R`      |
| Themes                         | Stable   | Light, dark, true AMOLED black                                             | `R`      |
| Accent palettes                | Stable   | Multiple palettes, Android system accent matching, dynamic colour          | `R`, `P` |
| Custom font import             | Stable   | Local file, size validated                                                 | `R`      |
| Text and layout controls       | Stable   | Text size, reflow, wide viewport, overview mode, rendering                 | `R`, `P` |
| Audio effects                  | Stable   | Global toggle, presets under Graphics                                      | `R`, `P` |

## Tools

| Feature              | State    | Detail                                                                              | Evidence |
| -------------------- | -------- | ----------------------------------------------------------------------------------- | -------- |
| Screenshot capture   | Stable   | Captures page without the rail; shrink animation, rounded corners, flash, vibration | `R`      |
| Screenshot Studio    | Stable   | Save and image-search workflows                                                     | `R`      |
| Image to JPEG        | Optional | Automatic conversion of downloaded images                                           | `R`      |
| Bookmarks            | Stable   | —                                                                                   | `R`      |
| History              | Stable   | Includes Decoy Mode                                                                 | `R`      |
| Downloads            | Stable   | Android `DownloadManager`                                                           | `R`      |
| Share and copy link  | Stable   | —                                                                                   | `R`      |
| Release notes in app | Stable   | Reads the GitHub release feed; can be disabled                                      | `R`      |
| Update reminders     | Optional | Stable releases only; links to the GitHub APK; can be disabled                      | `R`      |

## Antares (experimental core)

| Fact                | Detail                                                                                                              | Evidence |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- | -------- |
| Nature              | Servo continuation fork, separate APK, own process                                                                  | `A`      |
| Why separate        | Servo's native library is much larger; Android cannot remove native libraries at runtime                            | `A`      |
| IPC                 | AIDL protocol version 9, byte-for-byte compatible on both sides                                                     | `A`      |
| Verification        | Android 13+, 64-bit ARM/x86, exact package name, signing-certificate SHA-256, protocol version                      | `A`      |
| Switching           | Global to the session; prepared before commit; URL order, active index, tab type and incognito separation preserved | `A`      |
| Failure behaviour   | Existing tabs stay in place if preparation fails                                                                    | `A`      |
| State transfer      | History stacks, form state, media state and cookies are engine-private and not promised across a switch             | `A`      |
| Automatic switching | Never; core changes only on explicit user selection                                                                 | `A`      |
| Coordinate bridge   | Debug-only, disabled by default, doubles page requests, blocks taps when targets differ                             | `A`      |
| Stable fallback     | Android WebView                                                                                                     | `A`, `G` |

## Explicitly not claimed

The website must never state or imply:

- that Solipsism blocks all ads or trackers
- that its malware scanner guarantees safety
- that userscripts are Tampermonkey-compatible in full
- that site permissions are fully enforced where WebView does not expose them
- that Antares is stable, faster, or universally compatible
- that the app collects no data at all (only that the website runs no analytics)
- any user count, benchmark, award, rating, or testimonial
- support for iOS, desktop, or any platform other than Android
