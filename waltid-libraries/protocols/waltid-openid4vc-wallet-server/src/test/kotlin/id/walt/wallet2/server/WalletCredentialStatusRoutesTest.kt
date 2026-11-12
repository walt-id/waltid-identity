package id.walt.wallet2.server

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.BuildVpTokenRequest
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.status.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletCredentialStatusRoutesTest {
    @Test
    fun routesPreserveOwnershipExposeLocalMetadataAndRejectChangedConsentBeforeBuilding() = testApplication {
        val store = InMemoryCredentialStore().apply { addCredential(statusCredential()) }
        val wallet = Wallet("status-wallet", credentialStores = listOf(store))
        val wallets = InMemoryWalletStore().apply {
            saveWallet(wallet)
            linkWalletToAccount("owner", wallet.id)
        }
        val resolver = object : WalletResolver {
            override val publicBaseUrl = Url("https://wallet.example")
            override val walletStore = wallets
        }
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) { exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest) } }
            routing { registerWallet2Routes(resolver, getAccountId = { request.headers["X-Test-Account"] }) }
        }
        val base = "/wallet/${wallet.id}"
        for (path in listOf("$base/credentials", "$base/credentials/identity", "$base/credential-status")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
            assertEquals(HttpStatusCode.Forbidden, client.get(path) { header("X-Test-Account", "other") }.status)
            val response = client.get(path) { header("X-Test-Account", "owner") }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertTrue(response.bodyAsText().contains("NOT_CHECKED"))
        }
        val metadata = client.get("$base/credentials") { header("X-Test-Account", "owner") }.bodyAsText()
        assertFalse(metadata.contains("private_claim"), "Metadata listing must not add raw credential contents")
        assertTrue(metadata.contains("advisoryRevision"), "Default-valued consent revisions must be on the wire")
        for (account in listOf(null, "other")) {
            val response = client.post("$base/credential-status/refresh") {
                account?.let { header("X-Test-Account", it) }
                contentType(ContentType.Application.Json); setBody("{}")
            }
            assertEquals(if (account == null) HttpStatusCode.Unauthorized else HttpStatusCode.Forbidden, response.status)
        }
        val foreign = client.post("$base/credential-status/refresh") {
            header("X-Test-Account", "owner"); contentType(ContentType.Application.Json)
            setBody("""{"credentials":[{"storeIndex":0,"credentialId":"foreign"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, foreign.status)
        val changed = client.post("$base/credentials/present/build-vp-token/status-reviewed") {
            header("X-Test-Account", "owner"); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(StatusReviewedBuildVpTokenRequest(
                BuildVpTokenRequest(Url("https://must-not-resolve.invalid/request"),
                    selectedCredentialOptions = listOf(PresentationCredentialSelection("identity", "identity"))),
                reviewedStatus = mapOf("identity" to "old-warning"),
            )))
        }
        assertEquals(HttpStatusCode.Conflict, changed.status, changed.bodyAsText())
        assertEquals("credential_status_review_changed", Json.parseToJsonElement(changed.bodyAsText()).jsonObject.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun localNoOpDoesNotConsumeRefreshAdmissionAndBackendOrGenerationCannotShareACooldown() = runTest {
        var issuerRequests = 0
        val client = HttpClient(MockEngine { issuerRequests++; respond("unexpected issuer request") })
        val store = InMemoryCredentialStore()
        // This custom adapter deliberately has no snapshot capability, so each admitted request discovers it anew.
        val wallet = Wallet("same-path", credentialStores = listOf(object : id.walt.wallet2.data.WalletCredentialStore by store {}))
        val requests = WalletCredentialStatusRequests { WalletCredentialStatusService(it, client, parentScope = backgroundScope) }
        val backend = Any()
        val scope = WalletCredentialStatusRequests.Scope(backend, wallet.id, "first")
        try {
            assertTrue(requests.refresh(wallet, scope, RefreshCredentialStatusesRequest()).isEmpty())
            store.addCredential(statusCredential())
            // This unsigned fixture must return UNSUPPORTED_PROOF without contacting its URL.
            val first = requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(mode = CredentialStatusRefreshMode.FORCE)).single()
            assertEquals(CredentialStatusRefreshOutcome.COMPLETED, first.outcome)
            assertEquals(CredentialStatusRefreshOutcome.DEFERRED,
                requests.refresh(wallet, scope, RefreshCredentialStatusesRequest()).single().outcome)
            for (isolated in listOf(WalletCredentialStatusRequests.Scope(Any(), wallet.id, "first"),
                WalletCredentialStatusRequests.Scope(backend, wallet.id, "second"))) {
                assertEquals(CredentialStatusRefreshOutcome.COMPLETED,
                    requests.refresh(wallet, isolated, RefreshCredentialStatusesRequest()).single().outcome)
            }
            assertEquals(0, issuerRequests)
        } finally { client.close() }
    }

    private fun statusCredential() = StoredCredential("identity", SdJwtCredential(
        dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
        credentialData = buildJsonObject {
            put("vct", "urn:test:identity"); put("private_claim", "not-for-metadata-list")
            put("status", buildJsonObject { put("status_list", buildJsonObject {
                put("idx", 0); put("uri", "https://must-not-fetch.invalid/status")
            }) })
        }, issuer = "https://issuer.invalid", signature = null, signed = null,
    ))
}
