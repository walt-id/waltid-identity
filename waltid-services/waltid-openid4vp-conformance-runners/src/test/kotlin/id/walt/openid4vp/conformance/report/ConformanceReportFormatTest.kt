package id.walt.openid4vp.conformance.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConformanceReportFormatTest {

    @Test
    fun compactWalletProducerIdAndModule() {
        val display = ConformanceReportFormat.displayName(
            "oid4vci-1_0-wallet-test-plan/" +
                "authorization_request_type=rar,client_auth_type=private_key_jwt," +
                "credential_format=sd_jwt_vc,fapi_profile=vci," +
                "vci_grant_type=authorization_code/" +
                "oid4vci-1_0-wallet-test-credential-issuance",
        )
        assertEquals("credential-issuance", display.title)
        assertEquals(
            "sd_jwt_vc · authorization_code · private_key_jwt · vci · rar",
            display.variant,
        )
        assertEquals("wallet", display.plan)
    }

    @Test
    fun leavesShortVerifierNamesAlone() {
        val display = ConformanceReportFormat.displayName("MdlBaseline")
        assertEquals("MdlBaseline", display.title)
        assertNull(display.variant)
        assertNull(display.plan)
    }

    @Test
    fun remediatesKnownSkipAndFailurePatterns() {
        assertTrue(
            ConformanceReportFormat.remediation(
                status = "skipped",
                error = "Not applicable to this variant: no request_method value applies to this variant",
            )!!.startsWith("Expected skip"),
        )
        assertTrue(
            ConformanceReportFormat.remediation(
                status = "skipped",
                error = "FAPI 2.0 client test: needs wallet-initiated issuance, which the harness cannot yet trigger",
            )!!.contains("wallet-initiated OpenID4VCI"),
        )
        assertTrue(
            ConformanceReportFormat.remediation(
                status = "timeout",
                error = "Module did not complete within 90 seconds",
            )!!.contains("7006/7007"),
        )
        assertTrue(
            ConformanceReportFormat.remediation(
                status = "failed",
                error = "audience mismatch",
            )!!.contains("Align `aud`"),
        )
        assertNull(ConformanceReportFormat.remediation(status = "passed", error = null))
    }
}
