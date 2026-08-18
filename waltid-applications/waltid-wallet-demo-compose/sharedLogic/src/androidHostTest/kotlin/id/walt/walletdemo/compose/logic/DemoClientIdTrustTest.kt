package id.walt.walletdemo.compose.logic

import id.walt.x509.CertificateDer
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoClientIdTrustTest {
    @Test
    fun exampleAnchorsParseAsDistinctTrustAnchors() {
        val expected = DemoClientIdTrust.x509TrustAnchorPems.map(CertificateDer::fromPEMEncodedString)
        val trust = DemoClientIdTrust.configuration
        assertEquals(expected, trust.x509TrustAnchors)
        assertEquals(expected.toSet().size, expected.size)
    }
}
