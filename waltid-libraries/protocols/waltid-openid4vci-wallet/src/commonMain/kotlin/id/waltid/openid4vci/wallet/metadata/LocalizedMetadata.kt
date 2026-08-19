package id.waltid.openid4vci.wallet.metadata

/**
 * Selects localized protocol metadata using the wallet's ordered BCP 47 preferences.
 *
 * The protocol may return all display alternatives even when it receives `Accept-Language`, so
 * callers must still select locally. Selection is deterministic: exact tag, progressively less
 * specific tag, an unlocalized entry, then the first advertised entry.
 */
public object LocalizedMetadata {
    /**
     * Returns usable, de-duplicated BCP 47 language tags in the order the wallet supplied them.
     *
     * This deliberately accepts the compact, well-formed tag subset needed for HTTP
     * `Accept-Language` and display matching. Invalid application configuration is omitted rather
     * than being sent verbatim to an issuer.
     */
    public fun normalizedPreferences(preferredLocales: List<String>): List<String> =
        preferredLocales
            .map(String::trim)
            .filter(::isLanguageTag)
            .distinctBy(String::lowercase)

    /** Returns the HTTP `Accept-Language` value, or null when no usable preference exists. */
    public fun acceptLanguageValue(preferredLocales: List<String>): String? {
        val preferences = normalizedPreferences(preferredLocales)
            .take(MAX_ACCEPT_LANGUAGE_PREFERENCES)
        if (preferences.isEmpty()) return null

        return preferences.mapIndexed { index, languageTag ->
            if (index == 0) languageTag else "$languageTag;q=${qualityValue(index, preferences.size)}"
        }.joinToString(", ")
    }

    /**
     * Selects a localized entry using the normalized wallet preferences.
     *
     * [localeOf] returns the entry's language tag, or null for an unlocalized entry.
     */
    public fun <T> select(
        entries: List<T>?,
        preferredLocales: List<String>,
        localeOf: (T) -> String?,
    ): T? {
        val available = entries.orEmpty()
        if (available.isEmpty()) return null

        val normalizedEntries = available.mapNotNull { entry ->
            localeOf(entry)
                ?.trim()
                ?.takeIf(::isLanguageTag)
                ?.lowercase()
                ?.let { locale -> entry to locale }
        }
        normalizedPreferences(preferredLocales).forEach { preference ->
            progressiveTags(preference.lowercase()).forEach { candidate ->
                normalizedEntries.firstOrNull { (_, locale) -> locale == candidate }?.let { return it.first }
            }
        }
        return available.firstOrNull { localeOf(it) == null } ?: available.first()
    }

    private fun progressiveTags(tag: String): Sequence<String> = sequence {
        var candidate = tag
        while (true) {
            yield(candidate)
            val separator = candidate.lastIndexOf('-')
            if (separator < 0) return@sequence
            candidate = candidate.substring(0, separator)
            // RFC 4647 lookup removes a singleton extension/private-use subtag together with
            // the preceding separator instead of considering an incomplete extension as a range.
            if (candidate.substringAfterLast('-', candidate).length == 1) {
                val singletonSeparator = candidate.lastIndexOf('-')
                candidate = if (singletonSeparator < 0) return@sequence else candidate.substring(0, singletonSeparator)
            }
        }
    }

    private fun isLanguageTag(value: String): Boolean =
        LANGUAGE_TAG.matches(value)

    private fun qualityValue(index: Int, preferenceCount: Int): String {
        val decrement = if (preferenceCount <= 10) 100 else maxOf(1, 999 / preferenceCount)
        val thousandths = (1000 - index * decrement).coerceAtLeast(1)
        return "0.${thousandths.toString().padStart(3, '0').trimEnd('0')}"
    }

    private val LANGUAGE_TAG = Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*")
    private const val MAX_ACCEPT_LANGUAGE_PREFERENCES = 1000
}
