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
    ): MobileWallet = createWallet(config, clientIdTrustConfiguration, null)

    public actual suspend fun create(
        config: MobileWalletConfig,
        crypto2Config: MobileWalletCrypto2Config,
    ): MobileWallet = createWallet(config, ClientIdTrustConfiguration(), crypto2Config)

    public actual suspend fun create(
        config: MobileWalletConfig,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        crypto2Config: MobileWalletCrypto2Config,
    ): MobileWallet = createWallet(config, clientIdTrustConfiguration, crypto2Config)

    private suspend fun createWallet(
        config: MobileWalletConfig,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        crypto2Config: MobileWalletCrypto2Config?,
    ): MobileWallet {
        val driverFactory = DriverFactory(context)
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            crypto2Config = crypto2Config,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(context),
            platformKeyProvider = AndroidPlatformKeyProvider(),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
