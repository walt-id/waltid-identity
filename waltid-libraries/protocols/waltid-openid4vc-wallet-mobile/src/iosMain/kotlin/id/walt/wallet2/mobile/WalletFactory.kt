@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.mobile.IosBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.IosWifiAwareProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentRegistry
import id.walt.wallet2.mobile.digitalcredentials.WalletAppGroupAccess
import id.walt.wallet2.mobile.digitalcredentials.RegistryProjection
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * iOS [Wallet] factory backed by Keychain/Secure Enclave managed keys, the Crypto2 software-key fallback,
 * and a native SQLDelight database.
 */
public actual class WalletFactory(
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
) {
    /**
     * Opens the wallet with native SQLDelight storage and the default iOS key provider.
     */
    public actual suspend fun open(config: WalletConfiguration): Wallet {
        val sharedAccess = config.digitalCredentials.crossProcessAccess
        val driverFactory = DriverFactory().apply {
            sharedAccess?.let { useAppGroup(it.appGroupIdentifier) }
        }
        val registry = config.digitalCredentials.resolveRegistry {
            IosIdentityDocumentRegistry(
                appGroupIdentifier = sharedAccess?.appGroupIdentifier,
                walletId = config.walletId,
            )
        }
        return openEncryptedSqlDelightWallet(
            config = config,
            databaseLocation = "${sharedAccess?.appGroupIdentifier.orEmpty()}/wallet_${config.walletId}.db",
            keyNamespace = "${sharedAccess?.keychainAccessGroup.orEmpty()}/${config.walletId}",
            registry = registry,
            registrationProjection = if (registry is IosIdentityDocumentRegistry) {
                RegistryProjection.MdocIdentity
            } else RegistryProjection.Full,
            managedDatabaseKeyProvider = IosDatabaseEncryptionKeyProvider(sharedAccess?.keychainAccessGroup),
            // Signum's IosKeychainProvider does not expose kSecAttrAccessGroup, so signing keys land
            // in the app's default access group — the first `keychain-access-groups` entitlement entry.
            // Cross-process sharing is configured there, not here; see WalletAppGroupAccess.
            platformKeyProvider = IosPlatformKeyProvider(),
            proximityTransportFactory = IosBleProximityTransportFactory(),
            proximityNfcHostPlatformAdapter = nfcHostPlatformAdapter,
            proximityWifiAwareTransportFactory = IosWifiAwareProximityTransportFactory(),
            openEncryptedDriver = driverFactory::createEncryptedDriver,
            deleteDatabase = driverFactory::deleteDatabase,
            maintenanceLocation = { driverFactory.existingLocation() },
        )
    }
}
