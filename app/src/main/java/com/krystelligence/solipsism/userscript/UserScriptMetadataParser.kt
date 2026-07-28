package com.krystelligence.solipsism.userscript

/** Parses the standard userscript metadata block without executing the script. */
object UserScriptMetadataParser {
    private const val START = "==UserScript=="
    private const val END = "==/UserScript=="

    fun parse(source: String): UserScriptMetadata? {
        val start = source.indexOf(START)
        if (start < 0) return null
        val end = source.indexOf(END, start + START.length)
        if (end < 0) return null

        val values = linkedMapOf<String, MutableList<String>>()
        source.substring(start + START.length, end)
            .lineSequence()
            .forEach { line ->
                val match = HEADER_LINE.matchEntire(line) ?: return@forEach
                values.getOrPut(match.groupValues[1].lowercase()) { mutableListOf() }
                    .add(match.groupValues[2].trim())
            }

        val name = values.first("name") ?: return null
        val namespace = values.first("namespace") ?: "solipsism.local"
        val matches = values.all("match")
        val includes = values.all("include")
        val excludes = values.all("exclude") + values.all("exclude-match")
        if (matches.isEmpty() && includes.isEmpty()) return null

        val grants = values.all("grant").ifEmpty { listOf("none") }
        val runAt = when (values.first("run-at")?.lowercase()) {
            "document-start" -> UserScriptRunAt.DOCUMENT_START
            "document-body", "document-end" -> UserScriptRunAt.DOCUMENT_END
            "context-menu" -> return null
            else -> UserScriptRunAt.DOCUMENT_IDLE
        }

        return UserScriptMetadata(
            name = name,
            namespace = namespace,
            version = values.first("version") ?: "0",
            description = values.first("description") ?: "",
            matches = matches,
            includes = includes,
            excludes = excludes,
            grants = grants,
            requires = values.all("require"),
            runAt = runAt,
            noFrames = values.containsKey("noframes"),
            updateUrl = values.first("updateurl"),
            downloadUrl = values.first("downloadurl")
        )
    }

    private fun Map<String, List<String>>.first(key: String) = this[key]?.firstOrNull()?.takeIf { it.isNotBlank() }
    private fun Map<String, List<String>>.all(key: String) = this[key].orEmpty().filter(String::isNotBlank)

    private val HEADER_LINE = Regex("^\\s*//\\s*@([^\\s]+)(?:\\s+(.*?))?\\s*$")
}
