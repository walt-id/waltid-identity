package id.walt.openid4vci.requests.par

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pushed Authorization Request (PAR) as per RFC 9126 §2.1
 *
 * Represents the authorization request parameters pushed to the PAR endpoint.
 * All standard authorization request parameters may be included.
 */
@Serializable
data class PushedAuthorizationRequest(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("response_type")
    val responseType: String? = null,

    @SerialName("redirect_uri")
    val redirectUri: String? = null,

    @SerialName("scope")
    val scope: String? = null,

    @SerialName("state")
    val state: String? = null,

    @SerialName("code_challenge")
    val codeChallenge: String? = null,

    @SerialName("code_challenge_method")
    val codeChallengeMethod: String? = null,

    @SerialName("authorization_details")
    val authorizationDetails: String? = null,

    @SerialName("wallet_issuer")
    val walletIssuer: String? = null,

    @SerialName("user_hint")
    val userHint: String? = null,

    @SerialName("issuer_state")
    val issuerState: String? = null,

    /**
     * All other non-standard parameters
     */
    val additionalParameters: Map<String, String> = emptyMap(),
) {
    init {
        require(clientId.isNotBlank()) { "client_id is required" }
    }

    /**
     * Convert to full authorization parameters map
     */
    fun toAuthorizationParameters(): Map<String, List<String>> {
        val params = mutableMapOf<String, List<String>>()
        params["client_id"] = listOf(clientId)
        responseType?.let { params["response_type"] = listOf(it) }
        redirectUri?.let { params["redirect_uri"] = listOf(it) }
        scope?.let { params["scope"] = listOf(it) }
        state?.let { params["state"] = listOf(it) }
        codeChallenge?.let { params["code_challenge"] = listOf(it) }
        codeChallengeMethod?.let { params["code_challenge_method"] = listOf(it) }
        authorizationDetails?.let { params["authorization_details"] = listOf(it) }
        walletIssuer?.let { params["wallet_issuer"] = listOf(it) }
        userHint?.let { params["user_hint"] = listOf(it) }
        issuerState?.let { params["issuer_state"] = listOf(it) }
        additionalParameters.forEach { (key, value) ->
            params[key] = listOf(value)
        }
        return params
    }

    companion object {
        /**
         * Parse PAR request from form parameters (RFC 9126 §2.1)
         */
        fun fromParameters(params: Map<String, List<String>>): PushedAuthorizationRequest {
            val clientId = params["client_id"]?.firstOrNull()
                ?: throw IllegalArgumentException("client_id is required")

            val knownKeys = setOf(
                "client_id", "response_type", "redirect_uri", "scope", "state",
                "code_challenge", "code_challenge_method", "authorization_details",
                "wallet_issuer", "user_hint", "issuer_state"
            )

            val additionalParams = params
                .filterKeys { it !in knownKeys }
                .mapValues { (_, values) -> values.firstOrNull() ?: "" }

            return PushedAuthorizationRequest(
                clientId = clientId,
                responseType = params["response_type"]?.firstOrNull(),
                redirectUri = params["redirect_uri"]?.firstOrNull(),
                scope = params["scope"]?.firstOrNull(),
                state = params["state"]?.firstOrNull(),
                codeChallenge = params["code_challenge"]?.firstOrNull(),
                codeChallengeMethod = params["code_challenge_method"]?.firstOrNull(),
                authorizationDetails = params["authorization_details"]?.firstOrNull(),
                walletIssuer = params["wallet_issuer"]?.firstOrNull(),
                userHint = params["user_hint"]?.firstOrNull(),
                issuerState = params["issuer_state"]?.firstOrNull(),
                additionalParameters = additionalParams,
            )
        }
    }
}
