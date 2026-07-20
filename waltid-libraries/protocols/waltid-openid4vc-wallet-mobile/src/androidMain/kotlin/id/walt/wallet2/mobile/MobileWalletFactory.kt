package id.walt.wallet2.mobile

import android.content.Context
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

/**
 * Android [MobileWallet] factory backed by Android KeyStore and an app-private SQLDelight database.
 *
 * @param context Android context used to open the wallet database. Protected key use requires this
 * context to be an interactive [androidx.fragment.app.FragmentActivity].
 */
public actual class MobileWalletFactory(private val context: Context) {
    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId], and signing keys are created or loaded
     * through the Android platform key provider.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet {
        val driverFactory = DriverFactory(context)
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(context),
            platformKeyProvider = AndroidPlatformKeyProvider(
                context = context,
                authorizationPrompt = config.keyUseAuthorizationPrompt,
            ),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
