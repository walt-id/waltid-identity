package id.walt.certificate.x509

import id.walt.certificate.TestData.caIssuerPrivateKey
import id.walt.certificate.TestData.intermediateIssuerPrivateKey
import id.walt.certificate.TestData.intermediateIssuerPublicKeyHex
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.crypto.keys.JvmJWKKeyCreator
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import kotlin.test.*

class X509CertificateUtilCertificateSigningTest {

    @Test
    fun shouldSignSelfSignedCertificate() = runTest {
        val key = JvmJWKKeyCreator.importPEM(caIssuerPrivateKey).getOrThrow()

        val certificate = X509CertificateUtil.createSelfSignedCertificate(key) {
            issuerDn = "OU=waltid"
            subjectDn = "OU=waltid"

            extensionBasicConstraints {
                cA = true
                pathLenConstraint = 5
            }

            extensionKeyUsage {
                critical = true
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign)
            }

            extensionIssuerAltName {
                addEmail("issuer@walt.id")
            }
        }

        assertNotNull(certificate.data.extensionBasicConstraints) { constraints ->
            assertFalse(constraints.critical)
            assertTrue(constraints.cA)
            assertEquals(5, constraints.pathLenConstraint)
        }

        assertNotNull(certificate.data.extensionKeyUsage) { keyUsage ->
            assertTrue(keyUsage.critical)
            assertTrue(keyUsage.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign))
            assertFalse(keyUsage.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign))
        }

        assertNotNull(certificate.data.extensionIssuerAltName) { issuerAltName ->
            assertEquals(1, issuerAltName.alternativeNames.size)
            val email = issuerAltName.alternativeNames.first()
            assertEquals("issuer@walt.id", email.value)
            assertEquals(IssuerAlternativeNameExtension.NameType.rfc822Name, email.type)
        }

        val certPem = certificate.encodedPem
        assertEquals("OU=waltid", certificate.data.subjectDn)
        assertEquals("OU=waltid", certificate.data.issuerDn)
        verifyPemChain(certPem, certPem)
    }

    @Test
    fun shouldSignLeafCertificate() = runTest {
        val caKey = JvmJWKKeyCreator.importPEM(caIssuerPrivateKey).getOrThrow()
        val intermediateKey = JvmJWKKeyCreator.importPEM(intermediateIssuerPrivateKey).getOrThrow()

        val caCert = X509CertificateUtil.createSelfSignedCertificate(caKey) {
            issuerDn = "OU=waltid"
            subjectDn = "OU=waltid"
        }

        val intermediateCert = X509CertificateUtil.createCertificate(caKey, caCert) {
            subjectDn = "OU=test, CN=UnitTests"
            subjectPublicKey(intermediateKey)

            extensionExtendedKeyUsage {
                addKeyUsage(
                    ExtendedKeyUsageExtension.KeyUsage.clientAuth,
                    ExtendedKeyUsageExtension.KeyUsage.serverAuth
                )
            }
        }

        assertNotNull(intermediateCert.data.extensionExtendedKeyUsage) {
            assertTrue(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.clientAuth))
            assertTrue(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.serverAuth))
            assertFalse(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.eapOverLAN))
            assertFalse(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.anyExtendedKeyUsage))
        }

        assertNotNull(intermediateCert.data.subjectPublicKeyInfo) { keyInfo ->
            assertEquals("1.2.840.10045.2.1", keyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", keyInfo.algorithmName)
            assertEquals(intermediateIssuerPublicKeyHex, keyInfo.publicKeyHex)
        }

        println("Cert of intermediate certificate: ${intermediateCert.fingerprintSha256Hex}")
        println(intermediateCert.encodedPem)

        verifyPemChain(intermediateCert.encodedPem, caCert.encodedPem)
    }


    @Throws(Exception::class)
    fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {
        val certFactory = CertificateFactory.getInstance("X.509")

        // 1. Parse the entire certificate chain PEM string into a List
        // generateCertificates parses all headers (---BEGIN CERTIFICATE---) sequentially
        val chainToVerify = ByteArrayInputStream(chainPem.toByteArray()).use { stream ->
            certFactory.generateCertificates(stream).map { it as X509Certificate }
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
}