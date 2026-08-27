package id.walt.walletdemo.compose.logic

/**
 * Locale matching for OpenID4VCI display arrays on the Compose demo surface.
 *
 * Keep this aligned with `id.walt.credentials.display.DisplayLocales` and WalletSDK
 * `DisplayLocales`. commonMain cannot depend on that library because the web target
 * has no wasm artifact for digital-credentials.
 */
internal object DisplayLocaleSelector {
    fun normalize(locale: String?): String? =
        locale
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

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

    private fun lookupTags(locale: String): List<String> = buildList {
        val subtags = locale.split('-').filter(String::isNotEmpty).toMutableList()
        while (subtags.isNotEmpty()) {
            add(subtags.joinToString("-"))
            subtags.removeAt(subtags.lastIndex)
            if (subtags.lastOrNull()?.length == 1) subtags.removeAt(subtags.lastIndex)
        }
    }
}
