package id.waltid.openid4vci.wallet.clientauth

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClientAssertionBuilderTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private suspend fun signingKey(id: String = "wallet-static-key") = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )

    private fun part(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split(".")[index].decodeFromBase64Url().decodeToString()).jsonObject

    @Test
    fun carriesTheClaimsRfc7523Requires() = runTest {
        val assertion = ClientAssertionBuilder().buildAssertion(
            key = signingKey(),
            clientId = "wallet-conformance-test",
            audience = "https://issuer.example/",
            supportedAlgorithms = setOf("ES256"),
        )

        val header = part(assertion, 0)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        // kid lets a server holding several registered keys select the right one.
        assertEquals("wallet-static-key", header["kid"]?.jsonPrimitive?.content)

        val payload = part(assertion, 1)
        // RFC 7523 §3: iss and sub are both the client id for client authentication.
        assertEquals("wallet-conformance-test", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("wallet-conformance-test", payload["sub"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example/", payload["aud"]?.jsonPrimitive?.content)
        assertTrue(payload["jti"]?.jsonPrimitive?.content?.isNotBlank() == true)

        val issuedAt = payload["iat"]!!.jsonPrimitive.content.toLong()
        val expiresAt = payload["exp"]!!.jsonPrimitive.content.toLong()
        assertTrue(expiresAt > issuedAt, "exp must be after iat")
    }

    @Test
    fun neverEmbedsThePublicKey() = runTest {
        val assertion = ClientAssertionBuilder().buildAssertion(
            key = signingKey(),
            clientId = "client",
            audience = "https://issuer.example/",
        )

        // Unlike a DPoP proof, a client assertion must be verified against the key the
        // authorization server already holds from registration. Shipping the key would invite a
        // server to trust whatever the client sent.
        val header = part(assertion, 0)
        assertEquals(null, header["jwk"])
        assertEquals(null, header["x5c"])
    }

    @Test
    fun usesAFreshJtiPerAssertion() = runTest {
        val key = signingKey()
        val builder = ClientAssertionBuilder()
        val first = builder.buildAssertion(key, "client", "https://issuer.example/")
        val second = builder.buildAssertion(key, "client", "https://issuer.example/")

        // RFC 7523 §3 requires a unique jti; authorization servers reject reuse, which is why
        // TokenRequestBuilder regenerates the assertion for every request attempt.
        assertNotEquals(
            part(first, 1)["jti"]?.jsonPrimitive?.content,
            part(second, 1)["jti"]?.jsonPrimitive?.content,
        )
        assertNotEquals(first, second)
    }

    @Test
    fun honoursTheRequestedLifetime() = runTest {
        val assertion = ClientAssertionBuilder().buildAssertion(
            key = signingKey(),
            clientId = "client",
            audience = "https://issuer.example/",
            lifetime = 30.seconds,
        )

        val payload = part(assertion, 1)
        val issuedAt = payload["iat"]!!.jsonPrimitive.content.toLong()
        val expiresAt = payload["exp"]!!.jsonPrimitive.content.toLong()
        assertEquals(30, expiresAt - issuedAt)
    }

    @Test
    fun rejectsABlankClientId() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ClientAssertionBuilder().buildAssertion(signingKey(), "  ", "https://issuer.example/")
        }
    }

    @Test
    fun rejectsABlankAudience() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ClientAssertionBuilder().buildAssertion(signingKey(), "client", "")
        }
    }

    @Test
    fun rejectsANonPositiveLifetime() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ClientAssertionBuilder().buildAssertion(
                key = signingKey(),
                clientId = "client",
                audience = "https://issuer.example/",
                lifetime = 0.minutes,
            )
        }
    }

    @Test
    fun rejectsAnAlgorithmTheKeyCannotProduce() = runTest {
        // The authorization server advertises only RS256 via
        // token_endpoint_auth_signing_alg_values_supported, which a P-256 key cannot satisfy.
        assertFailsWith<IllegalArgumentException> {
            ClientAssertionBuilder().buildAssertion(
                key = signingKey(),
                clientId = "client",
                audience = "https://issuer.example/",
                supportedAlgorithms = setOf("RS256"),
            )
        }
    }
}
