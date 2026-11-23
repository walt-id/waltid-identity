package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import kotlinx.serialization.json.JsonObject

class X509ChainClientAttestationVerifier(
    private val trustedRootCertificates: X509CertificateTrustStore,
) : ClientAttestationVerifier {

    constructor(trustedRootCertificatesPem: List<String>)
            : this(InMemoryTrustStore(trustedRootCertificatesPem.map { X509CertificateUtil.parseCertificatePem(it) })) {
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
        val certificateChain = clientAttestationX5c(header) { return it }
        val validationResult = X509CertificateUtil.validateCertificateChain(
            certificateChain,
            this@X509ChainClientAttestationVerifier.trustedRootCertificates
        )
        if (!validationResult.valid) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c chain is not trusted")
        }
        return verifyClientAttestationLeafSignature(jwt, header, certificateChain.first())
    }
}
