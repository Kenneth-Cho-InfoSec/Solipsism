/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.krystelligence.solipsism.browser.engine

import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

/**
 * Connects user-initiated HTML media playback to Android's platform media stack.
 *
 * The Android Antares build intentionally uses a lightweight media backend. This document bridge
 * observes standard media elements and their own fetch/XHR source acquisition, then emits a
 * short-lived title signal for sources that Android Media3 can play. It has no host allow-list,
 * page-specific selectors, or privileged JavaScript interface.
 */
internal object AntaresHtmlMediaBridge {
    private const val TITLE_PREFIX = "__ANTARES_MEDIA_V1__:"

    val installScript: String =
        """
        (() => {
          if (window.__antaresMediaBridgeInstalled) return;
          window.__antaresMediaBridgeInstalled = true;

          const findVideo = target => {
            if (!target || typeof target.closest !== 'function') return null;
            if (typeof target.matches === 'function' && target.matches('video')) return target;
            const container = target.closest('.video-js, .vjscontainer');
            return container ?
              (container.matches('video') ? container : container.querySelector('video')) :
              target.closest('video');
          };

          const sources = [];
          const renewals = [];
          const renewalCandidates = [];
          const pending = new WeakSet();
          let lastPublishedAt = 0;

          const networkUrl = value => {
            if (typeof value !== 'string') return '';
            if (value.indexOf('//') === 0) return location.protocol + value;
            if (/^https?:/i.test(value)) return value;
            return /^https?:/i.test(value) ? value : '';
          };
          const looksLikeMedia = value =>
            /(?:\.(?:m3u8|mpd|mp4|m4v|webm|mov|m2ts|ts)(?:[?#]|${'$'}))|(?:\/manifest(?:[/?#]|${'$'}))|(?:\/playlist(?:[/?#]|${'$'}))/i
              .test(value || '');
          const rememberSource = value => {
            const url = networkUrl(value);
            if (!url) return '';
            const old = sources.findIndex(entry => entry.url === url);
            if (old >= 0) sources.splice(old, 1);
            sources.push({ url, observedAt: Date.now() });
            if (sources.length > 12) sources.shift();
            return url;
          };
          const rememberRenewal = request => {
            if (!request) return;
            const url = networkUrl(request.url);
            const method = String(request.method || 'GET').toUpperCase();
            const body = typeof request.body === 'string' ? request.body : '';
            if (!url || (method !== 'GET' && method !== 'POST') || body.length > 32768) return;
            renewals.push({
              url,
              method,
              body,
              contentType: typeof request.contentType === 'string' ? request.contentType : '',
              observedAt: Date.now()
            });
            if (renewals.length > 12) renewals.shift();
          };
          const inspectPayload = payload => {
            const discovered = [];
            const visit = (value, depth) => {
              if (value == null || depth > 7) return;
              if (typeof value === 'string') {
                const url = networkUrl(value);
                if (url) {
                  rememberSource(url);
                  discovered.push(url);
                }
                return;
              }
              if (Array.isArray(value)) {
                value.slice(0, 48).forEach(item => visit(item, depth + 1));
                return;
              }
              if (typeof value === 'object') {
                Object.keys(value).slice(0, 96).forEach(key => visit(value[key], depth + 1));
              }
            };
            visit(payload, 0);
            return discovered;
          };
          const inspectText = text => {
            if (!text || text.length > 524288) return [];
            try { return inspectPayload(JSON.parse(text)); } catch (_) {
              const found = text.match(/https?:[^\s\"'<>\\]+/g) || [];
              found.forEach(rememberSource);
              return found.map(networkUrl).filter(Boolean);
            }
          };
          const rememberRenewalCandidate = (request, discovered) => {
            if (!request || !discovered || !discovered.length) return;
            const normalised = discovered.map(networkUrl).filter(Boolean);
            if (!normalised.length) return;
            renewalCandidates.push({ request, sources: normalised, observedAt: Date.now() });
            if (renewalCandidates.length > 12) renewalCandidates.shift();
            if (normalised.some(looksLikeMedia)) rememberRenewal(request);
          };
          const associateRenewalWithSource = value => {
            const source = networkUrl(value);
            if (!source) return;
            for (let index = renewalCandidates.length - 1; index >= 0; index -= 1) {
              const candidate = renewalCandidates[index];
              if (!candidate.sources.includes(source)) continue;
              rememberRenewal(candidate.request);
              return;
            }
          };

          if (typeof window.fetch === 'function') {
            const originalFetch = window.fetch;
            window.fetch = function() {
              const input = arguments[0];
              const options = arguments[1] || {};
              const request = {
                url: typeof input === 'string' ? input : input && input.url,
                method: options.method || (input && input.method) || 'GET',
                body: options.body,
                contentType: options.headers &&
                  (options.headers['Content-Type'] || options.headers['content-type'])
              };
              return originalFetch.apply(this, arguments).then(response => {
                try {
                  response.clone().text().then(text =>
                    rememberRenewalCandidate(request, inspectText(text)))
                    .catch(() => {});
                } catch (_) {}
                return response;
              });
            };
          }

          if (window.XMLHttpRequest && XMLHttpRequest.prototype.send) {
            const originalOpen = XMLHttpRequest.prototype.open;
            const originalHeader = XMLHttpRequest.prototype.setRequestHeader;
            const originalSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
              this.__antaresRequest = { method, url, contentType: '' };
              return originalOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
              if (this.__antaresRequest && String(name).toLowerCase() === 'content-type') {
                this.__antaresRequest.contentType = String(value);
              }
              return originalHeader.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function(body) {
              if (this.__antaresRequest) {
                this.__antaresRequest.body = typeof body === 'string' ? body : '';
              }
              this.addEventListener('load', () => {
                try {
                  if (typeof this.responseText === 'string') {
                    rememberRenewalCandidate(
                      this.__antaresRequest,
                      inspectText(this.responseText)
                    );
                  }
                } catch (_) {}
              });
              return originalSend.apply(this, arguments);
            };
          }

          const observeJQuery = () => {
            const install = () => {
              const jquery = window.jQuery;
              if (!jquery || !jquery.ajax || jquery.__antaresMediaObserved) return;
              const originalAjax = jquery.ajax;
              jquery.ajax = function() {
                const request = originalAjax.apply(this, arguments);
                try {
                  if (request && typeof request.done === 'function') {
                    request.done(payload => inspectPayload(payload));
                  }
                } catch (_) {}
                return request;
              };
              jquery.__antaresMediaObserved = true;
            };
            install();
            setTimeout(install, 1000);
          };
          observeJQuery();

          if (window.HTMLMediaElement) {
            const descriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
            if (descriptor && descriptor.get && descriptor.set) {
              try {
                Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                  configurable: true,
                  enumerable: descriptor.enumerable,
                  get: descriptor.get,
                  set: function(value) {
                    rememberSource(value);
                    associateRenewalWithSource(value);
                    return descriptor.set.call(this, value);
                  }
                });
              } catch (_) {}
            }
          }

          const directSource = video => networkUrl(
            video.currentSrc || video.src ||
              (video.querySelector('source') && video.querySelector('source').src) || ''
          );
          const recentSource = notBefore => {
            for (let index = sources.length - 1; index >= 0; index -= 1) {
              const entry = sources[index];
              if (entry.observedAt >= notBefore) return entry.url;
            }
            return '';
          };
          const recentRenewal = notBefore => {
            for (let index = renewals.length - 1; index >= 0; index -= 1) {
              if (renewals[index].observedAt >= notBefore) return renewals[index];
            }
            return null;
          };
          const markNativePlayback = video => {
            try {
              const container = video.closest('.video-js');
              if (!container) return;
              if (!document.getElementById('antares-native-media-style')) {
                const style = document.createElement('style');
                style.id = 'antares-native-media-style';
                style.textContent =
                  '.antares-native-media.vjs-error .vjs-error-display{' +
                  'display:none!important;visibility:hidden!important}';
                (document.head || document.documentElement).appendChild(style);
              }
              container.classList.add('antares-native-media');
            } catch (_) {}
          };
          const publish = (video, source, renewal) => {
            if (!source && !renewal) return false;
            const now = Date.now();
            if (now - lastPublishedAt < 750) return true;
            lastPublishedAt = now;
            markNativePlayback(video);
            const request = {
              pageUrl: location.href,
              directSource: source || '',
              renewalRequest: renewal ? JSON.stringify(renewal) : '',
              cookies: document.cookie || '',
              title: window.__antaresOriginalTitle || document.title || ''
            };
            const original = window.__antaresOriginalTitle || document.title;
            window.__antaresOriginalTitle = original;
            document.title = '$TITLE_PREFIX' + encodeURIComponent(JSON.stringify(request));
            setTimeout(() => {
              if (document.title.indexOf('$TITLE_PREFIX') === 0) document.title = original;
            }, 750);
            return true;
          };
          const requestPlayback = event => {
            const video = findVideo(event.target);
            if (!video) return;
            if (pending.has(video)) return;
            pending.add(video);
            const pressedAt = Date.now();
            const sourceAtPress = directSource(video);
            let attempts = 24;
            const inspect = () => {
              const currentSource = directSource(video);
              const changedSource = currentSource && currentSource !== sourceAtPress ?
                currentSource : '';
              const source = changedSource || recentSource(pressedAt);
              const renewal = recentRenewal(pressedAt);
              if (source || renewal) {
                pending.delete(video);
                publish(video, source, renewal);
              } else if (attempts === 20) {
                pending.delete(video);
                publish(
                  video,
                  sourceAtPress || recentSource(0),
                  recentRenewal(0)
                );
              } else if (--attempts > 0) {
                setTimeout(inspect, 250);
              } else {
                pending.delete(video);
              }
            };
            setTimeout(inspect, 100);
          };
          [
            'pointerdown', 'touchstart', 'mousedown', 'touchend', 'pointerup', 'click'
          ].forEach(type => document.addEventListener(type, requestPlayback, true));
        })();
        """.trimIndent()

    fun decodeTitle(title: String): Bundle? {
        if (!title.startsWith(TITLE_PREFIX)) return null
        return runCatching {
            val json = JSONObject(Uri.decode(title.removePrefix(TITLE_PREFIX)))
            val pageUrl = json.optString("pageUrl").takeIf(String::isNotBlank)
                ?: return null
            val directSource = json.optString("directSource").takeIf(String::isNotBlank)
            val renewal = json.optString("renewalRequest").takeIf(String::isNotBlank)
            if (directSource == null && renewal == null) return null
            Bundle().apply {
                putString(AntaresProtocol.KEY_MEDIA_PAGE_URL, pageUrl)
                putString(AntaresProtocol.KEY_MEDIA_DIRECT_SOURCE, directSource)
                putString(AntaresProtocol.KEY_MEDIA_RENEWAL_REQUEST, renewal)
                putString(
                    AntaresProtocol.KEY_MEDIA_COOKIES,
                    json.optString("cookies").takeIf(String::isNotBlank),
                )
                putString(
                    AntaresProtocol.KEY_MEDIA_TITLE,
                    json.optString("title").takeIf(String::isNotBlank),
                )
            }
        }.getOrNull()
    }
}
