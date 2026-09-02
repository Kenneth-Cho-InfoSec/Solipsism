/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.krystelligence.solipsism.browser.engine

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A deliberately narrow JavaScript bridge used by Solipsism's optional coordinate diagnostics.
 * Servo's Android wrapper does not expose evaluation results to Kotlin yet, so the probe returns
 * its small JSON payload through the already audited title callback and immediately restores the
 * document title.
 */
internal object AntaresCoordinateProbe {
    private const val PREFIX = "__SOLIPSISM_ANTARES_PROBE__"

    data class Result(val requestId: Int, val descriptor: String)

    fun script(requestId: Int, physicalX: Float, physicalY: Float): String = """
        (() => {
          const marker = '$PREFIX$requestId:';
          const originalTitle = document.title;
          const dpr = window.devicePixelRatio || 1;
          const viewport = window.visualViewport;
          const viewportScale = (viewport && viewport.scale) || 1;
          const viewportWidth = Math.round((viewport && viewport.width) || innerWidth);
          const viewportHeight = Math.round((viewport && viewport.height) || innerHeight);
          const x = $physicalX / dpr / viewportScale;
          const y = $physicalY / dpr / viewportScale;
          const raw = document.elementFromPoint(x, y);
          const target = raw && raw.closest
            ? (raw.closest('a[href],button,input,select,textarea,[role="button"],[role="link"],[onclick],[tabindex]') || raw)
            : raw;
          const compact = value => String(value || '').replace(/\s+/g, ' ').trim().slice(0, 120);
          let result;
          if (!target) {
            result = { empty: true, x, y, viewportWidth, viewportHeight, dpr, viewportScale };
          } else {
            const rect = target.getBoundingClientRect();
            result = {
              empty: false,
              tag: compact(target.tagName).toLowerCase(),
              id: compact(target.id),
              name: compact(target.getAttribute && target.getAttribute('name')),
              type: compact(target.getAttribute && target.getAttribute('type')).toLowerCase(),
              role: compact(target.getAttribute && target.getAttribute('role')).toLowerCase(),
              label: compact(target.getAttribute && (target.getAttribute('aria-label') || target.getAttribute('title'))),
              text: compact(target.innerText || target.value || target.textContent),
              href: compact(target.href),
              left: Math.round(rect.left),
              top: Math.round(rect.top),
              right: Math.round(rect.right),
              bottom: Math.round(rect.bottom),
              x,
              y,
              viewportWidth,
              viewportHeight,
              dpr,
              viewportScale
            };
          }
          document.title = marker + encodeURIComponent(JSON.stringify(result));
          setTimeout(() => {
            if (document.title.indexOf(marker) === 0) document.title = originalTitle;
          }, 100);
        })();
    """.trimIndent()

    fun decodeTitle(title: String): Result? {
        if (!title.startsWith(PREFIX)) return null
        val separator = title.indexOf(':', PREFIX.length)
        if (separator < 0) return null
        val requestId = title.substring(PREFIX.length, separator).toIntOrNull() ?: return null
        val descriptor = runCatching {
            URLDecoder.decode(
                title.substring(separator + 1),
                StandardCharsets.UTF_8.name(),
            )
        }.getOrNull() ?: return null
        return Result(requestId, descriptor)
    }
}
