package dev.burooj.speedbreaker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RedirectsTest {
    @Test
    fun effectiveRedirects_preservesOrderAndDistinguishesInheritedCustomAndDisabled() {
        for (count in 0..4) {
            val destinations = (1..count).map { "alt.$it" }
            val settings = Settings(
                redirects = destinations,
                apps = mapOf(
                    "inherited" to AppPolicy(),
                    "custom" to AppPolicy(redirects = listOf("custom.alt")),
                    "disabled" to AppPolicy(redirects = emptyList()),
                ),
            )
            assertEquals(destinations, settings.effectiveRedirects("inherited"))
            assertEquals(listOf("custom.alt"), settings.effectiveRedirects("custom"))
            assertEquals(emptyList<String>(), settings.effectiveRedirects("disabled"))
        }
    }

    @Test
    fun invalidCustomSet_neverFallsBackToGlobalDestinations() {
        for (invalid in listOf(listOf("one", "one"), listOf("one", "target"), listOf(""), (1..5).map { "alt.$it" })) {
            val settings = Settings(redirects = listOf("global"), apps = mapOf("target" to AppPolicy(redirects = invalid)))
            assertEquals(emptyList<String>(), settings.effectiveRedirects("target"))
        }
    }
}
