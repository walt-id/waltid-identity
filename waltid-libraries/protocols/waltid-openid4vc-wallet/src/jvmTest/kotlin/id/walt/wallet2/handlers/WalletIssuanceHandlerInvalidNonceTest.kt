package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WalletIssuanceHandlerInvalidNonceTest {

    @Test
    fun retriesOnceWithFreshNonceAndNewProof() = runTest {
        var nonceRequests = 0
        var credentialRequests = 0
        val proofNonces = mutableListOf<String?>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        NONCE_ENDPOINT -> respondJson(
                            """{"c_nonce":"${if (++nonceRequests == 1) "stale-nonce" else "fresh-nonce"}"}"""
                        )

                        CREDENTIAL_ENDPOINT -> {
                            credentialRequests++
                            if (credentialRequests == 1) {
                                respondJson(
                                    """{"error":"invalid_nonce","error_description":"Proof nonce has expired"}""",
                                    HttpStatusCode.BadRequest,
                                )
                            } else {
                                respondJson("""{"credentials":[{"credential":"issued-credential"}]}""")
                            }
                        }

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = WalletIssuanceHandler.requestCredentialWithNonceRetry(
            request = fetchRequest(),
            nonceEndpoint = NONCE_ENDPOINT,
            httpClient = client,
            buildProof = { nonce ->
                proofNonces += nonce
                "proof-for-$nonce"
            },
        )

        assertEquals(listOf<String?>("stale-nonce", "fresh-nonce"), proofNonces)
        assertEquals(2, nonceRequests)
        assertEquals(2, credentialRequests)
        assertEquals("issued-credential", response.credentials?.single()?.credential.toString().removeSurrounding("\""))
    }

    @Test
    fun stopsAfterOneInvalidNonceRetry() = runTest {
        var nonceRequests = 0
        var credentialRequests = 0
        val proofNonces = mutableListOf<String?>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        NONCE_ENDPOINT -> respondJson("""{"c_nonce":"nonce-${++nonceRequests}"}""")
                        CREDENTIAL_ENDPOINT -> {
                            credentialRequests++
                            respondJson("""{"error":"invalid_nonce"}""", HttpStatusCode.BadRequest)
                        }
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val failure = try {
            WalletIssuanceHandler.requestCredentialWithNonceRetry(
                request = fetchRequest(),
                nonceEndpoint = NONCE_ENDPOINT,
                httpClient = client,
                buildProof = { nonce ->
                    proofNonces += nonce
                    "proof-for-$nonce"
                },
            )
            fail("Expected invalid_nonce to be propagated after the retry")
        } catch (error: CredentialEndpointException) {
            error
        }

        assertEquals(true, failure.isInvalidNonce)
        assertEquals(listOf<String?>("nonce-1", "nonce-2"), proofNonces)
        assertEquals(2, nonceRequests)
        assertEquals(2, credentialRequests)
    }

    @Test
    fun isolatedFetchPropagatesTypedInvalidNonceWithoutRetry() = runTest {
        var credentialRequests = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(CREDENTIAL_ENDPOINT, request.url.toString())
                    credentialRequests++
                    respondJson(
                        """{"error":"invalid_nonce","error_description":"Proof nonce has expired"}""",
                        HttpStatusCode.BadRequest,
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val failure = try {
            WalletIssuanceHandler.fetchCredential(fetchRequest(), client)
            fail("Expected invalid_nonce to be propagated")
        } catch (error: CredentialEndpointException) {
            error
        }

        assertEquals(HttpStatusCode.BadRequest.value, failure.statusCode)
        assertEquals(CredentialErrorCodes.INVALID_NONCE, failure.credentialError?.error)
        assertEquals("Proof nonce has expired", failure.credentialError?.description)
        assertEquals(1, credentialRequests)
    }

    /**
     * Server adapters account stored credentials, so the storing variant must report the batch size
     * before the first credential is persisted and report every credential it stored.
     */
    @Test
    fun storingFetchReportsBatchSizeBeforePersistingEachCredential() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondJson("""{"credentials":[{"credential":"$SD_JWT_CREDENTIAL"},{"credential":"$SD_JWT_CREDENTIAL"}]}""")
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val events = mutableListOf<String>()
        val store = RecordingCredentialStore(events)
        val wallet = Wallet(
            id = "isolated-fetch-accounting",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(store),
        )

        val result = WalletIssuanceHandler.fetchCredential(
            wallet = wallet,
            request = fetchRequest(storeInWallet = true),
            httpClient = client,
            beforeCredentialsStored = { events += "reserve:$it" },
            onCredentialStored = { events += "stored" },
        )

        assertEquals(2, result.rawCredentials.size)
        assertEquals(listOf("reserve:2", "persist", "stored", "persist", "stored"), events)
    }

    @Test
    fun statelessFetchStoresNothingAndReportsNoUsage() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respondJson("""{"credentials":[{"credential":"$SD_JWT_CREDENTIAL"}]}""") }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val events = mutableListOf<String>()
        val wallet = Wallet(
            id = "isolated-fetch-stateless",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(RecordingCredentialStore(events)),
        )

        val result = WalletIssuanceHandler.fetchCredential(
            wallet = wallet,
            request = fetchRequest(storeInWallet = false),
            httpClient = client,
            beforeCredentialsStored = { events += "reserve:$it" },
            onCredentialStored = { events += "stored" },
        )

        assertEquals(1, result.rawCredentials.size)
        assertEquals(emptyList(), events)
    }

    private fun fetchRequest(storeInWallet: Boolean = false) = FetchCredentialRequest(
        credentialEndpoint = Url(CREDENTIAL_ENDPOINT),
        accessToken = "access-token",
        credentialConfigurationId = "pid",
        storeInWallet = storeInWallet,
    )

    private class RecordingCredentialStore(private val events: MutableList<String>) : WalletCredentialStore {
        private val credentials = mutableListOf<StoredCredential>()

        override suspend fun addCredential(entry: StoredCredential) {
            events += "persist"
            credentials += entry
        }

        override suspend fun getCredential(id: String): StoredCredential? = credentials.firstOrNull { it.id == id }

        override suspend fun listCredentials(): Flow<StoredCredential> = credentials.toList().asFlow()

        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
    }

    private companion object {
        const val NONCE_ENDPOINT = "https://issuer.example/nonce"
        const val CREDENTIAL_ENDPOINT = "https://issuer.example/credential"
        const val SD_JWT_CREDENTIAL =
            "eyJhbGciOiJFZERTQSIsInR5cCI6ImRjK3NkLWp3dCJ9.eyJ2Y3QiOiJodHRwczovL2lzc3Vlci5leGFtcGxlL3BpZCIsInN1YiI6ImhvbGRlciJ9.c2lnbmF0dXJl~"
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
