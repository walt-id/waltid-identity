package id.walt.wallet2.server.models

/**
 * BCP 47 aware selection of the best entry from a list, given the caller's preferred locales.
 * Falls back to the first locale-less entry, then to the first entry.
 */
internal fun <T> List<T>?.selectPreferredByLocale(
    preferredLocales: List<String>,
    locale: (T) -> String?,
): T? {
    val entries = this.orEmpty()
    if (entries.isEmpty()) return null
    val preferences = preferredLocales.mapNotNull(::normalizeLocale).distinct()

    preferences.forEach { preferred ->
        localeLookupTags(preferred).forEach { candidate ->
            entries.firstOrNull { normalizeLocale(locale(it)) == candidate }?.let { return it }
        }
    }
    return entries.firstOrNull { locale(it).isNullOrBlank() } ?: entries.first()
}

internal fun normalizeLocale(locale: String?): String? = locale
    ?.trim()
    ?.replace('_', '-')
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

internal fun localeLookupTags(locale: String): List<String> = buildList {
    val subtags = locale.split('-').filter(String::isNotEmpty).toMutableList()
    while (subtags.isNotEmpty()) {
        add(subtags.joinToString("-"))
        subtags.removeAt(subtags.lastIndex)
        if (subtags.lastOrNull()?.length == 1) subtags.removeAt(subtags.lastIndex)
    }
}
