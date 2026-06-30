package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import kotlin.uuid.Uuid

/**
 * Configuration for creating a [MobileWallet].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by [MobileWallet.bootstrap] when no key type override is supplied.
 * @property attestationConfig Optional client-attestation configuration for issuer deployments that require it.
 * @property onEvent Optional callback for observing wallet issuance and presentation session events.
 */
data class MobileWalletConfig(
    val walletId: String = Uuid.random().toString(),
    val defaultKeyType: KeyType = KeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val onEvent: suspend (WalletSessionEvent) -> Unit = {},
)

/**
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults create a new wallet identifier and P-256 key material.
     */
    fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}
