package id.walt.certificate.x509

import id.walt.certificate.TestData.caIssuerPrivateKey
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.JvmJWKKeyCreator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BuildCertificateWithCrlExtensionTest {

    @Test
    fun shouldCreateCrlWithMultipleUrls(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
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
    }


    @Test
    fun shouldCreateCrlWithMultipleUrlsCrypto1(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(crypto1key) {
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
    }

    @Test
    fun shouldCreateCrlWithDistributionPointRelativeToCrlIssuer(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(crypto1key) {
            extensionCrlDistributionPoints {
                addDistributionPointRelativeName(
                    "ou = Walt.id ",
                    setOf(CrlDistributionPointsExtension.ReasonFlag.keyCompromise)
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
    fun shouldCreateCrlWithDistributionPointWithCrlIssuer(): Unit = runTest {

        val cert = X509CertificateUtil.createSelfSignedCertificate(crypto1key) {
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
                assertEquals(1, dp.cRLIssuer?.size)
                assertEquals(GeneralName.NameType.directoryName, dp.cRLIssuer?.first()?.type)
                assertEquals("CN=Test", dp.cRLIssuer?.first()?.value)
            }
        }
    }

    companion object {
        val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, encoding = EcdsaSignatureEncoding.DER)
        val key = runBlocking { TestKeyUtil.genEcKey("test-key") }
        val crypto1key = runBlocking {
            JvmJWKKeyCreator.importPEM(caIssuerPrivateKey).getOrThrow()
        }
    }
}