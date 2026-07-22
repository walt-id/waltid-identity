package id.walt.policies2.vc.policies.status.signature

import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

suspend fun authorizeStatusListSigner(
    request: StatusListSignerAuthorizationRequest,
    authorizer: StatusListSignerAuthorizer?,
) {
    val referencedIssuer = request.referencedCredential.issuer
        ?: request.referencedCredential.credentialData["iss"]?.jsonPrimitive?.contentOrNull
        ?: request.referencedCredential.credentialData["issuer"]?.let { issuer ->
            when (issuer) {
                is JsonObject -> issuer["id"]?.jsonPrimitive?.contentOrNull
                else -> (issuer as? JsonPrimitive)?.contentOrNull
            }
        }
    val authorized = authorizer?.authorize(request) ?: when (request.signer.source) {
        JwtKeyResolutionSource.DID,
        JwtKeyResolutionSource.WELL_KNOWN,
        -> request.signer.signerIdentifier == referencedIssuer

        JwtKeyResolutionSource.X5C,
        JwtKeyResolutionSource.INLINE_JWK,
        -> false
    }
    require(authorized) {
        "Status-list signer is not authorized for referenced credential issuer $referencedIssuer"
    }
}
