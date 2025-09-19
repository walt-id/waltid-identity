@file:OptIn(ExperimentalTime::class)

package id.walt.idp.oidc

import id.walt.idp.oidc.json.InstantAsEpochSecondsSerializer
import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * OAuth2 access tokens don't have to be JWTs, but Keycloak might expect it
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CustomAccessToken(
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

    val scope: String = "openid",

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    /** Optional: nonce value used to associate a client session with the ID Token */
    val nonce: String? = null,
)
