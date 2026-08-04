package id.walt.certificate.x509

import id.walt.certificate.TestData.caIssuerPrivateKey
import id.walt.certificate.TestData.intermediateIssuerKeyPem
import id.walt.certificate.TestData.intermediateIssuerPublicKeyIdHex
import id.walt.certificate.TestData.intermediateIssuerPublicKeyValueHex
import id.walt.certificate.TestKeys
import id.walt.certificate.x509.SignatureValidationUtil.verifyPemChain
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

class X509CertificateSigningTest {

    @Test
    fun shouldSignSelfSignedCertificate() = runTest {
        val key = JWKKey.importPEM(caIssuerPrivateKey).getOrThrow()

        val certificate = X509CertificateUtil.createSelfSignedCertificate(key) {
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

            extensionSubjectKeyIdentifier()
        }

        println("Cert: ${certificate.fingerprintSha256Hex}")
        println(certificate.encodedPem)

        assertNotNull(certificate.data.extensionBasicConstraints) { constraints ->
            assertFalse(constraints.critical)
            assertTrue(constraints.cA)
            assertEquals(5, constraints.pathLenConstraint)
        }

        assertNotNull(certificate.data.extensionKeyUsage) { keyUsage ->
            assertTrue(keyUsage.critical)
            assertEquals(1, keyUsage.keyPurposeIdList.size)
            assertTrue(keyUsage.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign))
        }

        assertNotNull(certificate.data.extensionIssuerAltName) { issuerAltName ->
            assertEquals(1, issuerAltName.alternativeNames.size)
            val email = issuerAltName.alternativeNames.first()
            assertEquals("issuer@walt.id", email.value)
            assertEquals(GeneralName.NameType.rfc822Name, email.type)
        }

        assertNotNull(certificate.data.extensionSubjectKeyIdentifier) { keyIdentifier ->
            assertEquals("bf1a4ae1c79b2c5b2e3c021661ebad0f4696bf02", keyIdentifier.keyIdentifier.toHexString())
        }

        assertNotNull(certificate.data.extensionAuthorityKeyIdentifier) { keyIdentifier ->
            assertEquals("bf1a4ae1c79b2c5b2e3c021661ebad0f4696bf02", keyIdentifier.keyIdentifier?.toHexString())
        }


        val certPem = certificate.encodedPem
        assertEquals("OU=waltid", certificate.data.subjectDn)
        assertEquals("OU=waltid", certificate.data.issuerDn)
        verifyPemChain(certPem, certPem)
    }


    @Test
    fun buildGenericSelfSignedCertificate() = runTest {
        val key = JWKKey.importPEM(TestKeys.ecP256KeyPem).getOrThrow()
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            subjectDn = "CN=Example CA, C=US"
            extensionBasicConstraints {
                cA = true
            }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }

        val pem = cert.encodedPem

        assertTrue(pem.contains("BEGIN CERTIFICATE"))
        verifyPemChain(pem, pem)

        assertEquals("CN=Example CA,C=US", cert.data.subjectDn)
        assertEquals("CN=Example CA,C=US", cert.data.issuerDn)
        assertNotNull(cert.data.extensionBasicConstraints) { bc ->
            assertEquals(true, bc.cA)
            assertNull(bc.pathLenConstraint)
        }
        assertNotNull(cert.data.extensionKeyUsage) { ku ->
            assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign))
            assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign))
            assertEquals(2, ku.keyPurposeIdList.size)
        }
    }

    @Test
    fun shouldSignLeafCertificate() = runTest {
        val caKey = JWKKey.importPEM(caIssuerPrivateKey).getOrThrow()
        val intermediateKey = JWKKey.importPEM(intermediateIssuerKeyPem).getOrThrow()

        println("Keys imported")

        val caCert = X509CertificateUtil.createSelfSignedCertificate(caKey) {
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

            extensionSubjectKeyIdentifier()
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
            assertEquals(intermediateIssuerPublicKeyValueHex, keyInfo.keyValueHex)
        }

        assertNotNull(intermediateCert.data.extensionSubjectKeyIdentifier) { keyIdentifier ->
            assertEquals(intermediateIssuerPublicKeyIdHex, keyIdentifier.keyIdentifier.toHexString())
        }

        verifyPemChain(intermediateCert.encodedPem, caCert.encodedPem)
    }
}