package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.VerifiableCredentialIdentifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** VC login against a mock verifier2 on a local port. */
class VerifiableCredentialLoginTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** The mock verifier2: one session, whose status and presented subject the test sets. */
    private var status = "IN_USE"
    /** When set, the verifier refuses to open sessions with this status. */
    private var refuseWith: HttpStatusCode? = null
    private var presentedSubject = "did:key:holder-${Uuid.random()}"
    private var createdWith: JsonObject? = null

    private val port = java.net.ServerSocket(0).use { it.localPort }
    private val verifier = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { json() }
        routing {
            post("/verification-session/create") {
                createdWith = call.receive<JsonObject>()
                refuseWith?.let { call.respond(it, "refused"); return@post }
                call.respond(buildJsonObject {
                    put("sessionId", "verifier-session-1")
                    put("bootstrapAuthorizationRequestUrl", "openid4vp://authorize?request_uri=https%3A%2F%2Fverifier%2Frequest")
                })
            }
            get("/verification-session/verifier-session-1/info") {
                call.respond(buildJsonObject {
                    put("status", status)
                    put("statusReason", "test")
                    putJsonObject("presented_credentials") {
                        putJsonArray("badge") {
                            addJsonObject {
                                put("type", "vc-w3c_2")
                                putJsonObject("credentialData") { putJsonObject("credentialSubject") { put("id", presentedSubject) } }
                            }
                        }
                    }
                })
            }
        }
    }.start(wait = false)

    @AfterTest
    fun stop() = verifier.stop()

    private val flow = AuthFlow.fromConfig(
        """{"method": "vc", "success": true, "config": {"verifierUrl": "http://127.0.0.1:$port", "setup": {"flow_type": "cross_device"}}}"""
    )

    private fun vcTest(register: Boolean, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? id.walt.errors.HttpStatusError)?.status?.let(HttpStatusCode::fromValue) ?: HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("auth") {
                    authFlows(listOf(flow)) {
                        if (register) functionAmendments = mapOf(VerifiableCredential to mapOf(AuthMethodFunctionAmendments.Registration to { identifier ->
                            KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(Uuid.random().toString(), identifier as VerifiableCredentialIdentifier)
                        }))
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.start(): AuthSessionInformation {
        val started = json.decodeFromString<AuthSessionInformation>(client.post("/auth/vc/start").bodyAsText())
        assertEquals(AuthSessionStatus.CONTINUE_NEXT_STEP, started.status)
        assertEquals("openid4vp", (started.nextStep as AuthSessionNextStepRedirectData).url.protocol.name)
        assertEquals(buildJsonObject { put("flow_type", "cross_device") }, createdWith, "the configured setup goes to the verifier")
        return started
    }

    private suspend fun ApplicationTestBuilder.status(sessionId: String) = client.get("/auth/$sessionId/vc/status")

    @Test
    fun `a presented credential logs in the account of its subject`() = vcTest(register = false) {
        val accountId = Uuid.random().toString()
        ExampleAccountStore.addAccountIdentifierToAccount(accountId, VerifiableCredentialIdentifier("credentialSubject/id", presentedSubject))
        val started = start()

        status = "IN_USE"
        assertEquals(AuthSessionStatus.CONTINUE_NEXT_STEP, json.decodeFromString<AuthSessionInformation>(status(started.id).bodyAsText()).status)

        status = "SUCCESSFUL"
        val done = json.decodeFromString<AuthSessionInformation>(status(started.id).bodyAsText())
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        assertNotNull(done.token)
        assertEquals(accountId, KtorAuthnzManager.tokenHandler.getTokenAccountId(done.token))
    }

    @Test
    fun `an unknown subject registers only with a registration function`() = vcTest(register = false) {
        val started = start()
        status = "SUCCESSFUL"
        assertEquals(HttpStatusCode.NotFound, status(started.id).status)
    }

    @Test
    fun `an unknown subject is registered with a registration function`() = vcTest(register = true) {
        val started = start()
        status = "SUCCESSFUL"
        assertEquals(AuthSessionStatus.SUCCESS, json.decodeFromString<AuthSessionInformation>(status(started.id).bodyAsText()).status)
    }

    @Test
    fun `a failing verifier answers 502, not 500`() = vcTest(register = false) {
        refuseWith = HttpStatusCode.InternalServerError
        assertEquals(HttpStatusCode.BadGateway, client.post("/auth/vc/start").status)
    }

    @Test
    fun `a failed verification fails the step`() = vcTest(register = false) {
        val started = start()
        status = "FAILED"
        assertEquals(HttpStatusCode.Unauthorized, status(started.id).status)
    }
}
