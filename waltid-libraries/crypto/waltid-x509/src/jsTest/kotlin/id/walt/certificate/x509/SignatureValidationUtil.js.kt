package id.walt.certificate.x509

import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import kotlin.test.assertTrue

@JsModule("crypto")
@JsNonModule
external object NodeCrypto {
    fun verify(algorithm: String?, data: ByteArray, key: String, signature: ByteArray): Boolean

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
        // Parse the CSR structure
        val csr = Pkcs10CertificationRequest.decodeFromPem(csrPem).getOrThrow()
        val hashingAlgorithm = when (csr.tbsCsr.publicKey.oid.toString()) {
            "1.2.840.10045.2.1" -> "SHA256"
            else -> error("Unsupported CSR public key algorithm: ${csr.tbsCsr.publicKey.oid}")
        }
        val signatureValue = Asn1BitString.decodeFromTlv(csr.rawSignature).rawBytes
        val isValidSignature = NodeCrypto.verify(
            hashingAlgorithm,
            csr.rawTbsCsr.derEncoded,
            csr.tbsCsr.publicKey.encodeToPEM().getOrThrow(),
            signatureValue
        )
        assertTrue(
            isValidSignature, "CSR signature is not valid: " +
                    "\nSignature: ${signatureValue.toHexString()}" +
                    "\n$csrPem"
        )
    }

    private val marker = "-----END CERTIFICATE-----"

}