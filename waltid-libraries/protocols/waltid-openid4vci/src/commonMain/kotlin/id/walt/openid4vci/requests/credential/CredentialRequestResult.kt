package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.OAuthError

sealed class CredentialRequestResult {
    data class Success(val request: CredentialRequest) : CredentialRequestResult()
    data class Failure(val error: CredentialError) : CredentialRequestResult()
    data class OAuthFailure(val error: OAuthError) : CredentialRequestResult()

    fun isSuccess(): Boolean = this is Success
}
