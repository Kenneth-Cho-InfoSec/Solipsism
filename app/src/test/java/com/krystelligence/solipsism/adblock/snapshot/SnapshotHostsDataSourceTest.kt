package com.krystelligence.solipsism.adblock.snapshot

import com.krystelligence.solipsism.adblock.source.HostsResult
import com.krystelligence.solipsism.database.adblock.Host
import com.krystelligence.solipsism.log.NoOpLogger
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import javax.inject.Provider

class SnapshotHostsDataSourceTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val manager: AdblockSnapshotManager = mock(AdblockSnapshotManager::class.java)
    private val dataSource = SnapshotHostsDataSource(
        snapshotManager = manager,
        hostsFileParserProvider = Provider {
            com.krystelligence.solipsism.adblock.parser.HostsFileParser(NoOpLogger())
        },
        logger = NoOpLogger()
    )

    @Test
    fun `loads domains from snapshot file`() {
        runBlocking {
        val file = temporaryFolder.newFile("hosts.txt")
        file.writeText("0.0.0.0 ads.example.com\n0.0.0.0 track.example.org\n")
        `when`(manager.snapshotHostsFile()).thenReturn(file)
        `when`(manager.status()).thenReturn(
            com.krystelligence.solipsism.adblock.snapshot.SnapshotStatus(
                hostsPresent = true,
                uboPresent = false,
                hostsUpdatedAt = 123L,
                uboUpdatedAt = 0L,
                hostsCount = 2,
                uboRuleCount = 0
            )
        )

        val result = dataSource.loadHosts()

        assertThat(result).isInstanceOf(HostsResult.Success::class.java)
        val hosts = (result as HostsResult.Success).hosts
        assertThat(hosts).containsExactly(Host("ads.example.com"), Host("track.example.org"))
        assertThat(dataSource.identifier()).isEqualTo("snapshot:123")
        }
    }

    @Test
    fun `missing snapshot reports failure`() {
        runBlocking {
        `when`(manager.snapshotHostsFile()).thenReturn(null)

        val result = dataSource.loadHosts()

        assertThat(result).isInstanceOf(HostsResult.Failure::class.java)
        }
    }
}
