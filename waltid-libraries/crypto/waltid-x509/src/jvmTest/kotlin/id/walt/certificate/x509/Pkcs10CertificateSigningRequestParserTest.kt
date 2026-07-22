package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import kotlin.test.*

class Pkcs10CertificateSigningRequestParserTest {

    @Test
    fun shouldParseCsrPem() {
        X509CertificateUtil.parseCsrPem(TestData.csrPem).also { csr ->
            assertEquals("C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id", csr.requestedCertificate.subjectDn)
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", csr.requestedCertificate.subjectPublicKeyInfo.algorithmName)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.publicKeyHex
            )
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "3046022100ed434954325834e6b6108f9bd28f5a038409866dee4b470e92f709d21c0c221a022100e7ac8154cd9d2928f98deb08c3b7821c2be2a1edbd92186cacb177f2476a42a8",
                csr.signatureValueHex
            )

            val extensions = csr.requestedCertificate.extensions
            assertEquals(1, extensions.size)
        }
    }

    @Test
    fun shouldParseCrsWithCrlDistributionPoint() {
        X509CertificateUtil.parseCsrPem(TestData.csrWithCrlPem).also { csr ->
            assertEquals(
                "C=AT,ST=Lower Austria,L=Ober-Grafendorf,O=My Organization,OU=IT Department,CN=yourdomain.com",
                csr.requestedCertificate.subjectDn
            )
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.publicKeyHex
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
}