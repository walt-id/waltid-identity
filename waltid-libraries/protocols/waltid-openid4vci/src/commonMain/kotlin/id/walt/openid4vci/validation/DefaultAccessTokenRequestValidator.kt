package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.serialization.SerializationException

class DefaultAccessTokenRequestValidator : AccessTokenRequestValidator {

    override fun validate(parameters: Map<String, List<String>>, session: Session): AccessTokenRequestResult {
        return try {
            // RFC6749 §4.1.3 / §5.2: grant_type is required and must be single-valued.
            val grantTypeRaw = parameters.requireSingle("grant_type")
            val grantType = GrantType.fromValue(grantTypeRaw)

            when (grantType) {
                GrantType.AuthorizationCode -> validateAuthorizationCodeGrant(parameters, session)
                GrantType.PreAuthorizedCode -> validatePreAuthorizedCodeGrant(parameters, session)
                else -> AccessTokenRequestResult.Failure(
                    OAuthError(
                        error = id.walt.openid4vci.errors.OAuthErrorCodes.UNSUPPORTED_GRANT_TYPE,
                        description = "Supported grants: authorization_code, ${GrantType.PreAuthorizedCode.value}",
                    ),
                )
            }
        } catch (e: SerializationException) {
            AccessTokenRequestResult.Failure(
                OAuthError(
                    error = id.walt.openid4vci.errors.OAuthErrorCodes.INVALID_REQUEST,
                    description = e.message,
                ),
            )
        }
    }

    private fun validateAuthorizationCodeGrant(
        parameters: Map<String, List<String>>,
        session: Session,
    ): AccessTokenRequestResult {
        // RFC6749 §4.1.3: client_id is optional; required only if client auth is not used.
        val clientId = parameters.optionalSingle("client_id")?.takeIf { it.isNotBlank() } ?: ""

        // RFC6749 §4.1.3: redirect_uri is required only if it was in the authorize request; if supplied, it must be single-valued.
        val redirectUri = parameters.optionalSingle("redirect_uri")?.takeIf { it.isNotBlank() }

        val client = DefaultClient(
            id = clientId,
            redirectUris = listOfNotNull(redirectUri),
            responseTypes = setOf("code"),
            grantTypes = setOf(GrantType.AuthorizationCode.value),
        )

        // RFC6749 §4.1.3: code is required and must be single-valued.
        val code = parameters.requireSingle("code").takeIf { it.isNotBlank() }
            ?: return AccessTokenRequestResult.Failure(
                OAuthError(
                    error = id.walt.openid4vci.errors.OAuthErrorCodes.INVALID_REQUEST,
                    description = "Missing authorization code",
                ),
            )

        // RFC6749 §3.3: scope is optional; if present, space-delimited and case-sensitive. Order not significant.
        val requestedScopes = parameters.optionalAll("scope").toSet()

        val request = DefaultAccessTokenRequest(
            client = client,
            grantTypes = setOf(GrantType.AuthorizationCode.value),
            requestedScopes = requestedScopes.toSet(),
            requestedAudience = emptySet(),
            grantedAudience = emptySet(),
            requestForm = parameters.toMap(),
            session = session,
        )

        return AccessTokenRequestResult.Success(request)
    }

    private fun validatePreAuthorizedCodeGrant(
        parameters: Map<String, List<String>>,
        session: Session,
    ): AccessTokenRequestResult {
        // OpenID4VCI (Pre-Authorized Code Flow): pre-authorized_code is required and must be single-valued.
        val code = parameters.requireSingle("pre-authorized_code").takeIf { it.isNotBlank() }
            ?: return AccessTokenRequestResult.Failure(
                OAuthError(
                    error = id.walt.openid4vci.errors.OAuthErrorCodes.INVALID_REQUEST,
                    description = "Missing pre-authorized_code",
                ),
            )

        // client_id remains optional; if provided, it must be single-valued.
        val clientId = parameters.optionalSingle("client_id")?.takeIf { it.isNotBlank() }

        val client = DefaultClient(
            id = clientId ?: "",
            redirectUris = emptyList(),
            grantTypes = setOf(GrantType.PreAuthorizedCode.value),
            responseTypes = emptySet(),
        )

        // RFC6749 §3.3: scope is optional; if present, space-delimited and case-sensitive. Order not significant.
        val requestedScopes = parameters.optionalAll("scope").toSet()

        val request = DefaultAccessTokenRequest(
            client = client,
            grantTypes = setOf(GrantType.PreAuthorizedCode.value),
            requestedScopes = requestedScopes.toSet(),
            requestedAudience = emptySet(),
            grantedAudience = emptySet(),
            requestForm = parameters.toMap(),
            session = session,
        )

        return AccessTokenRequestResult.Success(request)
    }
}
