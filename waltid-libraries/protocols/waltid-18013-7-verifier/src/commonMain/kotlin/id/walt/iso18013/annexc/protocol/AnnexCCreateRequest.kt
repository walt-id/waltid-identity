package id.walt.iso18013.annexc.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AnnexCCreateRequest(
    val docType: String,
    val requestedElements: Map<String, List<String>>,
    // val policies: Verification2Session.DefinedVerificationPolicies = Verification2Session.DefinedVerificationPolicies(),
    val origin: String,
    val ttlSeconds: Long? = null,
)

@Serializable
data class AnnexCRequestRequest(
    val sessionId: String,
    val intentToRetain: Boolean = false,
)

@Serializable
data class AnnexCRequestResponse(
    val protocol: String,
    val data: Data,
) {
    @Serializable
    data class Data(
        @SerialName("deviceRequest")
        val deviceRequest: String,
        @SerialName("encryptionInfo")
        val encryptionInfo: String,
    )
}

@Serializable
data class AnnexCResponseRequest(
    val sessionId: String,
    val response: String,
)

@Serializable
data class AnnexCResponseAck(
    val status: String = "received",
)

@Serializable
data class AnnexCInfoResponse(
    val sessionId: String,
    val status: AnnexCSessionStatus,
    val origin: String,
    val expiresAt: String,
    val docType: String,
    val requestedElements: Map<String, List<String>>,
    //  val policies: Verification2Session.DefinedVerificationPolicies = Verification2Session.DefinedVerificationPolicies(),

    val deviceRequest: String? = null,
    val encryptionInfo: String? = null,
    val encryptedResponse: String? = null,

    val deviceResponseCbor: String? = null,
    //val mdocVerificationResult: VerificationResult? = null,
    //     val policyResults: Verifier2PolicyResults? = null,
    val error: String? = null,
)

@Serializable
enum class AnnexCSessionStatus {
    created,
    request_built,
    response_received,
    processing,
    decrypted,
    verified,
    failed,
    expired,
}
