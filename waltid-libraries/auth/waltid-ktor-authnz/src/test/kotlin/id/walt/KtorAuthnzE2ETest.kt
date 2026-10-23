package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleServiceScope
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds


class KtorAuthnzE2ETest {

    private suspend fun testExample(jwt: Boolean) = ExampleServiceScope().use { resources ->
        val previousAccountStore = if (KtorAuthnzManager.isAccountStoreConfigured) KtorAuthnzManager.accountStore else null
        val previousSessionStore = KtorAuthnzManager.sessionStore
        val previousTokenHandler = KtorAuthnzManager.tokenHandler
        val previousExpiringStore = KtorAuthnzManager.expiringStore
        resources.onClose {
            previousAccountStore?.let { KtorAuthnzManager.accountStore = it }
            KtorAuthnzManager.sessionStore = previousSessionStore
            KtorAuthnzManager.tokenHandler = previousTokenHandler
            KtorAuthnzManager.expiringStore = previousExpiringStore
        }

        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        // Each mode must accept the current TOTP code independently.
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        val server = startExample(wait = false, jwt = jwt, host = "127.0.0.1", port = 0, setupLogging = false)
        resources.onClose { server.stop(0, 1_000) }
        val boundPort = server.engine.resolvedConnectors().single().port
        val http = resources.own(HttpClient {
            install(ContentNegotiation) { json() }
            install(DefaultRequest) {
                host = "127.0.0.1"
                port = boundPort
                contentType(ContentType.Application.Json)
            }
        })
        http.implicit1Test()
        http.explicit2Test()
    }

    private suspend fun HttpClient.testProtected(token: String) {
        get("/protected") {
            bearerAuth(token)
        }.run {
            check(status.isSuccess())
            check(bodyAsText().contains("Hello"))
        }
    }

    private suspend fun HttpClient.implicit1Test() = post("/auth/flows/global-implicit1/userpass") {
        setBody(mapOf("username" to "alice1", "password" to "123456"))
    }.body<AuthSessionInformation>().run {
        check(status == AuthSessionStatus.SUCCESS)
        check(nextMethod == null)
        check(token != null)
        check(expiration != null && Clock.System.now() < expiration)
        testProtected(token)
    }

    private suspend fun HttpClient.explicit2Test() {
        val r1 = post("/auth/flows/global-explicit2/start").body<AuthSessionInformation>()
        check(r1.status == AuthSessionStatus.CONTINUE_NEXT_FLOW)
        check(r1.nextMethod == listOf("userpass"))

        val r2 = post("/auth/flows/global-explicit2/${r1.id}/${r1.nextMethod.first()}") {
            setBody(mapOf("username" to "alice1", "password" to "123456"))
        }.body<AuthSessionInformation>()
        check(r2.status == AuthSessionStatus.CONTINUE_NEXT_FLOW)
        check(r2.nextMethod == listOf("totp"))

        val totp = TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString("JBSWY3DPEHPK3PXP")).value
        val r3Str = post("/auth/flows/global-explicit2/${r2.id}/${r2.nextMethod.first()}") {
            setBody(mapOf("code" to totp))
        }.bodyAsText()
        val r3 = Json.decodeFromString<AuthSessionInformation>(r3Str)

        check(r3.status == AuthSessionStatus.SUCCESS)
        check(r3.nextMethod == null)
    }

    @Test
    fun testNonJwt() = runTest(timeout = 60.seconds) {
        testExample(jwt = false)
    }

    @Test
    fun testJwt() = runTest(timeout = 60.seconds) {
        testExample(jwt = true)
    }

}
