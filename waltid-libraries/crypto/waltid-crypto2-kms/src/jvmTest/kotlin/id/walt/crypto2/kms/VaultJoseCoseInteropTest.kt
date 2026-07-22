package id.walt.crypto2.kms

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.kms.vault.VaultCredential
import id.walt.crypto2.kms.vault.VaultCredentialResolver
import id.walt.crypto2.kms.vault.VaultTransitKeyProvider
import id.walt.crypto2.kms.vault.VaultTransitOptions
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaultJoseCoseInteropTest {
    @Test
    fun `Vault key signs and verifies compact JWS and COSE Sign1`() = runTest {
        val signature = ByteArray(64) { it.toByte() }
        var requestIndex = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (requestIndex++) {
                        0 -> respond("", HttpStatusCode.NoContent)
                        1 -> respondJson(
                            """{"data":{"type":"ecdsa-p256","latest_version":1,"keys":{"1":{"public_key":"-----BEGIN PUBLIC KEY-----\nMAMCAQE=\n-----END PUBLIC KEY-----"}}}}"""
                        )
                        2, 4 -> {
                            assertTrue(request.url.encodedPath.endsWith("/sign/vault-protocol-key"))
                            respondJson("""{"data":{"signature":"vault:v1:${Base64.Default.encode(signature)}"}}""")
                        }
                        3, 5 -> {
                            assertTrue(request.url.encodedPath.endsWith("/verify/vault-protocol-key"))
                            respondJson("""{"data":{"valid":true}}""")
                        }
                        else -> error("Unexpected Vault request")
                    }
                }
            }
        }
        val provider = VaultTransitKeyProvider(
            client,
            VaultCredentialResolver { VaultCredential.Token("token") },
        )
        val key = CryptoRuntime(emptyList(), listOf(provider)).generateManagedKey(
            VaultTransitKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("vault-protocol-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = VaultTransitOptions(
                    apiBaseUrl = "https://vault.example/v1",
                    credentialReference = CredentialReference("vault"),
                ).encode(),
            ),
        )
        val jws = CompactJws.sign("jose".encodeToByteArray(), key, JwsAlgorithm.ES256)
        assertEquals("jose", CompactJws.verify(jws, key, JwsAlgorithm.ES256).payload.decodeToString())
        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = "cose".encodeToByteArray(),
            key = key,
        )
        assertTrue(cose.verify(key, Cose.Algorithm.ES256))
        assertEquals(6, requestIndex)
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
