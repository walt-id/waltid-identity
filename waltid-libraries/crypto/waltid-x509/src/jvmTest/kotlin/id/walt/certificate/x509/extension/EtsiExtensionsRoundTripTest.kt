package id.walt.certificate.x509.extension

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.signumImplementation
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.QcStatementsExtension.Companion.extensionQcStatements
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Round-trips the three new ETSI TS 119 412-6 / 119 411-8 supporting extensions
 * (CertificatePolicies, AuthorityInfoAccess, QcStatements) through both platform backends
 * (BouncyCastle, the JVM default, and Signum) to prove the DER encode/decode is symmetric on each.
 */
class EtsiExtensionsRoundTripTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private val bouncyUtil = X509CertificateUtil.Default
    private val signumUtil = X509CertificateUtil { signumImplementation() }

    @Test
    fun shouldRoundTripCertificatePoliciesOnBouncy() = runTest {
        roundTripCertificatePolicies(bouncyUtil)
    }

    @Test
    fun shouldRoundTripCertificatePoliciesOnSignum() = runTest {
        roundTripCertificatePolicies(signumUtil)
    }

    private suspend fun roundTripCertificatePolicies(util: X509CertificateUtil) {
        val key = TestKeyUtil.genEcKey("policies")
        val cert = util.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=Policies Test"
            extensionCertificatePolicies {
                addPolicy("0.4.0.194118.1.2")
                addPolicy("2.23.140.1.2.1")
            }
        }
        val parsed = util.parseCertificatePem(cert.encodedPem)
        val policies = assertNotNull(parsed.data.extensionCertificatePolicies)
        assertEquals(listOf("0.4.0.194118.1.2", "2.23.140.1.2.1"), policies.policyOids)
    }

    @Test
    fun shouldRoundTripAuthorityInfoAccessOnBouncy() = runTest {
        roundTripAuthorityInfoAccess(bouncyUtil)
    }

    @Test
    fun shouldRoundTripAuthorityInfoAccessOnSignum() = runTest {
        roundTripAuthorityInfoAccess(signumUtil)
    }

    private suspend fun roundTripAuthorityInfoAccess(util: X509CertificateUtil) {
        val key = TestKeyUtil.genEcKey("aia")
        val cert = util.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=AIA Test"
            extensionAuthorityInfoAccess {
                addCaIssuerUri("https://example.org/ca.crt")
                addOcspResponderUri("https://example.org/ocsp")
            }
        }
        val parsed = util.parseCertificatePem(cert.encodedPem)
        val aia = assertNotNull(parsed.data.extensionAuthorityInfoAccess)
        assertEquals(2, aia.accessDescriptions.size)
        assertEquals(AuthorityInfoAccessExtension.AccessMethod.caIssuers, aia.accessDescriptions[0].accessMethod)
        assertEquals(GeneralName.NameType.uniformResourceIdentifier, aia.accessDescriptions[0].accessLocation.type)
        assertEquals("https://example.org/ca.crt", aia.accessDescriptions[0].accessLocation.value)
        assertEquals(AuthorityInfoAccessExtension.AccessMethod.ocsp, aia.accessDescriptions[1].accessMethod)
        assertEquals("https://example.org/ocsp", aia.accessDescriptions[1].accessLocation.value)
    }

    @Test
    fun shouldRoundTripQcStatementsOnBouncy() = runTest {
        roundTripQcStatements(bouncyUtil)
    }

    @Test
    fun shouldRoundTripQcStatementsOnSignum() = runTest {
        roundTripQcStatements(signumUtil)
    }

    private suspend fun roundTripQcStatements(util: X509CertificateUtil) {
        val key = TestKeyUtil.genEcKey("qc")
        val cert = util.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=QC Test"
            extensionQcStatements {
                addQcCompliance()
                addQcType("0.4.0.194126.1.1")
            }
        }
        val parsed = util.parseCertificatePem(cert.encodedPem)
        val qcStatements = assertNotNull(parsed.data.extensionQcStatements)
        assertEquals(2, qcStatements.statements.size)
        assertEquals(QcStatementsExtension.QcStatement.QcCompliance, qcStatements.statements[0])
        assertEquals(
            QcStatementsExtension.QcStatement.QcType(listOf("0.4.0.194126.1.1")),
            qcStatements.statements[1]
        )
    }
}
