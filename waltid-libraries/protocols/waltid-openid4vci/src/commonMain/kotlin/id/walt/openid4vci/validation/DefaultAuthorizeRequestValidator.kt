package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.append
import id.walt.openid4vci.argumentsOf
import id.walt.openid4vci.newArguments
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.OAuthError
import id.walt.openid4vci.request.AuthorizationRequest

class DefaultAuthorizeRequestValidator : AuthorizeRequestValidator {

    override fun validate(parameters: Map<String, String>): AuthorizeRequestResult {
        val clientId = parameters["client_id"]?.takeIf { it.isNotBlank() }
            ?: return AuthorizeRequestResult.Failure(
                OAuthError("invalid_client", "Missing client_id"),
            )

        val responseTypeRaw = parameters["response_type"]?.takeIf { it.isNotBlank() }
            ?: return AuthorizeRequestResult.Failure(
                OAuthError("invalid_request", "Missing response_type"),
            )

        val responseTypes = responseTypeRaw
            .split(" ")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }

        if (responseTypes.isEmpty() || responseTypes.any { it != "code" }) {
            return AuthorizeRequestResult.Failure(
                OAuthError("unsupported_response_type", "Only response_type=code is supported"),
            )
        }

        val redirect = parameters["redirect_uri"]?.takeIf { it.isNotBlank() }
        val client = DefaultClient(
            id = clientId,
            redirectUris = listOfNotNull(redirect),
            grantTypes = argumentsOf("authorization_code"),
        )

        val request = AuthorizationRequest()
        request.setClient(client)
        request.getRequestForm().addAll(parameters)

        val arguments = newArguments()
        responseTypes.forEach { arguments.append(it) }
        request.setResponseTypes(arguments)

        request.redirectUri = redirect
        request.state = parameters["state"]
        request.defaultResponseMode = ResponseModeType.QUERY
        request.responseMode = ResponseModeType.QUERY

        parameters["scope"]
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach(request::appendRequestedScope)

        parameters["audience"]
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach(request::appendRequestedAudience)

        return AuthorizeRequestResult.Success(request)
    }

}
