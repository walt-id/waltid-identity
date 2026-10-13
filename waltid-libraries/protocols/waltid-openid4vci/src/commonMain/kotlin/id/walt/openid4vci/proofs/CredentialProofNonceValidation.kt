package id.walt.openid4vci.proofs

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared freshness check for the outer proof and its attestation. Nonces need not be single-use. */
internal suspend fun validateCredentialNonce(payload: JsonObject, context: CredentialProofValidationContext) {
    val validation = context.nonceValidation ?: return
    val nonce = (payload["nonce"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        ?: throw invalidCredentialNonce("Credential proof nonce is required and must be a string")
    val result = try {
        validation.service.validate(nonce, validation.binding)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw CredentialProofServiceException("Credential proof nonce verification service failed", e)
    }
    if (result != CredentialNonceValidationResult.VALID) throw invalidCredentialNonce("Credential proof nonce is invalid")
}
