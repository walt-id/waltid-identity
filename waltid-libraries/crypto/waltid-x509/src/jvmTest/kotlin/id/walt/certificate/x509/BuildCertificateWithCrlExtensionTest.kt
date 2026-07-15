package id.walt.certificate.x509

import id.walt.certificate.TestData.caIssuerPrivateKey
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.JvmJWKKeyCreator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BuildCertificateWithCrlExtensionTest {

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
    }

    @Test
    fun shouldCreateCrlWithDistributionPointRelativeToCrlIssuer(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
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
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
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


    companion object {
        val key = runBlocking {
            JvmJWKKeyCreator.importPEM(caIssuerPrivateKey).getOrThrow()
        }
    }
}