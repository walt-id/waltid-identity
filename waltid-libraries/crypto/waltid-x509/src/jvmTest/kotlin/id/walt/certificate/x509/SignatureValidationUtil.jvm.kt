package id.walt.certificate.x509

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate

actual object SignatureValidationUtil {

    actual fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {
        val certFactory = CertificateFactory.getInstance("X.509")

        // 1. Parse the entire certificate chain PEM string into a List
        // generateCertificates parses all headers (---BEGIN CERTIFICATE---) sequentially
        val chainToVerify = ByteArrayInputStream(chainPem.toByteArray()).use { stream ->
            certFactory.generateCertificates(stream).map { it as java.security.cert.X509Certificate }
        }

        // 2. Parse the self-signed Root CA PEM string
        val caCert = ByteArrayInputStream(selfSignedCaPem.toByteArray()).use { stream ->
            certFactory.generateCertificate(stream) as X509Certificate
        }

        // 3. Initialize the in-memory KeyStore and inject the Root CA
        val inMemoryTrustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("my-self-signed-ca", caCert)
        }

        // 4. Generate the CertPath path from the parsed list
        val certPath = certFactory.generateCertPath(chainToVerify)

        // 5. Configure validation using your in-memory truststore
        val params = PKIXParameters(inMemoryTrustStore).apply {
            isRevocationEnabled = false
        }

        // 6. Execute verification
        val validator = CertPathValidator.getInstance("PKIX")
        validator.validate(certPath, params)
    }

    actual suspend fun validateCertificateSignature(
        issuerPublicKey: id.walt.certificate.x509.X509Certificate.SubjectPublicKeyInfo,
        certificate: id.walt.certificate.x509.X509Certificate
    ): Boolean {
        TODO("Not yet implemented")
    }


}