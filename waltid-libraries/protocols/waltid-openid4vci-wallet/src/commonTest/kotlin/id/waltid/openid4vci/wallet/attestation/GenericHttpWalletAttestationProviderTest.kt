package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.toPublicJwk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GenericHttpWalletAttestationProviderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sendsPublicJwkInConfiguredRequestBody() = runTest {
        var capturedBody: JsonObject? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(
                        "https://wallet-provider.example.com/wallet-instance-attestation/jwk",
                        request.url.toString(),
                    )
                    capturedBody = json.parseToJsonElement(request.bodyText()).jsonObject
                    respond(
                        content = """{"walletInstanceAttestation":"attestation.jwt"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val key = attestationTestKey("generic-provider-key")
        val publicJwk = requireNotNull(key.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(key.spec)
        val provider = GenericHttpWalletAttestationProvider(
            attesterUrl = "https://wallet-provider.example.com/wallet-instance-attestation/jwk",
            httpClient = client,
            requestBodyTemplate = buildJsonObject { put("jwk", PUBLIC_JWK_PLACEHOLDER) },
        )

        assertEquals("attestation.jwt", provider.getAttestationJwt(publicJwk, "wallet-client"))
        val jwk = requireNotNull(capturedBody)["jwk"]!!.jsonObject
        assertEquals("EC", jwk["kty"]?.jsonPrimitive?.content)
        assertEquals("P-256", jwk["crv"]?.jsonPrimitive?.content)
        assertFalse("d" in jwk)
    }

    @Test
    fun rejectsPrivateJwkBeforeSendingRequest() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond(
                        content = """{"walletInstanceAttestation":"attestation.jwt"}""",
                        status = HttpStatusCode.OK,
                    )
                }
            }
        }
        val key = attestationTestKey("generic-private-key")
        val privateJwk = requireNotNull(key.capabilities.privateKeyExporter).exportPrivateKey() as EncodedKey.Jwk
        val provider = GenericHttpWalletAttestationProvider(
            attesterUrl = "https://wallet-provider.example.com/wallet-instance-attestation/jwk",
            httpClient = client,
            requestBodyTemplate = buildJsonObject { put("jwk", PUBLIC_JWK_PLACEHOLDER) },
        )

        assertFailsWith<IllegalArgumentException> {
            provider.getAttestationJwt(privateJwk, "wallet-client")
        }
        assertFailsWith<IllegalArgumentException> {
            provider.getAttestationJwt(privateJwk.copy(privateMaterial = false), "wallet-client")
        }
        assertEquals(0, requestCount)
    }

    private fun HttpRequestData.bodyText(): String = when (val requestBody = body) {
        is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
        is TextContent -> requestBody.text
        else -> error("Unsupported request body type: ${requestBody::class}")
    }
}
