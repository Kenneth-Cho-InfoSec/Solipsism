package com.krystelligence.solipsism.browser.engine

/**
 * Small standards-preserving fallback for CSS Masking Level 1.
 *
 * Antares does not yet paint `mask-image`, which otherwise leaves icon-only controls as empty
 * coloured boxes. When a page supplies a mask URL, use the same image as a background image. This
 * keeps the control's intrinsic size and accessible DOM intact, while avoiding page or host
 * specific selectors. Once native CSS masking is available this bridge becomes a no-op.
 */
internal object AntaresCssCompatibilityBridge {
    val installScript = """
        (() => {
          if (window.__antaresCssMaskFallbackInstalled) return;
          window.__antaresCssMaskFallbackInstalled = true;
          const applyElementStyles = () => {
            document.querySelectorAll('*').forEach(element => {
              const style = getComputedStyle(element);
              const mask = style.maskImage || style.webkitMaskImage ||
                style.getPropertyValue('mask-image') || style.getPropertyValue('-webkit-mask-image');
              if (!mask || mask === 'none' || element.dataset.antaresMaskFallback) return;
              element.dataset.antaresMaskFallback = '1';
              element.style.backgroundImage = mask;
              element.style.backgroundRepeat = 'no-repeat';
              element.style.backgroundPosition = 'center';
              element.style.backgroundSize = 'contain';
              element.style.backgroundColor = 'transparent';
            });
          };
          const applyRule = (selector, mask) => {
            if (!selector || !mask || mask === 'none') return;
            try {
              document.querySelectorAll(selector).forEach(element => {
                if (element.dataset.antaresMaskFallback) return;
                element.dataset.antaresMaskFallback = '1';
                element.style.backgroundImage = mask;
                element.style.backgroundRepeat = 'no-repeat';
                element.style.backgroundPosition = 'center';
                element.style.backgroundSize = 'contain';
                element.style.backgroundColor = 'transparent';
              });
            } catch (_) {}
          };
          const applyStylesheetRules = () => {
            const walk = rules => {
              for (const rule of Array.from(rules || [])) {
                if (rule.selectorText && rule.style) {
                  const mask = rule.style.maskImage || rule.style.webkitMaskImage ||
                    rule.style.getPropertyValue('mask-image') ||
                    rule.style.getPropertyValue('-webkit-mask-image');
                  applyRule(rule.selectorText, mask);
                }
                if (rule.cssRules) walk(rule.cssRules);
              }
            };
            for (const sheet of Array.from(document.styleSheets)) {
              try {
                walk(sheet.cssRules);
              } catch (_) {
                // Cross-origin stylesheets can deny cssRules; the element pass still applies.
              }
            }
          };
          const apply = () => {
            applyElementStyles();
            applyStylesheetRules();
          };
          apply();
          let scheduled = false;
          const schedule = () => {
            if (scheduled) return;
            scheduled = true;
            setTimeout(() => { scheduled = false; apply(); }, 50);
          };
          new MutationObserver(schedule).observe(document.documentElement, {
            subtree: true,
            childList: true,
            attributes: true,
            attributeFilter: ['class', 'style']
          });
          window.addEventListener('load', schedule, { once: true });
        })();
    """.trimIndent()
}
