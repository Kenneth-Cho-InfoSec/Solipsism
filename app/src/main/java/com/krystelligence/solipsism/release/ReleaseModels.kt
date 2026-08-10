package com.krystelligence.solipsism.release

data class ReleaseAsset(
    val name: String,
    val size: Long,
    val browserDownloadUrl: String,
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val apkAssets: List<ReleaseAsset>
        get() = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

    val preferredApk: ReleaseAsset?
        get() = apkAssets.firstOrNull { it.name.contains("universal", true) || it.name.contains("release", true) }
            ?: apkAssets.firstOrNull()
}
