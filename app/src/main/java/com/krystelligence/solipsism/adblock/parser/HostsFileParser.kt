package com.krystelligence.solipsism.adblock.parser

import com.krystelligence.solipsism.database.adblock.Host
import com.krystelligence.solipsism.extensions.containsChar
import com.krystelligence.solipsism.extensions.indexOfChar
import com.krystelligence.solipsism.extensions.inlineReplace
import com.krystelligence.solipsism.extensions.inlineReplaceChar
import com.krystelligence.solipsism.extensions.inlineTrim
import com.krystelligence.solipsism.extensions.stringEquals
import com.krystelligence.solipsism.extensions.substringToBuilder
import com.krystelligence.solipsism.log.Logger
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * A single threaded parser for a hosts file.
 */
class HostsFileParser @Inject constructor(
    private val logger: Logger
) {

    private val lineBuilder = StringBuilder()

    /**
     * Parse the lines of the [input] from a hosts file and return the list of [String] domains held
     * in that file.
     */
    fun parseInput(input: InputStreamReader): List<Host> {
        val time = System.currentTimeMillis()

        val domains = ArrayList<Host>(100)

        input.use { inputStreamReader ->
            inputStreamReader.forEachLine {
                parseLine(it, domains)
            }
        }

        logger.log(TAG, "Parsed ad list in: ${(System.currentTimeMillis() - time)} ms")

        return domains
    }

    /**
     * Parse a [line] from a hosts file and populate the [parsedList] with the extracted hosts.
     */
    private fun parseLine(line: String, parsedList: MutableList<Host>) {
        lineBuilder.setLength(0)
        lineBuilder.append(line)
        if (lineBuilder.isNotEmpty() && lineBuilder[0] != COMMENT_CHAR) {
            lineBuilder.inlineReplace(LOCAL_IP_V4, EMPTY)
            lineBuilder.inlineReplace(LOCAL_IP_V4_ALT, EMPTY)
            lineBuilder.inlineReplace(LOCAL_IP_V6, EMPTY)
            lineBuilder.inlineReplace(LOCAL_BROADCAST, EMPTY)
            lineBuilder.inlineReplaceChar(TAB_CHAR, SPACE_CHAR)

            val comment = lineBuilder.indexOfChar(COMMENT_CHAR)
            if (comment > 0) {
                lineBuilder.setLength(comment)
            } else if (comment == 0) {
                return
            }

            lineBuilder.inlineTrim()

            if (lineBuilder.isNotEmpty() && !isNonRoutable(lineBuilder)) {
                while (lineBuilder.containsChar(SPACE_CHAR)) {
                    val space = lineBuilder.indexOfChar(SPACE_CHAR)
                    val partial = lineBuilder.substringToBuilder(0, space)
                    partial.inlineTrim()

                    val partialLine = partial.toString()

                    // Skip non-routable leftovers such as bare localhost entries
                    // that can trail link-local lines.
                    if (partialLine.isNotEmpty() && !isNonRoutable(partial)) {
                        // Add string to list
                        parsedList.add(Host(partialLine))
                    }
                    lineBuilder.inlineReplace(partialLine, EMPTY)
                    lineBuilder.inlineTrim()
                }
                if (lineBuilder.isNotEmpty() && !isNonRoutable(lineBuilder)) {
                    // Add string to list.
                    parsedList.add(Host(lineBuilder.toString()))
                }
            }
        }
    }

    /**
     * Returns true for hostnames that are never real remote ad/tracking hosts:
     * loopback aliases and other non-routable local names. Blocking these would
     * either be dead weight in the filter or, in the case of localhost, risk
     * breaking local navigations.
     */
    private fun isNonRoutable(host: StringBuilder): Boolean =
        host.stringEquals(LOCALHOST) ||
            host.stringEquals(LOCALHOST_LOCALDOMAIN) ||
            host.stringEquals(BROADCASTHOST) ||
            host.stringEquals(LOCAL_NAME) ||
            host.containsChar(COLON_CHAR) ||
            host.containsChar(PERCENT_CHAR)

    companion object {
        private const val TAG = "HostsFileParser"

        private const val LOCAL_IP_V4 = "127.0.0.1"
        private const val LOCAL_IP_V4_ALT = "0.0.0.0"
        private const val LOCAL_IP_V6 = "::1"
        private const val LOCAL_BROADCAST = "255.255.255.255"
        private const val LOCALHOST = "localhost"
        private const val LOCALHOST_LOCALDOMAIN = "localhost.localdomain"
        private const val BROADCASTHOST = "broadcasthost"
        private const val LOCAL_NAME = "local"
        private const val COLON_CHAR = ':'
        private const val PERCENT_CHAR = '%'
        private const val COMMENT_CHAR = '#'
        private const val TAB_CHAR = '\t'
        private const val SPACE_CHAR = ' '
        private const val EMPTY = ""
    }
}
