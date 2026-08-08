# Solipsism Browser

Solipsism is an open-source, privacy-focused Android browser built around a rail-first interface. The rail keeps navigation, tabs and browser actions within easy reach while leaving the webpage as the main visual surface.

> Screenshots in this guide should be captured from the current app build. The paths below are reserved for the real emulator screenshots.

## Quick start

1. Install Solipsism from the [GitHub Releases](https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases) page or from [F-Droid](https://f-droid.org/).
2. Open a new tab from the tab button at the top of the rail.
3. Enter a web address or search term in the address field.
4. Use the rail for back, forward, home, bookmarks, tabs, refresh and more actions.

![Solipsism homepage and rail](docs/screenshots/home-rail-light.png)

## The rail

The rail is designed for one-handed browsing. It contains the most frequently used browser actions and can be moved to suit your grip.

### Change the rail position and size

Open **Settings → Display → Rail position** to choose the left or right side. Use **Settings → Display → Rail size** to choose a compact, medium or larger layout. Some additional rail layouts are experimental and may not be optimised for every screen size.

![Rail position and size settings](docs/screenshots/rail-settings.png)

## Tabs and browsing

- Tap the tab counter to open the tab overview.
- Tap **+** to create a tab.
- Use the tab overview to switch, close or reorder tabs.
- Long-press links to open them in the current tab, a new tab or a background tab.
- Use the back and forward buttons on the rail to move through page history.
- Pull down or use the refresh action to reload the current page.

![Tab overview](docs/screenshots/tab-overview.png)

## Privacy and security

### Incognito browsing

Open a private tab from the tab menu. Incognito tabs keep their browsing session separate from normal tabs. Private browsing does not make you anonymous to websites, networks or your internet provider.

### Per-site permissions

Use the site information or permissions controls for the current page to review and change access to the camera, microphone, location, notifications and other WebView permissions.

### Cookies and identifying headers

Open **Settings → Privacy** to manage cookies, third-party cookies, Do Not Track and identifying request headers. Cookie controls can affect sign-ins and website functionality.

### Cookie Manager (advanced)

Open **Settings → Privacy → Cookie Manager**. The manager first asks for an HTTP or HTTPS URL. This URL defines the cookie scope that Android WebView exposes; it is not a request to load the page.

The manager can:

- List cookies visible to the selected URL.
- Refresh the list after a page or cookie change.
- Add a cookie by entering its name, value and cookie attributes.
- Edit an existing cookie.
- Delete one cookie.
- Clear all cookies visible to the selected URL.

Cookie values are masked in the list because they can contain login sessions. Never paste a session cookie into an issue report or share it in a screenshot. Clearing a site’s cookies can sign you out, remove preferences and invalidate shopping baskets or other local sessions.

The manager is deliberately URL-scoped. Android WebView does not expose every stored cookie domain and path, so **Clear site** is best-effort rather than a guarantee that every related subdomain has been removed. If a site still appears signed in, repeat the operation for the relevant subdomain or use **Settings → Privacy → Clear browser cookies** to clear the complete browser cookie store.

Cookie behaviour also depends on the switches in **Settings → Privacy**:

- **Enable cookies** controls normal cookie storage.
- **Block third-party cookies** prevents cookies from third-party contexts where WebView supports the policy.
- **Clear cookies on exit** removes cookies when the normal browsing session exits.
- Incognito cookie behaviour follows the regular cookie preferences, and the incognito process is reset after its session ends.

Cookie attributes such as expiry, path, secure and same-site restrictions can prevent a manually added cookie from being sent. Use the manager for troubleshooting and controlled testing, not as a way to bypass a website’s authentication or security controls.

### Safe Browsing and dangerous schemes

Solipsism uses Android WebView security services where available and blocks or hardens handling of dangerous URL schemes. Protection depends on the installed WebView provider and Android version.

![Privacy settings](docs/screenshots/privacy-settings.png)

## Ad and tracker blocking

Solipsism includes host-based advertising and tracker blocking, cosmetic filtering and support for custom filter rules. The element picker can hide unwanted page elements.

Blocking lists and cosmetic rules can occasionally affect legitimate page content. If a site is broken, temporarily review the site’s blocking or permission settings before adding an exception.

> Solipsism is not a complete uBlock Origin implementation. Its filters and controls are designed for the Android WebView environment.

![Ad blocking settings and element picker](docs/screenshots/ad-blocking.png)

## Userscripts

Userscript support is available for Tampermonkey/Greasemonkey-style scripts. Add or manage scripts from the userscript settings and enable them for supported pages.

### Configure userscripts

1. Open **Settings → Userscripts**.
2. Enable **Enable userscripts**.
3. Choose one of the installation methods:
   - **Import from file** to select a `.user.js` or JavaScript file from device storage.
   - **Install from HTTPS URL** to download a script and its HTTPS `@require` dependencies.
   - **Write userscript** to create a script or paste one from the clipboard.
4. Open **Installed scripts** to review, edit or delete scripts.

A script must contain a valid metadata block with a name and at least one `@match` or `@include` rule. The source is limited to 1 MiB. Scripts run only when userscripts are enabled and the current page matches their metadata rules.

Solipsism executes only scripts using `@grant none`. Scripts that request privileged APIs can be saved for inspection, but `GM_*` APIs and other native Tampermonkey APIs are not available. `@require` dependencies must use HTTPS. Review every script and dependency before installing it: userscripts can read and change the pages on which they run.

Userscripts run inside Android WebView and do not provide the complete desktop Tampermonkey API. Some APIs, pages, frames and script behaviours may be unavailable or experimental.

![Userscript settings](docs/screenshots/userscripts.png)

## Downloads and safety

### Download manager

Solipsism can use its built-in download handling or hand downloads to a custom download manager. Open **Settings → General → Custom download manager** to select an installed manager or add one by Android package name.

The target application must be installed and accept download intents. If it is unavailable, Solipsism falls back to its normal download flow.

When an external download manager is selected, the download is handed to that application. External handling bypasses Solipsism’s image conversion and external malware-scanning pipeline, so use the built-in flow when those protections are required.

### Image downloads

When **Save images as JPEG** is enabled in **Settings → General**, supported raster images are converted to `.jpg` when saved. This is enabled by default and can be disabled. Conversion may remove transparency and is not suitable for images that require an alpha channel.

### Malware scanning

Solipsism’s Malware Scanner checks eligible downloads before they are saved. Local scanning uses a SHA-256 Bloom-filter database maintained by the Hypatia Databases project. It is enabled by default; definitions are downloaded before the first scan and can be refreshed manually.

### Configure local scanning

Open **Settings → Malware Scanner** and leave **Scan downloads for malware** enabled. Use **Update malware definitions** to fetch the latest validated database. **Automatically update definitions** checks weekly and keeps the on-device database within its 10 MiB limit. The definitions source and attribution are available from the same screen.

If a file matches the local known-malware database, Solipsism blocks it and does not save it. Bloom filters can produce rare false positives, so a block is a reason to investigate the source rather than proof of a particular malware family.

### Optional VirusTotal second opinion

VirusTotal is disabled as a cloud service by default and requires your own personal API key:

1. Open **Settings → Malware Scanner → VirusTotal**.
2. Review the VirusTotal privacy information.
3. Add your personal API key. It is encrypted with Android Keystore and is not written to downloads or logs.
4. Enable **Use VirusTotal as a second opinion**.
5. Choose whether to scan image and video downloads. Both options are off by default to reduce the exposure of personal files and large uploads.

After the local check, eligible files may be queried by hash or uploaded to VirusTotal for analysis. Unknown files can be shared with VirusTotal and its security partners. Files are staged privately and saved only after the scan passes. A VirusTotal detection blocks the download and can provide a report link.

If the API key is missing, invalid or rate-limited, or the file exceeds the 650 MB staging limit, the scan cannot complete. The download prompt offers **Download (Skip Scanning)** when appropriate; use it only when you understand the risk. A network timeout or failed verification does not silently save the file.

No scan can identify every malicious file. A clean result is not a guarantee of safety, and this feature does not replace device security, backups, sandboxing or careful download verification.

![Download and safety settings](docs/screenshots/download-safety.png)

## Appearance and accessibility

Open **Settings → Display** or **Settings → Accessibility** to customise:

- Light, dark and AMOLED-black themes
- Wallpapers and accent colours
- Custom fonts and text scaling
- Reflow and zoom controls
- Zoom override for pages that disable pinch zoom
- Full-screen mode and optional rail hiding
- Reduced visual effects and other accessibility preferences

AMOLED mode uses true-black primary surfaces, including the navigation rail, to reduce power use on OLED displays. Elevated controls may retain contrast so they remain usable.

![AMOLED mode](docs/screenshots/amoled-rail.png)

## Haptic feedback

Open **Settings → Haptics** to configure tactile feedback independently from audio. Android’s vibration setting and the device hardware still determine whether feedback can be felt.

### Global and feature controls

1. Enable **Enable haptics** to allow Solipsism to vibrate.
2. Use **Screenshot haptics** to control the confirmation vibration after a screenshot. Its duration and intensity can be adjusted and tested.
3. Use **Rail swipe haptics** to control feedback while moving between rail states. Adjust its intensity and use **Test rail vibration** before settling on a level.
4. Enable **Tab-switch completion feedback** for a completion pulse after changing tabs.
5. Open **Interaction haptics** to enable or disable feedback independently for tabs, bookmarks, QR scans, downloads and malware scans, ad blocking, site permissions, and refresh/navigation actions.

The **Test haptic** action in each interaction editor lets you check the current duration and intensity without performing the underlying browser action. Turning off the global switch disables all of these feature-level settings without losing their individual values.

### Curves and presets

The rail response curve can be **Linear** or **Nonlinear ease-in/ease-out**. The nonlinear curve makes changes build and settle more gradually; Linear keeps the response proportional to the gesture.

**Interaction haptic presets** include Default, Pixel, Samsung and Accessibility optimised profiles. Applying a preset replaces the current interaction values. Use **Export current preset** to save a `solipsism-haptics-preset.json` file, or **Import preset file** to restore a compatible preset. Invalid files and unsupported values are rejected rather than partially applied.

![Haptics settings](docs/screenshots/haptics-settings.png)

## Full-screen mode

Use the full-screen action from the browser controls or configure the full-screen preference. When enabled, Android system bars can be hidden and the rail can optionally be hidden so the page uses the full width.

Full-screen behaviour varies by Android version, navigation mode and the installed WebView provider. The system may reveal its bars temporarily after a gesture.

![Full-screen browsing](docs/screenshots/fullscreen.png)

## User-agent switching

The user-agent control lets you switch between the default, mobile, desktop or a custom user-agent string. Some sites may require a reload after changing the selection.

Changing the user-agent can change the layout, available features and login behaviour of a website. It does not turn an Android WebView into a desktop browser.

![User-agent chooser](docs/screenshots/user-agent.png)

## Homepage customisation

The homepage can be customised with shortcuts, wallpapers, accent colours and layout preferences. Use the homepage edit controls or **Settings → Homepage** to change the appearance.

![Custom homepage](docs/screenshots/homepage-customisation.png)

## Productivity tools

Solipsism includes several tools designed for quick, focused browsing:

- QR-code scanning from the address controls
- Screenshot capture from the browser tools
- Bookmarks, history and download pages
- Decoy Mode for displaying an alternative homepage state
- Audio equaliser controls and presets
- Multiple supported interface languages

![Browser tools](docs/screenshots/browser-tools.png)

## WebView limitations and experimental features

Solipsism is an Android WebView browser. Website behaviour can therefore differ between Android versions and WebView providers. Examples include media playback, DRM, file uploads, fullscreen, Web APIs and content rendering.

Some features are experimental or limited by WebView, including advanced userscript APIs, certain filter rules, zoom overrides, top/bottom rail layouts and optional malware scanning. Please report reproducible problems with the Solipsism version, Android version, device model, WebView provider and affected URL when possible.

## Troubleshooting

### A page does not scroll or respond

Reload the page, check whether a permission or content-blocking rule is interfering, and try the same page with the site’s permissions reviewed. If the issue continues, record the Android and WebView versions.

### A website looks different after changing the user-agent

Reload the page after changing the user-agent. Some sites cache the previous layout or serve different capabilities to each user-agent.

### A download has the wrong filename

Check the download manager setting and whether the server supplied a useful filename or MIME type. For image downloads, verify the **Save images as JPEG** preference.

### Imported bookmarks are not visible

Allow the import to finish, return to the browser and reopen the bookmarks page. If bookmarks are still missing after restarting, report the source browser, bookmark count and import file type.

## Privacy and open source

Solipsism is distributed under the [Mozilla Public License 2.0](LICENSE). Source code, issues and release APKs are available in the [GitHub repository](https://github.com/Kenneth-Cho-InfoSec/Solipsism).

Third-party components and their licences are listed in **Settings → About → Open Source Licences**.

## Reporting a problem

Before opening an issue, update to the latest release and retest with the default settings where practical. Include:

- Solipsism version
- Android version and device model
- WebView provider and version
- Reproduction steps
- Expected and actual behaviour
- A screenshot or screen recording without private information

Report problems on the [GitHub issue tracker](https://github.com/Kenneth-Cho-InfoSec/Solipsism/issues).
