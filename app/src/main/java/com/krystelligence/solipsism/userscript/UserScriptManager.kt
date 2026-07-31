package com.krystelligence.solipsism.userscript

import android.app.Application
import java.io.File
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserScriptManager @Inject constructor(application: Application) {
    private val root = File(application.filesDir, "userscripts")
    private val scripts = linkedMapOf<String, UserScript>()

    init {
        root.mkdirs()
        loadFromDisk()
    }

    @Synchronized
    fun all(): List<UserScript> = scripts.values.toList()

    @Synchronized
    fun find(id: String): UserScript? = scripts[id]

    @Synchronized
    fun matching(url: String, runAt: UserScriptRunAt): List<UserScript> = scripts.values
        .filter { it.enabled && it.metadata.isUnprivileged && it.metadata.runAt == runAt }
        .filter { UserScriptUrlMatcher.matches(it.metadata, url) }

    @Synchronized
    fun install(
        source: String,
        dependencies: List<String> = emptyList(),
        enabled: Boolean = true
    ): UserScript {
        validateSourceSize(source)
        dependencies.forEach(::validateSourceSize)
        val metadata = UserScriptMetadataParser.parse(source)
            ?: throw IllegalArgumentException("No valid userscript metadata block")
        val id = UUID.randomUUID().toString()
        val script = UserScript(id, metadata, source, dependencies, enabled)
        write(script)
        scripts[id] = script
        return script
    }

    fun installFromUrl(url: String, client: OkHttpClient): UserScript {
        require(url.startsWith("https://")) { "Userscript URLs must use HTTPS" }
        val source = fetchText(url, client)
        val metadata = UserScriptMetadataParser.parse(source)
            ?: throw IllegalArgumentException("Downloaded file has no valid userscript metadata")
        val dependencies = metadata.requires.map { dependencyUrl ->
            require(dependencyUrl.startsWith("https://")) { "@require must use HTTPS" }
            fetchText(dependencyUrl, client)
        }
        return install(source, dependencies)
    }

    @Synchronized
    fun updateSource(id: String, source: String) {
        validateSourceSize(source)
        val old = scripts[id] ?: throw IllegalArgumentException("Unknown userscript")
        val metadata = UserScriptMetadataParser.parse(source)
            ?: throw IllegalArgumentException("No valid userscript metadata block")
        val updated = old.copy(metadata = metadata, source = source)
        write(updated)
        scripts[id] = updated
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val old = scripts[id] ?: return
        val updated = old.copy(enabled = enabled)
        write(updated)
        scripts[id] = updated
    }

    @Synchronized
    fun delete(id: String) {
        scripts.remove(id)
        File(root, id).deleteRecursively()
    }

    private fun loadFromDisk() {
        root.listFiles()?.filter(File::isDirectory)?.forEach { directory ->
            val sourceFile = File(directory, SOURCE_FILE)
            val source = sourceFile.takeIf(File::exists)?.readText() ?: return@forEach
            val metadata = UserScriptMetadataParser.parse(source) ?: return@forEach
            val enabled = !File(directory, DISABLED_FILE).exists()
            val dependencies = directory.listFiles()
                .orEmpty()
                .filter { it.name.startsWith(REQUIRE_PREFIX) && it.extension == "js" }
                .sortedBy(File::getName)
                .map(File::readText)
            scripts[directory.name] = UserScript(directory.name, metadata, source, dependencies, enabled)
        }
    }

    private fun write(script: UserScript) {
        val directory = File(root, script.id).apply { mkdirs() }
        val temporary = File(directory, "$SOURCE_FILE.tmp")
        temporary.writeText(script.source)
        check(temporary.renameTo(File(directory, SOURCE_FILE))) { "Unable to store userscript" }
        directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(REQUIRE_PREFIX) && it.extension == "js" }
            .forEach(File::delete)
        script.dependencies.forEachIndexed { index, dependency ->
            File(directory, "$REQUIRE_PREFIX$index.js").writeText(dependency)
        }
        val disabled = File(directory, DISABLED_FILE)
        if (script.enabled) disabled.delete() else disabled.writeText("")
    }

    private fun validateSourceSize(source: String) {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_SOURCE_BYTES) {
            "Userscript is larger than 1 MiB"
        }
    }

    private companion object {
        const val SOURCE_FILE = "source.user.js"
        const val DISABLED_FILE = ".disabled"
        const val REQUIRE_PREFIX = "require-"
        const val MAX_SOURCE_BYTES = 1024 * 1024

        fun fetchText(url: String, client: OkHttpClient): String {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Unable to download $url (${response.code})" }
                val body = response.body ?: error("Empty userscript response")
                check(body.contentLength() <= MAX_SOURCE_BYTES || body.contentLength() == -1L) {
                    "Userscript or dependency is larger than 1 MiB"
                }
                val bytes = body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        check(output.size() <= MAX_SOURCE_BYTES) { "Userscript is larger than 1 MiB" }
                    }
                    output.toByteArray()
                }
                return bytes.toString(Charsets.UTF_8)
            }
        }
    }
}
