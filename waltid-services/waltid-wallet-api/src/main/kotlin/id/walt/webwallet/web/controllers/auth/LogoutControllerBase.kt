package id.walt.webwallet.web.controllers.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*

abstract class LogoutControllerBase(
    private val path: String = defaultAuthPath,
    private val tagList: List<String> = defaultAuthTags,
) : Controller {
    protected val logger = KotlinLogging.logger {}

    override fun routes(name: String): Route.() -> Route = {
        route(path, { tags = tagList }) {
            post(name, apiBuilder()) { execute() }
        }
    }

    override fun apiBuilder(): OpenApiRoute.() -> Unit = {
        summary = "Logout (delete session)"
        response { HttpStatusCode.OK to { description = "Logged out." } }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.execute() {
        clearUserSession()
        call.respond(HttpStatusCode.OK)
    }

    protected fun PipelineContext<Unit, ApplicationCall>.clearUserSession() {
        call.sessions.get<LoginTokenSession>()?.let {
            logger.debug { "Clearing login token session" }
            call.sessions.clear<LoginTokenSession>()
        }

        call.sessions.get<OidcTokenSession>()?.let {
            logger.debug { "Clearing OIDC token token session" }
            call.sessions.clear<OidcTokenSession>()
        }
    }
}