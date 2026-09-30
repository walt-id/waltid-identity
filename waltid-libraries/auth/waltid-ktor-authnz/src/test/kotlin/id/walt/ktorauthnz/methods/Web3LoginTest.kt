package id.walt.ktorauthnz.methods

import id.walt.errors.StatusException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class Web3LoginTest {

    @BeforeTest
    fun setUp() {
        KtorAuthnzManager.accountStore = ExampleAccountStore
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
    }

    private fun web3Test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val flow = AuthFlow(method = "web3", success = true)
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? StatusException)?.status?.let(HttpStatusCode::fromValue) ?: HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                registerAuthenticationMethod(
                    Web3,
                    { AuthContext(implicitSessionGeneration = true, initialFlow = flow) },
                    mapOf(AuthMethodFunctionAmendments.Registration to { identifier ->
                        KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(Uuid.random().toString(), identifier as Web3Identifier)
                    })
                )
            }
        }
        block()
    }

    private val wallet = Keys.createEcKeyPair()
    private val address = "0x" + Keys.getAddress(wallet)

    private fun sign(challenge: String): String {
        val signature = Sign.signPrefixedMessage(challenge.toByteArray(), wallet)
        return Numeric.toHexString(signature.r + signature.s + signature.v)
    }

    private suspend fun ApplicationTestBuilder.signed(challenge: String, signature: String) =
        client.post("/web3/signed") {
            contentType(ContentType.Application.Json)
            setBody("""{"challenge":"$challenge","signed":"$signature","publicKey":"$address"}""")
        }

    @Test
    fun `a signed challenge logs in once`() = web3Test {
        val challenge = client.get("/web3/nonce").bodyAsText()
        val signature = sign(challenge)

        assertEquals(HttpStatusCode.OK, signed(challenge, signature).status)
        assertEquals(HttpStatusCode.Unauthorized, signed(challenge, signature).status, "replayed")
    }

    @Test
    fun `a short signature is refused, not a server error`() = web3Test {
        val challenge = client.get("/web3/nonce").bodyAsText()
        assertEquals(HttpStatusCode.Unauthorized, signed(challenge, "0x1234").status)
    }
}
