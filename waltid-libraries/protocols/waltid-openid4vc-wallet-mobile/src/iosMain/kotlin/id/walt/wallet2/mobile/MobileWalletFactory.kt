@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.mobile.IosBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.IosWifiAwareProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentRegistry
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletRegistryProjection
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave managed keys, the Crypto2 software-key fallback,
 * and a native SQLDelight database.
 */
public actual class MobileWalletFactory(
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
) {
    /**
     * Creates an iOS mobile wallet using native SQLDelight storage and the default iOS platform key provider.
     */
    public actual suspend fun create(config: MobileWalletConfig): MobileWallet {
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
        return createEncryptedSqlDelightMobileWallet(
            config = config,
            databaseLocation = "${sharedAccess?.appGroupIdentifier.orEmpty()}/wallet_${config.walletId}.db",
            keyNamespace = "${sharedAccess?.keychainAccessGroup.orEmpty()}/${config.walletId}",
            registry = registry,
            registrationProjection = if (registry is IosIdentityDocumentRegistry) {
                MobileWalletRegistryProjection.MdocIdentity
            } else MobileWalletRegistryProjection.Full,
            managedDatabaseKeyProvider = IosDatabaseEncryptionKeyProvider(sharedAccess?.keychainAccessGroup),
            // Signum's IosKeychainProvider does not expose kSecAttrAccessGroup, so signing keys land
            // in the app's default access group — the first `keychain-access-groups` entitlement entry.
            // Cross-process sharing is configured there, not here; see MobileWalletCrossProcessAccess.
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
