package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

/** The authenticated caller of this call, if the ktor-authnz provider authenticated it. */
fun ApplicationCall.authnzPrincipal(): KtorAuthnzPrincipal? = principal<KtorAuthnzPrincipal>()

/** The login token of this call - from the ktor-authnz principal, or a `UserIdPrincipal` holding the token. */
fun ApplicationCall.getAuthToken(): String {
    val token = authnzPrincipal()?.token ?: principal<UserIdPrincipal>()?.name
    requireNotNull(token) { "Missing token: No token for request principal" }

    return token
}

@RequiresOptIn("Consider that external sessions can be used by passing a JWT as token, which was not created by the internal TokenHandler")
annotation class ExternallyProvidedJWTCannotResolveToAuthenticatedSession()

@ExternallyProvidedJWTCannotResolveToAuthenticatedSession
suspend fun RoutingContext.getAuthenticatedSession(): AuthSession =
    KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())

@ExternallyProvidedJWTCannotResolveToAuthenticatedSession
suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession =
    KtorAuthnzManager.tokenHandler.resolveTokenToSession(call.getAuthToken())

suspend fun ApplicationCall.getAuthenticatedAccount(): String =
    authnzPrincipal()?.accountId ?: KtorAuthnzManager.tokenHandler.getTokenAccountId(getAuthToken())

fun ApplicationCall.getEffectiveRequestAuthToken(): String? {
    val cookieName = SessionTokenCookieHandler.cookieName
    val ktorAuthnzHeader = request.headers.get(cookieName)
    val cookie = request.cookies[cookieName] ?: request.cookies["auth.token"]
    val authHeader = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ')
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val effectiveToken = ktorAuthnzHeader ?: authHeader ?: cookie
    return effectiveToken
}
