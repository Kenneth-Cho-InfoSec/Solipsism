package com.krystelligence.solipsism.release

object ReleaseVersion {
    fun tag(version: String): String = if (version.startsWith("v")) version else "v$version"

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        for (index in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val difference = (a.numbers.getOrElse(index) { 0 }).compareTo(b.numbers.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return when {
            a.preRelease == b.preRelease -> 0
            a.preRelease == null -> 1
            b.preRelease == null -> -1
            else -> a.preRelease.compareTo(b.preRelease)
        }
    }

    private fun parse(value: String): Parsed {
        val clean = value.removePrefix("v")
        val parts = clean.split('-', limit = 2)
        return Parsed(parts[0].split('.').map { it.toIntOrNull() ?: 0 }, parts.getOrNull(1))
    }

    private data class Parsed(val numbers: List<Int>, val preRelease: String?)
}
