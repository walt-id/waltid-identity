@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
public actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet =
        create(config, ClientIdTrustConfiguration())

    public actual suspend fun create(
        config: MobileWalletConfig,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
    ): MobileWallet = createWallet(config, clientIdTrustConfiguration)

    private suspend fun createWallet(
        config: MobileWalletConfig,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
    ): MobileWallet {
        val driverFactory = DriverFactory()
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            managedDatabaseKeyProvider = IosDatabaseEncryptionKeyProvider(),
            platformKeyProvider = IosPlatformKeyProvider(),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
