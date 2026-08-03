package id.walt.wallet2.mobile

import android.content.Context
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory

/**
 * Android [MobileWallet] factory backed by Android KeyStore and an app-private SQLDelight database.
 *
 * The single-context constructor is for non-interactive (`None`) key use. Protected signing requires
 * the provider-based constructor so the current [FragmentActivity] can be resolved after recreation.
 */
public actual class MobileWalletFactory private constructor(
    private val applicationContext: Context,
    private val interactionContextProvider: () -> FragmentActivity?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    /** Creates a context-only factory. Protected signing requires the provider-based constructor. */
    public constructor(context: Context) : this(
        applicationContext = context.applicationContext,
        interactionContextProvider = { null },
        marker = Unit,
    )

    /** Creates a factory that resolves the current activity after configuration changes. */
    public constructor(
        context: Context,
        interactionContextProvider: () -> FragmentActivity?,
    ) : this(
        applicationContext = context.applicationContext,
        interactionContextProvider = interactionContextProvider,
        marker = Unit,
    )

    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId], and signing keys are created or loaded
     * through the Android platform key provider.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet {
        val driverFactory = DriverFactory(applicationContext)
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(applicationContext),
            platformKeyProvider = AndroidPlatformKeyProvider(
                context = applicationContext,
                interactionContextProvider = interactionContextProvider,
                authorizationPrompt = config.keyUseAuthorizationPrompt,
            ),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
        )
    }
}
