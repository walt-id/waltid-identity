package id.walt.oid4vc.responses

import id.walt.oid4vc.data.HTTPDataObject
import id.walt.oid4vc.data.HTTPDataObjectFactory

data class AuthorizationCodeResponse private constructor(
    val code: String?,
    val state: String? = null,
    val error: String?,
    val errorDescription: String?,
    override val customParameters: Map<String, List<String>> = mapOf()
) : HTTPDataObject() {
    val isSuccess get() = error == null
    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            code?.let { put("code", listOf(it)) }
            state?.let { put("state", listOf(it)) }
            error?.let { put("error", listOf(it)) }
            errorDescription?.let { put("error_description", listOf(it)) }
            putAll(customParameters)
        }
    }

    companion object : HTTPDataObjectFactory<AuthorizationCodeResponse>() {
        private val knownKeys = setOf("code", "error", "error_description")
        override fun fromHttpParameters(parameters: Map<String, List<String>>): AuthorizationCodeResponse {
            return AuthorizationCodeResponse(
                parameters["code"]?.firstOrNull(),
                parameters["state"]?.firstOrNull(),
                parameters["error"]?.firstOrNull(),
                parameters["error_description"]?.firstOrNull(),
                parameters.filterKeys { !knownKeys.contains(it) }
            )
        }

        fun success(code: String, state: String? = null, customParameters: Map<String, List<String>> = mapOf()) =
            AuthorizationCodeResponse(code, state, null, null, customParameters)

        fun error(
            error: AuthorizationErrorCode,
            errorDescription: String? = null,
            customParameters: Map<String, List<String>> = mapOf()
        ) = AuthorizationCodeResponse(null, null, error.name, errorDescription, customParameters)
    }
}

enum class AuthorizationErrorCode {
    invalid_request,
    unauthorized_client,
    access_denied,
    unsupported_response_type,
    invalid_scope,
    server_error,
    temporarily_unavailable,
    invalid_client,
    vp_formats_not_supported,
    invalid_presentation_definition_uri,
    invalid_presentation_definition_reference
}
