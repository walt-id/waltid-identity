package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    actual suspend fun create(config: MobileWalletConfig): MobileWallet {
        val driverFactory = DriverFactory()
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            managedDatabaseKeyProvider = IosDatabaseEncryptionKeyProvider(),
            platformKeyProvider = IosPlatformKeyProvider(),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
