package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable

/**
 * Request to create one wallet signing key.
 *
 * @property id Stable identifier assigned to the generated key.
 * @property requirements Capabilities the generated key must satisfy.
 * @property prompt Text used by OS-owned authorization UI.
 */
public data class WalletKeyCreationRequest(
    public val id: KeyId,
    public val requirements: WalletKeyRequirements,
    public val prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    /** Native alias; identity recovery uses a fresh alias while preserving the logical key ID. */
    public val nativeAlias: String = id.value,
)

/**
 * Capabilities required from a wallet signing key.
 *
 * @property spec Cryptographic key specification.
 * @property usages Operations the key must support.
 * @property authorizationPolicy Authorization required for private-key use.
 * @property protection Required native protection, checked against observed key facts.
 * @property platform Advanced settings for the current platform backend.
 */
@Serializable
public data class WalletKeyRequirements(
    public val spec: KeySpec,
    public val usages: Set<KeyUsage>,
    public val authorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    public val protection: WalletKeyProtection = WalletKeyProtection.PlatformDefault,
    public val platform: PlatformKeyConfiguration = PlatformKeyConfiguration.Default,
    /** Fresh native generation challenge; import cannot satisfy native key-generation attestation. */
    public val attestationChallenge: BinaryData? = null,
) {
    init {
        require(usages.isNotEmpty()) { "Wallet key usages cannot be empty" }
    }
}

/** Signing-key protection is independent of the authorization needed to use the key. */
@Serializable
public enum class WalletKeyProtection {
    /** Preserves the historical native policy, including hardware required by biometric defaults. */
    PlatformDefault,
    /** Creation fails unless the native backend observes secure hardware. */
    HardwareRequired,
    /** Hardware is preferred; actual protection is reported separately. */
    HardwarePreferred,
    /** Native storage without a hardware requirement; iOS uses ordinary Keychain. */
    NativeStorage,
}
