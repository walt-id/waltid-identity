package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class KtorAuthnzE2ETest {

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
    }.also { println("Explicit1 test: $it") }.body<AuthSessionInformation>().run {
        check(status == AuthSessionStatus.OK)
        check(nextStep == null)
        check(token != null)
        testProtected(token!!)
    }

    suspend fun explicit2Test() {
        println("Starting explicit2 session...")
        val r1 = http.post("/auth/flows/global-explicit2/start").body<AuthSessionInformation>()
        check(r1.status == AuthSessionStatus.CONTINUE_NEXT_STEP)
        check(r1.nextStep == listOf("userpass"))

        println("Continuing with step: ${r1.nextStep!!.first()}...")
        val r2 = http.post("/auth/flows/global-explicit2/${r1.id}/${r1.nextStep!!.first()}") {
            setBody(mapOf("username" to "alice1", "password" to "123456"))
        }.body<AuthSessionInformation>()
        check(r2.status == AuthSessionStatus.CONTINUE_NEXT_STEP)
        check(r2.nextStep == listOf("totp"))

        // TOTP secret:
        println("Continuing with step: ${r1.nextStep!!.first()}...")
        val totp = TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString("JBSWY3DPEHPK3PXP")).value
        val r3 = http.post("/auth/flows/global-explicit2/${r2.id}/${r2.nextStep!!.first()}") {
            setBody(mapOf("code" to totp))
        }.body<AuthSessionInformation>()

        check(r3.status == AuthSessionStatus.OK)
        check(r3.nextStep == null)
        println("Done with explicit2!")
    }

    @Test
    fun test() = runTest(timeout = 10.seconds) {
        startExample(wait = false)

        implicit1Test()
        explicit2Test()
    }

}
