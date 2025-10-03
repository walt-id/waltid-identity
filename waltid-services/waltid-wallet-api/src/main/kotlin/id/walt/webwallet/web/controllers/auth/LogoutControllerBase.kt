package id.walt.webwallet.web.controllers.auth

import id.walt.webwallet.service.TokenBlacklistService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.time.Clock

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

    protected suspend fun RoutingContext.clearUserSession() {
        // Blacklist access tokens before clearing sessions
        call.sessions.get<LoginTokenSession>()?.let { session ->
            logger.debug { "Blacklisting login token session" }
            // Extract expiration from token to set proper blacklist expiration
            val tokenExpiration = extractTokenExpiration(session.token)
            TokenBlacklistService.blacklistToken(session.token, tokenExpiration)
            session.refreshToken?.let { refreshToken ->
                val refreshTokenExpiration = extractTokenExpiration(refreshToken)
                TokenBlacklistService.blacklistToken(refreshToken, refreshTokenExpiration)
            }
            call.sessions.clear<LoginTokenSession>()
        }

        call.sessions.get<OidcTokenSession>()?.let { session ->
            logger.debug { "Blacklisting OIDC token session" }
            val tokenExpiration = extractTokenExpiration(session.token)
            TokenBlacklistService.blacklistToken(session.token, tokenExpiration)
            session.refreshToken?.let { refreshToken ->
                val refreshTokenExpiration = extractTokenExpiration(refreshToken)
                TokenBlacklistService.blacklistToken(refreshToken, refreshTokenExpiration)
            }
            call.sessions.clear<OidcTokenSession>()
        }
        
        // Also check for bearer token in Authorization header
        call.request.authorization()?.removePrefix("Bearer ")?.let { token ->
            logger.debug { "Blacklisting bearer token from Authorization header" }
            val tokenExpiration = extractTokenExpiration(token)
            TokenBlacklistService.blacklistToken(token, tokenExpiration)
        }
    }
    
    private fun extractTokenExpiration(token: String): java.time.Instant {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                // If not a JWT, set expiration to 1 hour from now
                Clock.System.now().toJavaInstant().plusSeconds(3600)
            } else {
                val payload = parts[1]
                val decoded = String(java.util.Base64.getUrlDecoder().decode(payload))
                val json = kotlinx.serialization.json.Json.parseToJsonElement(decoded).jsonObject
                val exp = json["exp"]?.jsonPrimitive?.content?.toLongOrNull()
                if (exp != null) {
                    java.time.Instant.ofEpochSecond(exp)
                } else {
                    Clock.System.now().toJavaInstant().plusSeconds(3600)
                }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to extract token expiration, using default: ${e.message}" }
            Clock.System.now().toJavaInstant().plusSeconds(3600)
        }
    }
}
