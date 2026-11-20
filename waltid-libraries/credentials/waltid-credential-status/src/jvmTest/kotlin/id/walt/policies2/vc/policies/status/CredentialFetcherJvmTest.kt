package id.walt.policies2.vc.policies.status

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialFetcherJvmTest {

    @Test
    fun `one-argument constructor is present on the JVM`() {
        CredentialFetcher::class.java.getConstructor(HttpClient::class.java)
    }

    @Test
    fun `CWT ttl shortens a generous HTTP max-age`() = runTest {
        var now = Instant.parse("2024-01-01T00:00:00Z")
        var fetches = 0
        val cwt = signedCwt(ttl = 1, n = 0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    fetches++
                    respond(
                        content = cwt,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/statuslist+cwt"),
                            HttpHeaders.CacheControl to listOf("max-age=600"),
                        ),
                    )
                }
            }
        }
        val fetcher = CredentialFetcher(client) { now }

        fetcher.fetch("https://status.example/list").getOrThrow()
        fetcher.fetch("https://status.example/list").getOrThrow()
        assertEquals(1, fetches)

        now += 2.seconds
        fetcher.fetch("https://status.example/list").getOrThrow()
        assertEquals(2, fetches)
    }

    @Test
    fun `CWT ttl does not cache when Cache-Control is missing`() = runTest {
        var fetches = 0
        val cwt = signedCwt(ttl = 43_200, n = 0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    fetches++
                    respond(
                        content = cwt,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/statuslist+cwt"),
                        ),
                    )
                }
            }
        }
        val fetcher = CredentialFetcher(client)
        fetcher.fetch("https://status.example/list").getOrThrow()
        fetcher.fetch("https://status.example/list").getOrThrow()
        assertEquals(2, fetches)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun signedCwt(ttl: Long, n: Int): ByteArray {
        val key = JWKKey.generate(KeyType.Ed25519)
        val payload = coseCompliantCbor.encodeToByteArray(CwtClaims(ttl = ttl, n = n))
        return CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = -8),
            payload = payload,
            signer = key.toCoseSigner(),
        ).toTagged()
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CwtClaims(
    @CborLabel(65534) val ttl: Long? = null,
    @CborLabel(1) val n: Int? = null,
)
