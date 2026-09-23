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
import kotlin.test.assertTrue

actual object SignatureValidationUtil {

    actual fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {
        val certFactory = CertificateFactory.getInstance("X.509")
        val chainToVerify = ByteArrayInputStream(chainPem.toByteArray()).use { stream ->
            certFactory.generateCertificates(stream).map { it as java.security.cert.X509Certificate }
        }
        val caCert = ByteArrayInputStream(selfSignedCaPem.toByteArray()).use { stream ->
            certFactory.generateCertificate(stream) as java.security.cert.X509Certificate
        }
        val inMemoryTrustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("my-self-signed-ca", caCert)
        }
        val certPath = certFactory.generateCertPath(chainToVerify)
        val params = PKIXParameters(inMemoryTrustStore).apply {
            isRevocationEnabled = false
        }
        CertPathValidator.getInstance("PKIX").validate(certPath, params)
    }

    actual fun verifyCsrPem(csrPem: String) {
        StringReader(csrPem).use { csrReader ->
            PEMParser(csrReader).use { pemParser ->
                val parsedObject = pemParser.readObject()
                val csr: PKCS10CertificationRequest =
                    parsedObject as? PKCS10CertificationRequest
                        ?: if (parsedObject is CertificationRequest) {
                            PKCS10CertificationRequest(parsedObject)
                        } else {
                            throw IllegalArgumentException("Provided file is not a valid CSR")
                        }
                val verifierProvider: ContentVerifierProvider = JcaContentVerifierProviderBuilder()
                    .setProvider("BC")
                    .build(csr.subjectPublicKeyInfo)
                assertTrue(csr.isSignatureValid(verifierProvider))
            }
        }
    }
}
