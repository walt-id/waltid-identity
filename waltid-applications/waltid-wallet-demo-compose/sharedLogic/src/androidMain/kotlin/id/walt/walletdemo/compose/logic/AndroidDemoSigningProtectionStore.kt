package id.walt.walletdemo.compose.logic

import android.content.Context

fun createAndroidDemoSigningProtectionStore(
    context: Context,
    walletId: String,
): WalletDemoSigningProtectionStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val key = "$SELECTION_KEY_PREFIX$walletId"
    return object : WalletDemoSigningProtectionStore {
        override fun load(): WalletDemoSigningProtection? =
            preferences.getString(key, null)?.let { value ->
                runCatching { WalletDemoSigningProtection.parse(value) }.getOrNull()
            }

        override fun save(protection: WalletDemoSigningProtection) {
            check(preferences.edit().putString(key, protection.name.lowercase()).commit()) {
                "Could not persist wallet signing protection"
            }
        }
    }
}

private const val PREFERENCES_NAME = "walt_wallet_demo_signing_protection"
private const val SELECTION_KEY_PREFIX = "selection:"
