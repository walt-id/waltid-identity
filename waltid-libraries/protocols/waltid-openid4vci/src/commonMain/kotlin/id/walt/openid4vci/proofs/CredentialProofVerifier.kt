package id.walt.openid4vci.proofs

import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.CredentialRequest
import kotlinx.serialization.json.JsonObject

fun interface CredentialProofVerifier {
    suspend fun verify(
        credentialRequest: CredentialRequest,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): List<VerifiedCredentialProof>
}

data class CredentialProofValidationContext(
    val credentialIssuer: String,
    val clientId: String? = null,
    val anonymousPreAuthorizedAccess: Boolean = false,
    val nonceValidation: CredentialNonceValidationContext? = null,
) {
    init {
        require(credentialIssuer.isNotBlank()) { "credentialIssuer must not be blank" }
        clientId?.let { require(it.isNotBlank()) { "clientId must not be blank" } }
    }
}

data class VerifiedCredentialProof(
    val proofType: String,
    val jwt: String,
    val algorithm: String,
    val header: JsonObject,
    val payload: JsonObject,
    val holderKey: Key,
    val holderKid: String?,
    val holderDid: String?,
    val nonce: String?,
)

class CredentialProofValidationException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun invalidCredentialProof(message: String, cause: Throwable? = null): CredentialProofValidationException =
    CredentialProofValidationException(CredentialErrorCodes.INVALID_PROOF, message, cause)

internal fun invalidCredentialNonce(message: String, cause: Throwable? = null): CredentialProofValidationException =
    CredentialProofValidationException(CredentialErrorCodes.INVALID_NONCE, message, cause)
