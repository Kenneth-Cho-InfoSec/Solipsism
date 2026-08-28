package com.krystelligence.solipsism.browser.engine

/** Trusted starter pages used only by the hidden first-run quick-start gesture. */
object OnboardingStarterTabs {
    const val EXTRA_URLS = "com.krystelligence.solipsism.extra.ONBOARDING_STARTER_URLS"

    val urls: ArrayList<String> = arrayListOf(
        "https://www.amazon.com/",
        "https://www.youtube.com/",
        "https://www.google.com/search?q=Solipsism+Browser",
    )
}
