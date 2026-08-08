package com.krystelligence.solipsism.bookmark

import com.krystelligence.solipsism.database.Bookmark

object DecoyBookmarkFactory {

    private val entries = listOf(
        "https://www.bbc.co.uk/news" to "BBC News",
        "https://en.wikipedia.org/wiki/Main_Page" to "Wikipedia",
        "https://developer.android.com/" to "Android Developers",
        "https://www.mozilla.org/" to "Mozilla",
        "https://www.gov.uk/" to "GOV.UK",
        "https://www.openstreetmap.org/" to "OpenStreetMap",
        "https://www.theguardian.com/uk" to "The Guardian",
        "https://www.nhs.uk/" to "NHS",
        "https://www.weather.gov/" to "Weather"
    )

    fun create(): List<Bookmark.Entry> = entries.mapIndexed { index, (url, title) ->
        Bookmark.Entry(
            url = url,
            title = title,
            position = index,
            folder = Bookmark.Folder.Root
        )
    }
}
