package id.walt.webwallet.web.controllers.auth

import id.walt.webwallet.service.TokenBlacklistService
import id.walt.webwallet.web.model.RefreshTokenRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Clock
import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

class RefreshTokenController : Controller {
    private val logger = KotlinLogging.logger {}
    
    override fun routes(name: String): Route.() -> Route = {
        route("/auth/refresh", { tags = listOf("Authentication") }) {
            post(name, apiBuilder()) { execute() }
        }
    }

    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Refresh access token"
        description = "Use refresh token to get a new access token"
        request {
            body<RefreshTokenRequest> {
                required = true
                example("refresh token") {
                    value = RefreshTokenRequest(refreshToken = "eyJhb...")
                }
            }
        }
        response { 
            HttpStatusCode.OK to { 
                description = "New access token generated successfully"
                body<LoginResponseData> {
                    example("successful refresh") {
                        value = LoginResponseData(
                            id = kotlin.uuid.Uuid.random(),
                            token = "eyJhb...",
                            refreshToken = "eyJhb...",
                            expiresIn = 900,
                            tokenType = "Bearer"
                        )
                    }
                }
            }
            HttpStatusCode.Unauthorized to { description = "Invalid or expired refresh token" }
        }
    }

    override suspend fun RoutingContext.execute() {
        val request = call.receive<RefreshTokenRequest>()
        
        // Verify the refresh token
        val verificationResult = verifyToken(request.refreshToken)
        if (verificationResult.isFailure) {
            logger.warn { "Invalid refresh token provided" }
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid refresh token"))
            return
        }
        
        val userId = verificationResult.getOrThrow()
        logger.debug { "Refreshing token for user: $userId" }
        
        val now = Clock.System.now().toJavaInstant()
        
        // Create new access token
        val accessTokenPayload = kotlinx.serialization.json.Json.encodeToString(
            AuthTokenPayload(
                jti = kotlin.uuid.Uuid.random().toString(),
                sub = userId,
                iss = AuthKeys.issTokenClaim,
                aud = AuthKeys.audTokenClaim.takeIf { !it.isNullOrEmpty() }
                    ?: call.request.headers["Origin"] ?: "n/a",
                iat = now.epochSecond,
                nbf = now.epochSecond,
                exp = now.plus(AuthKeys.tokenLifetimeDuration).epochSecond,
            )
        )

        val accessToken = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()?.let {
            createRsaToken(it, accessTokenPayload)
        } ?: createHS256Token(accessTokenPayload)
        
        // Create new refresh token (rotate refresh token for security)
        val refreshTokenPayload = kotlinx.serialization.json.Json.encodeToString(
            AuthTokenPayload(
                jti = kotlin.uuid.Uuid.random().toString(),
                sub = userId,
                iss = AuthKeys.issTokenClaim,
                aud = AuthKeys.audTokenClaim.takeIf { !it.isNullOrEmpty() }
                    ?: call.request.headers["Origin"] ?: "n/a",
                iat = now.epochSecond,
                nbf = now.epochSecond,
                exp = now.plus(AuthKeys.refreshTokenLifetimeDuration).epochSecond,
            )
        )

        val newRefreshToken = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()?.let {
            createRsaToken(it, refreshTokenPayload)
        } ?: createHS256Token(refreshTokenPayload)
        
        // Blacklist the old refresh token
        val oldTokenExpiration = extractTokenExpiration(request.refreshToken)
        TokenBlacklistService.blacklistToken(request.refreshToken, oldTokenExpiration)
        
        // Update session with new tokens
        call.sessions.set(LoginTokenSession(accessToken, newRefreshToken))
        
        call.respond(LoginResponseData(
            id = kotlin.uuid.Uuid.parse(userId),
            token = accessToken,
            refreshToken = newRefreshToken,
            expiresIn = AuthKeys.tokenLifetime,
            tokenType = "Bearer"
        ))
    }
    
    private fun extractTokenExpiration(token: String): java.time.Instant {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
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

