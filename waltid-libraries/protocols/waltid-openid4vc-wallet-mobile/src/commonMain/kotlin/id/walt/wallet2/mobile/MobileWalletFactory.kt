package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

/**
 * Configuration for creating a [MobileWallet].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by [MobileWallet.bootstrap] when no key type override is supplied.
 * @property attestationConfig Optional client-attestation configuration for issuer deployments that require it.
 * @property onEvent Optional callback for observing wallet issuance and presentation session events.
 */
public data class MobileWalletConfig(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val onEvent: suspend (MobileWalletEvent) -> Unit = {},
)

/**
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
public expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults create a new wallet identifier and P-256 key material.
     */
    public fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}

internal fun createMobileWallet(
    config: MobileWalletConfig,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformKeyProvider,
): MobileWallet {
    val queries = db.walletPersistenceQueries
    val keyStore = PlatformKeyStore(keyProvider, queries)
    val credentialStore = SqlDelightCredentialStore(queries)
    val didStore = SqlDelightDidStore(queries)

    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        keyGenerator = { keyType -> keyProvider.generateKey(keyType) },
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        onEvent = config.onEvent,
    )
}
