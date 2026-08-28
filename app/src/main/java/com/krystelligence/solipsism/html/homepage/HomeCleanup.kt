package com.krystelligence.solipsism.html.homepage

import com.krystelligence.solipsism.migration.Cleanup
import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class HomeCleanup @Inject constructor(
    private val application: Application
) : Cleanup.Action {
    override val versionCode: Int = 101

    override suspend fun execute() {
        withContext(Dispatchers.IO) {
            listOf(
                File(application.filesDir, LEGACY_HOMEPAGE_FILENAME),
                File(File(application.filesDir, "generated-html"), LEGACY_HOMEPAGE_FILENAME),
            ).forEach(File::delete)
        }
    }

    private companion object {
        const val LEGACY_HOMEPAGE_FILENAME = "homepage.html"
    }
}
