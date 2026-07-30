package com.krystelligence.solipsism.audio

import com.krystelligence.solipsism.preference.IntEnum

enum class AudioPreset(override val value: Int) : IntEnum {
    FLAT(0),
    BASS_BOOST(1),
    VOCAL_BOOST(2),
    TREBLE_BOOST(3),
    ROCK(4),
    CLASSICAL(5),
    PODCAST(6),
    NIGHT(7)
}
