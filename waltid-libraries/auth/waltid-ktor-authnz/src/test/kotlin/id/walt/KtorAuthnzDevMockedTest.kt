@file:OptIn(ExperimentalTime::class)

package id.walt

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.devKtorAuthnzMocked
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.InMemoryKtorAuthNzTokenStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.time.ExperimentalTime

class KtorAuthnzDevMockedTest {

    @Test
    fun testUnMockedAuth() = testApplication {
        install(Authentication) {
            ktorAuthnz { }
        }

        routing {
            authenticate {
                get("/protected") {
                    call.respond("protected")
                }
            }
        }

        val resp = client.get("/protected")
        println(resp)

        check(!resp.status.isSuccess())
    }

    @Test
    fun testMockedAuth() = testApplication {
        install(Authentication) {
            devKtorAuthnzMocked("dev-auth", "dev-token") {
            }
        }

        val sessionStore = KtorAuthnzManager.sessionStore as InMemorySessionStore
        sessionStore.sessions["dev-session"] = AuthSession(
            id = "dev-session",
            status = AuthSessionStatus.SUCCESS,
            token = "dev-token",
            accountId = "11111111-1111-1111-1111-000000000000"
        )
        val tokenHandler = KtorAuthnzManager.tokenHandler as KtorAuthNzTokenHandler
        val tokenStore = tokenHandler.tokenStore as InMemoryKtorAuthNzTokenStore
        tokenStore.tokens["dev-token"] = "dev-session"

        routing {
            authenticate("dev-auth") {
                get("/protected") {
                    val acc = call.getAuthenticatedAccount()
                    call.respond("protected! you are: $acc")
                }
            }
        }

        val resp = client.get("/protected")
        println(resp)

        check(resp.status.isSuccess())
        check("11111111-1111-1111-1111-000000000000" in resp.bodyAsText())
    }
}
