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

/**
 * Explicit request for creating one mobile wallet signing key.
 *
 * @property keyType Type of signing key to create.
 * @property keyId Optional platform key identifier. The provider assigns one when omitted.
 * @property keyUseAuthorizationPolicy Immutable authorization policy enforced by the created key.
 */
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
 *
 * @property platform Mobile platform that evaluated the request.
 * @property keyType Requested signing-key type.
 * @property keyUseAuthorizationPolicy Requested immutable key-use authorization policy.
 * @property supported Whether the provider can enforce this exact request without fallback.
 * @property platformBackingAvailable Whether platform-backed key storage is available.
 * @property secureHardwareRequired Whether this request requires secure hardware backing.
 * @property secureHardwareAvailable Whether required secure hardware availability is known on this device.
 * @property effectiveHardwareBacking Effective backing when it can be determined from platform key information.
 * @property failure Stable reason the request is unsupported, or `null` when supported.
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
