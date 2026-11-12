package id.walt.openid4vci.attestation

import kotlinx.coroutines.CancellationException

internal enum class AttestationStatusFailureReason { LOOKUP, SIGNER, SIGNATURE, FRESHNESS, FORMAT }

/** Internal diagnostic context, never persisted or returned as an OAuth/credential error. */
internal class AttestationStatusValidationException(
    val reason: AttestationStatusFailureReason,
    cause: Throwable? = null,
) : Exception("Attestation status validation failed", cause)

internal inline fun <T> attestationStatusCheck(reason: AttestationStatusFailureReason, action: () -> T): T = try {
    action()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (classified: AttestationStatusValidationException) {
    throw classified
} catch (cause: Exception) {
    throw AttestationStatusValidationException(reason, cause)
}
