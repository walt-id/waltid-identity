package id.walt.webwallet.web.controllers.auth

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.server.routing.*

interface Controller {
    fun routes(name: String): Route.() -> Route
    fun apiBuilder(): RouteConfig.() -> Unit
    suspend fun RoutingContext.execute()
}
