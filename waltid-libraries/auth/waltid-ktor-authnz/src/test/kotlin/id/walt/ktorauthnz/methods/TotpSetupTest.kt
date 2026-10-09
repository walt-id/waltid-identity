package id.walt.ktorauthnz.methods

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepCustomData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class TotpSetupTest {

    private val accounts = InMemoryAccountStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val existingSecret = "JBSWY3DPEHPK3PXP"
    private val newUser = runBlocking {
        accounts.addAccount(UsernameIdentifier("new"), identifierData = mapOf(UserPass.id to UserPassStoredData("new-password")))
    }
    private val enrolledUser = runBlocking {
        accounts.addAccount(
            UsernameIdentifier("enrolled"),
            identifierData = mapOf(UserPass.id to UserPassStoredData("enrolled-password")),
            accountData = mapOf(TOTP.id to TOTPStoredData(existingSecret)),
        )
    }

    private val flow = AuthFlow.fromConfig(
        """{"method": "userpass", "continue": [
             {"method": "totp", "success": true},
             {"method": "totp-setup", "success": true, "config": {"issuer": "Example"}}]}"""
    )

    private fun setupTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing { route("auth") { authFlows(listOf(flow)) } }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null) = client.post(path) {
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.info(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    private fun code(secret: String) = TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString(secret)).value

    private suspend fun ApplicationTestBuilder.password(user: String) =
        post("/auth/userpass", """{"username": "$user", "password": "$user-password"}""").info().id

    @Test
    fun `an account without TOTP sets it up during login, and uses it from then on`() = setupTest {
        val session = password("new")
        val started = post("/auth/$session/totp-setup/start").info()
        val setup = (started.nextStep as AuthSessionNextStepCustomData).data.jsonObject
        val secret = setup["secret"]!!.jsonPrimitive.content
        assertEquals(
            "otpauth://totp/Example:$newUser?secret=$secret&issuer=Example&algorithm=SHA1&digits=6&period=30",
            setup["otpauth_uri"]!!.jsonPrimitive.content,
        )

        assertEquals(HttpStatusCode.Unauthorized, post("/auth/$session/totp-setup", """{"code": "000000"}""").status)
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/$session/totp-setup", """{"code": "${code(secret)}"}""").info().status)
        assertEquals(TOTPStoredData(secret), accounts.lookupStoredDataForAccount(newUser, TOTP))

        val next = password("new")
        assertEquals(HttpStatusCode.BadRequest, post("/auth/$next/totp-setup/start").status, "set up already")
    }

    @Test
    fun `an account with TOTP cannot replace it during login`() = setupTest {
        val session = password("enrolled")
        assertEquals(HttpStatusCode.BadRequest, post("/auth/$session/totp-setup/start").status)
        assertEquals(TOTPStoredData(existingSecret), accounts.lookupStoredDataForAccount(enrolledUser, TOTP))
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/$session/totp", """{"code": "${code(existingSecret)}"}""").info().status)
    }

    @Test
    fun `confirming needs a started setup`() = setupTest {
        val session = password("new")
        assertEquals(HttpStatusCode.BadRequest, post("/auth/$session/totp-setup", """{"code": "123456"}""").status)
    }
}
