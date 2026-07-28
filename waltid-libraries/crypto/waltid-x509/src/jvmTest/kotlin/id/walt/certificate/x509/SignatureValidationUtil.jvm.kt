package id.walt.certificate.x509

import org.bouncycastle.asn1.pkcs.CertificationRequest
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import kotlin.test.assertTrue

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

    actual fun verifyCsrPem(csrPem: String) {
        StringReader(csrPem).use { csrReader ->
            PEMParser(csrReader).use { pemParser ->
                // Read object from PEM file
                val parsedObject = pemParser.readObject()

                val csr: PKCS10CertificationRequest =
                    parsedObject as? PKCS10CertificationRequest
                        ?: if (parsedObject is CertificationRequest) {
                            PKCS10CertificationRequest(parsedObject as CertificationRequest?)
                        } else {
                            throw IllegalArgumentException("Provided file is not a valid CSR")
                        }

                // Build the verifier provider using standard JCA providers
                val verifierProvider: ContentVerifierProvider? = JcaContentVerifierProviderBuilder()
                    .setProvider("BC") // Uses Bouncy Castle
                    .build(csr.getSubjectPublicKeyInfo())

                // Validate the signature against the embedded public key
                assertTrue(csr.isSignatureValid(verifierProvider))
            }
        }
    }
}