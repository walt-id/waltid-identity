package id.walt.wallet2

import id.walt.commons.config.WaltConfig
import id.walt.crypto.keys.DirectSerializedKey
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Configuration for the optional auth feature.
 *
 * [signingKey] is a waltid-crypto key in the serialized JSON format produced by
 * [id.walt.crypto.keys.KeySerialization.serializeKey]. It deserializes directly to
 * a live [id.walt.crypto.keys.Key] via [DirectSerializedKey], so the config is
 * type-safe and no manual JSON parsing is required at startup.
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
 *   signingKey = { type = "jwk", jwk = { kty = "EC", crv = "P-256", x = "...", y = "...", d = "..." } }
 *   tokenExpiry = "PT24H"
 * }
 * ```
 */
@Serializable
data class OSSWallet2AuthConfig(
    /**
     * Waltid-crypto signing key for JWT session tokens.
     * Serialized form: output of [id.walt.crypto.keys.KeySerialization.serializeKey].
     * Must be identical on every replica.
     */
    val signingKey: DirectSerializedKey,
    /** JWT token lifetime. Accepts ISO-8601 duration strings, e.g. "PT24H", "P7D". */
    val tokenExpiry: Duration = 24.hours,
) : WaltConfig()
