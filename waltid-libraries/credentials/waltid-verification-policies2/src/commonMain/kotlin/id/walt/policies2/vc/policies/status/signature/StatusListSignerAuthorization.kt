@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vc.policies.status.signature

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.cose.CoseCertHash
import id.walt.cose.CoseHeaders
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.decodeFromByteArray
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
): Boolean =
    sameEmbeddedKey(request) ||
        sameWebResolution(request, referencedIssuer) ||
        delegatedStatusIssuer(request)

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
            "the status-list x5c leaf does not match the referenced credential x5c leaf, " +
                "and the status certificate is not a delegated status signer issued by the credential's CA " +
                "with id-kp-oauthStatusSigning"

        referencedIssuer == null ->
            "the referenced credential has no issuer claim, so DID/https direct trust cannot match the status-list signer"

        else ->
            "status-list signer '$signerLabel' does not match credential issuer '$issuerLabel'"
    }
    val hint =
        " Direct trust accepts the same DID or https issuer, the same embedded key referenced by x5c, x5t, " +
            "x5t#S256, or kid, or a status-signing certificate issued by the credential's certificate authority."
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

/**
 * draft-ietf-oauth-status-list-21 §11.3, same entity: the status list uses the credential's
 * embedded key, either as the same x5c/x5chain or through x5t, x5t#S256, or kid.
 * A matching iss claim is not a reference to that key.
 */
private suspend fun sameEmbeddedKey(request: StatusListSignerAuthorizationRequest): Boolean {
    if (statusListSignerMatchesCredentialCertificate(request)) return true
    val leafDer = referencedCredentialLeafDer(request.referencedCredential) ?: return false
    val leaf = parseCertificate(leafDer) ?: return false
    if (!embeddedKeyReferenceMatches(
            reference = request.embeddedKeyReference,
            leafDer = leafDer,
            credentialKeyId = referencedCredentialKeyId(request.referencedCredential),
            signerKeyId = request.signer.keyId,
        )
    ) {
        return false
    }
    return samePublicKey(request.signer, leaf)
}

/**
 * §11.3, same web-based resolution. DID and https issuer resolution already bind the verification
 * key to that identifier. x5u, jwks, and jwks_uri are inputs to that resolution, not a second check.
 */
private fun sameWebResolution(
    request: StatusListSignerAuthorizationRequest,
    referencedIssuer: String?,
): Boolean = when (request.signer.source) {
    JwtKeyResolutionSource.DID,
    JwtKeyResolutionSource.WELL_KNOWN,
    -> request.signer.signerIdentifier != null && request.signer.signerIdentifier == referencedIssuer

    JwtKeyResolutionSource.X5C,
    JwtKeyResolutionSource.INLINE_JWK,
    -> false
}

/**
 * §11.3, different status issuer: both certificates are issued by the credential's CA, and the
 * status certificate carries id-kp-oauthStatusSigning (IANA 1.3.6.1.5.5.7.3.45).
 */
private suspend fun delegatedStatusIssuer(request: StatusListSignerAuthorizationRequest): Boolean {
    val credentialChain = referencedCredentialCertificateChain(request.referencedCredential)
    val credentialLeaf = credentialChain.firstOrNull()?.let(::decodeCertificateDer)?.let(::parseCertificate) ?: return false
    val credentialCa = credentialChain.getOrNull(1)?.let(::decodeCertificateDer)?.let(::parseCertificate) ?: return false
    val statusLeaf = request.signer.certificateChain.firstOrNull()?.let(::decodeCertificateDer)?.let(::parseCertificate)
        ?: return false
    if (statusLeaf.encodedDer == credentialLeaf.encodedDer) return false
    if (!signedBy(credentialLeaf, credentialCa) || !signedBy(statusLeaf, credentialCa)) return false
    val purposes = statusLeaf.data.extensionExtendedKeyUsage?.keyPurposeIdList ?: return false
    return OAUTH_STATUS_SIGNING_EKU in purposes
}

internal fun embeddedKeyReference(
    x5tSha1: ByteArray? = null,
    x5tSha256: ByteArray? = null,
    coseHashAlgorithm: Int? = null,
    coseHash: ByteArray? = null,
): StatusListEmbeddedKeyReference? {
    if (x5tSha1 == null && x5tSha256 == null && coseHash == null) return null
    return StatusListEmbeddedKeyReference(
        x5tSha1 = x5tSha1,
        x5tSha256 = x5tSha256,
        coseHashAlgorithm = coseHashAlgorithm,
        coseHash = coseHash,
    )
}

internal fun decodeCertificateThumbprint(encoded: String): ByteArray? =
    runCatching { encoded.decodeFromBase64Url() }.getOrNull()
        ?: runCatching { encoded.decodeFromBase64() }.getOrNull()

internal suspend fun resolveReferencedCredentialKey(
    credential: DigitalCredential?,
    reference: StatusListEmbeddedKeyReference?,
    keyId: String?,
): ResolvedJwtVerificationKey? {
    credential ?: return null
    val leafDer = referencedCredentialLeafDer(credential) ?: return null
    if (!embeddedKeyReferenceMatches(reference, leafDer, referencedCredentialKeyId(credential), keyId)) return null
    val certificate = parseCertificate(leafDer) ?: return null
    val key = certificate.restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime)
    return ResolvedJwtVerificationKey(
        key = key,
        source = JwtKeyResolutionSource.X5C,
        signerIdentifier = null,
        keyId = keyId,
        certificateChain = emptyList(),
    )
}

internal suspend fun referencedCredentialLeafDer(credential: DigitalCredential): ByteArray? =
    referencedCredentialCertificateChain(credential).firstOrNull()?.let(::decodeCertificateDer)

internal fun embeddedKeyReferenceMatches(
    reference: StatusListEmbeddedKeyReference?,
    leafDer: ByteArray,
    credentialKeyId: String?,
    signerKeyId: String?,
): Boolean {
    val sha1 = ShaUtils.sha1(leafDer)
    val sha256 = ShaUtils.sha256(leafDer)
    if (reference?.x5tSha1 != null && reference.x5tSha1.contentEquals(sha1)) return true
    if (reference?.x5tSha256 != null && reference.x5tSha256.contentEquals(sha256)) return true
    if (reference?.coseHash != null && coseHashMatches(reference.coseHashAlgorithm, reference.coseHash, sha1, sha256)) {
        return true
    }
    return credentialKeyId != null && signerKeyId != null && credentialKeyId == signerKeyId
}

internal fun referencedCredentialKeyId(credential: DigitalCredential): String? {
    (credential.signature as? JwtBasedSignature)
        ?.jwtHeader
        ?.get("kid")
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.let { return it }
    if (credential is MdocsCredential) {
        return runCatching {
            coseCompliantCbor.decodeFromByteArray<CoseHeaders>(
                credential.document.issuerSigned.issuerAuth.protected,
            ).kid?.decodeToString()
        }.getOrNull()
    }
    return null
}

/**
 * Only SHA-1 (-14) and SHA-256 (-16) COSE `x5t` hashes are compared.
 * SHA-384 (-43) and SHA-512 (-44) fail closed rather than being treated as a match.
 */
private fun coseHashMatches(algorithm: Int?, hash: ByteArray, sha1: ByteArray, sha256: ByteArray): Boolean =
    when (algorithm) {
        COSE_SHA1 -> hash.contentEquals(sha1)
        CoseCertHash.SHA_256 -> hash.contentEquals(sha256)
        else -> false
    }

/**
 * Compares the key that verified the status-list signature with the credential key. `Key.id` is
 * not evidence of key identity: resolvers take it from signer-controlled metadata such as a JWK `kid`.
 */
private suspend fun samePublicKey(signer: ResolvedJwtVerificationKey, leaf: X509Certificate): Boolean {
    val signerSpki = spkiDer { signer.key } ?: return false
    val credentialSpki = spkiDer { leaf.restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime) } ?: return false
    return signerSpki.contentEquals(credentialSpki)
}

/** Both sides go through crypto2 export, so certificate encoding differences cannot cause a false mismatch. */
private suspend fun spkiDer(key: suspend () -> Key): ByteArray? = try {
    key().capabilities.publicKeyExporter
        ?.exportPublicKey(KeyEncodingFormat.SPKI_DER)
        ?.data
        ?.toByteArray()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private suspend fun signedBy(certificate: X509Certificate, issuer: X509Certificate): Boolean = runCatching {
    val services = X509CertificateUtil.services
    services.signatureValidator.validateCertificateSignature(
        services.cryptoRuntime,
        issuer.data.subjectPublicKeyInfo,
        certificate,
    )
}.getOrDefault(false)

private fun parseCertificate(der: ByteArray): X509Certificate? =
    runCatching { X509CertificateUtil.parseCertificateDerEncoded(ByteString(der)) }.getOrNull()

/**
 * IANA id-kp-oauthStatusSigning, allocated for draft-ietf-oauth-status-list-21.
 * The draft ASN.1 module still writes this arc as TBD.
 */
private const val OAUTH_STATUS_SIGNING_EKU = "1.3.6.1.5.5.7.3.45"

/** COSE algorithm identifier for SHA-1 (RFC 9054). */
private const val COSE_SHA1 = -14
