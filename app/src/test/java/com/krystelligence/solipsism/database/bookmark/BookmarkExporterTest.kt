package com.krystelligence.solipsism.database.bookmark

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BookmarkExporterTest {

    @Test
    fun `malformed bookmark input fails the import`() {
        val input = ByteArrayInputStream("not-json\n".toByteArray())
        assertThrows(Exception::class.java) {
            BookmarkExporter.importBookmarksFromFileStream(input)
        }
    }
}
