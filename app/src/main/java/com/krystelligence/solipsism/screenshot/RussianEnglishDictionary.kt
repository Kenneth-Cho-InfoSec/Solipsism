package com.krystelligence.solipsism.screenshot

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Offline token-by-token Russian-to-English lookup for image-search labels. */
internal class RussianEnglishDictionary internal constructor(private val entries: Map<String, String>) {
    fun translateWords(input: String): String = TOKEN_PATTERN.replace(input) { match ->
        val token = match.value
        if (!token.any { it.isLetter() }) return@replace token
        val translated = entries[token.lowercase(Locale.ROOT)] ?: return@replace token
        when {
            token.all { !it.isLetter() || it.isUpperCase() } -> translated.uppercase(Locale.ROOT)
            token.firstOrNull()?.isUpperCase() == true -> translated.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> translated
        }
    }

    companion object {
        private val TOKEN_PATTERN = Regex("[\\p{L}\\p{M}\\p{Nd}]+|[^\\p{L}\\p{M}\\p{Nd}]+")
        private const val COMPRESSED_ASSET_PATH = "dictionaries/ruen.dict.json.gz"
        private const val EXPANDED_ASSET_PATH = "dictionaries/ruen.dict.json"
        @Volatile private var instance: RussianEnglishDictionary? = null

        fun get(context: Context): RussianEnglishDictionary = instance ?: synchronized(this) {
            instance ?: load(context.applicationContext).also { instance = it }
        }

        private fun load(context: Context): RussianEnglishDictionary {
            // The Android asset pipeline may expand gzip assets and remove the
            // suffix, so support both the source and packaged representation.
            val json = runCatching {
                GZIPInputStream(context.assets.open(COMPRESSED_ASSET_PATH))
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse {
                context.assets.open(EXPANDED_ASSET_PATH)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val objectJson = JSONObject(json)
            val map = HashMap<String, String>(objectJson.length())
            objectJson.keys().forEach { key ->
                val value = objectJson.optString(key).substringBefore(',').trim()
                if (key.isNotBlank() && value.isNotBlank()) map[key.lowercase(Locale.ROOT)] = value
            }
            return RussianEnglishDictionary(map)
        }
    }
}
