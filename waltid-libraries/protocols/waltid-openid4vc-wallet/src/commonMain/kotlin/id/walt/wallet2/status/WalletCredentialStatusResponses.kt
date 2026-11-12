package id.walt.wallet2.status

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.status.CredentialStatusMetadata
import id.walt.credentials.status.CredentialStatusPresentation
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
    val label: String?,
    val addedAt: Instant?,
    val metadata: JsonObject?,
    val holderKeyBinding: HolderKeyBinding?,
    val reference: WalletCredentialReference,
    val status: CredentialStatusMetadata,
    val statusPresentation: CredentialStatusPresentation,
    val statusEvaluatedAt: Instant,
)

@Serializable
data class WalletCredentialMetadataResponse(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: Instant?,
    val reference: WalletCredentialReference,
    val status: CredentialStatusMetadata,
    val statusPresentation: CredentialStatusPresentation,
    val statusEvaluatedAt: Instant,
)

fun WalletCredentialRead.toResponse(): WalletCredentialResponse = WalletCredentialResponse(
    credential.id, credential.credential, credential.label, credential.addedAt, credential.metadata,
    credential.holderKeyBinding, reference, status, presentation, evaluatedAt,
)

fun WalletCredentialRead.toMetadataResponse(): WalletCredentialMetadataResponse = WalletCredentialMetadataResponse(
    credential.id, credential.credential.format, credential.credential.issuer, credential.credential.subject,
    credential.label, credential.addedAt, reference, status, presentation, evaluatedAt,
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
