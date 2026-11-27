package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.handlers.CredentialIssuanceFailureReason
import id.walt.wallet2.server.handlers.IssuerTrustErrorResponse
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import id.walt.wallet2.trust.WalletIssuanceTrust
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WalletIssuanceTrustRoutesTest {

    private enum class Trust { OFF, REFUSES, UNAVAILABLE }

    @Test
    fun isolatedDeferredPollAnswersAnIssuanceTrustRejectionWithItsReasonAndStoresNothing() = testApplication {
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        issuer.createContext("/deferred") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val bytes = """{"credentials":[{"credential":"${unresolvableIssuerSdJwt()}"}]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        issuer.start()
        try {
            val writes = AtomicInteger()
            val registryCalls = AtomicInteger()
            val stored = InMemoryCredentialStore()
            var trust = Trust.OFF
            val wallet = Wallet("issuance-trust-routes", credentialStores = listOf(object : WalletCredentialStore by stored {
                override suspend fun addCredential(entry: StoredCredential) {
                    writes.incrementAndGet()
                    stored.addCredential(entry)
                }
            })).attachIssuanceTrust {
                when (trust) {
                    Trust.OFF -> null
                    Trust.REFUSES -> WalletIssuanceTrust(TrustDecisionEvaluator { registryCalls.incrementAndGet(); error("not reached") })
                    Trust.UNAVAILABLE -> error("private resolver detail")
                }
            }
            val wallets = InMemoryWalletStore()
            wallets.saveWallet(wallet)
            wallets.linkWalletToAccount("owner", wallet.id)
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json() }
                routing { registerWallet2Routes(resolver, getAccountId = { request.headers["X-Test-Account"] }) }
            }
            suspend fun poll() = client.post("/wallet/${wallet.id}/credentials/receive/deferred") {
                header("X-Test-Account", "owner")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("deferredCredentialEndpoint", "http://127.0.0.1:${issuer.address.port}/deferred")
                    put("transactionId", "transaction")
                    put("accessToken", "access-token")
                }.toString())
            }

            // Without issuance trust the same response is stored, so the rejections below are the gate's.
            val accepted = poll()
            assertEquals(HttpStatusCode.OK, accepted.status, accepted.bodyAsText())
            assertEquals(1, writes.get())

            trust = Trust.REFUSES
            val refused = poll()
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status, refused.bodyAsText())
            assertEquals(CredentialIssuanceFailureReason.ISSUER_NOT_TRUSTED,
                Json.decodeFromString<IssuerTrustErrorResponse>(refused.bodyAsText()).reason)
            assertEquals(0, registryCalls.get(), "An issuer key that cannot be resolved is refused before the registry is asked")

            trust = Trust.UNAVAILABLE
            val unavailable = poll()
            assertEquals(HttpStatusCode.BadGateway, unavailable.status, unavailable.bodyAsText())
            val body = Json.decodeFromString<IssuerTrustErrorResponse>(unavailable.bodyAsText())
            assertEquals(CredentialIssuanceFailureReason.ISSUER_TRUST_UNAVAILABLE, body.reason)
            assertFalse("private resolver detail" in body.message, body.message)

            assertEquals(1, writes.get())
        } finally {
            issuer.stop(0)
        }
    }

    /** A parseable SD-JWT VC whose issuer key nothing resolves: no x5c, no DID, no HTTPS issuer. */
    private fun unresolvableIssuerSdJwt(): String {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = base64Url.encode("""{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray())
        val payload = base64Url.encode("""{"iss":"urn:example:issuer","vct":"identity"}""".encodeToByteArray())
        return "$header.$payload.${base64Url.encode(ByteArray(64))}~"
    }
}
