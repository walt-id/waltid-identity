package id.walt.webwallet.web.controllers.auth

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.server.routing.*

interface Controller {
    fun routes(name: String): Route.() -> Route
    fun apiBuilder(): OpenApiRoute.() -> Unit
    suspend fun RoutingContext.execute()
}
