@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.verifier.openid.TransactionDataUtils
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDataHashCheckSdJwtVPPolicyTest {

    private val policy = TransactionDataHashCheckSdJwtVPPolicy()

    @Test
    fun `succeeds when presentation hashes match requested transaction data`() = runTest {
        val transactionData = transactionData()
        val presentation = samplePresentation().copy(
            transactionDataHashes = TransactionDataUtils.calculateTransactionDataHashes(listOf(transactionData)),
            transactionDataHashesAlg = TransactionDataUtils.DEFAULT_HASH_ALGORITHM,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = listOf(transactionData)),
        )

        assertTrue(result.success)
    }

    @Test
    fun `fails when presentation hashes do not match requested transaction data`() = runTest {
        val transactionData = transactionData()
        val presentation = samplePresentation().copy(
            transactionDataHashes = listOf("invalid-hash"),
            transactionDataHashesAlg = TransactionDataUtils.DEFAULT_HASH_ALGORITHM,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = listOf(transactionData)),
        )

        assertFalse(result.success)
        assertTrue(result.errors.any { it.message?.contains("transaction_data_hashes", ignoreCase = true) == true })
    }

    private fun verificationContext(expectedTransactionData: List<String>? = null) = VerificationSessionContext(
        vpToken = "vp_token",
        expectedNonce = "3c04c5fc-9306-40fa-b544-0e00474ace09",
        expectedAudience = "x509_san_dns:test123",
        expectedOrigins = null,
        expectedTransactionData = expectedTransactionData,
        responseUri = null,
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        isSigned = true,
        isEncrypted = false,
        jwkThumbprint = null,
        isAnnexC = false,
        customData = null,
    )

    private suspend fun samplePresentation(): DcSdJwtPresentation =
        DcSdJwtPresentation.parse(
            "eyJ4NWMiOlsiTUlJQ0NUQ0NBYkNnQXdJQkFnSVVmcXlpQXJKWm9YN002MS80NzNVQVZpMi9VcGd3Q2dZSUtvWkl6ajBFQXdJd0tERUxNQWtHQTFVRUJoTUNRVlF4R1RBWEJnTlZCQU1NRUZkaGJIUnBaQ0JVWlhOMElFbEJRMEV3SGhjTk1qVXdOakF5TURZME1URXpXaGNOTWpZd09UQXlNRFkwTVRFeldqQXpNUXN3Q1FZRFZRUUdFd0pCVkRFa01DSUdBMVVFQXd3YlYyRnNkR2xrSUZSbGMzUWdSRzlqZFcxbGJuUWdVMmxuYm1WeU1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRVB6cDZlVlNBZFhFUnFBcDhxOE91REVobDJJTEdBYW9hUVhUSjJzRDJnNVhwM0NGUURNck1wUi9TUTBqdC9qVE9xRXhrMVBSempRNzlhS3BJc0pNMW1xT0JyRENCcVRBZkJnTlZIU01FR0RBV2dCVHhDbjJuV01yRTcwcVhiNjE0VTE0QndlWTJhekFkQmdOVkhRNEVGZ1FVeDVxa09MQzRscGwxeHBZWkdtRjlITHh0cDBnd0RnWURWUjBQQVFIL0JBUURBZ2VBTUJvR0ExVWRFZ1FUTUJHR0QyaDBkSEJ6T2k4dmQyRnNkQzVwWkRBVkJnTlZIU1VCQWY4RUN6QUpCZ2NvZ1l4ZEJRRUNNQ1FHQTFVZEh3UWRNQnN3R2FBWG9CV0dFMmgwZEhCek9pOHZkMkZzZEM1cFpDOWpjbXd3Q2dZSUtvWkl6ajBFQXdJRFJ3QXdSQUlnSFRhcDNjNnlDVU5oRFZmWldCUE1LajlkQ1daYnJNRTAza2g5TkpUYncxRUNJQXZWdnVHbGw5TzIxZVIxNlNrSkhIQUExcFBjb3ZoY1R2RjlmejljYzY2TSJdLCJ0eXAiOiJkYytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJfc2QiOlsiMFlvVXQxVXNjc24xLTEzQWI3dTkzSDJCdGI1aGNiZks4OXFXMGlETTdWTSIsIkwtUHZWYlNNbWVtQngyZFY0b0UzcEEyUXhCNVg3dU0tb2dGWFU0T1hYNmciLCJPSTZmMnQzT2RuZ04tQ1IwNXJzcENfUWZqZEVJWG13TkR5LVNBUlNjTHZVIiwiUXBla1hnYmowSm92UldEc2J5Wm0ySVpPQzRnM3o1aGRISXY4aWd3NkNSRSIsImFXSGhleEtiVEdodXVWb0RoMnhrSkdYdWttTHJ0UmtNV3A1ZnJqM1BKSkEiLCJ3enJsVmNGb3RCd1hvNFdGSlhhaVNYOHd6ckpvbXRzNFRmUG9TcTdTOW1NIiwieGwzMTVTam5WcGRhZlVKbFQtRXowVkt5aHpnX0s5WTVJY2RSdzlhWnBiRSJdLCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsImlzcyI6Imh0dHBzOi8vbG9jYWxob3N0LmVtb2JpeC5jby51azo4NDQzL3Rlc3QvMlVRejBIRUxnYXFBOWhoIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6ImUxaWZnNnlPUnpvbUtncEE5ZmhFY2xmTE9EX1l5aTdHWUVHRjVEc2dleXMiLCJ5IjoiWmNnWXBrbi1IVHNfZDJ0Q3dSeUE2T0lsN29STE1GTGtVOU92d2tkMmRfOCJ9fSwiZXhwIjoxNzYyMTQ3NjE0LCJpYXQiOjE3NjA5MzgwMTQsImFnZV9lcXVhbF9vcl9vdmVyIjp7Il9zZCI6WyItbHZ5MHlWQUxSUGUyMGk0UVZoaWlrUFZNdmlBUzJIMVl6VTJOMHpVcVVjIiwiN0hZR05qb2Jtd09YV01sRXBnSkZlN0loTnlPQV9jWWI3emdRdEZ3WmlsbyIsIkxaSTJnM3FRZWJTSE1Dajh2OEdxWUlyRDNERFdjWEhLcV9hazVJN0FpQU0iLCJtempqQjd1Z3FfSlJNTHZrcTFMZndlUmczZXlISURRVS1iNnFlNVZwcy04IiwieWtfN01WRGNaS2dBRE1FUloxZ2Rua2tGQmlYU29weTd3UmJKSGJnMk1pdyJdfX0.yOiR_tahOyeu8FNq_X0XMyCgtZ8JnXQ1w1_9YksCeV_ebRJGoWGd6dZxPFftO4ZUDkDfGqx_bt2IvXs7uKFqMA~WyJTQTVfTUhvZkJFclNWbWl6S245VktnIiwiZ2l2ZW5fbmFtZSIsIkplYW4iXQ~WyJHS3JLMGlWNXA5djNJWURTYnVUN21RIiwiZmFtaWx5X25hbWUiLCJEdXBvbnQiXQ~WyJSTm9pYVF0RmZGQTFaRDZPXy1sbzhnIiwiYmlydGhkYXRlIiwiMTk4MC0wNS0yMyJd~WyJRYW9zRHRreXRxazJvbHZuSFBjVFdBIiwiYWdlX2luX3llYXJzIiwiNDQiXQ~WyJsTXZ6MVBkN1lrZE5xYnFjeDJxbDV3IiwiMjEiLHRydWVd~WyI5a0Z5bGdXXzB0b01OVUZNREQ3S3ZnIiwiNjUiLGZhbHNlXQ~eyJ0eXAiOiJrYitqd3QiLCJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoiZ0NMeHF5ZXdpRE0wRzhCdEVTWjJFNzk1aTA4U1pIUjFTZUotSXNDeHZ1MCIsImF1ZCI6Ing1MDlfc2FuX2Ruczp0ZXN0MTIzIiwiaWF0IjoxNzYwOTM4MDE0LCJub25jZSI6IjNjMDRjNWZjLTkzMDYtNDBmYS1iNTQ0LTBlMDA0NzRhY2UwOSJ9.W07BdEyH_TMXxhdPzYwPaitcZ9SYxapQt8Q082UwyVxb8V4u1cIcJQ0tdAfhEaYk0--ymThiGsHuUwvjAH5xiw"
        ).getOrThrow()

    private fun transactionData(): String = buildJsonObject {
        put("type", "org.waltid.transaction-data.payment-authorization")
        put("credential_ids", buildJsonArray {
            add(JsonPrimitive("payment_credential"))
        })
        put("require_cryptographic_holder_binding", true)
        put("transaction_data_hashes_alg", buildJsonArray {
            add(JsonPrimitive(TransactionDataUtils.DEFAULT_HASH_ALGORITHM))
        })
        put("amount", "42.00")
        put("currency", "EUR")
        put("payee", "ACME Corp")
    }.toString().encodeToByteArray().encodeToBase64Url()
}
