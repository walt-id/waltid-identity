package id.walt.verifier2.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFailureSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun dcqlFulfillmentFailure_serializesWithTypeDiscriminator() {
        val failure: SessionFailure = SessionFailure.DcqlFulfillment(
            reason = "Required credential_set not satisfied",
            failure = DcqlFulfillmentFailure(
                missingQueryIds = emptyList(),
                unsatisfiedSets = listOf(UnsatisfiedSet(options = listOf(listOf("pid"), listOf("mdl")), required = true)),
                successfullyValidatedQueryIds = listOf("email"),
            ),
        )

        val encoded = json.encodeToString(SessionFailure.serializer(), failure)
        val parsed = json.parseToJsonElement(encoded).jsonObject

        assertEquals("dcql_fulfillment", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("Required credential_set not satisfied", parsed["reason"]?.jsonPrimitive?.content)

        val decoded = json.decodeFromString(SessionFailure.serializer(), encoded)
        assertEquals(failure, decoded)
    }

    @Test
    fun walletErrorResponse_serializesWithSnakeCaseErrorDescription() {
        val failure: SessionFailure = SessionFailure.WalletErrorResponse(
            reason = "Wallet returned OID4VP error response per §8.5",
            error = "access_denied",
            errorDescription = "User denied",
            state = "abc123",
        )
        val encoded = json.encodeToString(SessionFailure.serializer(), failure)
        val parsed = json.parseToJsonElement(encoded).jsonObject
        assertEquals("wallet_error_response", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("access_denied", parsed["error"]?.jsonPrimitive?.content)
        assertEquals("User denied", parsed["error_description"]?.jsonPrimitive?.content)
    }
}
