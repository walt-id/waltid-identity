package id.walt.wallet2.mobile

import android.os.Build
import android.os.LocaleList
import java.util.Locale

internal actual fun platformPreferredLocales(): List<String> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val locales = LocaleList.getDefault()
        if (!locales.isEmpty) return List(locales.size()) { locales[it].toLanguageTag() }
    }
    return listOf(Locale.getDefault().toLanguageTag())
}
