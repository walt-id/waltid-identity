package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

// Client metadata is a stable protocol model. Its serializer's experimental
// implementation details must not require an opt-in from callers. The compiler
// warning policy makes a missing caller opt-in fail test compilation.
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
}
