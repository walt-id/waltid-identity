@file:OptIn(ExperimentalTime::class)

package id.walt.idp.oidc

import id.walt.idp.oidc.json.InstantAsEpochSecondsSerializer
import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalSerializationApi::class)
@Suppress("PropertyName")
@Serializable
data class IdToken(
    /** Issuer Identifier for the OP (URL) */
    val iss: String,

    /** Subject Identifier (Unique identifier for the user) */
    val sub: String,

    /** Audience(s) that this ID Token is intended for */
    val aud: List<String>,

    /** Expiration time on or after which the ID Token must not be accepted */
    @Serializable(with = InstantAsEpochSecondsSerializer::class)
    val exp: Instant,

    /** Time at which the JWT was issued */
    @Serializable(with = InstantAsEpochSecondsSerializer::class)
    val iat: Instant,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = InstantAsEpochSecondsSerializer::class)
    /** Time when the End-User authentication occurred */
    val auth_time: Instant? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: nonce value used to associate a client session with the ID Token */
    val nonce: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: Authentication Context Class Reference */
    val acr: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: Authentication Methods References */
    val amr: List<String>? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: Authorized party - the party to which the ID Token was issued */
    val azp: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: Access Token hash value */
    val at_hash: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: Code hash value */
    val c_hash: String? = null,
)
