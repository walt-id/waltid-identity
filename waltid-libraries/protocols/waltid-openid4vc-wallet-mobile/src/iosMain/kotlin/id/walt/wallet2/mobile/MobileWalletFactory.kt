package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
public actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    public actual fun create(config: MobileWalletConfig): MobileWallet {
        val driver = DriverFactory().createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val keyProvider = IosPlatformKeyProvider()
        return createMobileWallet(config, db, keyProvider)
    }
}
