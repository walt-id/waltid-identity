package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.VerifiableCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

/**
 * Login with a verifiable credential through a verifier2 service.
 *
 * [setup] is the verifier2 session setup (`POST verification-session/create` body: flow type, DCQL query, policies).
 * The account is found by [identifierClaim] of the first credential presented for [credentialQueryId] (the first query
 * when null); it is a path into the credential data, e.g. `["credentialSubject", "id"]`, or for an mdoc
 * `["org.iso.18013.5.1", "document_number"]`.
 */
@Serializable
@SerialName("vc-auth")
data class VerifiableCredentialAuthConfiguration(
    val verifierUrl: String,
    val setup: JsonObject,
    val identifierClaim: List<String> = listOf("credentialSubject", "id"),
    val credentialQueryId: String? = null,
) : AuthMethodConfiguration {
    init {
        require(identifierClaim.isNotEmpty()) { "identifierClaim needs at least one path element" }
    }

    override fun authMethod() = VerifiableCredential
}
