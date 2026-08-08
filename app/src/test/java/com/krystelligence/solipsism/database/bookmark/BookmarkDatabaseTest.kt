package com.krystelligence.solipsism.database.bookmark

import com.krystelligence.solipsism.SDK_VERSION
import com.krystelligence.solipsism.TestApplication
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.asFolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = TestApplication::class,
    sdk = [SDK_VERSION]
)
class BookmarkDatabaseTest {

    private lateinit var database: BookmarkDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        database = BookmarkDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `bulk import persists every unique bookmark`() {
        val bookmarks = (0 until 400).map { index ->
            Bookmark.Entry(
                url = "https://example.test/bookmark-$index",
                title = "Bookmark $index",
                position = index,
                folder = if (index % 2 == 0) Bookmark.Folder.Root
                else "Imported".asFolder()
            )
        }

        database.addBookmarkList(bookmarks).blockingAwait()

        assertThat(database.count()).isEqualTo(400)
        assertThat(database.getAllBookmarksSorted().blockingGet()).hasSize(400)
        assertThat(database.getFoldersSorted().blockingGet())
            .extracting<String> { it.title }
            .containsExactly("Imported")
    }

    @Test
    fun `bulk import does not duplicate existing URLs`() {
        val bookmark = Bookmark.Entry(
            url = "https://example.test/duplicate",
            title = "Duplicate",
            position = 0,
            folder = Bookmark.Folder.Root
        )

        database.addBookmarkList(listOf(bookmark, bookmark)).blockingAwait()

        assertThat(database.count()).isEqualTo(1)
    }

    private companion object {
        const val DATABASE_NAME = "bookmarkManager"
    }
}
