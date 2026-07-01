package id.walt.openid4vci.clientauth

import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders

interface ClientAuthenticationServiceMethod {
    val name: String

    suspend fun authenticate(
        endpoint: ClientAuthenticationEndpoint,
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
        context: ClientAuthenticationContext,
    ): ClientAuthenticationResult
}

object ClientAuthenticationMethods {
    const val ATTEST_JWT_CLIENT_AUTH = "attest_jwt_client_auth"
    const val PRIVATE_KEY_JWT = "private_key_jwt"
    const val CLIENT_SECRET_BASIC = "client_secret_basic"
    const val CLIENT_SECRET_POST = "client_secret_post"
}

object ClientAuthenticationMethodDetector {
    fun detectRequestedMethods(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
    ): Set<String> = buildSet {
        if (
            headers.oauthHeaderValues(ClientAttestationHeaders.CLIENT_ATTESTATION).isNotEmpty() ||
            headers.oauthHeaderValues(ClientAttestationHeaders.CLIENT_ATTESTATION_POP).isNotEmpty()
        ) {
            add(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
        }

        if (
            parameters["client_assertion"].orEmpty().any { it.isNotBlank() } ||
            parameters["client_assertion_type"].orEmpty().any { it.isNotBlank() }
        ) {
            add(ClientAuthenticationMethods.PRIVATE_KEY_JWT)
        }

        if (headers.oauthHeaderValues("Authorization").any { it.startsWith("Basic ", ignoreCase = true) }) {
            add(ClientAuthenticationMethods.CLIENT_SECRET_BASIC)
        }

        if (
            parameters["client_id"].orEmpty().any { it.isNotBlank() } &&
            parameters["client_secret"].orEmpty().any { it.isNotBlank() }
        ) {
            add(ClientAuthenticationMethods.CLIENT_SECRET_POST)
        }
    }
}

internal fun Map<String, List<String>>.oauthHeaderValues(name: String): List<String> =
    entries
        .asSequence()
        .filter { (headerName, _) -> headerName.equals(name, ignoreCase = true) }
        .flatMap { (_, values) -> values.asSequence() }
        .filter { it.isNotBlank() }
        .toList()
