package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

fun ApplicationCall.getAuthToken(): String {
    val token = principal<UserIdPrincipal>()?.name
    requireNotNull(token) { "Missing token: No token for request principal" }

    return token
}

@RequiresOptIn("Consider that external sessions can be used by passing a JWT as token, which was not created by the internal TokenHandler")
annotation class ExternallyProvidedJWTCannotResolveToAuthenticatedSession()

@ExternallyProvidedJWTCannotResolveToAuthenticatedSession
suspend fun RoutingContext.getAuthenticatedSession(): AuthSession = KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())
@ExternallyProvidedJWTCannotResolveToAuthenticatedSession
suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession = KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())

suspend fun ApplicationCall.getAuthenticatedAccount(): String = KtorAuthnzManager.tokenHandler.getTokenAccountId(getAuthToken())

fun ApplicationCall.getEffectiveRequestAuthToken(): String? {
    val ktorAuthnzHeader = request.headers.get("ktor-authnz-auth")
    val cookie = request.cookies["ktor-authnz-auth"] ?: request.cookies["auth.token"]
    val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

    val effectiveToken = ktorAuthnzHeader ?: cookie ?: authHeader
    return effectiveToken
}
