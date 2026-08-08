package com.krystelligence.solipsism.download

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DownloadFilenameResolverTest {

    @Test
    fun `uses jpeg extension for extensionless image URL`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:test",
                "attachment",
                "image/jpeg"
            )
        ).isEqualTo("download.jpg")
    }

    @Test
    fun `uses png extension for extensionless image URL`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:test",
                "attachment",
                "image/png"
            )
        ).isEqualTo("download.png")
    }

    @Test
    fun `preserves a real filename from content disposition`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://example.test/image",
                "attachment; filename=\"cat-photo.jpg\"",
                "image/jpeg"
            )
        ).isEqualTo("cat-photo.jpg")
    }

    @Test
    fun `does not replace normal URL extension`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://example.test/cat.png",
                null,
                "image/png"
            )
        ).isEqualTo("cat.png")
    }

    @Test
    fun `keeps generic fallback when MIME is unknown`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://example.test/download",
                "attachment",
                null
            )
        ).isEqualTo("download.bin")
    }

    @Test
    fun `converts image filename when MIME is unavailable`() {
        assertThat(
            DownloadFilenameResolver.resolve(
                "https://example.test/photo.png",
                null,
                null,
                saveImagesAsJpeg = true
            )
        ).isEqualTo("photo.jpg")
    }
}
