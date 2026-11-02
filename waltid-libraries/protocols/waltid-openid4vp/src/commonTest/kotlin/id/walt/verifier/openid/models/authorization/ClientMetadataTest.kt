package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Client metadata is a stable protocol model. Its serializer's experimental
// implementation details must not require an opt-in from callers.
class ClientMetadataTest {
    @Test
    fun metadataRoundTripsWithoutSerializationOptIn() {
        val metadata = ClientMetadata(
            clientName = "Verifier",
            clientNameI18n = mapOf("de" to "Pruefstelle"),
            redirectUris = listOf("https://verifier.example/callback"),
            additionalFields = mapOf("custom_field" to JsonPrimitive("value")),
        )

        val encoded = Json.encodeToString(metadata)
        assertEquals(metadata, Json.decodeFromString<ClientMetadata>(encoded))
    }

    @Test
    fun malformedJwksIsRejected() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ClientMetadata>("""{"jwks": []}""")
        }
    }
}
