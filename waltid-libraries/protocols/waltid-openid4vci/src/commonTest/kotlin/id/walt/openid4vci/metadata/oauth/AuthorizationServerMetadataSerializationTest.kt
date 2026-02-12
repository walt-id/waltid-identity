package id.walt.openid4vci.metadata.oauth

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            authorizationDetailsTypesSupported = null,
        )

        val encoded = json.encodeToString(metadata)

        assertFalse(encoded.contains("response_modes_supported"))
        assertFalse(encoded.contains("scopes_supported"))
        assertFalse(encoded.contains("authorization_details_types_supported"))
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

    @Test
    fun `serializes grant types with custom values`() {
        val metadata = AuthorizationServerMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            grantTypesSupported = setOf(
                GrantType.AuthorizationCode.value,
                GrantType.Custom("urn:example:grant").value,
            ),
            authorizationDetailsTypesSupported = setOf("openid_credential"),
            preAuthorizedGrantAnonymousAccessSupported = true,
        )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<AuthorizationServerMetadata>(encoded)

        assertEquals(
            setOf(GrantType.AuthorizationCode.value, "urn:example:grant"),
            decoded.grantTypesSupported,
        )
        assertEquals(setOf("openid_credential"), decoded.authorizationDetailsTypesSupported)
        assertEquals(true, decoded.preAuthorizedGrantAnonymousAccessSupported)
    }

    @Test
    fun `serialized metadata encodes collections as json arrays`() {
        val metadata = AuthorizationServerMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            grantTypesSupported = setOf(
                GrantType.AuthorizationCode.value,
                GrantType.Custom("urn:example:grant").value,
            ),
            authorizationDetailsTypesSupported = setOf("openid_credential"),
            preAuthorizedGrantAnonymousAccessSupported = true,
        )

        val encoded = json.encodeToString(metadata)
        val jsonObject = json.parseToJsonElement(encoded).jsonObject

        val responseTypes = jsonObject["response_types_supported"] as? JsonArray
        val grantTypes = jsonObject["grant_types_supported"] as? JsonArray
        val authDetails = jsonObject["authorization_details_types_supported"] as? JsonArray

        assertTrue(responseTypes != null)
        assertTrue(grantTypes != null)
        assertTrue(authDetails != null)

        assertEquals(listOf(ResponseType.CODE.value), responseTypes.map { it.jsonPrimitive.content })
        assertEquals(
            listOf(GrantType.AuthorizationCode.value, "urn:example:grant"),
            grantTypes.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf("openid_credential"), authDetails.map { it.jsonPrimitive.content })
        assertEquals(
            true,
            jsonObject["pre-authorized_grant_anonymous_access_supported"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    @Test
    fun `custom parameters are serialized and deserialized`() {
        val metadata = AuthorizationServerMetadata(
            issuer = "https://issuer.example",
            authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token",
            responseTypesSupported = setOf(ResponseType.CODE.value),
            customParameters = mapOf(
                "custom_string" to JsonPrimitive("value"),
                "custom_object" to JsonObject(mapOf("nested" to JsonPrimitive("ok"))),
            ),
        )

        val encoded = json.encodeToString(metadata)
        val jsonObject = json.parseToJsonElement(encoded).jsonObject

        assertEquals("value", jsonObject["custom_string"]?.jsonPrimitive?.content)
        assertEquals("ok", jsonObject["custom_object"]?.jsonObject?.get("nested")?.jsonPrimitive?.content)

        val decoded = json.decodeFromString<AuthorizationServerMetadata>(encoded)
        assertEquals("value", decoded.customParameters?.get("custom_string")?.jsonPrimitive?.content)
        assertEquals(
            "ok",
            decoded.customParameters?.get("custom_object")?.jsonObject?.get("nested")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `custom parameters are captured from json input`() {
        val payload = """
            {
              "issuer": "https://issuer.example",
              "authorization_endpoint": "https://issuer.example/authorize",
              "token_endpoint": "https://issuer.example/token",
              "response_types_supported": ["code"],
              "custom_flag": true
            }
        """.trimIndent()

        val decoded = json.decodeFromString<AuthorizationServerMetadata>(payload)

        assertEquals(true, decoded.customParameters?.get("custom_flag")?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `custom parameters must not override standard fields`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationServerMetadata(
                issuer = "https://issuer.example",
                authorizationEndpoint = "https://issuer.example/authorize",
                tokenEndpoint = "https://issuer.example/token",
                responseTypesSupported = setOf(ResponseType.CODE.value),
                customParameters = mapOf("issuer" to JsonPrimitive("https://override.example")),
            )
        }
    }
}
