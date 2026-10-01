package id.walt.mobile.test.backend

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoTestBackendContractTest {

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
