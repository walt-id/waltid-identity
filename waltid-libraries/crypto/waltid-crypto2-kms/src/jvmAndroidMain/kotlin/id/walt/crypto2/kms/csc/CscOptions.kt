package id.walt.crypto2.kms.csc

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.requireHttpEndpoint
import kotlinx.serialization.Serializable

/**
 * Connection to a remote signing service that implements the CSC API v2.2.
 *
 * @property serviceUrl the CSC API base URI, for example `https://rssp.example/csc/v2`
 * @property tokenEndpoint the OAuth 2.0 token endpoint. Defaults to `oauth2/token` below the `oauth2` URI that the
 * service's `info` method returns, but only when that URI is HTTPS on the [serviceUrl] origin. Configure it for an
 * authorization server on another host.
 */
@Serializable
data class CscOptions(
    val serviceUrl: String,
    val credentialReference: CredentialReference,
    val tokenEndpoint: String? = null,
) {
    init {
        requireHttpEndpoint(serviceUrl, "CSC service URL")
        tokenEndpoint?.let { requireHttpEndpoint(it, "CSC token endpoint") }
    }
}

/**
 * OAuth 2.0 client credentials for the CSC service, plus the static values (PIN or password) of the credential's
 * `Password` authentication objects, keyed by authentication object ID.
 */
class CscCredentials(
    val clientId: String,
    val clientSecret: String,
    val authData: Map<String, String> = emptyMap(),
) {
    init {
        require(clientId.isNotBlank()) { "CSC client ID cannot be blank" }
        require(clientSecret.isNotBlank()) { "CSC client secret cannot be blank" }
        require(authData.all { (id, value) -> id.isNotBlank() && value.isNotEmpty() }) {
            "CSC authorization data needs non-blank IDs and non-empty values"
        }
    }
}

fun interface CscCredentialResolver {
    suspend fun resolve(reference: CredentialReference): CscCredentials
}
