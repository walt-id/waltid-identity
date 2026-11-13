package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CredentialStatusReferenceDto(val storeIndex: Int, val credentialId: String) {
    init { require(storeIndex >= 0 && credentialId.isNotBlank()) }
}
@Serializable
internal data class CredentialStatusSnapshotDto(
    val credentials: List<CredentialStatusRecordDto>,
    val refreshing: List<CredentialStatusReferenceDto> = emptyList(),
    val nextRefreshAt: String? = null,
    val statusContext: CredentialStatusContextDto? = null,
) {
    init { require(credentials.map { it.reference }.distinct().size == credentials.size) }
}
@Serializable
internal data class CredentialStatusRecordDto(
    val reference: CredentialStatusReferenceDto,
    val presentation: CredentialStatusPresentationDto,
)
@Serializable
internal data class RefreshCredentialStatusesDto(
    val credentials: List<CredentialStatusReferenceDto>? = null,
    val mode: String,
    val statusContext: String? = null,
)
@Serializable
internal data class CredentialStatusContextDto(val id: String, val expiresAt: String) {
    init { require(id.isNotBlank() && expiresAt.isNotBlank()) }
}

@Serializable
internal data class CredentialStatusRefreshResultDto(
    val reference: CredentialStatusReferenceDto,
    val outcome: String,
    val presentation: CredentialStatusPresentationDto,
    val evaluatedAt: String,
    val hasCheckErrors: Boolean,
    val statusContext: CredentialStatusContextDto? = null,
)

@Serializable
internal data class StatusReviewedBuildRequestDto(
    val request: BuildVpTokenRequestDto,
    val reviewedStatus: Map<String, String>,
    val statusContext: String? = null,
)
@Serializable
internal data class StatusReviewedSendRequestDto(
    val request: SendAuthorizationResponseRequestDto,
    val selectedCredentialIds: Set<String>,
    val reviewedStatus: Map<String, String>,
    val statusContext: String? = null,
)

/** Rendering DTO only; browser clients never evaluate issuer status bits. */
@Serializable
internal data class CredentialStatusPresentationDto(
    val kind: String,
    val issue: String,
    val freshness: String,
    val action: String,
    val advisoryRevision: String,
    val lastKnown: Boolean,
    val coverage: String = "NONE",
    val lastCheckedAt: String? = null,
    val lastAttemptAt: String? = null,
    val failure: CredentialStatusFailureDto? = null,
    val purposes: List<String> = emptyList(),
    val references: List<CredentialStatusReferencePresentationDto> = emptyList(),
    val discoveryProblem: String? = null,
) {
    init {
        require(advisoryRevision.isNotBlank())
        require(references.map { it.referenceId }.distinct().size == references.size)
    }
}

@Serializable
internal data class CredentialStatusFailureDto(
    val type: String,
    val reason: String? = null,
    val validFrom: String? = null,
    val retryAt: String? = null,
) {
    init {
        when (type) {
            "reason" -> require(!reason.isNullOrBlank() && validFrom == null && retryAt == null)
            "not_yet_valid" -> require(!validFrom.isNullOrBlank() && reason == null && retryAt == null)
            "rate_limited" -> require(reason == null && validFrom == null)
        }
    }
    val code: String get() = when (type) {
        "reason" -> requireNotNull(reason)
        "not_yet_valid" -> "NOT_YET_VALID"
        "rate_limited" -> "RATE_LIMITED"
        else -> "UNKNOWN"
    }
    val eligibleAt: String? get() = when (type) {
        "not_yet_valid" -> validFrom
        "rate_limited" -> retryAt
        else -> null
    }
}

@Serializable
internal data class CredentialStatusReferencePresentationDto(
    val referenceId: String,
    @SerialName("protocol") val protocolName: String? = null,
    val method: String? = null,
    val purpose: String? = null,
    val result: String? = null,
    val checkedAt: String? = null,
    val freshUntil: String? = null,
    val validUntil: String? = null,
    val attemptedAt: String? = null,
    val failure: CredentialStatusFailureDto? = null,
    val conflict: Boolean = false,
    val problem: String? = null,
    val freshness: String = "UNKNOWN",
) { init { require(referenceId.isNotBlank()) } }
