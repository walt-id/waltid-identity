package id.walt.credentials.display

/**
 * Shared locale matching for OpenID4VCI display metadata arrays.
 *
 * Preferences are matched from most specific tag to language-only, then an untagged entry,
 * then the first entry. Kotlin and Swift wallet surfaces should call this instead of
 * reimplementing the lookup.
 */
object DisplayLocales {
    fun normalize(locale: String?): String? =
        locale
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

    fun lookupTags(locale: String): List<String> = buildList {
        val subtags = locale.split('-').filter(String::isNotEmpty).toMutableList()
        while (subtags.isNotEmpty()) {
            add(subtags.joinToString("-"))
            subtags.removeAt(subtags.lastIndex)
            if (subtags.lastOrNull()?.length == 1) subtags.removeAt(subtags.lastIndex)
        }
    }

    fun <T> select(
        items: List<T>,
        preferredLocales: List<String>,
        localeOf: (T) -> String?,
    ): T? {
        if (items.isEmpty()) return null
        val preferences = preferredLocales.mapNotNull(::normalize).distinct()
        preferences.forEach { preferred ->
            lookupTags(preferred).forEach { candidate ->
                items.firstOrNull { normalize(localeOf(it)) == candidate }?.let { return it }
            }
        }
        return items.firstOrNull { localeOf(it).isNullOrBlank() } ?: items.firstOrNull()
    }
}
