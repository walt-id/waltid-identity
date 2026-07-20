package id.walt

import id.walt.ktorauthnz.auth.getEffectiveRequestAuthToken
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorAuthnzAuthenticationHelpersTest {

    @Test
    fun `explicit bearer token overrides ambient auth cookie`() = testApplication {
        application {
            routing {
                get("/token") { call.respondText(requireNotNull(call.getEffectiveRequestAuthToken())) }
            }
        }

        val response = client.get("/token") {
            cookie("ktor-authnz-auth", "cookie-token")
            header(HttpHeaders.Authorization, "Bearer bearer-token")
        }

        assertEquals("bearer-token", response.bodyAsText())
    }

    @Test
    fun `ktor auth header overrides bearer and cookie tokens`() = testApplication {
        application {
            routing {
                get("/token") { call.respondText(requireNotNull(call.getEffectiveRequestAuthToken())) }
            }
        }

        val response = client.get("/token") {
            cookie("ktor-authnz-auth", "cookie-token")
            header(HttpHeaders.Authorization, "Bearer bearer-token")
            header("ktor-authnz-auth", "explicit-token")
        }

        assertEquals("explicit-token", response.bodyAsText())
    }

    @Test
    fun `malformed authorization header falls back to auth cookie`() = testApplication {
        application {
            routing {
                get("/token") { call.respondText(requireNotNull(call.getEffectiveRequestAuthToken())) }
            }
        }

        val response = client.get("/token") {
            cookie("ktor-authnz-auth", "cookie-token")
            header(HttpHeaders.Authorization, "Basic invalid")
        }

        assertEquals("cookie-token", response.bodyAsText())
    }
}
