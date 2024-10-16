package id.walt.ktorauthnz.sessions

import io.ktor.server.application.*

object SessionTokenCookieHandler {

    /** domain to set cookie on */
    var domain: String? = null
    /** secure value for cookie */
    var secure: Boolean = true

    fun ApplicationCall.setCookie(token: String) {
        response.cookies.append(
            name = "ktor-authnz-auth",
            value = token,
            httpOnly = true,
            path = "/",
            secure = secure,
            domain = domain,
            extensions = mapOf("SameSite" to "Strict")
        )
    }

}
