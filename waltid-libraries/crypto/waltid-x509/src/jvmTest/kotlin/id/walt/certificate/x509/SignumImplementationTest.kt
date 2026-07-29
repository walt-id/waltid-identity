package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.TestData.caIssuerPrivateKey
import id.walt.certificate.TestData.intermediateIssuerKeyPem
import id.walt.certificate.TestData.intermediateIssuerPublicKeyIdHex
import id.walt.certificate.TestData.intermediateIssuerPublicKeyValueHex
import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.BuildCertificateWithCrlExtensionTest.Companion.key
import id.walt.certificate.x509.SignatureValidationUtil.verifyPemChain
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.X509TestCertificates
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

/**
 * This test is to debug the Signum implementation.
 * Java is easier to debug
 */
class SignumImplementationTest {

    //CRL distribution point crlIssuer is not implemented yet
    @Ignore
    @Test
    fun shouldCreateCrlWithDistributionPointWithCrlIssuer(): Unit = runTest {
        val cert = signumCertUtil.createSelfSignedCertificate(key) {
            extensionCrlDistributionPoints {
                addDistributionPointRelativeName(
                    "ou = Walt.id ",
                    setOf(CrlDistributionPointsExtension.ReasonFlag.keyCompromise),
                    listOf(GeneralName(GeneralName.NameType.directoryName, "cn=Test"))
                )
            }
        }
        assertNotNull(cert.data.extensionCrlDistributionPoints) { distributionPoints ->
            assertEquals(1, distributionPoints.distributionPoints.size)
            val dp = distributionPoints.distributionPoints.first()
            assertNull(dp.distributionPointFullName)
            assertEquals("OU=Walt.id", dp.distributionPointNameRelativeToCrlIssuer)
            assertNotNull(dp.reason) { reason ->
                assertEquals(1, reason.size)
                assertTrue(reason.contains(CrlDistributionPointsExtension.ReasonFlag.keyCompromise))
            }
        }
    }


    @Test
    fun shouldCreateCrlWithMultipleUrls(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            extensionCrlDistributionPoints {
                addUriDistributionPoint(
                    listOf(
                        "https://walt.id/crl",
                        "https://walt-id.com/crl"
                    ),
                    setOf(CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn)
                )
            }
        }

        assertNotNull(cert.data.extensionCrlDistributionPoints) { distributionPoints ->
            assertEquals(1, distributionPoints.distributionPoints.size)
            val dp = distributionPoints.distributionPoints.first()
            assertNull(dp.distributionPointNameRelativeToCrlIssuer)
            assertNotNull(dp.reason) { reason ->
                assertEquals(1, reason.size)
                assertTrue(reason.contains(CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn))
            }
            assertNull(dp.cRLIssuer)
            assertNotNull(dp.distributionPointFullName?.toList()) { fullName ->
                assertEquals(2, fullName.size)
                assertEquals("https://walt.id/crl", fullName[0].value)
                assertEquals(GeneralName.NameType.uniformResourceIdentifier, fullName[0].type)
                assertEquals("https://walt-id.com/crl", fullName[1].value)
                assertEquals(GeneralName.NameType.uniformResourceIdentifier, fullName[1].type)
            }
        }

        X509CertificateUtil.parseCertificatePem(cert.encodedPem).also { parsedCert ->
            assertNotNull(parsedCert.data.extensionCrlDistributionPoints) { distributionPoints ->
                assertEquals(1, distributionPoints.distributionPoints.size)
                val dp = distributionPoints.distributionPoints.first()
                assertNull(dp.distributionPointNameRelativeToCrlIssuer)
                assertNotNull(dp.reason) { reason ->
                    assertEquals(1, reason.size)
                    assertTrue(reason.contains(CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn))
                }
                assertNull(dp.cRLIssuer)
                assertNotNull(dp.distributionPointFullName?.toList()) { fullName ->
                    assertEquals(2, fullName.size)
                    assertEquals("https://walt.id/crl", fullName[0].value)
                    assertEquals(GeneralName.NameType.uniformResourceIdentifier, fullName[0].type)
                    assertEquals("https://walt-id.com/crl", fullName[1].value)
                    assertEquals(GeneralName.NameType.uniformResourceIdentifier, fullName[1].type)
                }
            }
        }
    }


    @Test
    fun shouldSignCsr() = runTest {

        val key = JWKKey.importPEM(intermediateIssuerKeyPem).getOrThrow()
        val csr = signumCertUtil.createCsr(key) {
            requestedCertificate.apply {
                subjectDn = "OU=unit test, O=Walt.id"

                extensionSan {
                    addDnsName("www.walt.id")
                    addIpAddress("127.0.0.1")
                }
            }
        }
        assertNotNull(csr).also { csr ->
            assertNotNull(csr.requestedCertificate).also { data ->
                assertEquals("OU=unit test,O=Walt.id", data.subjectDn)
                assertNotNull(data.subjectPublicKeyInfo) { pk ->
                    assertEquals("1.2.840.10045.2.1", pk.algorithmOid)
                    assertEquals("id-ecPublicKey", pk.algorithmName)
                    assertEquals(intermediateIssuerPublicKeyValueHex, pk.keyValueHex)
                }
                assertNotNull(data.extensionSan) { san ->
                    assertEquals(2, san.alternativeNames.size)
                    san.alternativeNames.get(0).also {
                        assertEquals(GeneralName.NameType.dNSName, it.type)
                        assertEquals("www.walt.id", it.value)
                    }
                    san.alternativeNames.get(1).also {
                        assertEquals(GeneralName.NameType.IPAddress, it.type)
                        assertEquals("127.0.0.1", it.value)
                    }
                }
            }
        }
        SignatureValidationUtil.verifyCsrPem(csr.encodedPem)
        X509CertificateUtil.parseCsrPem(csr.encodedPem).also { parsedCsr ->
            assertNotNull(parsedCsr.requestedCertificate.extensionSan) { san ->
                assertEquals(2, san.alternativeNames.size)
                san.alternativeNames.get(0).also {
                    assertEquals(GeneralName.NameType.dNSName, it.type)
                    assertEquals("www.walt.id", it.value)
                }
                san.alternativeNames.get(1).also {
                    assertEquals(GeneralName.NameType.IPAddress, it.type)
                    assertEquals("127.0.0.1", it.value)
                }
            }
        }
    }


    @Test
    fun shouldParseCsrPem() {
        signumCertUtil.parseCsrPem(TestData.csrPem).also { csr ->
            assertEquals("C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id", csr.requestedCertificate.subjectDn)
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", csr.requestedCertificate.subjectPublicKeyInfo.algorithmName)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.keyValueHex
            )
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "3046022100ed434954325834e6b6108f9bd28f5a038409866dee4b470e92f709d21c0c221a022100e7ac8154cd9d2928f98deb08c3b7821c2be2a1edbd92186cacb177f2476a42a8",
                csr.signatureValueHex
            )

            val extensions = csr.requestedCertificate.extensions
            assertEquals(1, extensions.size)

            assertNotNull(csr.requestedCertificate.extensionSan) { san ->
                assertEquals(3, san.alternativeNames.size)
                assertEquals(GeneralName.NameType.dNSName, san.alternativeNames[0].type)
                assertEquals("://waltid.com", san.alternativeNames[0].value)

                assertEquals(GeneralName.NameType.dNSName, san.alternativeNames[1].type)
                assertEquals("://waltid.cloud", san.alternativeNames[1].value)

                assertEquals(GeneralName.NameType.IPAddress, san.alternativeNames[2].type)
                assertEquals("192.168.1.100", san.alternativeNames[2].value)
            }
        }
    }


    @Test
    fun shouldParseCrsWithCrlDistributionPoint() {
        signumCertUtil.parseCsrPem(TestData.csrWithCrlPem).also { csr ->
            assertEquals(
                "C=AT,ST=Lower Austria,L=Ober-Grafendorf,O=My Organization,OU=IT Department,CN=yourdomain.com",
                csr.requestedCertificate.subjectDn
            )
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.keyValueHex
            )
            assertEquals(4, csr.requestedCertificate.extensions.size)
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "30450221009330b586e6d4d8972d1b69aca3ed6ebb086f195fe485b70e8fa87a456d4dcb450220779922b2b405591a2081f0ea522c03bca910a7a2c5748a76f11ca70abac44869",
                csr.signatureValueHex
            )

            assertNotNull(csr.requestedCertificate.extensionCrlDistributionPoints) { crl ->
                assertEquals(1, crl.distributionPoints.size)
                val dp = crl.distributionPoints.first()
                assertEquals("CN=My Relative CRL Distribution Point", dp.distributionPointNameRelativeToCrlIssuer)
                assertNotNull(dp.reason) { flags ->
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.keyCompromise))
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.cACompromise))
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.affiliationChanged))
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.superseded))
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.cessationOfOperation))
                    assertTrue(flags.contains(CrlDistributionPointsExtension.ReasonFlag.certificateHold))

                    assertFalse(flags.contains(CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn))
                    assertFalse(flags.contains(CrlDistributionPointsExtension.ReasonFlag.aACompromise))
                }
            }
        }
    }

    @Test
    fun shouldSignLeafCertificate() = runTest {
        val caKey = JWKKey.importPEM(caIssuerPrivateKey).getOrThrow()
        val intermediateKey = JWKKey.importPEM(intermediateIssuerKeyPem).getOrThrow()

        val caCert = signumCertUtil.createSelfSignedCertificate(caKey) {
            issuerDn = "OU=waltid"
            subjectDn = "OU=waltid"
        }

        val intermediateCert = signumCertUtil.createCertificate(caKey, caCert) {
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
            assertEquals(2, it.keyPurposeIdList.size)
            assertTrue(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.clientAuth))
            assertTrue(it.keyPurposeIdList.contains(ExtendedKeyUsageExtension.KeyUsage.serverAuth))
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

    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        val issuerCert = signumCertUtil.parseCertificateDerEncoded(X509TestCertificates.issuerCertificate)
        val leafCert = signumCertUtil.parseCertificateDerEncoded(X509TestCertificates.leafCertificate)

        val issuerSubjectKeyId =
            assertNotNull(issuerCert.data.extensionSubjectKeyIdentifier?.keyIdentifier, "issuerSubjectKeyId is null")
        val leafAuthKeyId =
            assertNotNull(leafCert.data.extensionAuthorityKeyIdentifier?.keyIdentifier, "leafAuthorityKeyId is null")
        assertEquals(issuerSubjectKeyId, leafAuthKeyId)
        assertEquals(
            "B1:3A:CD:04:B5:00:E5:DE:1F:FC:B1:3C:3C:EC:8F:60:BC:62:03:74",
            issuerSubjectKeyId.toHexString(opensslHexFormat)
        )
    }

    @Test
    fun rejectsLeafWithTamperedSignature() = runTest {

        val validationResult = signumCertUtil.validateCertificateChain(
            listOf(X509TestCertificates.tamperedLeafCertificate.let {
                signumCertUtil.parseCertificateDerEncoded(it)
            })
        )
        assertFalse(validationResult.valid)
    }

    @Test
    fun verifiesLeafSignedByIssuer() = runTest {
        val validationResult = signumCertUtil.validateCertificateChain(
            listOf(X509TestCertificates.leafCertificate.let {
                signumCertUtil.parseCertificateDerEncoded(it)
            }),
            InMemoryTrustStore(
                listOf(X509TestCertificates.issuerCertificate)
                    .map { signumCertUtil.parseCertificateDerEncoded(it) })
        )
        assertTrue(validationResult.valid)
    }

    @Test
    fun shouldParseCsr() {
        assertNotNull(signumCertUtil.parseCsrPem(TestData.csrPem)) { csr ->
            assertEquals("C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id", csr.requestedCertificate.subjectDn)
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", csr.requestedCertificate.subjectPublicKeyInfo.algorithmName)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.keyValueHex
            )
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "3046022100ed434954325834e6b6108f9bd28f5a038409866dee4b470e92f709d21c0c221a022100e7ac8154cd9d2928f98deb08c3b7821c2be2a1edbd92186cacb177f2476a42a8",
                csr.signatureValueHex
            )
            assertEquals(1, csr.requestedCertificate.extensions.size)
        }
    }

    @Test
    fun shouldParseCertificate() {
        val cert = signumCertUtil.parseCertificatePem(TestData.GOOGLE_CERTIFICATE_PEM)
        assertNotNull(cert)
        assertEquals("CN=*.google.com", cert.data.subjectDn)
    }


    companion object {
        val trustStore = InMemoryTrustStore(
            listOf(gtsRootR4CrtPem)
                .map { X509CertificateUtil.parseCertificatePem(it) })

        val signumCertUtil = X509CertificateUtil {
            setTrust(trustStore)
            signumImplementation()
        }
    }
}