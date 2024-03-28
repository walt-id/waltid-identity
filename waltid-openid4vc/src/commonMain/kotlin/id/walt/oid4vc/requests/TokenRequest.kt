package id.walt.oid4vc.requests

import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.HTTPDataObjectFactory

data class TokenRequest(
    val grantType: GrantType,
    val clientId: String? = null,
    val redirectUri: String? = null,
    val code: String? = null,
    val preAuthorizedCode: String? = null,
    val txCode: String? = null,
    val codeVerifier: String? = null,
    override val customParameters: Map<String, List<String>> = mapOf()
) : HTTPDataObject() {
    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            put("grant_type", listOf(grantType.value))
            clientId?.let { put("client_id", listOf(it)) }
            redirectUri?.let { put("redirect_uri", listOf(it)) }
            code?.let { put("code", listOf(it)) }
            preAuthorizedCode?.let { put("pre-authorized_code", listOf(it)) }
            txCode?.let { put("tx_code", listOf(it)) }
            codeVerifier?.let { put("code_verifier", listOf(it)) }
            putAll(customParameters)
        }
    }

    companion object : HTTPDataObjectFactory<TokenRequest>() {
        private val knownKeys =
            setOf("grant_type", "client_id", "redirect_uri", "code", "pre-authorized_code", "tx_code", "code_verifier")

        override fun fromHttpParameters(parameters: Map<String, List<String>>): TokenRequest {
            return TokenRequest(
                parameters["grant_type"]!!.first().let { GrantType.fromValue(it)!! },
                parameters["client_id"]?.firstOrNull(),
                parameters["redirect_uri"]?.firstOrNull(),
                parameters["code"]?.firstOrNull(),
                parameters["pre-authorized_code"]?.firstOrNull(),
                parameters["tx_code"]?.firstOrNull(),
                parameters["code_verifier"]?.firstOrNull(),
                parameters.filterKeys { !knownKeys.contains(it) }
            )
        }
    }
}
