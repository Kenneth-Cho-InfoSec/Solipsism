package com.krystelligence.solipsism.adblock

import com.krystelligence.solipsism.adblock.parser.HostsFileParser
import com.krystelligence.solipsism.database.adblock.Host
import com.krystelligence.solipsism.log.NoOpLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.InputStreamReader

/**
 * Unit tests for the assets ad blocker
 */
class HostsFileParserTest {

    @Test
    fun `line parsing is valid`() {
        val testInput = """
            127.0.0.1 localhost #comment comment
            ::1 localhost #comment comment
            #
            # another comment
            #
            127.0.0.1	fake.domain1.com
            127.0.0.1	fake.domain2.com    # comment
            0.0.0.0	fake.domain3.com    # comment
            # random comment
            ::1 domain4.com
            0.0.0.0 multiline1.com multiline2.com # comment
            0.0.0.0 comment.close.by.com#comment
            """

        val inputStreamReader = InputStreamReader(testInput.trimIndent().byteInputStream())
        val hostsFileParser = HostsFileParser(NoOpLogger())
        val mutableList = hostsFileParser.parseInput(inputStreamReader)

        assertThat(mutableList).hasSize(7)
        assertThat(mutableList).contains(
            Host("fake.domain1.com"),
            Host("fake.domain2.com"),
            Host("fake.domain3.com"),
            Host("domain4.com"),
            Host("multiline1.com"),
            Host("multiline2.com"),
            Host("comment.close.by.com")
        )
    }
}
