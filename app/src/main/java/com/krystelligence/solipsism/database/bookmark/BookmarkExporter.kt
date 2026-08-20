package com.krystelligence.solipsism.database.bookmark

import android.content.Context
import android.os.Environment
import android.util.Log
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.asFolder
import com.krystelligence.solipsism.utils.Preconditions
import io.reactivex.rxjava3.core.Completable
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/** Imports and exports the browser's line-delimited JSON bookmark format. */
object BookmarkExporter {

    private const val TAG = "BookmarkExporter"
    private const val KEY_URL = "url"
    private const val KEY_TITLE = "title"
    private const val KEY_FOLDER = "folder"
    private const val KEY_ORDER = "order"

    /** Retrieves the default bookmarks bundled in the application assets. */
    @JvmStatic
    fun importBookmarksFromAssets(context: Context): List<Bookmark.Entry> {
        val bookmarks = mutableListOf<Bookmark.Entry>()
        try {
            context.resources.openRawResource(R.raw.default_bookmarks).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.forEachLine { line ->
                        try {
                            val json = JSONObject(line)
                            bookmarks += Bookmark.Entry(
                                json.getString(KEY_URL),
                                json.getString(KEY_TITLE),
                                json.getInt(KEY_ORDER),
                                json.getString(KEY_FOLDER).asFolder()
                            )
                        } catch (exception: JSONException) {
                            Log.e(TAG, "Can't parse line $line", exception)
                        }
                    }
                }
            }
        } catch (exception: IOException) {
            Log.e(TAG, "Error reading the bookmarks file", exception)
        }
        return bookmarks
    }

    /** Exports bookmarks to a file on the RxJava worker selected by the caller. */
    @JvmStatic
    fun exportBookmarksToFile(bookmarkList: List<Bookmark.Entry>, file: File): Completable =
        Completable.fromAction {
            Preconditions.checkNonNull(bookmarkList)
            BufferedWriter(FileWriter(file, false)).use { writer ->
                bookmarkList.forEach { item ->
                    writer.write(item.toJson().toString())
                    writer.newLine()
                }
            }
        }

    /** Exports bookmarks to an output stream, retaining the previous close-on-completion behaviour. */
    @JvmStatic
    fun exportBookmarksToOutputStream(
        bookmarkList: List<Bookmark.Entry>,
        outputStream: OutputStream
    ): Completable = Completable.fromAction {
        Preconditions.checkNonNull(bookmarkList)
        BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
            bookmarkList.forEach { item ->
                writer.write(item.toJson().toString())
                writer.newLine()
            }
        }
    }

    /** Imports the line-delimited JSON format produced by this class. */
    @JvmStatic
    @Throws(Exception::class)
    fun importBookmarksFromFileStream(inputStream: InputStream): List<Bookmark.Entry> =
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            buildList {
                reader.forEachLine { line ->
                    val json = JSONObject(line)
                    add(
                        Bookmark.Entry(
                            json.getString(KEY_URL),
                            json.getString(KEY_TITLE),
                            json.getInt(KEY_ORDER),
                            json.getString(KEY_FOLDER).asFolder()
                        )
                    )
                }
            }
        }

    /** Creates a unique bookmark export file in the public Downloads directory. */
    @JvmStatic
    fun createNewExportFile(): File {
        var counter = 0
        var file = exportFile(counter)
        while (file.exists()) {
            counter++
            file = exportFile(counter)
        }
        return file
    }

    private fun exportFile(counter: Int): File {
        val filename = if (counter == 0) {
            "BookmarksExport.txt"
        } else {
            "BookmarksExport-$counter.txt"
        }
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            filename
        )
    }

    private fun Bookmark.Entry.toJson(): JSONObject = JSONObject().apply {
        put(KEY_TITLE, title)
        put(KEY_URL, url)
        put(KEY_FOLDER, folder.title)
        put(KEY_ORDER, position)
    }
}
