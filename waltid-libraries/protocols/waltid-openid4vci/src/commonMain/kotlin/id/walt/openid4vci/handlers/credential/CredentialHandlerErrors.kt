package id.walt.openid4vci.handlers.credential

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes

internal fun Exception.toCredentialHandlerError(): CredentialError =
    CredentialError(
        error = if (isProofError()) {
            CredentialErrorCodes.INVALID_PROOF
        } else {
            CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST
        },
        description = message,
    )

private fun Exception.isProofError(): Boolean {
    val message = message ?: return false
    return proofErrorMessages.any { message.contains(it, ignoreCase = true) }
}

private val proofErrorMessages = listOf(
    "Missing JWT proof",
    "Proof JWT",
    "holder key",
)
