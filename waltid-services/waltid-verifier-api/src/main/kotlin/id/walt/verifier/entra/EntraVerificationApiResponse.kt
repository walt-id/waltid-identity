package id.walt.verifier.entra


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EntraVerificationApiResponse(
    val requestId: String, // b1fc560c-c628-4492-95a8-9ff359620ea2
    val requestStatus: String, // presentation_verified
    val state: String, // 1234
    val subject: String? = null, // did:web:entra.walt.id:holder
    val verifiedCredentialsData: List<VerifiedCredentialsData>? = null,
) {
    @Serializable
    data class VerifiedCredentialsData(
        val claims: JsonObject,
        val credentialState: CredentialState,
        val expirationDate: String, // 2024-02-15T13:24:29.000Z
        val issuanceDate: String, // 2024-01-16T13:24:29.000Z
        val issuer: String, // did:web:entra.walt.id
        val type: List<String>,
    ) {

        @Serializable
        data class CredentialState(
            val revocationStatus: String,  // VALID
        )
    }
}
