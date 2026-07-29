package com.krystelligence.solipsism.virustotal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VirusTotalFilePolicyTest {

    @Test
    fun `scanning is disabled globally`() {
        assertThat(
            VirusTotalFilePolicy.shouldScan(false, true, true, "application/zip", "file.zip")
        ).isFalse()
    }

    @Test
    fun `images and videos are excluded by default`() {
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, false, "image/png", "photo.png")
        ).isFalse()
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, false, "video/mp4", "clip.mp4")
        ).isFalse()
    }

    @Test
    fun `media can be re-enabled independently`() {
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, true, false, "image/jpeg", "photo.jpg")
        ).isTrue()
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, true, "video/webm", "clip.webm")
        ).isTrue()
    }

    @Test
    fun `generic MIME type falls back to extension`() {
        assertThat(
            VirusTotalFilePolicy.shouldScan(
                true, false, false, "application/octet-stream", "photo.jpg"
            )
        ).isFalse()
    }

    @Test
    fun `archives executables and unknown files are scanned`() {
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, false, "application/zip", "archive.zip")
        ).isTrue()
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, false, null, "payload.apk")
        ).isTrue()
        assertThat(
            VirusTotalFilePolicy.shouldScan(true, false, false, null, "unknown.bin")
        ).isTrue()
    }
}
