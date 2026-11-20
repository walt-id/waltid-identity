package id.walt.wallet2

import id.walt.commons.config.WaltConfig
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Configuration for the optional auth feature.
 *
 * Exactly one of the two key representations is required, and [signingStoredKey] is the preferred one:
 *
 * - [signingStoredKey] alone is a complete, Crypto2-only configuration. It is an encoded
 *   [id.walt.crypto2.keys.StoredKey] and may describe either a software key or a managed
 *   (KMS/HSM) key. A managed key requires a [id.walt.crypto2.CryptoRuntime] holding the matching
 *   [id.walt.crypto2.providers.ManagedKeyProvider] to be passed to
 *   [id.walt.wallet2.auth.configureWallet2Auth]; the default runtime only carries software providers.
 *   The JWS algorithm is then derived from the StoredKey itself.
 * - [signingKey] alone is the legacy path: a waltid-crypto key in the serialized JSON format
 *   produced by [id.walt.crypto.keys.KeySerialization.serializeKey], which deserializes directly to
 *   a live [id.walt.crypto.keys.Key] via [DirectSerializedKey]. It is migrated to Crypto2 in memory
 *   at startup. It cannot represent a managed key.
 * - Both together are the migration configuration: they must describe the same key, and startup fails
 *   on a mismatch instead of falling back. Startup never rewrites `auth.conf`.
 *
 * The same key must be deployed to every replica so that JWT tokens issued by one
 * instance are accepted by all others (HA-safe).
 *
 * [tokenExpiry] accepts any ISO-8601 duration string, e.g. "PT24H", "PT30M", "P7D".
 *
 * Recommended key type: secp256r1 (ES256) or Ed25519 (EdDSA). Generate once, then
 * embed in your auth.conf:
 * ```hocon
 * auth {
 *   signingStoredKey = "{...encoded StoredKey...}"
 *   tokenExpiry = "PT24H"
 * }
 * ```
 */
@Serializable
data class OSSWallet2AuthConfig(
    /**
     * Legacy waltid-crypto signing key for JWT session tokens.
     * Serialized form: output of [id.walt.crypto.keys.KeySerialization.serializeKey].
     * Optional - omit it when [signingStoredKey] is set. Must be identical on every replica.
     */
    val signingKey: DirectSerializedKey? = null,
    /** JWT token lifetime. Accepts ISO-8601 duration strings, e.g. "PT24H", "P7D". */
    val tokenExpiry: Duration = 24.hours,
    /** Encoded Crypto2 StoredKey. Preferred, and sufficient on its own. */
    val signingStoredKey: String? = null,
    /** Optional OIDC authentication configuration. When absent, OIDC authentication is disabled. */
    val oidc: OidcAuthConfiguration? = null,
) : WaltConfig() {
    init {
        require(signingKey != null || signingStoredKey != null) {
            "Wallet auth requires signingStoredKey (preferred) or the legacy signingKey"
        }
    }

    /** Preserves the JVM constructor descriptor from before the StoredKey field was added. */
    constructor(signingKey: DirectSerializedKey, tokenExpiry: Duration) : this(signingKey, tokenExpiry, null, null)
}
