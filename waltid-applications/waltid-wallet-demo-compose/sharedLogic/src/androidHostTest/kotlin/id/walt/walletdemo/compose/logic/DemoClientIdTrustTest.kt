package id.walt.walletdemo.compose.logic

import id.walt.certificate.x509.X509CertificateUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    }
}
