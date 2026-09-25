package id.walt.policies2.vc.policies.status.signature

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

suspend fun authorizeStatusListSigner(
    request: StatusListSignerAuthorizationRequest,
    authorizer: StatusListSignerAuthorizer?,
) {
    val referencedIssuer = referencedCredentialIssuer(request.referencedCredential)
    val authorized = authorizer?.authorize(request) ?: isDirectTrustAuthorized(request, referencedIssuer)
    require(authorized) {
        statusListSignerUnauthorizedMessage(
            request = request,
            referencedIssuer = referencedIssuer,
            customAuthorizer = authorizer != null,
        )
    }
}

internal fun referencedCredentialIssuer(credential: DigitalCredential): String? =
    credential.issuer
        ?: credential.credentialData["iss"]?.jsonPrimitive?.contentOrNull
        ?: credential.credentialData["issuer"]?.let { issuer ->
            when (issuer) {
                is JsonObject -> issuer["id"]?.jsonPrimitive?.contentOrNull
                else -> (issuer as? JsonPrimitive)?.contentOrNull
            }
        }

internal fun isDirectTrustAuthorized(
    request: StatusListSignerAuthorizationRequest,
    referencedIssuer: String?,
): Boolean = when (request.signer.source) {
    JwtKeyResolutionSource.DID,
    JwtKeyResolutionSource.WELL_KNOWN,
        -> request.signer.signerIdentifier == referencedIssuer

    JwtKeyResolutionSource.X5C,
    JwtKeyResolutionSource.INLINE_JWK,
        -> false
}

internal fun statusListSignerUnauthorizedMessage(
    request: StatusListSignerAuthorizationRequest,
    referencedIssuer: String?,
    customAuthorizer: Boolean,
): String {
    val signerLabel = request.signer.signerIdentifier ?: "none"
    val issuerLabel = referencedIssuer ?: "none (no iss/issuer claim)"
    val details =
        "Status-list signer source=${request.signer.source}, signer=$signerLabel, " +
            "credential issuer=$issuerLabel, status-list URI=${request.statusListUri}."
    val reason = if (customAuthorizer) {
        "the configured status-list signer authorizer rejected this signer"
    } else when (request.signer.source) {
        JwtKeyResolutionSource.X5C ->
            "x5c-signed status lists are not accepted under direct trust"

        JwtKeyResolutionSource.INLINE_JWK ->
            "an inline JWK does not establish a trusted status-list signer identity"

        JwtKeyResolutionSource.DID,
        JwtKeyResolutionSource.WELL_KNOWN,
            -> if (referencedIssuer == null) {
            "the referenced credential has no issuer claim, so direct trust cannot match the status-list signer"
        } else {
            "status-list signer '$signerLabel' does not match credential issuer '$issuerLabel'"
        }
    }
    val hint = if (customAuthorizer) {
        ""
    } else {
        " Direct trust requires the status-list signer to be the same DID or https issuer as the credential; " +
            "a separate Status Provider is not accepted unless a status-list signer authorizer is configured."
    }
    return "Status-list signer is not authorized: $reason. $details$hint"
}
