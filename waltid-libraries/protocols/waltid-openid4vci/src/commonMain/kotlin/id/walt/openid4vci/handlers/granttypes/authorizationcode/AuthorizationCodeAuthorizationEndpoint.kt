@file:OptIn(ExperimentalEncodingApi::class)

package id.walt.openid4vci.handlers.granttypes.authorizationcode

import id.walt.openid4vci.handlers.authorization.AuthorizationEndpointHandler
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.authorization.AuthorizeResponse
import id.walt.openid4vci.responses.authorization.AuthorizeResponseResult
import korlibs.crypto.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

/**
 * Authorize endpoint handler that issues authorization codes. The handler:
 * 1. Grants requested scopes/audience (placeholder until consent is wired).
 * 2. Generates a code and persists the session via [AuthorizationCodeRepository].
 * 3. Returns redirect parameters containing the code and state.
 *
 * @param codeRepository repository used to persist and consume authorization codes
 * @param codeLifetimeSeconds lifetime of issued authorization codes in seconds
 * @param maxGenerateAttempts maximum retries if a generated code collides
 */
class AuthorizationCodeAuthorizationEndpoint(
    private val codeRepository: AuthorizationCodeRepository,
    private val codeLifetimeSeconds: Long = 300,
    private val maxGenerateAttempts: Int = 3,
) : AuthorizationEndpointHandler {

    override suspend fun handleAuthorizeEndpointRequest(
        authorizationRequest: AuthorizationRequest,
        session: Session,
    ): AuthorizeResponseResult {
        if (!authorizationRequest.responseTypes.contains("code")) {
            return AuthorizeResponseResult.Failure(
                OAuthError(
                    error = id.walt.openid4vci.errors.OAuthErrorCodes.UNSUPPORTED_RESPONSE_TYPE,
                    description = "Handler only supports response_type=code",
                ),
            )
        }

//         Redirect URI rules (RFC6749 §4.1.1/§4.1.2):
//         - Client must have at least one registered redirect URI.
//         - If the client has multiple, the request must include redirect_uri and it must match a registered one.
//         - If the client has exactly one registered URI and the request omits redirect_uri, we may use the registered one.
//         - If redirect_uri is present, it must be one of the registered URIs; otherwise reject.
        val redirectUri = authorizationRequest.redirectUri
            ?: authorizationRequest.client.redirectUris.firstOrNull()
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Client is missing redirect_uri"),
            )

        var updated = authorizationRequest
            .withRedirectUri(redirectUri)
            .grantScopes(authorizationRequest.requestedScopes)
            .grantAudience(authorizationRequest.requestedAudience)

        authorizationRequest.responseTypes.forEach { rt -> updated = updated.markResponseTypeHandled(rt) }

        val expiresAt = kotlin.time.Clock.System.now() + codeLifetimeSeconds.seconds

        val subject = session.subject?.takeIf { it.isNotBlank() }
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Session subject is required"),
            )

        val (code, _) = generateAndSaveUnique {
            DefaultAuthorizationCodeRecord(
                code = it,
                clientId = updated.client.id,
                redirectUri = redirectUri,
                grantedScopes = updated.grantedScopes.toSet(),
                grantedAudience = updated.grantedAudience.toSet(),
                session = session.copy().withSubject(subject).withExpiresAt(TokenType.AUTHORIZATION_CODE, expiresAt),
                expiresAt = expiresAt,
            )
        }

        val grantedScope = updated.grantedScopes.toSet()
        val scopeParam = grantedScope.takeIf { it.isNotEmpty() }?.joinToString(" ")

        return AuthorizeResponseResult.Success(
            AuthorizeResponse(
                redirectUri = redirectUri,
                code = code,
                state = authorizationRequest.state,
                scope = scopeParam,
                responseMode = authorizationRequest.responseMode,
                extraParameters = emptyMap(),
            ),
        )
    }

    private suspend fun generateAndSaveUnique(
        buildRecord: (String) -> DefaultAuthorizationCodeRecord,
    ): Pair<String, DefaultAuthorizationCodeRecord> {
        repeat(maxGenerateAttempts) { attempt ->
            val code = generateCode()
            try {
                val record = buildRecord(code)
                codeRepository.save(record)
                return code to record
            } catch (e: DuplicateCodeException) {
                if (attempt == maxGenerateAttempts - 1) throw e
            }
        }
        throw IllegalStateException("Failed to generate unique authorization code after $maxGenerateAttempts attempts")
    }

    private fun generateCode(): String {
        val bytes = ByteArray(33).also { SecureRandom.nextBytes(it) } //prevent padding
        return Base64.UrlSafe.encode(bytes)
    }
}
