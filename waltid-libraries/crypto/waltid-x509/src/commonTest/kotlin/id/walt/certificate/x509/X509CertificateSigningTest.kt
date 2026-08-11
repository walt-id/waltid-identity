package id.walt.certificate.x509

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
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

class X509CertificateSigningTest {

    @Test
    fun shouldSignCertificateWithRsaKey() = runTest {
        val issuerKey = TestKeyUtil.genRsaKey("rsa-key")
        val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    @Test
    fun shouldSignCertificateWithEcKey() = runTest {
        val issuerKey = TestKeyUtil.genEcKey("ec-key", EcCurve.P256)
        val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    @Ignore //Signum doesn't support Ed25519 yet
    @Test
    fun shouldSignCertificateWithEdKey() = runTest {
        val issuerKey = TestKeyUtil.genEdKey("ed-key", EdwardsCurve.ED25519)
        val sigAlg = SignatureAlgorithm.EdDsa
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    suspend fun signAndValidateCertificate(issuerKey: Key, sigAlg: SignatureAlgorithm) {
        val rootCert = assertNotNull(caTool.createSelfSignedCertificate(issuerKey, sigAlg) {
            subjectDn = "CN=Test, OU=Walt.id, O=Walt.id, L=Graz, C=AT"
        })
            .also { cert ->
                cert.restoreSubjectPublicKey(TestKeyUtil.runtime).also {
                    assertEquals(issuerKey.spec, it.spec)
                }
                val result =
                    caTool.validateCertificateChain(listOf(cert), InMemoryTrustStore(listOf(cert)))
                assertTrue(result.valid)
            }

        assertNotNull(X509CertificateUtil.createCertificate(issuerKey, rootCert, sigAlg) {
            subjectDn = "CN=Test Leaf, OU=Walt.id, O=Walt.id, L=Graz, C=AT"
            subjectPublicKey(issuerKey)
        }).also { cert ->
            cert.restoreSubjectPublicKey(TestKeyUtil.runtime).also {
                assertEquals(issuerKey.spec, it.spec)
            }
            val result = X509CertificateUtil.validateCertificateChain(
                listOf(cert),
                InMemoryTrustStore(listOf(rootCert))
            )
            assertTrue(result.valid)
        }
    }

    @Test
    fun shouldSignSelfSignedCertificateWithCrypto1Api() = runTest {
        val certificate = withCertificateTestKey(KeyType.RSA) { key ->
            X509CertificateUtil.createSelfSignedCertificate(key) {
                subjectDn = "OU=waltid"

                extensionBasicConstraints {
                    critical = false
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
        }

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

        val subjectKeyId = assertNotNull(certificate.data.extensionSubjectKeyIdentifier).keyIdentifier
        val authorityKeyId = assertNotNull(certificate.data.extensionAuthorityKeyIdentifier).keyIdentifier
        assertEquals(subjectKeyId, authorityKeyId)

        val certPem = certificate.encodedPem
        assertEquals("OU=waltid", certificate.data.subjectDn)
        assertEquals("OU=waltid", certificate.data.issuerDn)
        verifyPemChain(certPem, certPem)
    }

    @Test
    fun buildGenericSelfSignedCertificate() = runTest {
        val (cert, expectedPublicPem) = withCertificateTestKey(KeyType.secp256r1) { key ->
            val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
                subjectDn = "CN=Example CA, C=US"
                extensionBasicConstraints {
                    cA = true
                }
                extensionKeyUsage {
                    addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
                }
            }
            cert to key.getPublicKey().exportPEM()
        }

        val pem = cert.encodedPem

        assertTrue(pem.contains("BEGIN CERTIFICATE"))
        verifyPemChain(pem, pem)

        assertEquals("CN=Example CA,C=US", cert.data.subjectDn)
        assertEquals("CN=Example CA,C=US", cert.data.issuerDn)
        assertNotNull(cert.data.subjectPublicKeyInfo) { publicKeyInfo ->
            assertEquals(normalizePem(expectedPublicPem), normalizePem(publicKeyInfo.encodedPem))
        }
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
        withCertificateTestKey(KeyType.secp256r1) { caKey ->
            val intermediateKey = JWKKey.importPEM(TestKeys.ecP256PublicKeyPem).getOrThrow()
            val expectedPublicPem = intermediateKey.exportPEM()

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
                assertTrue(it.keyPurposeList.contains(ExtendedKeyUsageExtension.KeyUsage.clientAuth))
                assertTrue(it.keyPurposeList.contains(ExtendedKeyUsageExtension.KeyUsage.serverAuth))
                assertFalse(it.keyPurposeList.contains(ExtendedKeyUsageExtension.KeyUsage.eapOverLAN))
                assertFalse(it.keyPurposeList.contains(ExtendedKeyUsageExtension.KeyUsage.anyExtendedKeyUsage))
            }

            assertNotNull(intermediateCert.data.subjectPublicKeyInfo) { keyInfo ->
                assertEquals("1.2.840.10045.2.1", keyInfo.algorithmOid)
                assertEquals("ecPublicKey", keyInfo.algorithmName)
                assertEquals(intermediateIssuerPublicKeyValueHex, keyInfo.keyValueHex)
                assertEquals(normalizePem(expectedPublicPem), normalizePem(keyInfo.encodedPem))
                assertFalse(normalizePem(keyInfo.encodedPem) == normalizePem(caCert.data.subjectPublicKeyInfo.encodedPem))
            }

            assertEquals(caCert.data.subjectDn, intermediateCert.data.issuerDn)

            assertNotNull(intermediateCert.data.extensionSubjectKeyIdentifier) { keyIdentifier ->
                assertEquals(intermediateIssuerPublicKeyIdHex, keyIdentifier.keyIdentifier.toHexString())
            }

            verifyPemChain(intermediateCert.encodedPem, caCert.encodedPem)
        }
    }

    companion object {
        val caTool = X509CertificateUtil {
            addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true))
        }
    }
}
