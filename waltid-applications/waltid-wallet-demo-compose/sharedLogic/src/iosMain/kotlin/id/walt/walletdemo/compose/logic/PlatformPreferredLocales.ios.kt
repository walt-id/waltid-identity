package id.walt.walletdemo.compose.logic

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun platformPreferredLocales(): List<String> =
    NSLocale.preferredLanguages.mapNotNull { it as? String }
