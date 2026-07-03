package id.walt.wallet2.mobile

import android.content.Context
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.coroutines.runBlocking

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
     * through the Android platform key provider.
     */
    actual fun create(config: MobileWalletConfig): MobileWallet {
        return createMobileWallet(config) {
            val databaseName = "wallet_${config.walletId}"
            val persistence = config.persistence
            val encryptionKey = runBlocking {
                when (persistence) {
                    is MobileWalletPersistenceConfig.SdkManagedEncrypted ->
                        AndroidDatabaseEncryptionKeyProvider(context).getOrCreateKey(config.walletId, databaseName)

                    is MobileWalletPersistenceConfig.IntegratorManagedKey ->
                        persistence.keyProvider.getOrCreateKey(config.walletId, databaseName)

                    is MobileWalletPersistenceConfig.CustomStores ->
                        error("Custom store wallets do not use Android SQLDelight persistence")
                }
            }
            val driver = DriverFactory(context).createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = encryptionKey,
                useNoBackupDirectory = persistence is MobileWalletPersistenceConfig.SdkManagedEncrypted &&
                    persistence.backupPolicy == BackupPolicy.DeviceLocalOnly,
            )
            val db = WalletPersistenceDatabase(driver)
            val keyProvider = AndroidPlatformKeyProvider()
            createMobileWallet(config, db, keyProvider)
        }
    }
}
