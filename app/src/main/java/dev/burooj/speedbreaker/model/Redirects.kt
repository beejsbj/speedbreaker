package dev.burooj.speedbreaker.model

/** Redirects are optional; a custom empty list deliberately disables inheritance. */
internal object Redirects {
    const val MAX_COUNT = 4

    /** Settings edits remove newly targeted apps and duplicates, preserving destination order. */
    fun normalize(values: List<String>, targets: Set<String>): List<String> =
        if (values.size > MAX_COUNT) emptyList() else {
            values.filter { it.isNotBlank() && it !in targets }.distinct()
        }

    /** Runtime choices must come from a complete valid set; never truncate malformed input. */
    fun isValid(values: List<String>, targets: Set<String> = emptySet()): Boolean =
        values.size <= MAX_COUNT && values.toSet().size == values.size &&
            values.all { it.isNotBlank() && it !in targets }
}

internal fun Settings.effectiveRedirects(packageName: String): List<String> =
    (apps[packageName]?.redirects ?: redirects)
        .takeIf { Redirects.isValid(it, apps.keys) }.orEmpty()
