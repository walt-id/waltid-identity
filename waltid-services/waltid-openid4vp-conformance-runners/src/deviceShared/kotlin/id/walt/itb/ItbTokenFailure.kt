package id.walt.itb

import id.waltid.openid4vci.wallet.token.TokenRequestException
import kotlinx.serialization.json.*

/** The same bounded token classification is used by local reports and the native device channel. */
internal object ItbTokenFailure {
    private val protocolCodes = setOf(
        "invalid_request", "invalid_client", "invalid_grant", "unauthorized_client",
        "unsupported_grant_type", "invalid_scope", "invalid_dpop_proof",
        "use_dpop_nonce", "use_attestation_challenge", "unsafe_redirect",
    )

    fun reportCode(error: TokenRequestException): String =
        "token_endpoint_http_${error.statusCode}" +
            (allowedCode(error.oauthError)?.let { "_$it" } ?: "") +
            (if (error.nonOAuthErrorBody) "_non_oauth_body" else "")

    /** Descriptions, arbitrary error values and causes never cross the device boundary. */
    fun encode(error: TokenRequestException): JsonObject = buildJsonObject {
        put("statusCode", error.statusCode)
        allowedCode(error.oauthError)?.let { put("oauthError", it) }
        put("nonOAuthErrorBody", error.nonOAuthErrorBody)
    }

    /** Validate device-supplied fields and apply the allowlist again before reporting them. */
    fun decode(value: JsonObject): TokenRequestException {
        val status = (value["statusCode"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        require(status != null && (status == 0 || status in 100..999)) { "Invalid token failure status" }
        val nonOAuth = (value["nonOAuthErrorBody"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        require(nonOAuth != null) { "Invalid token failure body marker" }
        val code = value["oauthError"]?.let {
            require(it is JsonPrimitive && it.isString) { "Invalid token failure code" }
            allowedCode(it.content)
        }
        return TokenRequestException(statusCode = status, oauthError = code, nonOAuthErrorBody = nonOAuth)
    }

    private fun allowedCode(value: String?): String? = value?.takeIf(protocolCodes::contains)
}
