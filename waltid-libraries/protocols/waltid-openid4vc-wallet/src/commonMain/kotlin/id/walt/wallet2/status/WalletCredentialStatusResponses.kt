package id.walt.wallet2.status

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentationData
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.handlers.BuildVpTokenRequest
import id.walt.wallet2.handlers.SendAuthorizationResponseRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** Additive HTTP projection; storage models and signed credential payloads remain independent. */
@Serializable
data class WalletCredentialResponse(
    val id: String,
    val credential: DigitalCredential,
    val label: String? = null,
    val addedAt: Instant? = null,
    val metadata: JsonObject? = null,
    val holderKeyBinding: HolderKeyBinding? = null,
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val statusPresentation: CredentialStatusPresentationData,
    val statusEvaluatedAt: Instant,
)

@Serializable
data class WalletCredentialMetadataResponse(
    val id: String,
    val format: String,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val addedAt: Instant? = null,
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val statusPresentation: CredentialStatusPresentationData,
    val statusEvaluatedAt: Instant,
)

fun WalletCredentialRead.toResponse(): WalletCredentialResponse = WalletCredentialResponse(
    credential.id, credential.credential, credential.label, credential.addedAt, credential.metadata,
    credential.holderKeyBinding, reference, status, presentation.toData(), evaluatedAt,
)

fun WalletCredentialRead.toMetadataResponse(): WalletCredentialMetadataResponse = WalletCredentialMetadataResponse(
    credential.id, credential.credential.format, credential.credential.issuer, credential.credential.subject,
    credential.label, credential.addedAt, reference, status, presentation.toData(), evaluatedAt,
)

/** IDs refer only to records already stored in this wallet. A host never accepts a caller's status URL. */
@Serializable
data class RefreshCredentialStatusesRequest(
    val credentials: Set<WalletCredentialReference>? = null,
    val mode: CredentialStatusRefreshMode = CredentialStatusRefreshMode.IF_DUE,
) {
    init { require(credentials == null || credentials.isNotEmpty() && credentials.size <= 100) }
}

@Serializable
data class StatusReviewedBuildVpTokenRequest(val request: BuildVpTokenRequest, val reviewedStatus: Map<String, String>)

@Serializable
data class StatusReviewedSendResponseRequest(
    val request: SendAuthorizationResponseRequest,
    val selectedCredentialIds: Set<String>,
    val reviewedStatus: Map<String, String>,
) { init { require(selectedCredentialIds.isNotEmpty() && selectedCredentialIds.size <= 100) } }

@Serializable
data class CredentialStatusReviewChangedResponse(val code: String = "credential_status_review_changed")

/** Transport-only snapshot; decoded presentations never enter the local service state. */
@Serializable
data class WalletCredentialStatusSnapshotResponse(
    val evaluatedAt: Instant,
    val credentials: List<WalletCredentialStatusResponse>,
    val refreshing: Set<WalletCredentialReference> = emptySet(),
    val nextRefreshAt: Instant? = null,
)

@Serializable
data class WalletCredentialStatusResponse(
    val reference: WalletCredentialReference,
    val status: CredentialStatus,
    val presentation: CredentialStatusPresentationData,
)

fun WalletCredentialStatusSnapshot.toResponse(): WalletCredentialStatusSnapshotResponse = WalletCredentialStatusSnapshotResponse(
    evaluatedAt, credentials.map { WalletCredentialStatusResponse(it.reference, it.status, it.presentation.toData()) },
    refreshing, nextRefreshAt,
)
