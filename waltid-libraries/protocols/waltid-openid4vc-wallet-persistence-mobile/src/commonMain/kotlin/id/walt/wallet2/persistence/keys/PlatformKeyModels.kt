package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.serialization.Serializable

/** Mobile platform that evaluates a platform-key capability request. */
@Serializable
public enum class PlatformKeyPlatform {
    Android,
    iOS,
    Custom,
}

/** Explicit request for creating one mobile wallet signing key. */
public data class PlatformKeyGenerationRequest(
    public val keyType: KeyType,
    public val keyId: String? = null,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
)

/**
 * Preflight result for a key type and immutable key-use authorization policy.
 *
 * [effectiveHardwareBacking] is populated only when reliable platform key information is available.
 * In particular, an Android preference for StrongBox is never reported as effective StrongBox backing.
 */
public data class PlatformKeyCapability(
    public val platform: PlatformKeyPlatform,
    public val keyType: KeyType,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    public val supported: Boolean,
    public val platformBackingAvailable: Boolean,
    public val secureHardwareRequired: Boolean,
    public val secureHardwareAvailable: Boolean?,
    public val effectiveHardwareBacking: KeyHardwareBacking? = null,
    public val failure: KeyUseAuthorizationFailure? = null,
)
