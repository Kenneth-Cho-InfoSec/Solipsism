package com.krystelligence.solipsism.adblock.util.hash

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MurmurHashTest {

    @Test
    fun `32 bit and 64 bit hashes retain known vectors`() {
        assertThat(MurmurHash.hash32("hello")).isEqualTo(2_132_663_229)
        assertThat(MurmurHash.hash64("hello")).isEqualTo(-4_155_090_522_938_856_779L)
    }

    @Test
    fun `substring overload hashes the selected text`() {
        assertThat(MurmurHash.hash32("xxexample.comyy", 2, 11))
            .isEqualTo(MurmurHash.hash32("example.com"))
    }

    @Test
    fun `byte array length limits the hashed input`() {
        val data = "prefix-and-suffix".toByteArray()
        assertThat(MurmurHash.hash32(data, 6)).isEqualTo(MurmurHash.hash32("prefix"))
    }
}
