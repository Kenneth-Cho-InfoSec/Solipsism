package com.solipsism.spike

import kotlin.test.Test
import kotlin.test.assertEquals

class SpikeTest {
    @Test
    fun spikeMessage_isDeterministic() {
        assertEquals("KMP on iOS: OK", spikeMessage())
    }
}
