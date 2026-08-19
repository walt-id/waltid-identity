package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.*

class X509ChainClientAttestationVerifier(
    private val trustedRootCertificates: X509CertificateTrustStore,
) : ClientAttestationVerifier {

    constructor(trustedRootCertificatesPem: List<String>)
            : this(InMemoryTrustStore(trustedRootCertificatesPem.map { X509CertificateUtil.parseCertificatePem(it) })) {
        require(trustedRootCertificatesPem.isNotEmpty()) {
            "trustedRootCertificatesPem must not be empty"
        }
    }

    private val keyResolver = Crypto2JwtKeyResolver()

    @Suppress("UNUSED_PARAMETER")
    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val certificateChain = header.x5cCertificates()
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is required")

        if (certificateChain.isEmpty()) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is empty")
        }
        val validationResult = X509CertificateUtil.validateCertificateChain(
            certificateChain,
            this@X509ChainClientAttestationVerifier.trustedRootCertificates
        )
        if (!validationResult.valid) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c chain is not trusted")
        }
        val leafKey = keyResolver.resolveFromJwt(header, JsonObject(emptyMap()))?.key
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

        return ClientAttestationVerificationResult.Verified
    }

    private fun JsonObject.x5cCertificates(): List<X509Certificate>? {
        val x5c = this["x5c"] as? JsonArray ?: return null
        if (x5c.isEmpty()) return emptyList()

        return x5c.map { element ->
            val encodedCertificate = (element as? JsonPrimitive)?.contentOrNull ?: return null
            val der = runCatching { encodedCertificate.decodeFromBase64() }.getOrNull() ?: return null
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(der))
        }
    }
}
