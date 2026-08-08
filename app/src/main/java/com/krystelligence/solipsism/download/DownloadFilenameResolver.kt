package com.krystelligence.solipsism.download

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DownloadFilenameResolver {
    @JvmStatic
    fun resolve(url: String, contentDisposition: String?, mimeType: String?, saveImagesAsJpeg: Boolean = false): String {
        val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.lowercase()
        val guessed = sanitizeFileName(fallbackGuess(url, contentDisposition))
        if (saveImagesAsJpeg && isRasterImage(mimeType) && !guessed.equals("download", true)) {
            return sanitizeFileName(guessed.substringBeforeLast('.', guessed) + ".jpg")
        }
        if (!isGeneric(guessed)) return guessed
        val extension = extensionForMimeType(normalizedMimeType)
            ?: return guessed
        val stem = guessed.substringBeforeLast('.', "download")
            .takeUnless { it.isBlank() || it.equals("download", ignoreCase = true) }
            ?: "download"
        return sanitizeFileName("$stem.$extension")
    }

    @JvmStatic
    fun isRasterImage(mimeType: String?): Boolean {
        return mimeType?.substringBefore(';')?.trim()?.lowercase() in
            setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif", "image/bmp")
    }

    private fun isGeneric(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower == "download" || lower == "download.bin" || lower.endsWith(".bin") || !lower.contains('.')
    }

    private fun fallbackGuess(url: String, contentDisposition: String?): String {
        val dispositionName = Regex("filename\\*?=(?:UTF-8''|\\\")?([^;\\\"]+)", RegexOption.IGNORE_CASE)
            .find(contentDisposition.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        if (!dispositionName.isNullOrBlank()) return dispositionName

        return runCatching {
            URI(url).path
                ?.substringAfterLast('/')
                ?.takeUnless(String::isNullOrBlank)
                ?.takeIf { it.contains('.') }
                ?: "download.bin"
        }.getOrDefault("download.bin")
    }

    private fun extensionForMimeType(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        val commonExtension = COMMON_EXTENSIONS[mimeType]
        if (commonExtension != null) return commonExtension
        return null
        }

    private fun sanitizeFileName(fileName: String?): String {
            if (fileName.isNullOrBlank()) return "download"
            var sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
            while (sanitized.startsWith('.')) sanitized = sanitized.substring(1)
            if (sanitized.isEmpty()) return "download"
            return sanitized.take(120)
    }

    private val COMMON_EXTENSIONS = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/avif" to "avif",
        "image/bmp" to "bmp",
        "image/svg+xml" to "svg"
    )
}
