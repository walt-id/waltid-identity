package id.walt.oid4vc.requests

import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.HTTPDataObjectFactory

sealed class TokenRequest() : HTTPDataObject() {
    abstract val grantType: GrantType
    abstract val clientId: String?
    abstract override val customParameters: Map<String, List<String>>

    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            put("grant_type", listOf(grantType.value))
            clientId?.let { put("client_id", listOf(it)) }
            putAll(getSpecificParameters())
            putAll(customParameters)
        }
    }

    protected abstract fun getSpecificParameters(): Map<String, List<String>>

    data class AuthorizationCode(
        override val clientId: String,
        val redirectUri: String? = null,
        val code: String,
        val codeVerifier: String? = null,
        override val customParameters: Map<String, List<String>> = mapOf()
    ) : TokenRequest() {
        override val grantType = GrantType.authorization_code

        override fun getSpecificParameters(): Map<String, List<String>> = buildMap {
            redirectUri?.let {put("redirect_uri", listOf(redirectUri)) }
            put("code", listOf(code))
            codeVerifier?.let { put("code_verifier", listOf(it)) }
        }
    }

    data class PreAuthorizedCode(
        val preAuthorizedCode: String,
        val txCode: String? = null,
        val userPIN: String? = null,
        override val clientId: String? = null,
        override val customParameters: Map<String, List<String>> = mapOf()
    ) : TokenRequest() {
        override val grantType = GrantType.pre_authorized_code

        override fun getSpecificParameters(): Map<String, List<String>> = buildMap {
            put("pre-authorized_code", listOf(preAuthorizedCode))
            txCode?.let { put("tx_code", listOf(it)) }
        }
    }



    companion object : HTTPDataObjectFactory<TokenRequest>() {
        private val knownKeys = setOf(
            "grant_type", "client_id", "redirect_uri", "code", "pre-authorized_code", "tx_code", "code_verifier"
        )

        override fun fromHttpParameters(parameters: Map<String, List<String>>): TokenRequest {
            val grantType = parameters["grant_type"]!!.first().let { GrantType.fromValue(it)!! }
            val customParams = parameters.filterKeys { !knownKeys.contains(it) }

            return  when (grantType) {
                GrantType.authorization_code -> AuthorizationCode(
                    clientId = parameters["client_id"]?.firstOrNull() ?: throw IllegalArgumentException("Missing 'client_id' for Authorization Code flow."),
                    code = parameters["code"]?.firstOrNull() ?: throw IllegalArgumentException("Missing 'code' for Authorization Code flow."),
                    redirectUri = parameters["redirect_uri"]?.firstOrNull(),
                    codeVerifier = parameters["code_verifier"]?.firstOrNull(),
                    customParameters = customParams
                )

                GrantType.pre_authorized_code -> PreAuthorizedCode(
                    preAuthorizedCode = parameters["pre-authorized_code"]?.firstOrNull() ?: throw IllegalArgumentException("Missing 'pre-authorized_code' for Pre-Authorized flow."),
                    txCode = parameters["tx_code"]?.firstOrNull(),
                    userPIN = parameters["user_pin"]?.firstOrNull(),
                    clientId = parameters["client_id"]?.firstOrNull(),
                    customParameters = customParams
                )

                GrantType.implicit -> TODO() // token endpoint should not be used with implicit flows.
            }
        }
    }
}
