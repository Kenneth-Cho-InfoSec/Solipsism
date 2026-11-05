export interface FaqItem {
  question: string;
  answer: string[];
}

export const FAQS: FaqItem[] = [
  {
    question: 'Is Solipsism a Chromium fork?',
    answer: [
      'No. It is an Android application built around Android System WebView, the browser engine your device already keeps updated. Solipsism adds the rail, tabs, privacy controls, blocking, userscripts and download handling around it.',
      'Because the renderer is the system WebView, page compatibility and Safe Browsing follow your installed provider and Android version.',
    ],
  },
  {
    question: 'What is Antares, and should I use it?',
    answer: [
      'Antares is an experimental Servo-based core, distributed as a separate signed package. It is not required. Solipsism 9.4.0 bundles Antares 0.5.0 support, and you choose it explicitly in Debug Settings.',
      'Use Android WebView unless you are deliberately testing Antares. Complex sites, CAPTCHA and media can fail on Antares, and page history, form state, media state and cookies are not promised across a switch.',
    ],
  },
  {
    question: 'Does it block every ad and tracker?',
    answer: [
      'No. Solipsism supports host-based blocking, cosmetic filter rules and an element picker, but WebView does not expose a complete uBlock Origin engine. Treat it as a focused, browser-safe subset.',
    ],
  },
  {
    question: 'Are userscripts fully Tampermonkey-compatible?',
    answer: [
      'No. The manager reads common metadata such as @match, @include, @exclude, @run-at, @name and @description, and injects at supported lifecycle points. Privileged Tampermonkey APIs are not emulated.',
      'Scripts are executable code. Only install scripts whose source you trust.',
    ],
  },
  {
    question: 'Is the malware scanner a guarantee?',
    answer: [
      'No. Local definitions-based scanning works without a key, and VirusTotal is optional with your own API key. The app states plainly that no scanner is completely accurate and that this is one layer, not a replacement for trusted sources and Android updates.',
    ],
  },
  {
    question: 'Where do I get the APK?',
    answer: [
      'From the project’s GitHub Releases page. The 9.4.0 release contains a single APK, Solipsism-Browser-v9.4.0.apk, and uses the same signing key as 9.3.1, so it installs as an in-place update.',
      'Do not install builds copied from unofficial download sites.',
    ],
  },
];
