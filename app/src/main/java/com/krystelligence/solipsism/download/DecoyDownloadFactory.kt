package com.krystelligence.solipsism.download

import com.krystelligence.solipsism.database.downloads.DownloadEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object DecoyDownloadFactory {

    private val templates = listOf(
        "travel-itinerary-%s.pdf" to "1.2 MB",
        "meeting-notes-%s.docx" to "248 KB",
        "project-roadmap-%s.pdf" to "842 KB",
        "IMG_%s.jpg" to "3.4 MB",
        "receipt-%s.pdf" to "176 KB",
        "presentation-%s.pptx" to "2.1 MB",
        "weekly-budget-%s.xlsx" to "92 KB",
        "reading-list-%s.epub" to "1.8 MB",
        "podcast-episode-%s.m4a" to "44.6 MB",
        "release-notes-%s.pdf" to "510 KB"
    )

    fun create(count: Int, now: Long = System.currentTimeMillis()): List<DownloadEntry> {
        val random = Random(now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return templates.shuffled(random).take(count.coerceIn(5, templates.size)).mapIndexed { index, (template, size) ->
            val date = dateFormat.format(Date(now - index * 86_400_000L))
            DownloadEntry(
                url = "solipsism://decoy-download/$index",
                title = template.format(date),
                contentSize = size,
                isDecoy = true
            )
        }
    }
}
