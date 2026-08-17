package id.walt.wallet2.handlers

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import kotlin.test.assertFailsWith

class WalletIssuanceHandlerPersistenceCallbackTest {

    @Test
    fun `callback reports first credential when second persistence fails`() = runTest {
        val store = FailingCredentialStore(failAtAttempt = 2)
        val callbacks = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            WalletIssuanceHandler.pollDeferredFlow(
                wallet = Wallet(id = "wallet", credentialStores = listOf(store)),
                request = deferredRequest(),
                httpClient = credentialResponseClient(credentialCount = 2),
                onCredentialStored = { callbacks += it.id },
            ).toList()
        }

        assertEquals(1, store.stored.size)
        assertEquals(store.stored.map { it.id }, callbacks)
    }

    @Test
    fun `callback reports nothing when persistence fails before first credential`() = runTest {
        val store = FailingCredentialStore(failAtAttempt = 1)
        var callbacks = 0

        assertFailsWith<IllegalStateException> {
            WalletIssuanceHandler.pollDeferredFlow(
                wallet = Wallet(id = "wallet", credentialStores = listOf(store)),
                request = deferredRequest(),
                httpClient = credentialResponseClient(credentialCount = 2),
                onCredentialStored = { callbacks++ },
            ).toList()
        }

        assertEquals(0, store.stored.size)
        assertEquals(0, callbacks)
    }

    private fun deferredRequest() = PollDeferredRequest(
        deferredCredentialEndpoint = Url("https://issuer.example/deferred"),
        accessToken = "token",
        transactionId = "transaction",
    )

    private fun credentialResponseClient(credentialCount: Int) = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = """{"credentials":[${List(credentialCount) { "{\"credential\":$CREDENTIAL}" }.joinToString()}]}""",
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

    private companion object {
        const val CREDENTIAL = """{
            "@context":["https://www.w3.org/2018/credentials/v1"],
            "type":["VerifiableCredential"],
            "issuer":"did:example:issuer",
            "credentialSubject":{"id":"did:example:holder"}
        }"""
    }
}
