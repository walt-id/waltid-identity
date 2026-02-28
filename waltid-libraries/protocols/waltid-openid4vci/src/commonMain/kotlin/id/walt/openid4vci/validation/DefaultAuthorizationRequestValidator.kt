package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import kotlinx.serialization.SerializationException

class DefaultAuthorizationRequestValidator : AuthorizationRequestValidator {

    override fun validate(parameters: Map<String, List<String>>): AuthorizationRequestResult {
        return try {
            // RFC6749 §4.1.1: client_id and response_type are required and must be single-valued.
            val clientId = parameters.requireSingle("client_id")
            val responseTypeRaw = parameters.requireSingle("response_type")

            val responseTypes = responseTypeRaw
                .split(" ")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }

            if (responseTypes.isEmpty() || responseTypes.any { it != "code" }) {
                return AuthorizationRequestResult.Failure(
                    OAuthError(
                        error = id.walt.openid4vci.errors.OAuthErrorCodes.UNSUPPORTED_RESPONSE_TYPE,
                        description = "Only response_type=code is supported",
                    ),
                )
            }

            // RFC6749 §4.1.1: redirect_uri is optional but, if supplied, must not repeat.
            parameters.rejectDuplicate("redirect_uri")
            val redirect = parameters.optionalSingle("redirect_uri")

            // RFC6749 §4.1.1: state is optional, but if provided must be single-valued.
            val state = parameters.optionalSingle("state")

            val client = DefaultClient(
                id = clientId,
                redirectUris = listOfNotNull(redirect),
                grantTypes = setOf("authorization_code"),
                responseTypes = setOf("code"),
            )

            // RFC6749 §4.1.1: scope is optional, if present may contain multiple space-delimited values.
            val requestedScopes = parameters.optionalAll("scope").toSet()

            val request = DefaultAuthorizationRequest(
                client = client,
                responseTypes = responseTypes.toMutableSet(),
                requestedScopes = requestedScopes.toMutableSet(),
                redirectUri = redirect,
                state = state,
                responseMode = ResponseModeType.QUERY,
                defaultResponseMode = ResponseModeType.QUERY,
                requestForm = parameters.toMutableMap(),
            )

            AuthorizationRequestResult.Success(request)
        } catch (e: SerializationException) {
            AuthorizationRequestResult.Failure(
                OAuthError(
                    error = id.walt.openid4vci.errors.OAuthErrorCodes.INVALID_REQUEST,
                    description = e.message,
                ),
            )
        }
    }
}
