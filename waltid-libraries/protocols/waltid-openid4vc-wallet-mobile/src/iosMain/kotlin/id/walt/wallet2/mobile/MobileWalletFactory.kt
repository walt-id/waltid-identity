package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

actual class MobileWalletFactory {
    actual fun create(config: MobileWalletConfig): MobileWallet {
        val driver = DriverFactory().createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val keyProvider = IosPlatformKeyProvider()
        return createMobileWallet(config, db, keyProvider)
    }
}
