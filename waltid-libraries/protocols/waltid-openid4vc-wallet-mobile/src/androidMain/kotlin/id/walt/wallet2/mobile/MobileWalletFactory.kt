@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import android.content.ComponentName
import android.content.Context
import androidx.fragment.app.FragmentActivity
import id.walt.mdoc.proximity.mobile.AndroidBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.AndroidMdocHostApduService
import id.walt.mdoc.proximity.mobile.AndroidNfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.AndroidWifiAwareProximityTransportFactory
import id.walt.wallet2.mobile.digitalcredentials.AndroidDigitalCredentialRegistry
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * App-owned Android HCE service used for mdoc NFC engagement and retrieval.
 *
 * @property serviceClass Dedicated manifest-declared service subclass routing the three mdoc AIDs.
 */
public data class AndroidMobileWalletNfcConfiguration(
    public val serviceClass: Class<out AndroidMdocHostApduService>,
)

/**
 * Android [MobileWallet] factory backed by Android KeyStore and an app-private SQLDelight database.
 *
 * @param context Android context used to open the wallet database.
 */
public actual class MobileWalletFactory(
    private val context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
    private val nfcConfiguration: AndroidMobileWalletNfcConfiguration? = null,
) {
    /**
     * Creates an Android mobile wallet for [config].
     *
     * The database is named from [MobileWalletConfig.walletId]. The factory wires the Android managed-key
     * provider together with the Crypto2 software-key fallback used for supported unprotected requests.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet {
        val applicationContext = context.applicationContext
        val driverFactory = DriverFactory(applicationContext)
        val nfcHostAdapter = nfcConfiguration?.let { nfc ->
            val service = ComponentName(applicationContext, nfc.serviceClass)
            AndroidNfcHostPlatformAdapter(applicationContext, service) {
                interactionContextProvider()
            }
        }
        val registry = config.digitalCredentials.resolveRegistry {
            AndroidDigitalCredentialRegistry(applicationContext)
        }
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            databaseLocation = applicationContext.getDatabasePath("wallet_${config.walletId}.db").canonicalPath,
            keyNamespace = "${applicationContext.packageName}/${config.walletId}",
            registry = registry,
            managedDatabaseKeyProvider = AndroidDatabaseEncryptionKeyProvider(applicationContext),
            platformKeyProvider = AndroidPlatformKeyProvider(applicationContext, interactionContextProvider),
            proximityTransportFactory = AndroidBleProximityTransportFactory(applicationContext),
            proximityNfcHostPlatformAdapter = nfcHostAdapter,
            proximityWifiAwareTransportFactory = AndroidWifiAwareProximityTransportFactory(applicationContext),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
            maintenanceLocation = driverFactory::existingLocation,
        )
    }
}
