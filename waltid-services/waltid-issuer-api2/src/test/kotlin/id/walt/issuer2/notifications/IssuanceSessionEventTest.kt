package id.walt.issuer2.notifications

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IssuanceSessionEventTest {

    @Test
    fun `wire catalogue is explicit and has no duplicate names`() {
        val expected = setOf(
            "credential_offer_created",
            "credential_offer_retrieved",
            "pushed_authorization_request_succeeded",
            "pushed_authorization_request_failed",
            "authorization_request_succeeded",
            "authorization_request_failed",
            "token_request_failed",
            "token_request_authorization_code_succeeded",
            "token_request_authorization_code_failed",
            "token_request_pre_authorized_code_succeeded",
            "token_request_pre_authorized_code_failed",
            "token_request_refresh_token_succeeded",
            "token_request_refresh_token_failed",
            "nonce_request_succeeded",
            "nonce_request_failed",
            "credential_request_failed",
            "credential_request_sd_jwt_vc_succeeded",
            "credential_request_sd_jwt_vc_failed",
            "credential_request_w3c_vc_succeeded",
            "credential_request_w3c_vc_failed",
            "credential_request_mso_mdoc_succeeded",
            "credential_request_mso_mdoc_failed",
            "issuance_status_changed",
        )
        val actual = IssuanceSessionEvent.entries.map { it.value }

        assertEquals(expected, actual.toSet())
        assertEquals(actual.size, actual.distinct().size)
    }
}
