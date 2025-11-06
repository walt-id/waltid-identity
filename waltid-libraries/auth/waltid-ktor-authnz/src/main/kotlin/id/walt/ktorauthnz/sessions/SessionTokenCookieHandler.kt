package id.walt.ktorauthnz.sessions

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.date.*

object SessionTokenCookieHandler {

    /** domain to set cookie on */
    var domain: String? = null

    /** secure value for cookie */
    var secure: Boolean = true

    var cookieName = "ktor-authnz-auth"

    fun ApplicationCall.setCookie(token: String) {
        response.cookies.append(
            name = cookieName,
            value = token,
            domain = domain,
            path = "/",
            httpOnly = true,
            secure = secure,
            extensions = mapOf("SameSite" to "Strict")
        )
    }

    fun RoutingCall.deleteCookie() {
        response.cookies.append(
            name = cookieName,
            value = "",
            maxAge = 0,
            expires = GMTDate(),
            domain = domain,
            path = "/",
            httpOnly = true,
            secure = secure,
            extensions = mapOf("SameSite" to "Strict")
        )
    }

}
