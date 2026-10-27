package id.walt.wallet2.walletprovider

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.wallet2.handlers.KeyAttestationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletProviderHttpClientTest {
    @Test
    fun `activates once and signs only the proof key`() = runTest {
        val requests = mutableListOf<String>()
        val verificationKey = softwareKey("provider")
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val body = (request.body as TextContent).text
                    requests += request.url.encodedPath + " " + body
                    val json = when {
                        request.url.encodedPath.endsWith("/units") ->
                            """{"unit_id":"wu-1","state":"operational","wia":"wia-token"}"""
                        request.url.encodedPath.endsWith("/wua") ->
                            """{"unit_id":"wu-1","state":"operational","ka":"ka-one"}"""
                        request.url.encodedPath.endsWith("/refresh") ->
                            """{"unit_id":"wu-1","state":"operational","ka":"ka-two"}"""
                        else -> error(request.url.encodedPath)
                    }
                    respond(json, HttpStatusCode.OK)
                }
            }
        }
        val provider = provider(client, verificationKey)
        val first = publicKey("holder-1")
        val second = publicKey("holder-2")

        assertEquals("wia-token", provider.getAttestationJwt(first, "wallet-client"))
        assertEquals("ka-one", provider.attest(request(first, "n-1")))
        assertEquals("ka-two", provider.attest(request(second, "n-2")))

        assertTrue(requests[0].contains("/v2/org.demo.wallet-provider/wallet-provider-api/units "))
        assertTrue(requests[1].contains(""""need":"ka""""))
        assertTrue(requests[2].contains("/refresh"))
        assertTrue(requests[2].contains(""""sign_attested_keys""""))
        assertEquals(verificationKey, provider.verificationKey)
    }

    @Test
    fun `refreshes the wallet instance attestation when the holder key changes`() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val body = (request.body as TextContent).text
                    requests += "${request.url.encodedPath} $body"
                    val json = if (request.url.encodedPath.endsWith("/refresh")) {
                        """{"unit_id":"wu-1","state":"operational","wia":"wia-rotated"}"""
                    } else {
                        """{"unit_id":"wu-1","state":"operational","wia":"wia-first"}"""
                    }
                    respond(json, HttpStatusCode.OK)
                }
            }
        }
        val provider = provider(client, softwareKey("provider"))
        assertEquals("wia-first", provider.getAttestationJwt(publicKey("holder-1"), "wallet-client"))
        assertEquals("wia-rotated", provider.getAttestationJwt(publicKey("holder-2"), "wallet-client"))
        assertTrue(requests[1].contains("/refresh"))
        assertTrue(requests[1].contains(""""rotate":"wia""""))
    }

    @Test
    fun `reports the provider status when activation fails`() = runTest {
        val provider = provider(
            HttpClient(MockEngine) { engine { addHandler { respond("missing", HttpStatusCode.NotFound) } } },
            softwareKey("provider"),
        )
        val failure = assertFailsWith<IllegalStateException> {
            provider.getAttestationJwt(publicKey("holder-1"), "wallet-client")
        }
        assertTrue(failure.message!!.contains("HTTP 404"))
        assertTrue(failure.message!!.contains("missing"))
    }

    private suspend fun softwareKey(id: String): Key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
    )

    private suspend fun publicKey(id: String): EncodedKey.Jwk {
        val key = softwareKey(id)
        return requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
    }

    private fun request(key: EncodedKey.Jwk, nonce: String) = KeyAttestationRequest(
        credentialIssuer = "https://issuer.example",
        proofKey = key,
        nonce = nonce,
        requirements = KeyAttestationsRequired(),
    )

    private fun provider(httpClient: HttpClient, verificationKey: Key) = WalletProviderHttpClient(
        baseUrl = "http://enterprise.example",
        servicePath = "org.demo.wallet-provider",
        httpClient = httpClient,
        verificationKeyFromJwt = { verificationKey },
    )

}
