package com.krystelligence.solipsism.audio

import android.webkit.WebView
import com.krystelligence.solipsism.preference.UserPreferences

/**
 * Best-effort Web Audio processing for HTML audio/video elements.
 *
 * The bridge is deliberately opt-in: disabling effects disconnects the processing graph and
 * reconnects media directly to the WebView's normal output path.
 */
object AudioEffectsRuntime {
    fun injectAfterPageFinished(view: WebView, preferences: UserPreferences) {
        val gains = if (preferences.audioCustomEqEnabled) {
            listOf(preferences.audioEq60, preferences.audioEq250, preferences.audioEq1000,
                preferences.audioEq4000, preferences.audioEq12000)
        } else {
            presetGains(preferences.audioPreset)
        }
        val settings = """
            {enabled:${preferences.audioEffectsEnabled},bands:[${gains.joinToString(",")}],
            preamp:${preferences.audioPreampDb},limiter:${preferences.audioLimiterEnabled},
            mono:${preferences.audioMonoEnabled},balance:${preferences.audioBalance}}
        """.trimIndent()
        view.evaluateJavascript("window.__solipsismAudioEffectsApply($settings)", null)
    }

    private fun presetGains(preset: AudioPreset): List<Int> = when (preset) {
        AudioPreset.FLAT -> listOf(0, 0, 0, 0, 0)
        AudioPreset.BASS_BOOST -> listOf(7, 5, 1, 0, 0)
        AudioPreset.VOCAL_BOOST -> listOf(-2, 1, 5, 4, 1)
        AudioPreset.TREBLE_BOOST -> listOf(0, 0, 1, 5, 7)
        AudioPreset.ROCK -> listOf(5, 2, -1, 3, 5)
        AudioPreset.CLASSICAL -> listOf(3, 1, 0, 2, 4)
        AudioPreset.PODCAST -> listOf(-3, 1, 5, 4, -1)
        AudioPreset.NIGHT -> listOf(2, 1, 0, -2, -4)
    }

    /** Injected once per document; it does nothing unless explicitly enabled. */
    const val SCRIPT = """
        (() => {
          if (window.__solipsismAudioEffectsApply) return;
          const state = { media: new Map(), settings: null, observer: null };
          const bands = [60, 250, 1000, 4000, 12000];
          function disconnect(item) {
            try { item.source.disconnect(); } catch (_) {}
            try { item.source.connect(item.context.destination); } catch (_) {}
          }
          function attach(element) {
            if (state.media.has(element)) return;
            try {
              const context = new (window.AudioContext || window.webkitAudioContext)();
              const source = context.createMediaElementSource(element);
              const filters = bands.map((frequency) => {
                const filter = context.createBiquadFilter();
                filter.type = 'peaking'; filter.frequency.value = frequency; filter.Q.value = 1;
                return filter;
              });
              const gain = context.createGain();
              const compressor = context.createDynamicsCompressor();
              const panner = context.createStereoPanner ? context.createStereoPanner() : null;
              let node = source;
              filters.forEach((filter) => { node.connect(filter); node = filter; });
              node.connect(gain); node = gain;
              node.connect(compressor); node = compressor;
              if (panner) { node.connect(panner); node = panner; }
              node.connect(context.destination);
              state.media.set(element, { context, source, filters, gain, compressor, panner });
            } catch (_) {
              // WebView may reject a media element (for example, after another script owns it).
              // Leave that element on its normal playback path.
            }
          }
          function apply(settings) {
            state.settings = settings;
            const elements = document.querySelectorAll('audio,video');
            if (!settings.enabled) {
              state.media.forEach(disconnect);
              return;
            }
            elements.forEach(attach);
            state.media.forEach((item) => {
              settings.bands.forEach((value, index) => { item.filters[index].gain.value = value; });
              item.gain.gain.value = Math.pow(10, settings.preamp / 20);
              item.compressor.threshold.value = settings.limiter ? -8 : 0;
              item.compressor.ratio.value = settings.limiter ? 8 : 1;
              if (item.panner) item.panner.pan.value = Math.max(-1, Math.min(1, settings.balance / 100));
            });
          }
          window.__solipsismAudioEffectsApply = apply;
          state.observer = new MutationObserver(() => { if (state.settings) apply(state.settings); });
          state.observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """
}
