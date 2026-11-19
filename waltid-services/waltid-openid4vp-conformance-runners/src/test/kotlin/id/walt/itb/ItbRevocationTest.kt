package id.walt.itb

import id.walt.credentials.CredentialParser
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusTestIssuer
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItbRevocationTest {
    @Test
    fun sharedListRequiresTwoDistinctIndicesAndFreshClearObservations() = runBlocking<Unit> {
        val issuer = StatusTestIssuer.create()
        val wallet = wallet(
            issuer.credential("first", 0), issuer.credential("second", 1),
            issuer.credential("duplicate-index", 0), issuer.credential("different-list", 1, "https://other.example/list"),
        )
        val list = issuer.list(value = 0u)
        var requests = 0
        HttpClient(MockEngine { request ->
            requests++
            assertEquals(issuer.uri, request.url.toString())
            assertEquals("application/statuslist+jwt", request.headers[HttpHeaders.Accept])
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(list, headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt"))
        }).use { client ->
            WalletCredentialStatusService(wallet, client).use { status ->
                for (id in listOf("duplicate-index", "different-list")) {
                    val error = assertFailsWith<ItbWalletRejection> {
                        checkRevocationStatus(status, listOf("first", id), CredentialStatusKind.CHECKED)
                    }
                    assertEquals("status_expected_shared_list_with_distinct_indices", error.code)
                }
                assertEquals(0, requests)
                checkRevocationStatus(status, listOf("first", "second"), CredentialStatusKind.CHECKED)
                assertEquals(1, requests)
            }
        }
    }

    @Test
    fun alreadyRevokedRequiresTheWalletToObserveInvalid() = runBlocking<Unit> {
        val issuer = StatusTestIssuer.create()
        for (value in listOf(0u, 1u)) {
            val wallet = wallet(issuer.credential("issued", 1))
            val list = issuer.list(value = value)
            HttpClient(MockEngine {
                respond(list, headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt"))
            }).use { client ->
                WalletCredentialStatusService(wallet, client).use { status ->
                    if (value == 1u) {
                        checkRevocationStatus(status, listOf("issued"), CredentialStatusKind.INVALID)
                    } else {
                        assertEquals("status_checked", assertFailsWith<ItbWalletRejection> {
                            checkRevocationStatus(status, listOf("issued"), CredentialStatusKind.INVALID)
                        }.code)
                    }
                }
            }
        }
    }

    private suspend fun wallet(vararg credentials: StoredCredential) = Wallet(
        "itb-revocation", credentialStores = listOf(InMemoryCredentialStore().apply {
            credentials.forEach { addCredential(it) }
        }),
    )

    private suspend fun StatusTestIssuer.credential(id: String, index: Int, uri: String = this.uri): StoredCredential {
        val jwt = sign(buildJsonObject {
            put("iss", did)
            put("vct", "https://example.invalid/vct")
            put("exp", 4_000_000_000)
            putJsonObject("status") {
                putJsonObject("status_list") {
                    put("uri", uri)
                    put("idx", index)
                }
            }
        }, "dc+sd-jwt")
        return StoredCredential(id, CredentialParser.detectAndParse("$jwt~").second)
    }
}
