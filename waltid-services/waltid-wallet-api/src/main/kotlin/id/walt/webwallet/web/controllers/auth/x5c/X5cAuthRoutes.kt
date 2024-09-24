package id.walt.webwallet.web.controllers.auth.x5c

import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.ktor.server.application.*

const val x5cAuthPath = "auth/x5c"
val x5cAuthTags = listOf("X5C Authentication")

fun Application.x5cAuthRoutes() = webWalletRoute {
    X5CLoginController().routes("login")(this)
    X5CRegisterController().routes("register")(this)
    X5CLogoutController().routes("logout")(this)
}