package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.serialization.Serializable

/**
 * Explicit request for creating one mobile wallet signing key.
 *
 * @property keyType Type of signing key to create.
 * @property keyId Optional platform key identifier. The provider assigns one when omitted.
 * @property keyUseAuthorizationPolicy Immutable authorization policy enforced by the created key.
 */
public data class PlatformKeyRequest(
    public val keyType: KeyType,
    public val keyId: String? = null,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
)

/** Result of checking whether a provider can enforce an exact request. */
public data class PlatformKeyPreflight(
    public val supported: Boolean,
    public val failure: KeyUseAuthorizationFailure? = null,
)

/** The authoritative result of a successful platform-key generation. */
public data class GeneratedPlatformKey(
    public val key: id.walt.crypto.keys.Key,
    public val record: id.walt.wallet2.persistence.stores.MobileWalletKeyRecord,
)
