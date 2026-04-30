# Solipsism

<p align="center">
  <img src="docs/logo.png" alt="Solipsism browser logo" width="220">
</p>

Solipsism is a focused Android browser with a calm right-rail interface, full WebView browsing, native tabs, bookmarks, history, downloads, incognito browsing, ad blocking, search suggestions, settings, and a built-in QR scanner for opening links directly in the current tab.

## Highlights

- Minimal right-side browser rail with back, forward, home, refresh, tabs, overflow, and QR scan controls.
- Address/search overlay that accepts direct URLs or search terms.
- Built-in QR scanner using the device camera to scan website links.
- Tab overview, bookmarks, history, downloads, find-in-page, sharing, and copy-link actions.
- Existing Solipsism privacy features, including incognito browsing, ad blocking, cookie controls, WebRTC settings, and clear-on-exit options.
- Material-style settings screens with grouped rows, icons, and themed switches.

## Build

Requirements:

- Android Studio or Android SDK command line tools
- JDK 17

Debug build:

```powershell
.\gradlew.bat assembleSolipsismPlusDebug
```

The generated APK is written to:

```text
app/build/outputs/apk/solipsismPlus/debug/app-solipsismPlus-debug.apk
```

Lint:

```powershell
.\gradlew.bat lintSolipsismPlusDebug
```

## Permissions

Automatically granted or declared:

- `INTERNET`: access the web.
- `ACCESS_NETWORK_STATE`: respond to network availability.
- `INSTALL_SHORTCUT`: support add-to-home-screen shortcuts.
- `POST_NOTIFICATIONS`: display browser notifications.

Requested only when needed:

- `CAMERA`: QR scanning and optional WebRTC video capture.
- `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`: optional WebRTC audio capture.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: website location requests when enabled.

## License

This project is based on Solipsism and remains licensed under the Mozilla Public License 2.0. See [LICENSE](LICENSE).
