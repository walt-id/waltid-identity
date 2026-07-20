package id.walt.wallet2.data

import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.serialization.Serializable

/** Lightweight metadata about a key; does not expose private key material. */
@Serializable
data class WalletKeyInfo(
    val keyId: String,
    val keyType: String,
    val algorithm: String? = null,
    val requestedKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    val effectiveKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    val isPlatformBacked: Boolean = false,
    val effectiveHardwareBacking: KeyHardwareBacking? = null,
)
