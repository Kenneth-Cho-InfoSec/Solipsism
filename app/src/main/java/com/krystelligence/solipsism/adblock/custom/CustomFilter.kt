package com.krystelligence.solipsism.adblock.custom

enum class CustomFilterSource { PICKER, MANUAL, IMPORTED }

data class CustomFilter(
    val line: String,
    val source: CustomFilterSource,
    val enabled: Boolean = true
)

data class CosmeticFilter(
    val domains: List<String>,
    val excludedDomains: List<String>,
    val selector: String,
    val exception: Boolean
) {
    fun appliesTo(host: String): Boolean {
        val normalized = host.lowercase()
        val included = domains.isEmpty() || domains.any { normalized == it || normalized.endsWith(".$it") }
        val excluded = excludedDomains.any { normalized == it || normalized.endsWith(".$it") }
        return included && !excluded
    }
}

sealed class CustomFilterParseResult {
    data class Valid(val filter: CustomFilter, val cosmetic: CosmeticFilter?) : CustomFilterParseResult()
    data class Invalid(val message: String) : CustomFilterParseResult()
    data object Comment : CustomFilterParseResult()
}
