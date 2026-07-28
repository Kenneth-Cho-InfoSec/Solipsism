package com.krystelligence.solipsism.adblock.custom

import java.net.URI

object CustomFilterParser {
    private val cosmeticSeparator = Regex("(#@#|##)")
    private val validDomain = Regex("^[a-z0-9.-]+$")

    fun parse(line: String, source: CustomFilterSource = CustomFilterSource.MANUAL): CustomFilterParseResult {
        val normalized = line.trim()
        if (normalized.isEmpty() || normalized.startsWith("!")) return CustomFilterParseResult.Comment
        if (normalized.length > 4096) return CustomFilterParseResult.Invalid("Filter is longer than 4096 characters")
        if (normalized.contains("+js(") || normalized.contains("#$#") || normalized.contains("#?#")) {
            return CustomFilterParseResult.Invalid("Procedural and JavaScript filters are not supported")
        }

        val match = cosmeticSeparator.find(normalized)
        if (match != null) {
            val domainPart = normalized.substring(0, match.range.first)
            val selector = normalized.substring(match.range.last + 1).trim()
            if (selector.isBlank()) return CustomFilterParseResult.Invalid("CSS selector is empty")
            if (selector.contains('{') || selector.contains('}') || selector.contains(';')) {
                return CustomFilterParseResult.Invalid("Use a CSS selector, not a CSS declaration block")
            }
            val domains = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            try {
                domainPart.split(',').map(String::trim).filter(String::isNotBlank).forEach { domain ->
                    val target = domain.removePrefix("~").lowercase()
                    if (!validDomain.matches(target)) throw InvalidDomainException(target)
                    if (domain.startsWith("~")) excluded += target else domains += target
                }
            } catch (exception: InvalidDomainException) {
                return CustomFilterParseResult.Invalid(exception.message ?: "Invalid domain")
            }
            val exception = match.value == "#@#"
            val parsed = CosmeticFilter(domains, excluded, selector, exception)
            return CustomFilterParseResult.Valid(CustomFilter(normalized, source), parsed)
        }

        if (normalized.startsWith("||") || normalized.startsWith("@@||")) {
            val network = normalized.removePrefix("@@").removePrefix("||")
                .substringBefore('^').substringBefore('/')
            if (!validDomain.matches(network)) return CustomFilterParseResult.Invalid("Network filter has an invalid domain")
            return CustomFilterParseResult.Valid(CustomFilter(normalized, source), null)
        }
        return CustomFilterParseResult.Invalid("Expected a cosmetic rule (## or #@#) or a basic domain rule")
    }

    fun hostFromUrl(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    private class InvalidDomainException(domain: String) : IllegalArgumentException("Invalid domain: $domain")
}
