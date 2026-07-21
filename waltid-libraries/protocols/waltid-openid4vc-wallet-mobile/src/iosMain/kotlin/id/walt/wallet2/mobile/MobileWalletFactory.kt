package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
public actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet {
        val sharedAccess = config.crossProcessAccess
        val driverFactory = DriverFactory().apply {
            sharedAccess?.let { useAppGroup(it.appGroupIdentifier) }
        }
        val platformConfig = if (config.credentialRegistry === UnavailableMobileWalletCredentialRegistry) {
            config.copy(
                credentialRegistry = IosIdentityDocumentRegistry(sharedAccess?.appGroupIdentifier),
            )
        } else config
        return createEncryptedSqlDelightMobileWallet(
            config = platformConfig,
            managedDatabaseKeyProvider = IosDatabaseEncryptionKeyProvider(sharedAccess?.keychainAccessGroup),
            platformKeyProvider = IosPlatformKeyProvider(accessGroup = sharedAccess?.keychainAccessGroup),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
