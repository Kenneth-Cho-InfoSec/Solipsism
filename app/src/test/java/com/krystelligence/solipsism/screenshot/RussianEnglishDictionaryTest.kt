package com.krystelligence.solipsism.screenshot

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RussianEnglishDictionaryTest {
    @Test
    fun `translates each word and preserves unknown words and punctuation`() {
        val dictionary = RussianEnglishDictionary(
            mapOf("красный" to "red", "дом" to "house")
        )

        assertThat(dictionary.translateWords("Красный дом, неизвестно!")).isEqualTo("Red house, неизвестно!")
    }

    @Test
    fun `does not translate a multi word phrase as one lookup`() {
        val dictionary = RussianEnglishDictionary(
            mapOf("красный" to "red", "дом" to "house")
        )

        assertThat(dictionary.translateWords("красный дом")).isEqualTo("red house")
    }
}
