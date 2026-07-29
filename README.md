# Solipsism Browser

<p align="center">
  <img src="docs/logo.png" alt="Solipsism Browser logo" width="220">
</p>

Solipsism Browser is an Android WebView browser built around a rail-first, one-handed interface. It turns the usual browser chrome sideways: navigation, tabs, search, QR scanning, refresh, and the overflow menu live in a compact left or right rail, leaving the page itself as the main surface.

The current identity is privacy-aware, highly tactile, and mobile-native. Solipsism combines a Material Design 3 Expressive-inspired UI with compact rail modes, a customizable homepage, practical browsing tools, and privacy controls that are meant to feel close at hand instead of buried in menus.

## What Makes It Different

- **Rail-first browsing**: A left or right side rail keeps browser controls reachable without covering the web page.
- **Compact by design**: Small, Medium, Large, and Super Compact rail sizes let the UI adapt to different hands, screens, and habits.
- **Material 3 Expressive direction**: Rounded surfaces, motion, shadows, themed colors, and physical-feeling popup menus shape the app’s visual language.
- **Custom homepage**: Wallpaper support, bookmark shortcuts, and a quiet Solipsism start page replace the generic blank tab feeling.
- **Privacy tools**: Incognito browsing, ad blocking, cookie controls, WebRTC settings, clear-on-exit options, and Decoy Mode for history replacement.
- **Fast daily actions**: QR scanning, find in page, add bookmark, downloads, install website as app, share, copy link, and history tools are designed for mobile use.

## Core Features

- WebView browsing with tabs, tab overview, and animated tab switching from the URL rail.
- Vertical URL rail with a horizontal full-URL editor when needed.
- Built-in QR scanner with light and dark mode support.
- Bookmark list and homepage bookmark shortcuts.
- History page with Clear All History and Decoy Mode.
- Download handling with a donation prompt section.
- Install Website as App using Android pinned shortcuts.
- File upload support through Android’s file picker.
- Fullscreen video support with system bars and rail hidden during landscape fullscreen playback.
- Material-style settings with grouped sections, search, appearance controls, privacy controls, and accessibility options.

## Privacy And Decoy Mode

Solipsism includes conventional browser privacy features such as incognito mode, ad blocking, cookie controls, stored data cleanup, and WebRTC toggles.

Decoy Mode is a more opinionated Solipsism feature: from the history page, long-pressing Clear All History can replace recent browsing history with randomized, realistic-looking browsing sessions. The generated activity follows coherent browsing paths across search, Amazon, Reddit, and YouTube-style destinations instead of creating a flat list of unrelated searches.

## Appearance

The app is designed around the rail:

- Put the rail on the left or right.
- Choose rail size, including Super Compact mode.
- Match rail colors to Android system theming.
- Use dark or light mode-sensitive UI.
- Customize the homepage wallpaper or keep it blacked out.

## Build

Requirements:

- Android Studio or Android SDK command line tools
- JDK 17 or newer

Debug build:

```powershell
.\gradlew.bat assembleSolipsismBrowserDebug
```

Release build:

```powershell
.\gradlew.bat assembleSolipsismBrowserRelease
```

Lint:

```powershell
.\gradlew.bat lintSolipsismBrowserDebug
```

Generated APKs are written under:

```text
app/build/outputs/apk/
```

## Permissions

Declared permissions:

- `INTERNET`: access the web.
- `ACCESS_NETWORK_STATE`: respond to network availability.
- `INSTALL_SHORTCUT`: support installed website shortcuts.
- `POST_NOTIFICATIONS`: display browser notifications.

Requested only when needed:

- `CAMERA`: QR scanning and optional WebRTC video capture.
- `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`: optional WebRTC audio capture.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: website location requests when enabled.

## Support

If Solipsism is useful to you, development can be supported on [Ko-fi](https://ko-fi.com/kennethchoinfosec).

## License

Solipsism Browser remains licensed under the Mozilla Public License 2.0. See [LICENSE](LICENSE).
