@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import android.content.Context
import androidx.fragment.app.FragmentActivity
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
public actual class MobileWalletFactory(
    private val context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) {
    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId]. The factory wires the Android managed-key
     * provider together with the Crypto2 software-key fallback used for supported unprotected requests.
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
        val applicationContext = context.applicationContext
        val driverFactory = DriverFactory(applicationContext)
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(applicationContext),
            platformKeyProvider = AndroidPlatformKeyProvider(context, interactionContextProvider),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
