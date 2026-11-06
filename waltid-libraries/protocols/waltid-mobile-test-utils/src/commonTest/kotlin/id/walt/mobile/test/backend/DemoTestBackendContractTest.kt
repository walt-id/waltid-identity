package id.walt.mobile.test.backend

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoTestBackendContractTest {

    @Test
    fun scheduledAndRecurringVectorsKeepExactNumbersAndRefreshDates() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val scheduled = DemoPayment.SCHEDULED.payload(now)
        assertEquals("2026-11-07T12:00:00Z", scheduled["execution_date"]?.jsonPrimitive?.content)
        assertFalse("recurrence" in scheduled)
        val recurring = DemoPayment.RECURRING.payload(now)
        assertFalse("execution_date" in recurring)
        val terms = recurring.getValue("recurrence").jsonObject
        assertEquals("2027-11-06T12:00:00Z", terms["end_date"]?.jsonPrimitive?.content)
        val mit = terms.getValue("mit_options").jsonObject
        for ((key, expected) in mapOf("max_amount" to "9007199254740993.01", "apr" to "0.1234567890123456789",
            "initial_amount" to "-1.23", "min_amount" to "0", "initial_amount_number" to "2.0")) {
            assertEquals(expected, mit.getValue(key).jsonPrimitive.content)
            assertFalse(mit.getValue(key).jsonPrimitive.isString)
        }
        assertEquals(JsonPrimitive(false), mit["amount_variable"])
    }

    @Test
    fun dcApiVerifierSessionUsesCurrentAnnexDWireShape() {
        val payload = DemoTestBackend.buildDcApiVerifierSessionPayload(
            credentialQueries = listOf(buildJsonObject { put("id", JsonPrimitive("query")) }),
            expectedOrigins = listOf("android:apk-key-hash:test"),
            encryptedResponse = true,
        )

        assertEquals("dc_api_openid4vp", payload["flow_type"]?.jsonPrimitive?.content)
        assertTrue("core_flow" in payload)
        assertFalse("core" in payload)
        assertTrue("encrypted_response" in payload["core_flow"]!!.jsonObject)
        assertEquals(
            "android:apk-key-hash:test",
            payload["expectedOrigins"]!!.jsonArray.single().jsonPrimitive.content,
        )
    }

    @Test
    fun scaPaymentRequestsUseSignedRequestsAndKeepDefaultPolicies() {
        val scenario = DemoTestBackend.scaPaymentSdJwtScenario
        val payload = DemoTestBackend.buildDcApiVerifierSessionPayload(
            credentialQueries = listOf(scenario.verifierCredentialQuery),
            expectedOrigins = listOf("android:apk-key-hash:test"),
            signedRequest = true,
        )
        val core = payload.getValue("core_flow").jsonObject
        assertEquals(JsonPrimitive(true), core["signed_request"])
        assertEquals("x509_san_dns:verifier.example.com", core["clientId"]?.jsonPrimitive?.content)
        assertFalse("vp_policies" in core)
        val query = core.getValue("dcql_query").jsonObject.getValue("credentials").jsonArray.single().jsonObject
        assertEquals("sca_payment", query["id"]?.jsonPrimitive?.content)
        assertEquals("dc+sd-jwt", query["format"]?.jsonPrimitive?.content)
        assertEquals(3, query.getValue("claims").jsonArray.size)
    }

    @Test
    fun genericPaymentTransactionDataUsesCurrentFlatProfile() {
        val transactionData = DemoTestBackend.buildPaymentAuthorizationTransactionData("pid")

        assertEquals("ACME Corp", transactionData["merchant_name"]?.jsonPrimitive?.content)
        assertEquals("42.00", transactionData["amount"]?.jsonPrimitive?.content)
        assertEquals("EUR", transactionData["currency"]?.jsonPrimitive?.content)
        assertFalse("payee" in transactionData)
    }
}
