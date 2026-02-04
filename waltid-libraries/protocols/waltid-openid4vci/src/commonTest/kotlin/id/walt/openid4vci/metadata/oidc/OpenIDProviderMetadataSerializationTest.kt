package id.walt.openid4vci.metadata.oidc

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `serializes grant types with custom values`() {
        val metadata = OpenIDProviderMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            jwksUri = "https://issuer.example/jwks",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            subjectTypesSupported = setOf("public"),
            idTokenSigningAlgValuesSupported = setOf("RS256"),
            grantTypesSupported = setOf(
                GrantType.AuthorizationCode.value,
                GrantType.Custom("urn:example:grant").value,
            ),
        )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<OpenIDProviderMetadata>(encoded)

        assertEquals(
            setOf(GrantType.AuthorizationCode.value, "urn:example:grant"),
            decoded.grantTypesSupported,
        )
    }

    @Test
    fun `serialized metadata encodes grant types as json array`() {
        val metadata = OpenIDProviderMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            jwksUri = "https://issuer.example/jwks",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            subjectTypesSupported = setOf("public"),
            idTokenSigningAlgValuesSupported = setOf("RS256"),
            grantTypesSupported = setOf(
                GrantType.AuthorizationCode.value,
                GrantType.Custom("urn:example:grant").value,
            ),
        )

        val encoded = json.encodeToString(metadata)
        val jsonObject = json.parseToJsonElement(encoded).jsonObject

        val grantTypes = jsonObject["grant_types_supported"] as? JsonArray
        assertTrue(grantTypes != null)
        assertEquals(
            listOf(GrantType.AuthorizationCode.value, "urn:example:grant"),
            grantTypes.map { it.jsonPrimitive.content },
        )
    }
}
