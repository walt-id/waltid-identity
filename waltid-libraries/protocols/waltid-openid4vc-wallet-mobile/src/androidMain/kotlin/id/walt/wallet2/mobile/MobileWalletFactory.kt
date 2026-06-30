package id.walt.wallet2.mobile

import android.content.Context
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

actual class MobileWalletFactory(private val context: Context) {
    actual fun create(config: MobileWalletConfig): MobileWallet {
        val driver = DriverFactory(context).createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries

        val keyProvider = AndroidPlatformKeyProvider()
        val keyStore = PlatformKeyStore(keyProvider, queries)
        val credentialStore = SqlDelightCredentialStore(queries)
        val didStore = SqlDelightDidStore(queries)

        return MobileWallet(
            walletId = config.walletId,
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            keyGenerator = { keyType -> keyProvider.generateKey(keyType) },
            defaultKeyType = config.defaultKeyType,
            attestationConfig = config.attestationConfig,
            onEvent = config.onEvent,
        )
    }
}
