package com.krystelligence.solipsism.html.history

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.theme.ThemeProvider
import com.krystelligence.solipsism.constant.FILE
import com.krystelligence.solipsism.database.history.HistoryRepository
import com.krystelligence.solipsism.html.HtmlPageFactory
import com.krystelligence.solipsism.html.ListPageReader
import com.krystelligence.solipsism.html.jsoup.andBuild
import com.krystelligence.solipsism.html.jsoup.body
import com.krystelligence.solipsism.html.jsoup.clone
import com.krystelligence.solipsism.html.jsoup.findId
import com.krystelligence.solipsism.html.jsoup.id
import com.krystelligence.solipsism.html.jsoup.parse
import com.krystelligence.solipsism.html.jsoup.removeElement
import com.krystelligence.solipsism.html.jsoup.style
import com.krystelligence.solipsism.html.jsoup.tag
import com.krystelligence.solipsism.html.jsoup.title
import android.app.Application
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Factory for the history page.
 */
class HistoryPageFactory @Inject constructor(
    private val listPageReader: ListPageReader,
    private val application: Application,
    private val historyRepository: HistoryRepository,
    private val themeProvider: ThemeProvider
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_history)

    private fun Int.toColor(): String {
        val string = Integer.toHexString(this)

        return string.substring(2) + string.substring(0, 2)
    }

    private val backgroundColor: String
        get() = themeProvider.color(R.attr.colorPrimary).toColor()
    private val dividerColor: String
        get() = themeProvider.color(R.attr.autoCompleteBackgroundColor).toColor()
    private val textColor: String
        get() = themeProvider.color(R.attr.autoCompleteTitleColor).toColor()
    private val subtitleColor: String
        get() = themeProvider.color(R.attr.autoCompleteUrlColor).toColor()

    override fun buildPage(): Single<String> = historyRepository
        .lastHundredVisitedHistoryEntries()
        .map { list ->
            parse(listPageReader.provideHtml()) andBuild {
                title { title }
                style { content ->
                    content.replace("--body-bg: {COLOR}", "--body-bg: #$backgroundColor;")
                        .replace("--divider-color: {COLOR}", "--divider-color: #$dividerColor;")
                        .replace("--title-color: {COLOR}", "--title-color: #$textColor;")
                        .replace("--subtitle-color: {COLOR}", "--subtitle-color: #$subtitleColor;")
                }
                body {
                    val repeatedElement = findId("repeated").removeElement()
                    id("content") {
                        list.forEach {
                            appendChild(repeatedElement.clone {
                                tag("a") { attr("href", it.url) }
                                id("title") { text(it.title) }
                                id("url") { text(it.url) }
                            })
                        }
                    }
                }
            }
        }
        .map { content -> Pair(createHistoryPage(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use { it.write(content) }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Use this observable to immediately delete the history page. This will clear the cached
     * history page that was stored on file.
     *
     * @return a completable that deletes the history page when subscribed to.
     */
    fun deleteHistoryPage(): Completable = Completable.fromAction {
        with(createHistoryPage()) {
            if (exists()) {
                delete()
            }
        }
    }

    private fun createHistoryPage(): File {
        val generatedHtml = File(application.filesDir, "generated-html")
        generatedHtml.mkdirs()
        return File(generatedHtml, FILENAME)
    }

    companion object {
        const val FILENAME = "history.html"
    }

}
