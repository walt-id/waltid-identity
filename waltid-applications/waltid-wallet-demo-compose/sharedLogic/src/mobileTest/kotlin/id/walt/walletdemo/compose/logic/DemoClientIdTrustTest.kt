package id.walt.walletdemo.compose.logic

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.openid4vp.clientidprefix.prefixes.AttestationX509CertificateValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoClientIdTrustTest {
    @Test
    fun exampleAnchorsParseAsDistinctTrustAnchors() {
        val expected = DemoClientIdTrust.x509TrustAnchorPems.map(X509CertificateUtil::parseCertificatePem)
        val trustStore = assertNotNull(DemoClientIdTrust.configuration.x509TrustAnchors)
        expected.forEach { certificate ->
            val stored = trustStore.findCertificateBySubjectDn(certificate.data.subjectDn)
                .single { it.encodedDer == certificate.encodedDer }
            assertEquals(certificate.encodedDer, stored.encodedDer)
        }
        assertEquals(expected.map { it.encodedDer }.toSet().size, expected.size)
        assertEquals(14, expected.size)
    }

    @Test
    fun gatewayChainRequiresConfiguredTrustAnchors() = runTest {
        val util = X509CertificateUtil { addValidators(AttestationX509CertificateValidator()) }
        val gatewayPems = listOf(
            DemoClientIdTrust.WALLET_GATEWAY_TEST_LEAF_PEM,
            DemoClientIdTrust.WALLET_GATEWAY_TEST_SUB_CA_PEM,
        )
        val certificates = gatewayPems.map(util::parseCertificatePem)
        val configuredTrust = assertNotNull(DemoClientIdTrust.configuration.x509TrustAnchors)
        val withoutGatewayTrust = InMemoryTrustStore(
            DemoClientIdTrust.x509TrustAnchorPems.filterNot { it in gatewayPems }.map(util::parseCertificatePem),
        )
        for (size in 1..certificates.size) {
            val chain = certificates.take(size)
            val trusted = util.validateCertificateChain(chain, configuredTrust)
            assertTrue(trusted.valid, "Gateway x5c size=$size: ${trusted.log}")
            val untrusted = util.validateCertificateChain(chain, withoutGatewayTrust)
            assertFalse(untrusted.valid, "Gateway x5c size=$size must require a configured anchor")
        }
    }
}
