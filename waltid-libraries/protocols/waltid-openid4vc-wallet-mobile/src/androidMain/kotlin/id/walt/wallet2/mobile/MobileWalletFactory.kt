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
public actual class MobileWalletFactory(private val context: Context) {
    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId], and signing keys are created or loaded
     * through the Android platform key provider.
     */
    public actual fun create(config: MobileWalletConfig): MobileWallet {
        return createMobileWallet(config) {
            val databaseName = "wallet_${config.walletId}"
            val persistence = config.persistence
            val databaseKeyProvider = when (persistence) {
                is MobileWalletPersistenceConfig.SdkManagedEncrypted ->
                    AndroidDatabaseEncryptionKeyProvider(context)

                is MobileWalletPersistenceConfig.IntegratorManagedKey ->
                    persistence.keyProvider

                is MobileWalletPersistenceConfig.CustomStores ->
                    error("Custom store wallets do not use Android SQLDelight persistence")
            }
            val encryptionKey = runBlocking {
                databaseKeyProvider.getOrCreateKey(config.walletId, databaseName)
            }
            val driverFactory = DriverFactory(context)
            val driver = driverFactory.createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = encryptionKey,
                useNoBackupDirectory = persistence is MobileWalletPersistenceConfig.SdkManagedEncrypted &&
                    persistence.backupPolicy == BackupPolicy.DeviceLocalOnly,
            )
            val db = WalletPersistenceDatabase(driver)
            val keyProvider = AndroidPlatformKeyProvider()
            createMobileWallet(
                config = config,
                db = db,
                keyProvider = keyProvider,
                deleteLocalPersistence = {
                    runCatching { driver.close() }
                    driverFactory.deleteDatabase(databaseName)
                    databaseKeyProvider.deleteKey(config.walletId, databaseName)
                },
            )
        }
    }
}
