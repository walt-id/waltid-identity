package id.walt.oid4vc.responses

import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.HTTPDataObjectFactory
import id.walt.oid4vc.data.ResponseMode

// Naming :/
@ConsistentCopyVisibility
data class AuthorizationCodeWithAuthorizationRequestResponse private constructor(
    val state: String?,
    val clientId: String?,
    val redirectUri: String?,
    val responseType: String?,
    val responseMode: ResponseMode?,
    val scope: Set<String>,
    val nonce: String?,
    val requestUri: String? = null,
    val request: String?,
    val presentationDefinition: String? = null,
    override val customParameters: Map<String, List<String>> = mapOf()
) : HTTPDataObject() {

    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            state?.let { put("state", listOf(it)) }
            clientId?.let { put("client_id", listOf(it)) }
            redirectUri?.let { put("redirect_uri", listOf(it)) }
            responseType?.let { put("response_type", listOf(it)) }
            responseMode?.let { put("response_mode", listOf(it.name)) }
            scope.let { put("scope", it.toList()) }
            nonce?.let { put("nonce", listOf(it)) }
            requestUri?.let { put("request_uri", listOf(it)) }
            request?.let { put("request", listOf(it)) }
            presentationDefinition?.let { put("presentation_definition", listOf(it)) }
            putAll(customParameters)
        }
    }

    companion object : HTTPDataObjectFactory<AuthorizationCodeWithAuthorizationRequestResponse>() {
        private val knownKeys = setOf("code", "error", "error_description")
        override fun fromHttpParameters(parameters: Map<String, List<String>>): AuthorizationCodeWithAuthorizationRequestResponse {
            return AuthorizationCodeWithAuthorizationRequestResponse(
                parameters["state"]?.firstOrNull(),
                parameters["client_id"]?.firstOrNull(),
                parameters["redirect_uri"]?.firstOrNull(),
                parameters["response_type"]?.firstOrNull(),
                parameters["response_mode"]?.firstOrNull()?.let { ResponseMode.valueOf(it) },
                parameters["scope"]!!.toSet(),
                parameters["nonce"]?.firstOrNull(),
                parameters["request_uri"]?.firstOrNull(),
                parameters["request"]?.firstOrNull(),
                parameters["presentation_definition"]?.firstOrNull(),
                parameters.filterKeys { !knownKeys.contains(it) }
            )
        }

        fun success(
            state: String,
            clientId: String,
            redirectUri: String,
            responseType: String,
            responseMode: ResponseMode,
            scope: Set<String>,
            nonce: String,
            requestUri: String?,
            request: String?,
            presentationDefinition: String?,
            customParameters: Map<String, List<String>> = mapOf()
        ) =
            AuthorizationCodeWithAuthorizationRequestResponse(
                state,
                clientId,
                redirectUri,
                responseType,
                responseMode,
                scope,
                nonce,
                requestUri,
                request,
                presentationDefinition,
                customParameters
            )
    }
}
