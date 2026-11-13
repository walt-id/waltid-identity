package id.walt.verifier2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Verifier session lifecycle policy; independent of OpenID4VP presentation validation. */
@Serializable
enum class VerificationSessionUsage {
    /** Consume the session when its first presentation or wallet error is admitted. */
    @SerialName("single_attempt") SINGLE_ATTEMPT,
    /** Failed attempts may retry; the first successful presentation consumes the session. */
    @SerialName("single_success") SINGLE_SUCCESS,
    /** Further presentations may be accepted until expiration, including after success. */
    @SerialName("reusable") REUSABLE,
}
