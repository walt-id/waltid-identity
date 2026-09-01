package id.walt.walletdemo.compose.logic

import platform.Foundation.NSUserDefaults

fun createIosDemoSigningProtectionStore(walletId: String): WalletDemoSigningProtectionStore {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = "$SELECTION_KEY_PREFIX$walletId"
    return object : WalletDemoSigningProtectionStore {
        override fun load(): WalletDemoSigningProtection? =
            defaults.stringForKey(key)?.let { value ->
                runCatching { WalletDemoSigningProtection.parse(value) }.getOrNull()
            }

        override fun save(protection: WalletDemoSigningProtection) {
            defaults.setObject(protection.name.lowercase(), forKey = key)
        }
    }
}

private const val SELECTION_KEY_PREFIX = "wallet-signing-protection:"
