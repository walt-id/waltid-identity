package id.walt.policies2.vc.policies.status.signature

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class StatusListSignatureException : Exception() {
    abstract override val message: String
}

@Serializable
@SerialName("SIGNATURE_INVALID")
data class SignatureInvalidException(
    override val message: String,
) : StatusListSignatureException()

@Serializable
@SerialName("KEY_RESOLUTION_FAILED")
data class KeyResolutionFailedException(
    override val message: String,
) : StatusListSignatureException()

@Serializable
@SerialName("UNSUPPORTED_FORMAT")
data class UnsupportedFormatException(
    override val message: String,
) : StatusListSignatureException()
