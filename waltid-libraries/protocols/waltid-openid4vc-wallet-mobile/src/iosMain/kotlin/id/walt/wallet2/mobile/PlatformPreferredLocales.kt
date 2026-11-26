package id.walt.wallet2.mobile

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun platformPreferredLocales(): List<String> =
    NSLocale.preferredLanguages.filterIsInstance<String>()
