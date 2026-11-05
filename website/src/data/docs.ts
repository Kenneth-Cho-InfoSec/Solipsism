export type Block =
  | { type: 'p'; html: string }
  | { type: 'ul'; items: string[] }
  | { type: 'ol'; items: string[] }
  | { type: 'code'; code: string }
  | {
      type: 'callout';
      tone: 'note' | 'warn' | 'experimental';
      title: string;
      html: string;
    }
  | { type: 'table'; head: string[]; rows: string[][] }
  | { type: 'dl'; items: { term: string; html: string }[] };

export interface DocSection {
  id: string;
  heading: string;
  blocks: Block[];
}

export interface DocPage {
  slug: string;
  nav: string;
  title: string;
  description: string;
  lead: string;
  sections: DocSection[];
}

export interface DocNavGroup {
  label: string;
  items: { label: string; path: string }[];
}

export const DOC_NAV: DocNavGroup[] = [
  {
    label: 'Start',
    items: [
      { label: 'Overview', path: '/docs/' },
      { label: 'Installation', path: '/docs/installation/' },
      { label: 'Getting started', path: '/guides/getting-started/' },
    ],
  },
  {
    label: 'Using Solipsism',
    items: [
      { label: 'Privacy controls', path: '/docs/privacy/' },
      { label: 'Blocking', path: '/docs/blocking/' },
      { label: 'Userscripts', path: '/docs/userscripts/' },
      { label: 'Appearance and access', path: '/docs/appearance/' },
    ],
  },
  {
    label: 'Browser cores',
    items: [{ label: 'Antares', path: '/docs/antares/' }],
  },
  {
    label: 'Help',
    items: [
      { label: 'Troubleshooting', path: '/docs/troubleshooting/' },
      { label: 'Development', path: '/docs/development/' },
    ],
  },
];

export const DOC_PAGES: DocPage[] = [
  {
    slug: 'installation',
    nav: 'Installation',
    title: 'Install and update Solipsism',
    description:
      'Download the official Solipsism Browser APK, check what you are installing, update in place, and remove it cleanly.',
    lead: 'Solipsism is distributed as a signed APK on GitHub Releases. There is no store account, no installer service, and no build variant to choose for a normal install.',
    sections: [
      {
        id: 'requirements',
        heading: 'Requirements',
        blocks: [
          {
            type: 'ul',
            items: [
              'Android 8.0 (API 26) or newer.',
              'An installed Android System WebView provider for the stable core.',
              'Roughly 40–80 MB of free storage, depending on the release and your WebView data.',
            ],
          },
          {
            type: 'callout',
            tone: 'note',
            title: 'One APK in 9.4.0',
            html: 'The 9.4.0 release contains a single asset, <code>Solipsism-Browser-v9.4.0.apk</code>. Later releases may change their assets; read the release notes for the version you install.',
          },
        ],
      },
      {
        id: 'download',
        heading: 'Download the APK',
        blocks: [
          {
            type: 'p',
            html: 'Open the <a href="https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases/latest" target="_blank" rel="noopener noreferrer">latest release</a> and download the APK attached to it. The download button on this site points at the exact asset published by the project. It is not a mirror.',
          },
          {
            type: 'p',
            html: 'Do not install an APK copied from a download aggregator. A repackaged build can carry a different signing key and cannot update over an official install.',
          },
        ],
      },
      {
        id: 'verify',
        heading: 'Check what you downloaded',
        blocks: [
          {
            type: 'p',
            html: 'The release body names the signing identity and whether the build updates in place. 9.4.0 uses the same signing key as 9.3.1, so it installs as an in-place update over that version.',
          },
          {
            type: 'p',
            html: 'Android verifies the package signature at install time. If the signature does not match the installed app, Android refuses the update rather than replacing it.',
          },
        ],
      },
      {
        id: 'install',
        heading: 'Install',
        blocks: [
          {
            type: 'ol',
            items: [
              'Open the downloaded file from your notification shade or file manager.',
              'Follow Android’s prompt for that installation source.',
              'Keep the “install unknown apps” permission limited to the app you actually use to install packages. Revoke it afterwards if you do not need it.',
              'Launch Solipsism from your app list.',
            ],
          },
        ],
      },
      {
        id: 'update',
        heading: 'Update',
        blocks: [
          {
            type: 'p',
            html: 'Install the newer APK over the existing app. Solipsism keeps its data when the signature matches. In-app update reminders read the official GitHub release feed, can be limited to stable releases, and can be turned off in About settings.',
          },
          {
            type: 'callout',
            tone: 'note',
            title: 'Antares is a separate package',
            html: 'If you installed the Antares companion, update it from its own release page. The 9.4.0 notes say to uninstall Antares 0.4.0 before installing 0.5.0.',
          },
        ],
      },
      {
        id: 'uninstall',
        heading: 'Uninstall',
        blocks: [
          {
            type: 'p',
            html: 'Uninstalling Solipsism removes its app-private data, including bookmarks, history, custom homepage files and imported fonts. Clear anything you want to keep before you uninstall, and remove the Antares package separately if you installed it.',
          },
        ],
      },
    ],
  },

  {
    slug: 'privacy',
    nav: 'Privacy controls',
    title: 'Privacy controls and their limits',
    description:
      'How incognito, cookie controls, header stripping, site permissions and clear-on-exit work in Solipsism, and where Android WebView sets the boundary.',
    lead: 'Solipsism gives you switches for the things WebView exposes. It does not pretend to control the things WebView hides.',
    sections: [
      {
        id: 'model',
        heading: 'The model',
        blocks: [
          {
            type: 'p',
            html: 'The browser is a shell around Android System WebView. Cookies, storage, cache and rendering belong to that engine. Solipsism can clear them, scope them and request behaviour, but it cannot add an enforcement layer the engine does not provide.',
          },
          {
            type: 'p',
            html: 'That is why the settings separate <strong>standard controls</strong> from controls labelled <strong>Experimental</strong>. The label is a statement about enforcement, not about how finished the UI looks.',
          },
        ],
      },
      {
        id: 'cookies',
        heading: 'Cookies and storage',
        blocks: [
          {
            type: 'ul',
            items: [
              'The Cookie Manager lists cookies per site and lets you edit or remove them.',
              'Third-party cookies have a separate switch.',
              'Cache, history, cookies and web storage can each be cleared on demand or on exit.',
              'Incognito browsing uses a separate session so ordinary history is not extended.',
            ],
          },
        ],
      },
      {
        id: 'headers',
        heading: 'Headers and tracking signals',
        blocks: [
          {
            type: 'p',
            html: 'Do Not Track sends the DNT header. Sites may ignore it. “Remove identifying headers” strips selected request headers that are commonly used to correlate sessions. WebRTC can be disabled entirely; it is off by default.',
          },
          {
            type: 'callout',
            tone: 'warn',
            title: 'A header is a request, not a guarantee',
            html: 'DNT and header removal reduce the information a cooperating site receives. They do not stop fingerprinting by a site that does not cooperate.',
          },
        ],
      },
      {
        id: 'permissions',
        heading: 'Site permissions',
        blocks: [
          {
            type: 'p',
            html: 'Standard controls cover location, camera, microphone, notifications, clipboard, motion sensors, protected content, embedded content, local network access and automatic downloads.',
          },
          {
            type: 'p',
            html: 'These are labelled Experimental because WebView does not reliably expose or enforce them independently: NFC, USB, serial devices, file editing, virtual reality, augmented reality, device use, apps on the device, and JavaScript JIT.',
          },
          {
            type: 'callout',
            tone: 'experimental',
            title: 'Best-effort signals',
            html: 'Experimental permission entries are policy signals where enforcement is unavailable. Do not treat them as a desktop-style permission sandbox.',
          },
        ],
      },
      {
        id: 'clearing',
        heading: 'Clearing data',
        blocks: [
          {
            type: 'dl',
            items: [
              {
                term: 'Clear on exit',
                html: 'Turn on cache, history, cookie or web-storage clearing individually. Each is independent, so you can keep history while dropping cookies.',
              },
              {
                term: 'Clear now',
                html: 'The same categories can be cleared on demand from Privacy settings.',
              },
              {
                term: 'Decoy Mode',
                html: 'Long-press Clear All History to replace recent history with randomised, coherent-looking sessions. It changes the local history list; it does not change what a network observed.',
              },
            ],
          },
        ],
      },
      {
        id: 'limits',
        heading: 'What this does not do',
        blocks: [
          {
            type: 'ul',
            items: [
              'It does not hide your IP address from sites or your network.',
              'It does not prevent a site from fingerprinting your device and WebView version.',
              'It does not enforce every permission the web platform defines.',
              'It does not make an untrusted download safe.',
            ],
          },
        ],
      },
    ],
  },

  {
    slug: 'blocking',
    nav: 'Blocking',
    title: 'Blocking ads and elements',
    description:
      'Host lists, cosmetic filters, custom rules and the element picker in Solipsism, and the WebView limits that make this a subset rather than a full uBlock Origin engine.',
    lead: 'Blocking in Solipsism is network filtering plus cosmetic hiding, implemented against what WebView exposes. It is deliberately described as a subset.',
    sections: [
      {
        id: 'how',
        heading: 'How blocking works',
        blocks: [
          {
            type: 'ol',
            items: [
              'A host list blocks requests to known ad and tracker domains.',
              'Cosmetic rules hide matching elements that remain in the page.',
              'Custom rules let you add either kind yourself.',
              'The element picker writes a cosmetic rule from a page element.',
            ],
          },
        ],
      },
      {
        id: 'hosts',
        heading: 'Host lists',
        blocks: [
          {
            type: 'p',
            html: 'Choose the host-list sources you want and refresh them manually. A larger list blocks more, and also breaks more sites. Keep a short list of exceptions for the sites you use daily rather than disabling blocking everywhere.',
          },
        ],
      },
      {
        id: 'cosmetic',
        heading: 'Cosmetic rules',
        blocks: [
          {
            type: 'table',
            head: ['Syntax', 'Meaning', 'Example'],
            rows: [
              [
                '<code>##selector</code>',
                'Hide matching elements everywhere',
                '<code>##[data-ad-slot]</code>',
              ],
              [
                '<code>domain##selector</code>',
                'Hide only on that domain',
                '<code>example.com##.sponsored</code>',
              ],
              [
                '<code>domain#@#selector</code>',
                'Make an exception on that domain',
                '<code>news.example#@#.promo</code>',
              ],
            ],
          },
          {
            type: 'p',
            html: 'The selector after the separator is ordinary CSS. If a rule hides too much, make the selector more specific rather than turning blocking off.',
          },
        ],
      },
      {
        id: 'custom',
        heading: 'Custom rules and the element picker',
        blocks: [
          {
            type: 'p',
            html: 'The custom filter editor has the filter-language help beside it. The element picker is the safer route when you can see the element but do not know its selector: it derives the rule from the element you point at.',
          },
          {
            type: 'p',
            html: 'You can also stop animated GIFs from playing. That is a comfort setting, not a privacy control.',
          },
        ],
      },
      {
        id: 'limits',
        heading: 'Limits',
        blocks: [
          {
            type: 'callout',
            tone: 'warn',
            title: 'Not a full uBlock Origin',
            html: 'WebView does not expose the extension APIs a desktop blocker relies on. Expect a focused subset of filter syntax and behaviour, and expect some sites to need an exception.',
          },
        ],
      },
    ],
  },

  {
    slug: 'userscripts',
    nav: 'Userscripts',
    title: 'Userscripts',
    description:
      'Import, manage and scope userscripts in Solipsism: supported metadata, lifecycle injection, security expectations and the privileged APIs that are not emulated.',
    lead: 'The userscript manager runs ordinary JavaScript in pages you choose. It supports common metadata and lifecycle points. It is not the Tampermonkey extension API.',
    sections: [
      {
        id: 'what',
        heading: 'What a userscript is here',
        blocks: [
          {
            type: 'p',
            html: 'A userscript is a JavaScript file with a metadata block at the top. Solipsism reads that block, decides whether the current page matches, and injects the script at a supported lifecycle point.',
          },
          {
            type: 'p',
            html: 'The runtime can be disabled globally. Individual scripts can be enabled, disabled or removed.',
          },
        ],
      },
      {
        id: 'import',
        heading: 'Import',
        blocks: [
          {
            type: 'ul',
            items: [
              'From a local <code>.js</code> file on the device.',
              'From an HTTPS URL.',
            ],
          },
          {
            type: 'p',
            html: 'Prefer a source you can read. A URL import fetches whatever that server returns at that moment; it is not pinned to a hash.',
          },
        ],
      },
      {
        id: 'metadata',
        heading: 'Supported metadata',
        blocks: [
          {
            type: 'table',
            head: ['Key', 'Effect'],
            rows: [
              ['<code>@name</code>', 'Display name in the manager'],
              ['<code>@description</code>', 'Short description in the manager'],
              ['<code>@match</code>', 'Pages the script may run on'],
              ['<code>@include</code>', 'Additional page patterns'],
              ['<code>@exclude</code>', 'Pages the script must not run on'],
              ['<code>@run-at</code>', 'Supported injection point'],
            ],
          },
        ],
      },
      {
        id: 'security',
        heading: 'Security',
        blocks: [
          {
            type: 'callout',
            tone: 'warn',
            title: 'Scripts are executable code',
            html: 'A userscript can read and change the page it runs in. Install only scripts whose source you trust, and remove scripts you no longer use.',
          },
        ],
      },
      {
        id: 'limits',
        heading: 'What is not supported',
        blocks: [
          {
            type: 'ul',
            items: [
              'Privileged Tampermonkey APIs are not emulated.',
              'Scripts that depend on extension storage, cross-origin requests or manager UI will not work as written.',
              'There is no silent fallback: an unsupported call fails rather than pretending to succeed.',
            ],
          },
        ],
      },
    ],
  },

  {
    slug: 'appearance',
    nav: 'Appearance and access',
    title: 'Appearance, homepage and access',
    description:
      'Themes, AMOLED black, accent palettes, rail sizing, homepage customisation and its sandbox, plus the accessibility controls in Solipsism.',
    lead: 'The browser should fit the hand, the eyes and the screen in front of you. These settings are the ones that change that fit.',
    sections: [
      {
        id: 'themes',
        heading: 'Themes and colour',
        blocks: [
          {
            type: 'ul',
            items: [
              'Light, dark and true AMOLED black.',
              'Multiple accent palettes, plus Android system accent matching.',
              'Dynamic colour updates where the platform supports them.',
              'A black status bar option and fullscreen behaviour controls.',
            ],
          },
          {
            type: 'callout',
            tone: 'note',
            title: 'AMOLED black is a real theme',
            html: 'It is not a darker grey. Background pixels are turned off, which is why the homepage wallpapers have a separate AMOLED presentation.',
          },
        ],
      },
      {
        id: 'rail',
        heading: 'Rail size and layout',
        blocks: [
          {
            type: 'p',
            html: 'The rail can sit on the left or right and comes in Small, Medium, Large and Super Compact. Rail & Menu Studio moves supported actions between the rail and the overflow menu, reorders rail icons by drag, and restores the default layout.',
          },
          {
            type: 'p',
            html: 'Up to eight movable rail actions are supported. Tabs and the menu action are permanent, and the same action cannot appear in both places.',
          },
        ],
      },
      {
        id: 'homepage',
        heading: 'Homepage',
        blocks: [
          {
            type: 'p',
            html: 'The built-in homepage shows bookmark shortcuts, a wallpaper, a motto, a clock and a configurable date. Bookmark visibility, column count, motto size and opacity, wallpaper position and time format are all adjustable.',
          },
          {
            type: 'p',
            html: 'You can also import static HTML and CSS, or edit it in the app. An HTTPS homepage is available in a restricted safe mode.',
          },
        ],
      },
      {
        id: 'sandbox',
        heading: 'The homepage sandbox',
        blocks: [
          {
            type: 'p',
            html: 'Custom homepage files are sanitised and stored in app-private storage. For static custom homepages, JavaScript, downloads, forms, frames, remote resources, executable content and file access are disabled.',
          },
          {
            type: 'callout',
            tone: 'note',
            title: 'Private by construction',
            html: 'Homepage file navigation is restricted to Solipsism-generated private pages. Arbitrary public-storage files are not accepted as browser pages.',
          },
        ],
      },
      {
        id: 'access',
        heading: 'Accessibility',
        blocks: [
          {
            type: 'ul',
            items: [
              'Text size, text reflow, wide viewport and overview mode.',
              'Custom local font import with size validation.',
              'Text-to-speech for page content.',
              'Fullscreen video handling that keeps the rail reachable.',
            ],
          },
        ],
      },
      {
        id: 'audio',
        heading: 'Audio',
        blocks: [
          {
            type: 'p',
            html: 'Graphics contains a global audio-effects toggle and equaliser presets. The toggle applies across the browser rather than per tab.',
          },
        ],
      },
    ],
  },

  {
    slug: 'antares',
    nav: 'Antares',
    title: 'Antares: the experimental core',
    description:
      'What Antares is, why it ships as a separate package, what Solipsism verifies before enabling it, what happens to your tabs on a switch, and why WebView remains the stable fallback.',
    lead: 'Antares is a Servo continuation fork that Solipsism can use instead of Android WebView. It is experimental, optional, separately installed, and never selected for you.',
    sections: [
      {
        id: 'what',
        heading: 'What it is',
        blocks: [
          {
            type: 'p',
            html: 'Solipsism 9.4.0 bundles support for Antares 0.5.0, rebuilt from 703 upstream Servo commits with refreshed Stylo and an updated Android platform-window lifecycle. The engine runs in its own process and is distributed as a separate package, <code>com.krystelligence.antares</code>.',
          },
          {
            type: 'p',
            html: 'Android WebView remains the complete, production-compatible core and the default.',
          },
        ],
      },
      {
        id: 'separate',
        heading: 'Why it is a separate package',
        blocks: [
          {
            type: 'p',
            html: 'Servo’s native library is much larger than Solipsism itself. Android cannot remove native libraries from an installed APK at runtime, so a “download on first run” flow would only create the illusion of saving space. A separate package keeps the engine out of a WebView user’s storage and makes installation, update, signature and removal explicit.',
          },
        ],
      },
      {
        id: 'verification',
        heading: 'What is verified before it runs',
        blocks: [
          {
            type: 'ol',
            items: [
              'Android 13 or newer.',
              'A 64-bit ARM or x86 device.',
              'The exact Antares package name.',
              'The engine signing-certificate SHA-256 digest.',
              'AIDL protocol version 9 after binding.',
            ],
          },
          {
            type: 'callout',
            tone: 'experimental',
            title: 'Experimental',
            html: 'Passing these checks proves identity and compatibility of the contract. It does not make the engine a stable renderer for every site.',
          },
        ],
      },
      {
        id: 'switching',
        heading: 'Switching cores',
        blocks: [
          {
            type: 'p',
            html: 'The selected core applies to all tabs in a browser session. A switch is prepared before it is committed: Solipsism reconstructs each replacement tab, preserves URL order, the active-tab index, tab type and incognito process separation, then swaps the pager in one commit. If preparation fails, existing tabs stay in place.',
          },
          {
            type: 'p',
            html: 'Solipsism never changes the core because a site or feature opened. There are no automatic compatibility warnings for particular websites. The change happens only when you select it in Debug Settings.',
          },
        ],
      },
      {
        id: 'state',
        heading: 'What does not transfer',
        blocks: [
          {
            type: 'ul',
            items: [
              'Page history stacks.',
              'Form state.',
              'Media playback state.',
              'Cookies.',
            ],
          },
          {
            type: 'p',
            html: 'These belong to the engine. A switch is a new browsing context, not a migration of the old one.',
          },
        ],
      },
      {
        id: 'diagnostics',
        heading: 'The coordinate bridge',
        blocks: [
          {
            type: 'p',
            html: 'Debug Settings includes an optional Antares coordinate bridge. For each foreground Antares tab it loads the same HTTP or HTTPS address in an inaccessible Chromium WebView behind the Antares surface. Before a tap is sent to Antares, both engines describe the nearest interactive DOM target. Matching targets allow the tap; different targets, unavailable probes and timeouts block it and write both descriptions to Logcat.',
          },
          {
            type: 'callout',
            tone: 'warn',
            title: 'It doubles page requests',
            html: 'The bridge is disabled by default and doubles page requests while active. It does not copy a live DOM, cookies, storage, JavaScript state or pixels into Antares. Use it to identify reproducible differences, not to repair a page at runtime.',
          },
        ],
      },
      {
        id: 'fallback',
        heading: 'Falling back',
        blocks: [
          {
            type: 'p',
            html: 'Select Android WebView in the core chooser and reload. If a site shows a blank surface, fails a CAPTCHA, or will not play media under Antares, WebView is the supported path.',
          },
        ],
      },
    ],
  },

  {
    slug: 'troubleshooting',
    nav: 'Troubleshooting',
    title: 'Troubleshooting',
    description:
      'Fix a blank page, a permission prompt that never appears, an APK that will not install, a download that will not open, or a userscript that does nothing.',
    lead: 'Most problems are one of five causes: the WebView provider, an Android permission, the selected core, a blocking rule, or a build from the wrong source.',
    sections: [
      {
        id: 'blank',
        heading: 'A page is blank or broken',
        blocks: [
          {
            type: 'ol',
            items: [
              'Update Android System WebView and Android itself.',
              'If Antares is selected, switch back to Android WebView and reload.',
              'Disable cosmetic filtering for that site and reload.',
              'Try the page in incognito to rule out a cookie or extension-like injection.',
            ],
          },
        ],
      },
      {
        id: 'permissions',
        heading: 'A camera or location prompt never appears',
        blocks: [
          {
            type: 'p',
            html: 'Check the Android app permission first, then the site-specific setting inside Solipsism. Availability also depends on the WebView provider and the device. Some entries are experimental and may not be independently enforceable.',
          },
        ],
      },
      {
        id: 'install',
        heading: 'The APK will not install',
        blocks: [
          {
            type: 'ul',
            items: [
              'Confirm the Android version meets the release requirement.',
              'Use the APK attached to the official release, not a mirror.',
              'If an older build is installed with a different signing key, uninstall it first, and accept that its data will be removed.',
            ],
          },
        ],
      },
      {
        id: 'downloads',
        heading: 'A download is blocked or will not open',
        blocks: [
          {
            type: 'p',
            html: 'Read the detection details and verify the source. The scanner can produce false positives and can miss threats. Do not disable scanning for an unknown file just to get past a warning. Downloads are handed to Android’s DownloadManager, so a missing file may also be a storage-permission or free-space problem.',
          },
        ],
      },
      {
        id: 'userscripts',
        heading: 'A userscript does nothing',
        blocks: [
          {
            type: 'ul',
            items: [
              'Check that the runtime is enabled globally and the script is enabled.',
              'Check @match, @include and @exclude against the page URL.',
              'Check whether the script depends on a privileged API that is not emulated.',
              'Open the page again after enabling the script; injection happens at load time.',
            ],
          },
        ],
      },
      {
        id: 'report',
        heading: 'Reporting a problem',
        blocks: [
          {
            type: 'p',
            html: 'Include the app version, Android version, device model where relevant, WebView provider, selected core, and the steps that led to the issue. Remove private URLs, account details, API keys and browsing data from screenshots and logs before posting publicly.',
          },
          {
            type: 'p',
            html: 'Open an issue at <a href="https://github.com/Kenneth-Cho-InfoSec/Solipsism/issues" target="_blank" rel="noopener noreferrer">github.com/Kenneth-Cho-InfoSec/Solipsism/issues</a>.',
          },
        ],
      },
    ],
  },

  {
    slug: 'development',
    nav: 'Development',
    title: 'Build and contribute',
    description:
      'Build Solipsism from source, run the tests, understand the module layout, and follow the release process without shipping secrets.',
    lead: 'The Android app and the website are separate builds. You need the Android SDK and JDK for the app; the website needs Node.js 22 or newer.',
    sections: [
      {
        id: 'android',
        heading: 'Build the Android app',
        blocks: [
          {
            type: 'p',
            html: 'The project targets compile SDK 36 with a minimum of API 26. Open the repository in Android Studio, or use the Gradle wrapper:',
          },
          {
            type: 'code',
            code: './gradlew :app:assembleSolipsismBrowserDebug\n./gradlew :app:assembleSolipsismBrowserRelease\n./gradlew :app:testSolipsismBrowserDebugUnitTest',
          },
          {
            type: 'p',
            html: 'Localised resources are checked separately:',
          },
          {
            type: 'code',
            code: 'python3 scripts/check_localized_resources.py',
          },
        ],
      },
      {
        id: 'website',
        heading: 'Build the website',
        blocks: [
          {
            type: 'p',
            html: 'The website is a static Astro application in <code>website/</code>. It is deployed from the <code>website</code> branch to GitHub Pages under <code>/Solipsism/</code>.',
          },
          {
            type: 'code',
            code: 'cd website\nnpm ci\nnpm run dev\nnpm run build',
          },
          {
            type: 'p',
            html: 'Quality gates: <code>npm run format:check</code>, <code>npm run lint</code>, <code>npm run typecheck</code>, <code>npm test</code>, <code>npm run test:e2e</code>, then <code>npm run build</code>.',
          },
        ],
      },
      {
        id: 'layout',
        heading: 'Where things live',
        blocks: [
          {
            type: 'dl',
            items: [
              {
                term: '<code>app/</code>',
                html: 'Android application, Kotlin sources, preferences, resources.',
              },
              {
                term: '<code>website/</code>',
                html: 'Astro site, release client, tests, design records.',
              },
              {
                term: '<code>docs/</code>',
                html: 'Antares engine notes and project screenshots.',
              },
              {
                term: '<code>scripts/</code>',
                html: 'Repository checks, including localised-resource validation.',
              },
            ],
          },
        ],
      },
      {
        id: 'secrets',
        heading: 'Secrets',
        blocks: [
          {
            type: 'callout',
            tone: 'warn',
            title: 'Never commit signing material',
            html: 'Release signing credentials, API keys and private configuration stay outside the repository. Certificate digests are public metadata and are not secrets; private keys are.',
          },
        ],
      },
      {
        id: 'release',
        heading: 'Release process',
        blocks: [
          {
            type: 'p',
            html: 'A release publishes a signed APK on GitHub Releases. The website reads release names, notes, dates, assets and download URLs at runtime from the GitHub API, validates them, and caches them for five minutes. A release event also triggers a site redeploy.',
          },
        ],
      },
    ],
  },
];
