@file:OptIn(ExperimentalTime::class)

package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import io.klogging.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class KtorAuthnzE2ETest {

    init {
        KtorAuthnzManager.accountStore = ExampleAccountStore
    }

    private val log = logger("KtorAuthnzE2ETest")

    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        install(DefaultRequest) {
            host = "localhost"
            port = 8088
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun testProtected(token: String) {
        http.get("/protected") {
            bearerAuth(token)
        }.run {
            check(status.isSuccess())
            check(bodyAsText().contains("Hello"))
        }
    }

    suspend fun implicit1Test() = http.post("/auth/flows/global-implicit1/userpass") {
        setBody(mapOf("username" to "alice1", "password" to "123456"))
    }.also { log.info { "Explicit1 test: $it" } }.body<AuthSessionInformation>().run {
        check(status == AuthSessionStatus.SUCCESS)
        check(nextMethod == null)
        check(token != null)
        check(expiration != null && Clock.System.now() < expiration)
        testProtected(token)
    }

    suspend fun explicit2Test() {
        log.info { "Starting explicit2 session..." }
        val r1 = http.post("/auth/flows/global-explicit2/start").body<AuthSessionInformation>()
        check(r1.status == AuthSessionStatus.CONTINUE_NEXT_FLOW)
        check(r1.nextMethod == listOf("userpass"))

        log.info { "Continuing with step: ${r1.nextMethod.first()}..." }
        val r2 = http.post("/auth/flows/global-explicit2/${r1.id}/${r1.nextMethod.first()}") {
            setBody(mapOf("username" to "alice1", "password" to "123456"))
        }.body<AuthSessionInformation>()
        check(r2.status == AuthSessionStatus.CONTINUE_NEXT_FLOW)
        check(r2.nextMethod == listOf("totp"))

        // TOTP secret:
        log.info { "Continuing with step: ${r1.nextMethod.first()}..." }
        val totp = TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString("JBSWY3DPEHPK3PXP")).value
        val r3Str = http.post("/auth/flows/global-explicit2/${r2.id}/${r2.nextMethod.first()}") {
            setBody(mapOf("code" to totp))
        }.bodyAsText()
        log.info { "Body response: $r3Str" }
        System.out.flush()
        delay(1000)
        val r3 = Json.decodeFromString<AuthSessionInformation>(r3Str)

        check(r3.status == AuthSessionStatus.SUCCESS)
        check(r3.nextMethod == null)
        log.info { "Done with explicit2!" }
    }

    @Test
    fun testNonJwt() = runTest(timeout = 20.seconds) {
        val s = startExample(wait = false, jwt = false)

        implicit1Test()
        explicit2Test()

        s.stop()
    }

    @Test
    fun testJwt() = runTest(timeout = 20.seconds) {
        val s = startExample(wait = false, jwt = true)

        implicit1Test()
        explicit2Test()

        s.stop()
    }

}
