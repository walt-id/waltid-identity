package id.walt.wallet2.server.handlers

import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeErrorDescriptions
import id.waltid.openid4vci.wallet.token.TokenRequestException
import io.ktor.http.HttpStatusCode

/** Maps known issuer descriptions to fixed messages for Wallet2 APIs. */
fun TokenRequestException.wallet2ErrorMessage(): String {
    val fallback = oauthError ?: "token_request_failed"
    if (statusCode != HttpStatusCode.BadRequest.value || oauthError != OAuthErrorCodes.INVALID_GRANT) return fallback

    return when (oauthErrorDescription) {
        PreAuthorizedCodeErrorDescriptions.WRONG_TX_CODE -> "Incorrect transaction code. Check the code and try again."
        PreAuthorizedCodeErrorDescriptions.TX_CODE_ATTEMPTS_EXHAUSTED ->
            "Too many incorrect transaction code attempts. Request a new credential offer from the issuer."
        PreAuthorizedCodeErrorDescriptions.INVALID_CODE ->
            "This credential offer is no longer usable. Request a new credential offer from the issuer."
        else -> fallback
    }
}
