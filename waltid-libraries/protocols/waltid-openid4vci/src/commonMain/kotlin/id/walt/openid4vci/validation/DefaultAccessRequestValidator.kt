package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.Session
import id.walt.openid4vci.argumentsOf
import id.walt.openid4vci.newArguments
import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.OAuthError
import id.walt.openid4vci.request.AccessTokenRequest

class DefaultAccessRequestValidator : AccessRequestValidator {

    override fun validate(parameters: Map<String, String>, session: Session): AccessRequestResult {
        val grantTypeRaw = parameters["grant_type"]?.takeIf { it.isNotBlank() }
            ?: return AccessRequestResult.Failure(
                OAuthError("invalid_request", "Missing grant_type"),
            )
        val grantType = GrantType.fromValue(grantTypeRaw)

        return when (grantType) {
            GrantType.AuthorizationCode -> validateAuthorizationCodeGrant(parameters, session)
            GrantType.PreAuthorizedCode -> validatePreAuthorizedCodeGrant(parameters, session)
            else -> AccessRequestResult.Failure(
                OAuthError(
                    "unsupported_grant_type",
                    "Supported grants: authorization_code, ${GrantType.PreAuthorizedCode.value}",
                ),
            )
        }
    }

    private fun validateAuthorizationCodeGrant(
        parameters: Map<String, String>,
        session: Session,
    ): AccessRequestResult {
        val clientId = parameters["client_id"]?.takeIf { it.isNotBlank() } ?: ""
//            ?: return AccessRequestResult.Failure(
//                OAuthError("invalid_client", "Missing client_id")
//            )

        val client = DefaultClient(
            id = clientId,
            redirectUris = listOfNotNull(parameters["redirect_uri"]?.takeIf { it.isNotBlank() }),
            responseTypes = argumentsOf("code"),
            grantTypes = argumentsOf(GrantType.AuthorizationCode.value),
        )

        val code = parameters["code"]?.takeIf { it.isNotBlank() }
            ?: return AccessRequestResult.Failure(
                OAuthError("invalid_request", "Missing authorization code"),
            )

        val request = AccessTokenRequest(session = session)
        request.setClient(client)
        request.appendGrantType(GrantType.AuthorizationCode.value)
        populateCommonRequestData(request, parameters)
        request.getRequestForm().set("code", code)

        return AccessRequestResult.Success(request)
    }

    private fun validatePreAuthorizedCodeGrant(
        parameters: Map<String, String>,
        session: Session,
    ): AccessRequestResult {
        val code = parameters["pre-authorized_code"]?.takeIf { it.isNotBlank() }
            ?: return AccessRequestResult.Failure(
                OAuthError("invalid_request", "Missing pre-authorized_code"),
            )

        val clientId = parameters["client_id"]?.takeIf { it.isNotBlank() }

        val client = DefaultClient(
            id = clientId ?: "",
            grantTypes = argumentsOf(GrantType.PreAuthorizedCode.value),
            responseTypes = newArguments(),
        )

        val request = AccessTokenRequest(session = session)
        request.setClient(client)
        request.appendGrantType(GrantType.PreAuthorizedCode.value)
        populateCommonRequestData(request, parameters)
        request.getRequestForm().set("pre-authorized_code", code)

        return AccessRequestResult.Success(request)
    }

    private fun populateCommonRequestData(request: AccessTokenRequest, parameters: Map<String, String>) {
        request.getRequestForm().addAll(parameters)

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
    }
}
