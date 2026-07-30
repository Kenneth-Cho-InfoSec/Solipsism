package com.krystelligence.solipsism.html.homepage

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import java.nio.charset.StandardCharsets

/**
 * Sanitizes user-authored homepage HTML before it is persisted or rendered.
 * This is deliberately an allowlist; executable and remotely-loaded content is not supported.
 */
object StaticHomepageSanitizer {

    const val MAX_HTML_BYTES = 512 * 1024
    const val MAX_CSS_BYTES = 128 * 1024
    const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

    private val allowedTags = arrayOf(
        "html", "head", "body", "title", "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "div", "span", "br", "hr", "strong", "b", "em", "i", "u", "s",
        "small", "sub", "sup", "ul", "ol", "li", "blockquote", "pre", "code", "a"
    )

    private val safeCssProperties = setOf(
        "align-items", "background-color", "border", "border-radius", "color", "display",
        "font-size", "font-style", "font-weight", "gap", "justify-content", "letter-spacing",
        "line-height", "margin", "opacity", "padding", "text-align", "text-decoration",
        "width", "height", "max-width", "min-height", "position", "top", "right", "bottom",
        "left", "inset", "z-index", "flex-direction", "flex-wrap", "grid-template-columns",
        "grid-auto-flow", "overflow", "transform", "background", "background-size",
        "background-position"
    )

    private val safeList = Safelist.none()
        .addTags(*allowedTags)
        .addTags("style")
        .addAttributes("*", "class", "style", "data-solipsism-style-index")
        .addAttributes("a", "href", "title")
        .addProtocols("a", "href", "http", "https")

    fun sanitize(source: String): String {
        require(source.toByteArray(StandardCharsets.UTF_8).size <= MAX_HTML_BYTES) {
            "Homepage HTML exceeds ${MAX_HTML_BYTES / 1024} KB"
        }

        val parsed = Jsoup.parse(source)
        var cssBytes = 0
        val safeStyles = mutableListOf<String>()
        parsed.select("[style]").forEachIndexed { index, element ->
            val style = element.attr("style")
            val safeStyle = sanitizeStyle(style)
            safeStyles += safeStyle
            element.attr("data-solipsism-style-index", index.toString())
            cssBytes += safeStyle.toByteArray(StandardCharsets.UTF_8).size
        }
        val safeStyleBlocks = parsed.select("style").map {
            sanitizeCss(it.data().ifBlank { it.html() })
        }
        parsed.select("style").remove()
        cssBytes += safeStyleBlocks.sumOf { it.toByteArray(StandardCharsets.UTF_8).size }
        require(cssBytes <= MAX_CSS_BYTES) {
            "Homepage CSS exceeds ${MAX_CSS_BYTES / 1024} KB"
        }

        val cleaned = Cleaner(safeList).clean(parsed)
        cleaned.select("[data-solipsism-style-index]").forEach { element ->
            val index = element.attr("data-solipsism-style-index").toIntOrNull()
            val safeStyle = index?.let(safeStyles::getOrNull).orEmpty()
            if (safeStyle.isNotBlank()) element.attr("style", safeStyle)
            else element.removeAttr("style")
            element.removeAttr("data-solipsism-style-index")
        }
        safeStyleBlocks.filter(String::isNotBlank).forEach { safeStyle ->
            cleaned.head().appendElement("style").text(safeStyle)
        }
        cleaned.outputSettings().prettyPrint(false)
        return cleaned.outerHtml()
    }

    private fun sanitizeStyle(style: String): String {
        return sanitizeDeclarations(style)
    }

    private fun sanitizeCss(style: String): String {
        val rulePattern = Regex("(?s)([^{}]+)\\{([^{}]*)\\}")
        return rulePattern.findAll(style).mapNotNull { match ->
            val selector = match.groupValues[1].trim()
            val lowerSelector = selector.lowercase()
            if (selector.isBlank() || listOf("@", "url(", "javascript", "<", ">").any(lowerSelector::contains)) {
                return@mapNotNull null
            }
            val declarations = sanitizeDeclarations(match.groupValues[2])
            if (declarations.isBlank()) null else "$selector { $declarations; }"
        }.joinToString(" ")
    }

    private fun sanitizeDeclarations(style: String): String {
        return style.split(';').mapNotNull { declaration ->
            val separator = declaration.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val property = declaration.substring(0, separator).trim().lowercase()
            val value = declaration.substring(separator + 1).trim()
            val lowerValue = value.lowercase()
            val unsafeValue = listOf(
                "url(", "@import", "expression", "javascript", "-moz-binding", "behavior", "<", ">"
            ).any(lowerValue::contains)
            if (property in safeCssProperties && value.isNotBlank() && !unsafeValue) {
                "$property: $value"
            } else null
        }.joinToString("; ")
    }
}
