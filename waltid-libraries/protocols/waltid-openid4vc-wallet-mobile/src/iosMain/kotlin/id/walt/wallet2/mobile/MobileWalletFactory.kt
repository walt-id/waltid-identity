@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.mdoc.proximity.mobile.IosBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.IosWifiAwareProximityTransportFactory
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
        val sharedAccess = config.crossProcessAccess
        val driverFactory = DriverFactory().apply {
            sharedAccess?.let { useAppGroup(it.appGroupIdentifier) }
        }
        val platformConfig = if (config.credentialRegistry === UnavailableMobileWalletCredentialRegistry) {
            config.copy(
                // The wallet id goes into the projection so the provider extension opens this
                // wallet's `wallet_${walletId}` database rather than assuming the default one.
                credentialRegistry = IosIdentityDocumentRegistry(
                    appGroupIdentifier = sharedAccess?.appGroupIdentifier,
                    walletId = config.walletId,
                ),
            )
        } else config
        return createEncryptedSqlDelightMobileWallet(
            config = platformConfig,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
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
        )
    }
}
