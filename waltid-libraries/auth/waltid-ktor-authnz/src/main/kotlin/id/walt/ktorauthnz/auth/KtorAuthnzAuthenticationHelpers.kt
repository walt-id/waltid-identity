package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*

fun PipelineContext<Unit, ApplicationCall>.getAuthToken(): String {
    val token = call.principal<UserIdPrincipal>()?.name
    check(token != null) { "No token for request principal" }

    return token
}

// TODO: switch to @OptIn instead of @Deprecated
@Deprecated("Externally provided JWT token cannot resolve to authenticated session")
suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession {
    val token = getAuthToken()

    return KtorAuthnzManager.tokenHandler.resolveTokenToSession(token)
}

suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedAccount(): String {
    val token = getAuthToken()

    return KtorAuthnzManager.tokenHandler.getTokenAccountId(token)
}
