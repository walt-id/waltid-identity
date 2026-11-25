package id.walt.wallet2.server.handlers

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class Wallet2ListRouteTest {
    @Test
    fun `account lookup without an identity cannot list wallets`() = testApplication {
        val store = InMemoryWalletStore()
        store.saveWallet(Wallet(id = "other-wallet"))
        val resolver = object : WalletResolver {
            override val publicBaseUrl = Url("http://localhost")
            override val walletStore = store
        }
        application {
            install(ContentNegotiation) { json() }
            routing {
                registerWallet2Routes(resolver, getAccountId = { null })
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/wallet").status)
    }
}
