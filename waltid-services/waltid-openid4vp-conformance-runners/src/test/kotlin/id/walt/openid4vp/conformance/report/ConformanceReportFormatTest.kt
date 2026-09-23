package id.walt.openid4vp.conformance.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
