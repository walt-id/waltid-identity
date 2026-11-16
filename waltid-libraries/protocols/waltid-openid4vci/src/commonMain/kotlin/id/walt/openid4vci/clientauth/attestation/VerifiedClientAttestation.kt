package id.walt.openid4vci.clientauth.attestation

import id.walt.openid4vci.attestation.VerifiedAttestationStatus

import id.walt.openid4vci.Session
import id.walt.openid4vci.attestation.VerifiedAttestationLifetime
import id.walt.openid4vci.clientauth.AuthenticatedClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class VerifiedClientAttestation(
    val subject: String,
    val confirmationJwk: JsonObject,
    val lifetime: VerifiedAttestationLifetime,
    val status: VerifiedAttestationStatus? = null,
)

@Serializable
internal data class AccessTokenClientAuthentication(
    val method: String? = null,
    val anonymousPreAuthorizedAccess: Boolean = false,
)

private const val CLIENT_ATTESTATION_CLAIM = "walt_client_attestation"
private const val CLIENT_AUTHENTICATION_CLAIM = "walt_client_authentication"

/** Bind at the original grant. Refresh preserves its authentication context and WIA evidence. */
internal fun Session.withClientAuthentication(
    client: AuthenticatedClient?,
    anonymousPreAuthorizedAccess: Boolean = false,
): Session {
    if (client == null && !anonymousPreAuthorizedAccess) return this
    val bound = withCustomAttribute(CLIENT_AUTHENTICATION_CLAIM, Json.encodeToString(
        AccessTokenClientAuthentication(client?.authenticationMethod, anonymousPreAuthorizedAccess),
    ))
    return client?.attestation?.let {
        bound.withCustomAttribute(CLIENT_ATTESTATION_CLAIM, Json.encodeToString(it))
    } ?: bound
}

internal fun Session.clientAuthenticationClaims(): Map<String, JsonElement> = buildMap {
    for (name in listOf(CLIENT_ATTESTATION_CLAIM, CLIENT_AUTHENTICATION_CLAIM)) {
        customAttributes[name]?.let { put(name, Json.parseToJsonElement(it)) }
    }
}

/** Decode only claims returned by access-token verification. */
internal fun JsonObject.verifiedClientAttestation(): VerifiedClientAttestation? =
    this[CLIENT_ATTESTATION_CLAIM]?.let { Json.decodeFromJsonElement<VerifiedClientAttestation>(it) }

internal fun JsonObject.verifiedClientAuthentication(): AccessTokenClientAuthentication? =
    this[CLIENT_AUTHENTICATION_CLAIM]?.let { Json.decodeFromJsonElement<AccessTokenClientAuthentication>(it) }
