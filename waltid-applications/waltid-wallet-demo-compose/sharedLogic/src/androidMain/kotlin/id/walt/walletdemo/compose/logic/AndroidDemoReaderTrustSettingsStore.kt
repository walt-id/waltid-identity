package id.walt.walletdemo.compose.logic

import android.content.Context

fun createAndroidDemoReaderTrustSettingsStore(context: Context): DemoReaderTrustSettingsStore {
    val preferences = context.applicationContext.getSharedPreferences(
        "walt_wallet_demo_settings",
        Context.MODE_PRIVATE,
    )
    return PersistentDemoReaderTrustSettingsStore(
        read = { preferences.getString(READER_TRUST_SETTINGS_KEY, null) },
        write = { encoded ->
            check(preferences.edit().putString(READER_TRUST_SETTINGS_KEY, encoded).commit()) {
                "Reader Authentication settings could not be persisted"
            }
        },
    )
}
