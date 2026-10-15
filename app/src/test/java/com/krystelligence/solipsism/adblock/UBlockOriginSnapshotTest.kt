package com.krystelligence.solipsism.adblock

import android.app.Application
import com.krystelligence.solipsism.adblock.snapshot.AdblockSnapshotManager
import com.krystelligence.solipsism.log.NoOpLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.krystelligence.solipsism.SDK_VERSION
import com.krystelligence.solipsism.TestApplication

/**
 * Snapshot-backed loading of [UBlockOriginAdBlocker]: uses the downloaded
 * snapshot when present and picks up replacements without a restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class UBlockOriginSnapshotTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val manager: AdblockSnapshotManager = mock(AdblockSnapshotManager::class.java)
    private lateinit var blocker: UBlockOriginAdBlocker

    @Before
    fun setUp() {
        `when`(manager.snapshotUboFile()).thenReturn(null)
        blocker = UBlockOriginAdBlocker(
            application = RuntimeEnvironment.getApplication(),
            snapshotManager = manager,
            logger = NoOpLogger()
        )
    }

    @Test
    fun `no snapshot and unloadable asset blocks nothing instead of crashing`() {
        // The bundled asset is not packaged with unit tests, so the loader
        // falls back to the empty list; blocking must fail open, never crash.
        assertThat(blocker.isAd("https://bluekai.com/ads.js")).isFalse()
        assertThat(blocker.isAd("https://example.com/ads.js")).isFalse()
    }

    @Test
    fun `picks up replaced snapshot file`() {
        val first = temporaryFolder.newFile("ubo1.txt")
        first.writeText("||first-example-blocked.com^\n")
        first.setLastModified(1000L)
        `when`(manager.snapshotUboFile()).thenReturn(first)

        assertThat(blocker.isAd("https://first-example-blocked.com/x.js")).isTrue()
        assertThat(blocker.isAd("https://second-example-blocked.com/x.js")).isFalse()

        val second = temporaryFolder.newFile("ubo2.txt")
        second.writeText("||second-example-blocked.com^\n")
        second.setLastModified(2000L)
        `when`(manager.snapshotUboFile()).thenReturn(second)

        assertThat(blocker.isAd("https://first-example-blocked.com/x.js")).isFalse()
        assertThat(blocker.isAd("https://second-example-blocked.com/x.js")).isTrue()
    }
}
