package id.walt.ktorauthnz.methods

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData.TokenValidationData
import id.walt.ktorauthnz.methods.sessiondata.OidcTokenValidationPolicyData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OidcTokenValidationTest {
    @Test
    fun `discovery signing algorithms are persisted with session validation data`() {
        val configuration = OIDC.OpenIdConfiguration(
            issuer = "https://idp.example",
            authorizationEndpoint = "https://idp.example/authorize",
            tokenEndpoint = "https://idp.example/token",
            jwksUri = "https://idp.example/jwks",
            idTokenSigningAlgValuesSupported = listOf("ES256", "Ed25519"),
        )

        assertEquals(
            setOf("ES256", "Ed25519"),
            OidcTokenValidationPolicyData(configuration).idTokenSigningAlgorithms,
        )
        val legacy = TokenValidationData(configuration.jwksUri, configuration.issuer)
        assertEquals(configuration.issuer, legacy.copy().idpIss)
        assertEquals(
            legacy,
            Json.decodeFromString<TokenValidationData>(
                """{"idpJwksUrl":"https://idp.example/jwks","idpIss":"https://idp.example"}"""
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            OidcTokenValidationPolicyData(configuration.copy(idTokenSigningAlgValuesSupported = emptyList()))
        }
    }

    @Test
    fun `multi-audience token requires matching azp`() {
        val audiences = buildJsonArray {
            add(JsonPrimitive("client"))
            add(JsonPrimitive("api"))
        }

        OIDC.validateAudience(
            buildJsonObject {
                put("aud", audiences)
                put("azp", "client")
            },
            "client",
        )
        assertFailsWith<IllegalArgumentException> {
            OIDC.validateAudience(buildJsonObject { put("aud", audiences) }, "client")
        }
        assertFailsWith<IllegalArgumentException> {
            OIDC.validateAudience(
                buildJsonObject {
                    put("aud", audiences)
                    put("azp", "other")
                },
                "client",
            )
        }
    }

    @Test
    fun `single-audience token does not require azp`() {
        OIDC.validateAudience(buildJsonObject { put("aud", "client") }, "client")
        OIDC.validateAudience(
            buildJsonObject {
                put("aud", "client")
                put("azp", "client")
            },
            "client",
        )
        assertFailsWith<IllegalArgumentException> {
            OIDC.validateAudience(
                buildJsonObject {
                    put("aud", "client")
                    put("azp", "other")
                },
                "client",
            )
        }
    }

    @Test
    fun `same-kid signing key rotation refreshes JWKS once`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val staleKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("stale"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val rotatedKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("rotated"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val token = CompactJws.sign(
            payload = "{}".encodeToByteArray(),
            key = rotatedKey,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject { put("kid", "same-kid") },
        )
        val refreshes = mutableListOf<Boolean>()

        val payload = OIDC.verifyOidcSignatureWithRefresh(token, JwsAlgorithm.ES256) { forceRefresh ->
            refreshes += forceRefresh
            if (forceRefresh) rotatedKey else staleKey
        }

        assertTrue(payload.contentEquals("{}".encodeToByteArray()))
        assertEquals(listOf(false, true), refreshes)
    }
}
