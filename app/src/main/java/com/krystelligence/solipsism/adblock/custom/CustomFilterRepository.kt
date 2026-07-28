package com.krystelligence.solipsism.adblock.custom

import android.app.Application
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFilterRepository @Inject constructor(application: Application) {
    private val file = File(application.filesDir, "adblock/custom-filters.txt")
    private val filters = linkedMapOf<String, CustomFilter>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    @Synchronized fun all(): List<CustomFilter> = filters.values.toList()

    @Synchronized fun add(line: String, source: CustomFilterSource = CustomFilterSource.MANUAL): CustomFilter {
        val result = CustomFilterParser.parse(line, source)
        val valid = result as? CustomFilterParseResult.Valid
            ?: throw IllegalArgumentException((result as? CustomFilterParseResult.Invalid)?.message ?: "Comment cannot be saved")
        filters[valid.filter.line] = valid.filter
        save()
        return valid.filter
    }

    @Synchronized fun addAll(lines: Iterable<String>, source: CustomFilterSource = CustomFilterSource.IMPORTED): List<String> {
        val errors = mutableListOf<String>()
        lines.forEach { line ->
            when (val result = CustomFilterParser.parse(line, source)) {
                is CustomFilterParseResult.Valid -> filters[result.filter.line] = result.filter
                is CustomFilterParseResult.Invalid -> errors += "$line — ${result.message}"
                CustomFilterParseResult.Comment -> Unit
            }
        }
        save()
        return errors
    }

    @Synchronized fun setEnabled(line: String, enabled: Boolean) {
        filters[line]?.let { filters[line] = it.copy(enabled = enabled); save() }
    }

    @Synchronized fun delete(line: String) {
        filters.remove(line)
        save()
    }

    @Synchronized fun clear() {
        filters.clear()
        save()
    }

    @Synchronized fun cosmeticFor(url: String): List<CosmeticFilter> {
        val host = CustomFilterParser.hostFromUrl(url) ?: return emptyList()
        return filters.values.asSequence()
            .filter { it.enabled }
            .mapNotNull { filter ->
                (CustomFilterParser.parse(filter.line, filter.source) as? CustomFilterParseResult.Valid)?.cosmetic
            }
            .filter { it.appliesTo(host) }
            .toList()
    }

    @Synchronized fun shouldBlockNetwork(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        val rules = filters.values.filter { it.enabled }.map { it.line }
        val exceptions = rules.filter { it.startsWith("@@||") }.map { it.removePrefix("@@||").substringBefore('^').lowercase() }
        if (exceptions.any { host == it || host.endsWith(".$it") }) return false
        return rules.filter { it.startsWith("||") }.any {
            val domain = it.removePrefix("||").substringBefore('^').substringBefore('/').lowercase()
            host == domain || host.endsWith(".$domain")
        }
    }

    private fun load() {
        if (!file.exists()) return
        file.readLines().forEach { line ->
            when (val result = CustomFilterParser.parse(line)) {
                is CustomFilterParseResult.Valid -> filters[result.filter.line] = result.filter
                else -> Unit
            }
        }
    }

    private fun save() {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(filters.values.joinToString("\n") { it.line } + if (filters.isNotEmpty()) "\n" else "")
        check(temporary.renameTo(file)) { "Unable to save custom filters" }
    }
}
