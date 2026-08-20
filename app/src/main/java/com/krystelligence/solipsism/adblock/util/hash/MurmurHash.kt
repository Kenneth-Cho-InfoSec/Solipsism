package com.krystelligence.solipsism.adblock.util.hash

/** MurmurHash 2.0 implementation used by the ad-blocking bloom filters. */
object MurmurHash {

    /** Generates a 32-bit hash from the first [length] bytes using [seed]. */
    @JvmStatic
    fun hash32(data: ByteArray, length: Int, seed: Int): Int {
        val m = 0x5bd1e995
        val r = 24
        var hash = seed xor length
        val length4 = length / 4

        for (i in 0 until length4) {
            val offset = i * 4
            var k = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 24)
            k *= m
            k = k xor (k ushr r)
            k *= m
            hash *= m
            hash = hash xor k
        }

        val tail = length and 3
        val tailOffset = length and -4
        if (tail == 3) {
            hash = hash xor ((data[tailOffset + 2].toInt() and 0xff) shl 16)
        }
        if (tail >= 2) {
            hash = hash xor ((data[tailOffset + 1].toInt() and 0xff) shl 8)
        }
        if (tail >= 1) {
            hash = hash xor (data[tailOffset].toInt() and 0xff)
            hash *= m
        }

        hash = hash xor (hash ushr 13)
        hash *= m
        hash = hash xor (hash ushr 15)
        return hash
    }

    /** Generates a 32-bit hash using the original default seed. */
    @JvmStatic
    fun hash32(data: ByteArray, length: Int): Int = hash32(data, length, 0x9747b28c.toInt())

    /** Generates a 32-bit hash from a string. */
    @JvmStatic
    fun hash32(text: String): Int {
        val bytes = text.toByteArray()
        return hash32(bytes, bytes.size)
    }

    /** Generates a 32-bit hash from a substring. */
    @JvmStatic
    fun hash32(text: String, from: Int, length: Int): Int =
        hash32(text.substring(from, from + length))

    /** Generates a 64-bit hash from the first [length] bytes using [seed]. */
    @JvmStatic
    fun hash64(data: ByteArray, length: Int, seed: Int): Long {
        val m = 0xc6a4a7935bd1e995UL.toLong()
        val r = 47
        var hash = (seed.toLong() and 0xffffffffL) xor (length.toLong() * m)
        val length8 = length / 8

        for (i in 0 until length8) {
            val offset = i * 8
            var k = (data[offset].toLong() and 0xffL) or
                ((data[offset + 1].toLong() and 0xffL) shl 8) or
                ((data[offset + 2].toLong() and 0xffL) shl 16) or
                ((data[offset + 3].toLong() and 0xffL) shl 24) or
                ((data[offset + 4].toLong() and 0xffL) shl 32) or
                ((data[offset + 5].toLong() and 0xffL) shl 40) or
                ((data[offset + 6].toLong() and 0xffL) shl 48) or
                ((data[offset + 7].toLong() and 0xffL) shl 56)
            k *= m
            k = k xor (k ushr r)
            k *= m
            hash = hash xor k
            hash *= m
        }

        val tail = length and 7
        val tailOffset = length and -8
        if (tail >= 7) hash = hash xor ((data[tailOffset + 6].toLong() and 0xffL) shl 48)
        if (tail >= 6) hash = hash xor ((data[tailOffset + 5].toLong() and 0xffL) shl 40)
        if (tail >= 5) hash = hash xor ((data[tailOffset + 4].toLong() and 0xffL) shl 32)
        if (tail >= 4) hash = hash xor ((data[tailOffset + 3].toLong() and 0xffL) shl 24)
        if (tail >= 3) hash = hash xor ((data[tailOffset + 2].toLong() and 0xffL) shl 16)
        if (tail >= 2) hash = hash xor ((data[tailOffset + 1].toLong() and 0xffL) shl 8)
        if (tail >= 1) {
            hash = hash xor (data[tailOffset].toLong() and 0xffL)
            hash *= m
        }

        hash = hash xor (hash ushr r)
        hash *= m
        hash = hash xor (hash ushr r)
        return hash
    }

    /** Generates a 64-bit hash using the original default seed. */
    @JvmStatic
    fun hash64(data: ByteArray, length: Int): Long = hash64(data, length, 0xe17a1465.toInt())

    /** Generates a 64-bit hash from a string. */
    @JvmStatic
    fun hash64(text: String): Long {
        val bytes = text.toByteArray()
        return hash64(bytes, bytes.size)
    }

    /** Generates a 64-bit hash from a substring. */
    @JvmStatic
    fun hash64(text: String, from: Int, length: Int): Long =
        hash64(text.substring(from, from + length))
}
