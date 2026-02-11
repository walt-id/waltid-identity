@file:OptIn(ExperimentalEncodingApi::class)

package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.AuthorizeEndpointHandler
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.core.AuthorizeResponse
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.core.OAuthError
import korlibs.crypto.SecureRandom
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.request.AuthorizationRequest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

/**
 * Authorize endpoint handler that issues authorization codes. The handler:
 * 1. Grants requested scopes/audience (placeholder until consent is wired).
 * 2. Generates a code and persists the session via [AuthorizationCodeRepository].
 * 3. Returns redirect parameters containing the code and state.
 */
class AuthorizationCodeAuthorizeHandler(
    private val codeRepository: AuthorizationCodeRepository,
    private val codeLifetimeSeconds: Long = 300,
    private val maxGenerateAttempts: Int = 3,
) : AuthorizeEndpointHandler {

    override suspend fun handleAuthorizeEndpointRequest(request: AuthorizationRequest, session: Session): AuthorizeResponseResult {
        if (!request.getResponseTypes().contains("code")) {
            return AuthorizeResponseResult.Failure(
                OAuthError("unsupported_response_type", "Handler only supports response_type=code"),
            )
        }

        val redirectUri = request.redirectUri
            ?: request.getClient().redirectUris.firstOrNull()
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Client is missing redirect_uri"),
            )

        request.redirectUri = redirectUri

        // For the skeleton we auto-grant everything that was requested and mark the response type as handled,
        // the behaviour expected once consent is complete.
        request.getRequestedScopes().forEach { request.grantScope(it) }
        request.getRequestedAudience().forEach { request.grantAudience(it) }
        request.getResponseTypes().forEach { request.setResponseTypeHandled(it) }

        val expiresAt = kotlin.time.Clock.System.now() + codeLifetimeSeconds.seconds

        val subject = session.getSubject()?.takeIf { it.isNotBlank() }
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Session subject is required"),
            )

        val (code, _) = generateAndSaveUnique {
            DefaultAuthorizationCodeRecord(
                code = it,
                clientId = request.getClient().id,
                redirectUri = redirectUri,
                grantedScopes = request.getGrantedScopes().toSet(),
                grantedAudience = request.getGrantedAudience().toSet(),
                session = session.cloneSession().apply {
                    setSubject(subject)
                    setExpiresAt(TokenType.AUTHORIZATION_CODE, expiresAt)
                },
                expiresAt = expiresAt,
            )
        }

        val parameters = buildMap {
            put("code", code)
            request.state?.let { put("state", it) }
            val grantedScope = request.getGrantedScopes().toSet()
            if (grantedScope.isNotEmpty()) {
                put("scope", grantedScope.joinToString(" "))
            }
        }

        return AuthorizeResponseResult.Success(
            AuthorizeResponse(
                redirectUri = redirectUri,
                parameters = parameters,
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
