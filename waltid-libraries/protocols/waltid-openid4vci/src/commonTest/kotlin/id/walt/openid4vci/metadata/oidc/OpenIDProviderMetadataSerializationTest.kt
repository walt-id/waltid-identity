package id.walt.openid4vci.metadata.oidc

import id.walt.openid4vci.ResponseType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class OpenIDProviderMetadataSerializationTest {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `rejects empty collections`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIDProviderMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                jwksUri = "https://issuer.example/jwks",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                subjectTypesSupported = setOf("public"),
                idTokenSigningAlgValuesSupported = setOf("RS256"),
                responseModesSupported = emptySet(),
            )
        }
    }

    @Test
    fun `omits null collections from json output`() {
        val metadata = OpenIDProviderMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            jwksUri = "https://issuer.example/jwks",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            subjectTypesSupported = setOf("public"),
            idTokenSigningAlgValuesSupported = setOf("RS256"),
            scopesSupported = null,
        )

        val encoded = json.encodeToString(metadata)

        assertFalse(encoded.contains("scopes_supported"))
        assertFalse(encoded.contains("response_modes_supported"))
        assertFalse(encoded.contains("claims_supported"))
    }

    @Test
    fun `deserializes when optional fields are omitted`() {
        val payload = """
            {
              "issuer": "https://issuer.example",
              "authorization_endpoint": "https://issuer.example/authorize",
              "token_endpoint": "https://issuer.example/token",
              "jwks_uri": "https://issuer.example/jwks",
              "response_types_supported": ["${ResponseType.CODE.value}"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": ["RS256"]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<OpenIDProviderMetadata>(payload)

        assertFalse(decoded.scopesSupported != null)
        assertFalse(decoded.responseModesSupported != null)
        assertFalse(decoded.claimsSupported != null)
    }

    @Test
    fun `deserialization rejects empty arrays`() {
        val payload = """
            {
              "issuer": "https://issuer.example",
              "authorization_endpoint": "https://issuer.example/authorize",
              "token_endpoint": "https://issuer.example/token",
              "jwks_uri": "https://issuer.example/jwks",
              "response_types_supported": ["${ResponseType.CODE.value}"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": ["RS256"],
              "claims_supported": []
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<OpenIDProviderMetadata>(payload)
        }
    }
}
