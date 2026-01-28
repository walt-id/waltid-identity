package id.walt.openid4vci.metadata

import id.walt.openid4vci.ResponseType
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AuthorizationServerMetadataSerializationTest {

    private val json = Json

    @Test
    fun `rejects empty collections`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                responseModesSupported = emptySet(),
            )
        }
    }

    @Test
    fun `omits null collections from json output`() {
        val metadata = AuthorizationServerMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            responseModesSupported = null,
            scopesSupported = null,
        )

        val encoded = json.encodeToString(metadata)

        assertFalse(encoded.contains("response_modes_supported"))
        assertFalse(encoded.contains("scopes_supported"))
    }

    @Test
    fun `deserializes when optional fields are omitted`() {
        val payload = """
            {
              "issuer": "https://issuer.example",
              "authorization_endpoint": "https://issuer.example/authorize",
              "token_endpoint": "https://issuer.example/token",
              "response_types_supported": ["code"]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<AuthorizationServerMetadata>(payload)

        assertEquals(decoded.responseModesSupported, null)
        assertEquals(decoded.scopesSupported, null)
    }

    @Test
    fun `deserialization rejects empty arrays`() {
        val payload = """
            {
              "issuer": "https://issuer.example",
              "authorization_endpoint": "https://issuer.example/authorize",
              "token_endpoint": "https://issuer.example/token",
              "response_types_supported": ["code"],
              "response_modes_supported": []
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<AuthorizationServerMetadata>(payload)
        }
    }
}
