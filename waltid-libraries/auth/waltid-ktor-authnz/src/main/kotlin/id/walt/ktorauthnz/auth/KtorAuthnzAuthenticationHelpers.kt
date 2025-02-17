package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.RoutingContext
import io.ktor.util.pipeline.*

fun ApplicationCall.getAuthToken(): String {
    val token = principal<UserIdPrincipal>()?.name
    check(token != null) { "No token for request principal" }

    return token
}

// TODO: switch to @OptIn instead of @Deprecated

@Deprecated("Externally provided JWT token cannot resolve to authenticated session")
suspend fun RoutingContext.getAuthenticatedSession(): AuthSession = KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())
@Deprecated("Externally provided JWT token cannot resolve to authenticated session")
suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession = KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())

suspend fun ApplicationCall.getAuthenticatedAccount(): String = KtorAuthnzManager.tokenHandler.getTokenAccountId(getAuthToken())
