package id.walt.wallet2.mobile

import android.content.Context
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

actual class MobileWalletFactory(private val context: Context) {
    actual fun create(config: MobileWalletConfig): MobileWallet {
        val driver = DriverFactory(context).createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val keyProvider = AndroidPlatformKeyProvider()
        return createMobileWallet(config, db, keyProvider)
    }
}
