/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.download

import android.util.Patterns.GOOD_IRI_CHAR
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses the URI-like text users actually type into a browser address field.
 * Unlike [java.net.URI], this accepts addresses without a scheme.
 */
class WebAddress(address: String?) {

    var scheme: String
    var host: String
    var port: Int
    var path: String
    var authInfo: String

    init {
        requireNotNull(address) { "address can't be null" }

        scheme = ""
        host = ""
        port = -1
        path = "/"
        authInfo = ""

        val matcher = ADDRESS_PATTERN.matcher(address)
        require(matcher.matches()) { "Parsing of address '$address' failed" }

        matcher.group(MATCH_GROUP_SCHEME)?.let { scheme = it.lowercase(Locale.ROOT) }
        matcher.group(MATCH_GROUP_AUTHORITY)?.let { authInfo = it }
        matcher.group(MATCH_GROUP_HOST)?.let { host = it }
        matcher.group(MATCH_GROUP_PORT)?.takeIf { it.isNotEmpty() }?.let {
            port = try {
                it.toInt()
            } catch (exception: NumberFormatException) {
                throw RuntimeException("Parsing of port number failed", exception)
            }
        }
        matcher.group(MATCH_GROUP_PATH)?.takeIf { it.isNotEmpty() }?.let {
            path = if (it[0] == '/') it else "/$it"
        }

        if (port == 443 && scheme.isEmpty()) {
            scheme = "https"
        } else if (port == -1) {
            port = if (scheme == "https") 443 else 80
        }
        if (scheme.isEmpty()) scheme = "http"
    }

    override fun toString(): String {
        val portPart = if ((port != 443 && scheme == "https") ||
            (port != 80 && scheme == "http")
        ) {
            ":$port"
        } else {
            ""
        }
        val authPart = if (authInfo.isEmpty()) "" else "$authInfo@"
        return "$scheme://$authPart$host$portPart$path"
    }

    private companion object {
        const val MATCH_GROUP_SCHEME = 1
        const val MATCH_GROUP_AUTHORITY = 2
        const val MATCH_GROUP_HOST = 3
        const val MATCH_GROUP_PORT = 4
        const val MATCH_GROUP_PATH = 5

        val ADDRESS_PATTERN: Pattern = Pattern.compile(
            "(?:(http|https|file)://)?" +
                """(?:([-A-Za-z0-9${'$'}_.+!*'(),;?&=]+(?::[-A-Za-z0-9${'$'}_.+!*'(),;?&=]+)?)@)?""" +
                """([${GOOD_IRI_CHAR}%_-][${GOOD_IRI_CHAR}%_\.-]*|\[[0-9a-fA-F:\.]+\])?""" +
                "(?::([0-9]*))?" +
                "(/?[^#]*)?" +
                ".*",
            Pattern.CASE_INSENSITIVE
        )
    }
}
