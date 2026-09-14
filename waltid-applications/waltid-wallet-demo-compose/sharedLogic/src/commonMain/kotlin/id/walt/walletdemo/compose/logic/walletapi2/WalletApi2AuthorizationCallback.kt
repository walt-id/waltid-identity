package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.http.Url

internal sealed interface WalletApi2AuthorizationCallback {
    data class Code(val code: String) : WalletApi2AuthorizationCallback
    data object Denied : WalletApi2AuthorizationCallback
    data class Invalid(val message: String) : WalletApi2AuthorizationCallback
}

internal fun parseWalletApi2AuthorizationCallback(
    callbackUri: String,
    expectedState: String?,
    expectedRedirectUri: String,
): WalletApi2AuthorizationCallback {
    if (callbackUri.isBlank()) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback is missing")
    }
    val expected = expectedState?.takeIf { it.isNotBlank() }
        ?: return WalletApi2AuthorizationCallback.Invalid("Authorization session is missing state")
    val url = try {
        Url(callbackUri)
    } catch (_: Exception) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback is invalid")
    }
    if (url.fragment.isNotBlank()) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback must use query parameters")
    }
    if (!matchesRedirectTarget(expectedRedirectUri, url)) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback redirect URI does not match the issuance session")
    }
    val parameters = url.parameters
    if (callbackParameterNames.any { name -> parameters.getAll(name).orEmpty().size > 1 }) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback contains duplicate parameters")
    }
    val state = parameters["state"]
        ?: return WalletApi2AuthorizationCallback.Invalid("Authorization callback is missing state")
    if (!stateMatches(expected, state)) {
        return WalletApi2AuthorizationCallback.Invalid("Authorization callback state does not match the issuance session")
    }
    val error = parameters["error"]
    if (error != null) {
        return if (error == "access_denied") {
            WalletApi2AuthorizationCallback.Denied
        } else {
            WalletApi2AuthorizationCallback.Invalid("Authorization failed: $error")
        }
    }
    val code = parameters["code"]?.takeIf { it.isNotBlank() }
        ?: return WalletApi2AuthorizationCallback.Invalid("Authorization callback is missing code")
    return WalletApi2AuthorizationCallback.Code(code)
}

private val callbackParameterNames = listOf("code", "state", "error", "error_description", "error_uri", "iss")

private fun stateMatches(expected: String, actual: String): Boolean {
    if (expected.length != actual.length) return false
    var mismatch = 0
    for (index in expected.indices) {
        mismatch = mismatch or (expected[index].code xor actual[index].code)
    }
    return mismatch == 0
}

private fun matchesRedirectTarget(expectedRedirectUri: String, callback: Url): Boolean {
    val expected = try {
        Url(expectedRedirectUri)
    } catch (_: Exception) {
        return false
    }
    if (expected.fragment.isNotBlank()) return false
    if (expected.protocol != callback.protocol ||
        !expected.host.equals(callback.host, ignoreCase = true) ||
        expected.port != callback.port ||
        expected.normalizedPath() != callback.normalizedPath()
    ) {
        return false
    }
    val callbackBaseNames = callback.parameters.names() - callbackParameterNames.toSet()
    if (callbackBaseNames != expected.parameters.names()) return false
    return callbackBaseNames.all { name ->
        callback.parameters.getAll(name) == expected.parameters.getAll(name)
    }
}

private fun Url.normalizedPath(): String = encodedPath.ifBlank { "/" }
