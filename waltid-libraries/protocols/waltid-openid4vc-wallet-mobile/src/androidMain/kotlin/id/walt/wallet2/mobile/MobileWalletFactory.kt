@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import android.content.Context
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.serialization.ExperimentalSerializationApi

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
        val driverFactory = DriverFactory(context)
        val platformConfig = if (config.credentialRegistry === UnavailableMobileWalletCredentialRegistry) {
            config.copy(credentialRegistry = AndroidDigitalCredentialRegistry(context))
        } else config
        return createEncryptedSqlDelightMobileWallet(
            config = platformConfig,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(context),
            platformKeyProvider = AndroidPlatformKeyProvider(),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
