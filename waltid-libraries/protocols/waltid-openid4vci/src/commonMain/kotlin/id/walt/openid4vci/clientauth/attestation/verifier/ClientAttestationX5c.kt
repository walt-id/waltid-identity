package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Header checks shared by the x5c-based client attestation verifiers. Neither establishes trust in the
// chain: each verifier anchors it on its own terms.

/** The certificates of the x5c header in header order, or the rejection when they are missing or unreadable. */
internal inline fun clientAttestationX5c(
    header: JsonObject,
    reject: (ClientAttestationVerificationResult.Rejected) -> Nothing,
): List<X509Certificate> {
    val x5c = header["x5c"] as? JsonArray
        ?: reject(ClientAttestationVerificationResult.Rejected("Client attestation x5c header is required"))
    if (x5c.isEmpty()) reject(ClientAttestationVerificationResult.Rejected("Client attestation x5c header is empty"))
    return x5c.map { element ->
        parseX5cCertificate(element)
            ?: reject(ClientAttestationVerificationResult.Rejected("Client attestation x5c header is required"))
    }
}

internal fun parseX5cCertificate(element: JsonElement): X509Certificate? {
    val encoded = (element as? JsonPrimitive)?.contentOrNull ?: return null
    return try {
        X509CertificateUtil.parseCertificateDerEncoded(ByteString(encoded.decodeFromBase64()))
    } catch (_: Exception) {
        null
    }
}

private val leafKeyResolver = Crypto2JwtKeyResolver()

/**
 * Verifies [jwt] with the public key of the x5c leaf certificate and returns that key as the signer,
 * for later status checks.
 */
internal suspend fun verifyClientAttestationLeafSignature(
    jwt: String,
    header: JsonObject,
): ClientAttestationVerificationResult {
    val leafKey = leafKeyResolver.resolveFromJwt(header, JsonObject(emptyMap()))?.key
        ?: return ClientAttestationVerificationResult.Rejected("Client attestation certificate key is invalid")
    val algorithm = runCatching {
        JwsAlgorithm.parse(requireNotNull(header["alg"]?.jsonPrimitive?.contentOrNull))
    }.getOrElse {
        return ClientAttestationVerificationResult.Rejected("Client attestation algorithm is invalid")
    }
    try {
        CompactJws.verify(jwt, leafKey, algorithm)
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        return ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid")
    }
    return ClientAttestationVerificationResult.VerifiedWithSigner(leafKey.exportPublicJwkObject())
}
