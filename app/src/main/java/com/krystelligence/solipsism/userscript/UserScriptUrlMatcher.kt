package com.krystelligence.solipsism.userscript

import java.net.URI

/** Safe, bounded matching for @match and the common userscript @include form. */
object UserScriptUrlMatcher {
    fun matches(metadata: UserScriptMetadata, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https")) return false
        if (metadata.matches.none { matchPattern(it, uri) } &&
            metadata.includes.none { includePattern(it, url) }
        ) return false
        return metadata.excludes.none { pattern ->
            matchPattern(pattern, uri) || includePattern(pattern, url)
        }
    }

    private fun matchPattern(pattern: String, uri: URI): Boolean {
        val separator = pattern.indexOf("://")
        if (separator <= 0) return false
        val schemePattern = pattern.substring(0, separator).lowercase()
        val rest = pattern.substring(separator + 3)
        val pathSeparator = rest.indexOf('/')
        val hostAndPort = if (pathSeparator < 0) rest else rest.substring(0, pathSeparator)
        val pathPattern = if (pathSeparator < 0) "/*" else rest.substring(pathSeparator)
        val portSeparator = hostAndPort.lastIndexOf(':').takeIf {
            it > 0 && hostAndPort.substring(it + 1).toIntOrNull() != null
        }
        val hostPattern = portSeparator?.let { hostAndPort.substring(0, it) } ?: hostAndPort
        val requestedPort = portSeparator?.let { hostAndPort.substring(it + 1).toInt() }
        val schemeMatches = schemePattern == "*" || schemePattern == uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        val hostMatches = when {
            hostPattern == "*" -> true
            hostPattern.startsWith("*.") -> host.endsWith(hostPattern.drop(1)) && host != hostPattern.drop(2)
            else -> host == hostPattern.lowercase()
        }
        val portMatches = requestedPort == null || requestedPort == uri.port
        return schemeMatches && hostMatches && portMatches && glob(pathPattern, uri.rawPath ?: "/")
    }

    private fun includePattern(pattern: String, url: String): Boolean = glob(pattern, url, ignoreCase = false)

    private fun glob(pattern: String, value: String, ignoreCase: Boolean = true): Boolean {
        if (pattern.length > MAX_PATTERN_LENGTH || value.length > MAX_URL_LENGTH) return false
        val regex = buildString(pattern.length * 2 + 2) {
            append('^')
            pattern.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append('$')
        }
        return Regex(regex, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).matches(value)
    }

    private const val MAX_PATTERN_LENGTH = 2048
    private const val MAX_URL_LENGTH = 32 * 1024
}
