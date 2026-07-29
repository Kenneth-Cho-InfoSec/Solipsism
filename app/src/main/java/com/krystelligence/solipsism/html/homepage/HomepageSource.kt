package com.krystelligence.solipsism.html.homepage

/** The source used when a new tab opens its homepage. */
enum class HomepageSource(val value: Int) {
    BUILT_IN(0),
    STATIC_HTML(1),
    DOMAIN(2);

    companion object {
        fun fromValue(value: Int): HomepageSource = entries.firstOrNull { it.value == value } ?: BUILT_IN
    }
}
