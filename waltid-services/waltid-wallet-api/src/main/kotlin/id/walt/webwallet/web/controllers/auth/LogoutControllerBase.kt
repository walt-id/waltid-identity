package id.walt.webwallet.web.controllers.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

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

    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Logout (delete session)"
        response { HttpStatusCode.OK to { description = "Logged out." } }
    }

    override suspend fun RoutingContext.execute() {
        clearUserSession()
        call.respond(HttpStatusCode.OK)
    }

    protected fun RoutingContext.clearUserSession() {
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
