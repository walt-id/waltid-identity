package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletIssuanceHandlerPersistenceCallbackTest {

    @Test
    fun `deferred polling observes pending issued and consumed issuer transaction`() = runTest {
        val store = FailingCredentialStore(failAtAttempt = Int.MAX_VALUE)
        val wallet = Wallet(id = "wallet", credentialStores = listOf(store))
        var requests = 0
        var transactionConsumed = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests++
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    assertEquals("""{"transaction_id":"transaction"}""", request.body.toByteArray().decodeToString())
                    val (body, status) = when {
                        requests == 1 -> """{"error":"issuance_pending","interval":1}""" to HttpStatusCode.BadRequest
                        transactionConsumed -> """{"error":"invalid_transaction_id"}""" to HttpStatusCode.BadRequest
                        else -> {
                            transactionConsumed = true
                            """{"credentials":[{"credential":$CREDENTIAL}]}""" to HttpStatusCode.OK
                        }
                    }
                    // Deliberately omit Cache-Control, as an external issuer may do.
                    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            assertTrue(WalletIssuanceHandler.pollDeferredFlow(wallet, deferredRequest(), httpClient = client).toList().isEmpty())
            assertEquals(1, requests)
            assertEquals(1, WalletIssuanceHandler.pollDeferredFlow(wallet, deferredRequest(), httpClient = client).toList().size)
            assertEquals(2, requests)
            assertTrue(transactionConsumed)
            val error = assertFailsWith<IllegalStateException> {
                WalletIssuanceHandler.pollDeferredFlow(wallet, deferredRequest(), httpClient = client).toList()
            }
            assertTrue(error.message.orEmpty().contains("invalid_transaction_id"))
            assertEquals(3, requests)
            assertEquals(1, store.stored.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun `callback reports first credential when second persistence fails`() = runTest {
        val keys = InMemoryKeyStore()
        val holders = List(2) { JWKKey.generate(KeyType.Ed25519) }
        val bindings = holders.map { CredentialHolderBinding(keyId = keys.addKey(it)) }
        val credentials = holders.map { batchTestCredential(it) }
        val store = FailingCredentialStore(failAtAttempt = 2)
        val callbacks = mutableListOf<String>()

        val failure = assertFailsWith<CredentialStorageException> {
            WalletIssuanceHandler.pollDeferredFlow(
                wallet = Wallet(id = "wallet", keyStores = listOf(keys), credentialStores = listOf(store)),
                request = deferredRequest().copy(holderBindings = bindings),
                httpClient = credentialResponseClient(credentials),
                onCredentialStored = { callbacks += it.id },
            ).toList()
        }

        assertEquals(1, store.stored.size)
        assertEquals(store.stored.map { it.id }, callbacks)
        assertEquals(callbacks, failure.outcome.storedCredentialIds)
        assertNull(failure.outcome.deferredCredentials.single().credentialConfigurationId)
    }

    @Test
    fun `callback reports nothing when persistence fails before first credential`() = runTest {
        val keys = InMemoryKeyStore()
        val holders = List(2) { JWKKey.generate(KeyType.Ed25519) }
        val bindings = holders.map { CredentialHolderBinding(keyId = keys.addKey(it)) }
        val credentials = holders.map { batchTestCredential(it) }
        val store = FailingCredentialStore(failAtAttempt = 1)
        var callbacks = 0

        val failure = assertFailsWith<CredentialStorageException> {
            WalletIssuanceHandler.pollDeferredFlow(
                wallet = Wallet(id = "wallet", keyStores = listOf(keys), credentialStores = listOf(store)),
                request = deferredRequest().copy(holderBindings = bindings),
                httpClient = credentialResponseClient(credentials),
                onCredentialStored = { callbacks++ },
            ).toList()
        }

        assertEquals(0, store.stored.size)
        assertEquals(0, callbacks)
        assertEquals(emptyList(), failure.outcome.storedCredentialIds)
        assertNull(failure.outcome.deferredCredentials.single().credentialConfigurationId)
    }

    private fun deferredRequest() = PollDeferredRequest(
        deferredCredentialEndpoint = Url("https://issuer.example/deferred"),
        accessToken = "token",
        transactionId = "transaction",
    )

    private fun credentialResponseClient(credentials: List<String>) = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = """{"credentials":[${credentials.map { "{\"credential\":${Json.encodeToString(it)}}" }.joinToString()}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private class FailingCredentialStore(private val failAtAttempt: Int) : WalletCredentialStore {
        val stored = mutableListOf<StoredCredential>()
        private var attempts = 0

        override suspend fun getCredential(id: String): StoredCredential? = stored.find { it.id == id }

        override suspend fun listCredentials(): Flow<StoredCredential> = stored.asFlow()

        override suspend fun addCredential(entry: StoredCredential) {
            check(++attempts != failAtAttempt) { "Persistence failed" }
            stored += entry
        }

        override suspend fun removeCredential(id: String): Boolean = stored.removeAll { it.id == id }
    }

}
