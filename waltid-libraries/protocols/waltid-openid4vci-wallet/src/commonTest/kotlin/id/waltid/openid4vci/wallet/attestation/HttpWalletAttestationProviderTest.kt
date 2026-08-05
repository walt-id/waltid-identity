package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.toPublicJwk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HttpWalletAttestationProviderTest {
    @Test
    fun cachesByClientAndPublicKeyIdentity() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    requestCount++
                    val publicJwk = Json.parseToJsonElement(request.bodyText()).jsonObject
                        .getValue("instancePublicKeyJwk").jsonObject
                    assertFalse("d" in publicJwk)
                    respond(
                        content = """{"clientAttestationJwt":"jwt-$requestCount","expiresAt":4102444800}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val firstKey = attestationTestKey("cached-first")
        val secondKey = attestationTestKey("cached-second")
        val firstPublic = requireNotNull(firstKey.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(firstKey.spec)
        val secondPublic = requireNotNull(secondKey.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(secondKey.spec)
        val provider = HttpWalletAttestationProvider("https://enterprise.example", "attester", httpClient = client)

        assertEquals("jwt-1", provider.getAttestationJwt(firstPublic, "client-a"))
        assertEquals("jwt-1", provider.getAttestationJwt(firstPublic, "client-a"))
        assertEquals("jwt-2", provider.getAttestationJwt(firstPublic, "client-b"))
        assertEquals("jwt-3", provider.getAttestationJwt(secondPublic, "client-b"))
        assertEquals(3, requestCount)
    }

    @Test
    fun rejectsPrivateJwkBeforeSendingRequest() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler {
                    requestCount++
                    respond(
                        content = """{"clientAttestationJwt":"jwt","expiresAt":4102444800}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val key = attestationTestKey("http-private-key")
        val privateJwk = requireNotNull(key.capabilities.privateKeyExporter).exportPrivateKey() as EncodedKey.Jwk
        val provider = HttpWalletAttestationProvider("https://enterprise.example", "attester", httpClient = client)

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
