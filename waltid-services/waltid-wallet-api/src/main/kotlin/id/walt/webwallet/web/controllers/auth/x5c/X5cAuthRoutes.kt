package id.walt.webwallet.web.controllers.auth.x5c

import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.application.*

const val x5cAuthPath = "auth/x5c"
val x5cAuthTags = listOf("X5C Authentication")

fun Application.x5cAuthRoutes() = webWalletRoute {
    route(x5cAuthPath, { tags = x5cAuthTags }) {
        X5CLoginController().routes("login")(this)
        X5CRegisterController().routes("register")(this)
        X5CLogoutController().routes("logout")(this)
    }
}