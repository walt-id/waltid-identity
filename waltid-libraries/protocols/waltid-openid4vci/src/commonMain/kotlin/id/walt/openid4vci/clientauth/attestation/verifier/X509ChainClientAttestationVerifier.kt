package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class X509ChainClientAttestationVerifier(
    trustedRootCertificatesPem: List<String>,
) : ClientAttestationVerifier {

    private val trustedRootCertificates = InMemoryTrustStore(trustedRootCertificatesPem.map {
        X509CertificateUtil.parseCertificatePem(it)
    })

    init {
        require(trustedRootCertificatesPem.isNotEmpty()) {
            "trustedRootCertificatesPem must not be empty"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val certificateChain = header.x5cCertificates()
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is required")

        val leafCertificate = certificateChain.firstOrNull()
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is empty")

        val validationResult = X509CertificateUtil.validateCertificateChain(certificateChain, trustedRootCertificates)

        if (!validationResult.valid) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c chain is not trusted")
        }

        val leafKey = JWKKey.importFromDerCertificate(leafCertificate.encodedDer.toByteArray()).getOrNull()
        if (leafKey?.verifyJws(jwt)?.isSuccess != true) {
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
