package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.coroutines.runBlocking

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
public actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    public actual fun create(config: MobileWalletConfig): MobileWallet {
        return createMobileWallet(config) {
            val databaseName = "wallet_${config.walletId}"
            val persistence = config.persistence
            val databaseKeyProvider = when (persistence) {
                is MobileWalletPersistenceConfig.SdkManagedEncrypted ->
                    IosDatabaseEncryptionKeyProvider()

                is MobileWalletPersistenceConfig.IntegratorManagedKey ->
                    persistence.keyProvider

                is MobileWalletPersistenceConfig.CustomStores ->
                    error("Custom store wallets do not use iOS SQLDelight persistence")
            }
            val encryptionKey = runBlocking {
                databaseKeyProvider.getOrCreateKey(config.walletId, databaseName)
            }
            val driverFactory = DriverFactory()
            val driver = driverFactory.createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = encryptionKey,
                walletId = config.walletId,
            )
            val db = WalletPersistenceDatabase(driver)
            val keyProvider = IosPlatformKeyProvider()
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
