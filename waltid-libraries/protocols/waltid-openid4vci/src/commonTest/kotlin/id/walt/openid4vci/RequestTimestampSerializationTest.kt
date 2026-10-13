package id.walt.openid4vci

import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestTimestampSerializationTest {
    private val json = Json { encodeDefaults = false }
    private val client = DefaultClient(
        id = "test-client",
        redirectUris = emptyList(),
        grantTypes = emptySet(),
        responseTypes = emptySet(),
    )

    @Test
    fun `authorization request preserves its generated timestamp when defaults are omitted`() {
        val request = DefaultAuthorizationRequest(
            client = client,
            responseTypes = setOf("code"),
            redirectUri = null,
            state = null,
        )
        val encoded = json.encodeToString(request)

        assertEquals(request.requestedAt.toString(), json.parseToJsonElement(encoded).jsonObject["requestedAt"]?.jsonPrimitive?.content)
        assertEquals(request, json.decodeFromString<DefaultAuthorizationRequest>(encoded))
    }

    @Test
    fun `credential request preserves its generated timestamp when defaults are omitted`() {
        val request = DefaultCredentialRequest(
            client = client,
            credentialIdentifier = null,
            credentialConfigurationId = "test-credential",
            proofs = null,
            credentialResponseEncryption = null,
        )
        val encoded = json.encodeToString(request)

        assertEquals(request.requestedAt.toString(), json.parseToJsonElement(encoded).jsonObject["requested_at"]?.jsonPrimitive?.content)
        assertEquals(request, json.decodeFromString<DefaultCredentialRequest>(encoded))
    }
}
