package id.walt.walletdemo.compose.logic

import id.walt.x509.CertificateDer
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoClientIdTrustTest {
    @Test
    fun exampleAnchorsParseAsDistinctTrustAnchors() {
        val trust = DemoClientIdTrust.configuration
        assertEquals(2, trust.x509TrustAnchors.size)
        assertEquals(
            listOf(
                CertificateDer.fromPEMEncodedString(DemoClientIdTrust.VERIFIER2_EXAMPLE_LEAF_PEM),
                CertificateDer.fromPEMEncodedString(DemoClientIdTrust.WALTID_VERIFIER_CA_PEM),
            ),
            trust.x509TrustAnchors,
        )
    }
}
