package id.walt.certificate.x509


@JsModule("crypto")
@JsNonModule
external object NodeCrypto {
    class X509Certificate(pem: String) {
        val subject: String
        val issuer: String
        val fingerprint: String
        val publicKey: dynamic // Returns a KeyObject
        fun verify(publicKey: dynamic): Boolean
    }
}

actual object SignatureValidationUtil {
    actual fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {

        val chainCertificates = chainPem.split(marker)
            .map { it.trim() }
            // Filter out empty strings caused by trailing whitespace or splits
            .filter { it.isNotEmpty() && it.contains("-----BEGIN CERTIFICATE-----") }
            .map { "$it\n$marker" } // Append the closing marker back onto each cert
            .map { NodeCrypto.X509Certificate(it) } // Instantiate the certificate wrappers

        val caCertificate = NodeCrypto.X509Certificate(selfSignedCaPem)

        val validCa = caCertificate.verify(caCertificate.publicKey)
        if (!validCa) {
            throw Exception("CA certificate signature is not valid")
        }

        var validatedCertsCount = 0

        val issuerToCertMap: Map<String, NodeCrypto.X509Certificate> = chainCertificates
            .filter {
                if (it.subject == caCertificate.subject) {
                    if (it.fingerprint != caCertificate.fingerprint) {
                        throw Exception("CA certificate fingerprint does not match self-signed CA certificate: ${it.fingerprint} != ${caCertificate.fingerprint}")
                    }
                    validatedCertsCount += 1
                    false
                } else {
                    true
                }
            }
            .groupingBy { it.issuer }
            .aggregate { key, accumulator, element, first ->
                if (accumulator != null) {
                    error("Duplicate issuer found: $key")
                } else {
                    element
                }
            }

        var currentIssuerCert = caCertificate
        var currentCert = issuerToCertMap[currentIssuerCert.subject]

        while (currentCert != null) {
            val isValid = currentCert.verify(currentIssuerCert.publicKey)
            if (isValid) {
                validatedCertsCount += 1
                currentIssuerCert = currentCert
                currentCert = issuerToCertMap[currentIssuerCert.subject]
            } else {
                break
            }
        }

        if (validatedCertsCount != chainCertificates.size) {
            throw Exception("Validation failed. Expected ${chainCertificates.size} certificates to have valid signature, but only $validatedCertsCount were validated.")
        }
    }

    actual fun verifyCsrPem(csrPem: String) {
        TODO()
    }

    private val marker = "-----END CERTIFICATE-----"

}