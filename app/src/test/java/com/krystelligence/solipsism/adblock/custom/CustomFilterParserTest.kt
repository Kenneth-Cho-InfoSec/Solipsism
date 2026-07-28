package com.krystelligence.solipsism.adblock.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFilterParserTest {
    @Test
    fun parsesCosmeticRuleAndException() {
        val result = CustomFilterParser.parse("example.com##.ad") as CustomFilterParseResult.Valid
        assertEquals(listOf("example.com"), result.cosmetic?.domains)
        assertEquals(".ad", result.cosmetic?.selector)
        assertTrue((CustomFilterParser.parse("example.com#@#.ad") as CustomFilterParseResult.Valid).cosmetic!!.exception)
    }

    @Test
    fun rejectsJavaScriptAndProceduralRules() {
        assertTrue(CustomFilterParser.parse("example.com##+js(set,foo,bar)") is CustomFilterParseResult.Invalid)
        assertTrue(CustomFilterParser.parse("example.com#?#div:has-text(ad)") is CustomFilterParseResult.Invalid)
    }

    @Test
    fun acceptsBasicNetworkRules() {
        assertTrue(CustomFilterParser.parse("||ads.example.com^") is CustomFilterParseResult.Valid)
        assertTrue(CustomFilterParser.parse("@@||trusted.example.com^") is CustomFilterParseResult.Valid)
    }
}
