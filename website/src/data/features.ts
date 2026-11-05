export interface Feature {
  name: string;
  detail: string;
  limit?: string;
}

export interface FeatureGroup {
  id: string;
  title: string;
  summary: string;
  items: Feature[];
}

export const FEATURE_GROUPS: FeatureGroup[] = [
  {
    id: 'navigation',
    title: 'Navigation',
    summary:
      'The rail is the interface. Everything else gets out of the page’s way.',
    items: [
      {
        name: 'Left or right rail',
        detail:
          'Move the rail to either edge and choose Small, Medium, Large or Super Compact. The URL bar resizes as rail actions change.',
      },
      {
        name: 'Rail & Menu Studio',
        detail:
          'Move supported actions between the rail and overflow menu, drag to reorder, and keep up to eight movable rail actions plus five quick actions. Tabs and the menu action always stay put.',
      },
      {
        name: 'Tabs and tab overview',
        detail:
          'Open multiple tabs and switch from a full overview rather than a cramped strip.',
      },
      {
        name: 'Find in page',
        detail: 'Search the current document without leaving it.',
      },
      {
        name: 'Long-press refresh',
        detail: 'Reload the current page with JavaScript disabled.',
      },
      {
        name: 'QR scanning',
        detail:
          'Scan a code to open a link. The scanner module can be downloaded separately.',
      },
      {
        name: 'Fullscreen video',
        detail:
          'Landscape video takes the full screen, with rail and system-bar handling that keeps the controls reachable.',
      },
      {
        name: 'File upload',
        detail:
          'Uploads go through Android’s system picker, so the browser never gets broad storage access.',
      },
      {
        name: 'Install site as app',
        detail: 'Create an Android shortcut for a site you use every day.',
      },
      {
        name: 'Top and bottom rail',
        detail:
          'Experimental alternative rail positions live behind Debug settings and are not part of the normal layout.',
        limit: 'Experimental',
      },
    ],
  },
  {
    id: 'privacy',
    title: 'Privacy',
    summary:
      'Controls you can point at, not a single private-mode switch that hides the details.',
    items: [
      {
        name: 'Incognito browsing',
        detail:
          'A separate session for pages you do not want in the ordinary history.',
      },
      {
        name: 'Cookie Manager',
        detail:
          'View, edit and remove cookies per site, and control third-party cookies separately.',
      },
      {
        name: 'Do Not Track',
        detail: 'Send the DNT header. Sites are free to ignore it.',
      },
      {
        name: 'Remove identifying headers',
        detail:
          'Strip selected request headers that sites use to correlate sessions.',
      },
      {
        name: 'WebRTC control',
        detail:
          'Turn WebRTC on or off. It is off by default in the privacy settings.',
      },
      {
        name: 'Clear on exit',
        detail:
          'Clear cache, history, cookies or web storage individually, or automatically when the app closes.',
      },
      {
        name: 'Safe Browsing',
        detail:
          'Enabled when the installed WebView provider supports it. Solipsism cannot add it to a provider that does not.',
        limit: 'Depends on the WebView provider',
      },
      {
        name: 'URL hardening',
        detail:
          'Extra checks for intent://, file://, content://, data: and javascript: navigation, plus canonical-path checks on app-generated pages.',
      },
      {
        name: 'Decoy Mode',
        detail:
          'Long-press Clear All History to replace recent history with randomised, coherent-looking sessions instead of an obvious empty list.',
      },
    ],
  },
  {
    id: 'blocking',
    title: 'Blocking',
    summary:
      'A focused, WebView-safe subset, described as exactly that, not as a desktop engine.',
    items: [
      {
        name: 'Host-based blocking',
        detail:
          'Block ad and tracker domains from selectable host-list sources, with manual refresh.',
      },
      {
        name: 'Cosmetic filtering',
        detail:
          'Hide matching page elements with ## rules and make exceptions with #@#, where the WebView supports it.',
      },
      {
        name: 'Custom filter rules',
        detail:
          'Write your own rules with filter-language help beside the editor.',
      },
      {
        name: 'Element picker',
        detail:
          'Point at an element on a page and create a rule from it without writing a selector by hand.',
      },
      {
        name: 'Animated GIF blocking',
        detail: 'Stop animated GIFs from playing, optionally.',
      },
      {
        name: 'Not a full uBlock Origin',
        detail:
          'WebView does not expose the extension APIs a desktop blocker relies on. Expect a subset of filter syntax and behaviour.',
        limit: 'Deliberate limitation',
      },
    ],
  },
  {
    id: 'userscripts',
    title: 'Userscripts',
    summary:
      'A real injection runtime for common scripts, with the privileged APIs left out on purpose.',
    items: [
      {
        name: 'Import from a file or HTTPS URL',
        detail:
          'Add a .js file from the device or fetch one over HTTPS, then enable, disable or remove it.',
      },
      {
        name: 'Common metadata',
        detail:
          '@match, @include, @exclude, @run-at, @name and @description are read and respected.',
      },
      {
        name: 'Lifecycle injection',
        detail:
          'Scripts run at supported document lifecycle points rather than after an arbitrary delay.',
      },
      {
        name: 'Not the Tampermonkey API',
        detail:
          'Privileged APIs are not silently emulated. A script that depends on them will not work.',
        limit: 'Experimental · subset',
      },
    ],
  },
  {
    id: 'downloads',
    title: 'Downloads',
    summary: 'Layered checking with an honest disclaimer attached to it.',
    items: [
      {
        name: 'Local definitions scanning',
        detail:
          'Check downloads against local definitions without an API key or a network round trip.',
      },
      {
        name: 'Definition refresh',
        detail: 'Update the definitions and configure how they are used.',
      },
      {
        name: 'Image and video scanning',
        detail: 'Enable media scanning separately from document scanning.',
      },
      {
        name: 'Optional VirusTotal',
        detail:
          'Add your own VirusTotal API key if you want a remote check. Nothing is sent unless you configure it.',
      },
      {
        name: 'Explicit choices',
        detail:
          'Scan, download while skipping the scan, or cancel. Suspicious files are blocked with a detection prompt.',
      },
      {
        name: 'Not a guarantee',
        detail:
          'No scanner is completely accurate. Keep using trusted sources and Android security updates.',
        limit: 'Detection can miss threats',
      },
    ],
  },
  {
    id: 'appearance',
    title: 'Appearance and access',
    summary:
      'The browser should fit the hand, the eyes and the screen in front of you.',
    items: [
      {
        name: 'Themes',
        detail:
          'Light, dark and true AMOLED black, with accent palettes, Android system accent matching and dynamic colour where supported.',
      },
      {
        name: 'Custom font',
        detail:
          'Import a local font file. Size and format are validated before it is applied.',
      },
      {
        name: 'Text and layout controls',
        detail:
          'Text size, reflow, wide viewport, overview mode and rendering controls for pages that assume a desktop.',
      },
      {
        name: 'Homepage',
        detail:
          'Bookmarks, wallpaper, motto, clock, date and time formats. Import static HTML and CSS, or edit it in the app.',
      },
      {
        name: 'Homepage sandbox',
        detail:
          'Custom homepage content is sanitised and stored in app-private storage. Scripts, forms, frames, downloads, remote resources and file access are disabled.',
      },
      {
        name: 'Audio',
        detail:
          'A global audio-effects toggle and equaliser presets under Graphics.',
      },
      {
        name: 'Text to speech',
        detail: 'Read page content aloud through Android’s speech engine.',
      },
    ],
  },
];
