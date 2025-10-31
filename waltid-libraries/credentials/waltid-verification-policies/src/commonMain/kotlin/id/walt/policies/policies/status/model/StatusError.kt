package id.walt.policies.policies.status.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class StatusError : Throwable() {
    abstract override val message: String
}

@Serializable
@SerialName("STATUS_RETRIEVAL_ERROR")
data class StatusRetrievalError(
    override val message: String,
) : StatusError()

@Serializable
@SerialName("STATUS_VERIFICATION_ERROR")
data class StatusVerificationError(
    override val message: String,
) : StatusError()

@Serializable
@SerialName("STATUS_LIST_LENGTH_ERROR")
data class StatusLengthError(
    override val message: String,
) : StatusError()