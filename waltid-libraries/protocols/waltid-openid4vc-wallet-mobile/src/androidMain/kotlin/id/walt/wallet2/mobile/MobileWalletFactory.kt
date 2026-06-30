package id.walt.wallet2.mobile

import android.content.Context
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.persistence.stores.HardwareKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

/**
 * Android [MobileWallet] factory backed by Android KeyStore and an app-private SQLDelight database.
 *
 * @param context Android context used to open the wallet database.
 */
actual class MobileWalletFactory(private val context: Context) {
    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId], and signing keys are created or loaded
     * through Android KeyStore.
     */
    actual fun create(config: MobileWalletConfig): MobileWallet {
        val driver = DriverFactory(context).createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries

        val keyProvider = AndroidPlatformKeyProvider()
        val keyStore = HardwareKeyStore(keyProvider, queries)
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
