package id.walt.policies2.vc.policies.status.signature

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

suspend fun authorizeStatusListSigner(
    request: StatusListSignerAuthorizationRequest,
    authorizer: StatusListSignerAuthorizer?,
) {
    val referencedIssuer = referencedCredentialIssuer(request.referencedCredential)
    val directTrust = isDirectTrustAuthorized(request, referencedIssuer)
    val customAuthorized = authorizer?.authorize(request) == true
    require(directTrust || customAuthorized) {
        statusListSignerUnauthorizedMessage(
            request = request,
            referencedIssuer = referencedIssuer,
            customAuthorizerConfigured = authorizer != null,
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

internal suspend fun isDirectTrustAuthorized(
    request: StatusListSignerAuthorizationRequest,
    referencedIssuer: String?,
): Boolean {
    if (request.signer.source == JwtKeyResolutionSource.INLINE_JWK) return false
    if (request.signer.signerIdentifier != null && request.signer.signerIdentifier == referencedIssuer) return true
    return statusListSignerMatchesCredentialCertificate(request)
}

internal suspend fun referencedCredentialCertificateChain(credential: DigitalCredential): List<String> {
    when (val signature = credential.signature) {
        is JwtBasedSignature -> jwtCertificateChain(signature.jwtHeader)?.let { return it }
        is CoseCredentialSignature -> signature.x5cList?.x5c?.map { it.base64Der }?.takeIf { it.isNotEmpty() }?.let { return it }
        else -> Unit
    }
    if (credential is MdocsCredential) {
        return runCatching { credential.document.issuerSigned.getParsedIssuerAuthCrypto2().x5c }.getOrDefault(emptyList())
    }
    return emptyList()
}

internal fun statusListSignerUnauthorizedMessage(
    request: StatusListSignerAuthorizationRequest,
    referencedIssuer: String?,
    customAuthorizerConfigured: Boolean,
): String {
    val signerLabel = request.signer.signerIdentifier ?: "none"
    val issuerLabel = referencedIssuer ?: "none (no iss/issuer claim)"
    val details =
        "Status-list signer source=${request.signer.source}, signer=$signerLabel, " +
            "credential issuer=$issuerLabel, status-list URI=${request.statusListUri}."
    val reason = when {
        request.signer.source == JwtKeyResolutionSource.INLINE_JWK ->
            "an inline JWK does not establish a trusted status-list signer identity"

        request.signer.source == JwtKeyResolutionSource.X5C && request.signer.certificateChain.isEmpty() ->
            "the status-list token is x5c-signed but carries no certificate chain"

        request.signer.source == JwtKeyResolutionSource.X5C ->
            "the status-list x5c leaf does not match the referenced credential x5c leaf"

        referencedIssuer == null ->
            "the referenced credential has no issuer claim, so DID/https direct trust cannot match the status-list signer"

        else ->
            "status-list signer '$signerLabel' does not match credential issuer '$issuerLabel'"
    }
    val hint =
        " Direct trust requires the same DID, https issuer, or x5c leaf certificate as the credential."
    val authorizerHint = if (customAuthorizerConfigured) {
        " A configured status-list signer authorizer also did not authorize this signer."
    } else {
        " A separate Status Provider with a different identity is not accepted unless a status-list signer authorizer is configured."
    }
    return "Status-list signer is not authorized: $reason. $details$hint$authorizerHint"
}

private suspend fun statusListSignerMatchesCredentialCertificate(
    request: StatusListSignerAuthorizationRequest,
): Boolean {
    val statusListLeaf = request.signer.certificateChain.firstOrNull()?.let(::decodeCertificateDer) ?: return false
    val credentialLeaf = referencedCredentialCertificateChain(request.referencedCredential)
        .firstOrNull()
        ?.let(::decodeCertificateDer)
        ?: return false
    return statusListLeaf.contentEquals(credentialLeaf)
}

private fun jwtCertificateChain(header: JsonObject?): List<String>? {
    val x5c = header?.get("x5c")?.jsonArray ?: return null
    val chain = x5c.mapNotNull { it.jsonPrimitive.contentOrNull }
    return chain.takeIf { it.isNotEmpty() }
}

private fun decodeCertificateDer(encoded: String): ByteArray? {
    val body = encoded.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("-----") }
        .joinToString("")
    if (body.isEmpty()) return null
    return runCatching { body.decodeFromBase64() }.getOrNull()
        ?: runCatching { body.decodeFromBase64Url() }.getOrNull()
}
