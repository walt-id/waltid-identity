package id.walt.walletdemo.compose.logic

import android.os.LocaleList
import java.util.Locale

internal actual fun platformPreferredLocales(): List<String> {
    val fromList = runCatching {
        LocaleList.getDefault().let { locales ->
            List(locales.size()) { index -> locales[index].toLanguageTag() }
        }
    }.getOrDefault(emptyList())
    if (fromList.isNotEmpty()) return fromList
    return listOfNotNull(Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() })
}
