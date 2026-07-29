package com.krystelligence.solipsism.virustotal

object VirusTotalFilePolicy {
    fun shouldScan(
        scanningEnabled: Boolean,
        scanImages: Boolean,
        scanVideos: Boolean,
        mimeType: String?,
        fileName: String
    ): Boolean {
        if (!scanningEnabled) return false
        val declaredMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeUnless { it.isBlank() || it == "application/octet-stream" }
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val isImage = declaredMime?.startsWith("image/") == true ||
            (declaredMime == null && extension in IMAGE_EXTENSIONS)
        val isVideo = declaredMime?.startsWith("video/") == true ||
            (declaredMime == null && extension in VIDEO_EXTENSIONS)

        if (isImage && !scanImages) return false
        if (isVideo && !scanVideos) return false
        return true
    }

    private val IMAGE_EXTENSIONS = setOf(
        "avif", "bmp", "gif", "heic", "heif", "ico", "jpeg", "jpg", "jxl", "png", "svg",
        "tif", "tiff", "webp"
    )
    private val VIDEO_EXTENSIONS = setOf(
        "3g2", "3gp", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg",
        "mts", "ogv", "ts", "webm", "wmv"
    )
}
