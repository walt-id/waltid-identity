package id.walt.webwallet.web.controllers.auth

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

interface Controller {
    fun routes(name: String): Route.() -> Route
    fun apiBuilder(): OpenApiRoute.() -> Unit
    suspend fun PipelineContext<Unit, ApplicationCall>.execute()
}