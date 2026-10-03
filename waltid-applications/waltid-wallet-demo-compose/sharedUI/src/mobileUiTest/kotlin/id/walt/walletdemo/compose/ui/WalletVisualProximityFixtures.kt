package id.walt.walletdemo.compose.ui

import id.walt.wallet2.mobile.*
import id.walt.walletdemo.compose.logic.*
import kotlinx.serialization.json.*
import kotlin.time.Instant

/** Both native and Compose renderers read these synthetic nearby facts. */
internal object WalletVisualProximityFixtures {
    private fun text(key: String) = WalletVisualFixtures.nearbyReviewData.getValue(key).jsonPrimitive.content
    val completedAt get() = Instant.parse(text("completedAt"))
    private val element get() = ProximityElementReference(text("namespace"), text("element"))
    val credential get() = CredentialSummary(id = text("credentialId"), format = "mso_mdoc", issuer = text("issuer"), label = text("title"),
        credentialDataJson = buildJsonObject { putJsonObject(text("namespace")) { put(text("element"), text("value")) } }.toString(),
        metadataJson = buildJsonObject {
            putJsonArray("credentialDisplay") { add(buildJsonObject { put("name", text("title")); put("background_color", text("backgroundColor")) }) }
            putJsonArray("credentialClaims") { add(buildJsonObject {
                putJsonArray("path") { add(text("namespace")); add(text("element")) }
                putJsonArray("display") { add(buildJsonObject { put("name", text("label")); put("locale", "en") }) }
            }) }
        }.toString())
    val details get() = mapOf(credential.id to credential.toCredentialDetails())
    val review get() = ProximityReview(
        reviewId = ProximityReviewId(text("id")), exchange = 1,
        documents = listOf(ProximityDocumentReview(requestIndex = 0, docType = text("docType"), credentialOptions = listOf(
            ProximityCredentialOption(credentialId = text("credentialId"), label = text("title"), issuer = text("issuer"),
                validUntil = Instant.parse("2030-01-01T00:00:00Z"), deviceAuthentication = ProximityDeviceAuthenticationMethod.Signature,
                requestedElements = listOf(ProximityRequestedElement(text("namespace"), text("element"), intentToRetain = true)))
        ))),
        readerAuthentication = listOf(ProximityReaderAuthentication(scope = ProximityReaderAuthenticationScope.WholeRequest,
            outcome = ProximityReaderAuthenticationOutcome.Valid(ProximityReaderTrustDecision(
                state = ProximityReaderTrustState.Trusted, certificatePath = ProximityReaderCertificatePathState.Valid,
                displayName = text("reader"))))),
        useCases = emptyList(), applicationAuthorizations = emptyList())
    val selections get() = listOf(WalletDemoProximityDocumentSelection(0, text("credentialId"), setOf(element)))
    val submission get() = ProximitySubmission(listOf(ProximityDocumentSubmission(0, text("credentialId"), setOf(element))))
    fun state(kind: String) = WalletDemoProximityUiState(active = true, selections = selections, sessionState = when (kind) {
        "permission" -> ProximityState.CheckingPrerequisites(permissionBlockedCapabilities)
        "review" -> ProximityState.ReviewRequired(review)
        "expired" -> ProximityState.Failed(ProximityError(category = ProximityErrorCategory.Policy, code = "prepared_sharing_expired",
            message = text("expiredMessage"), recovery = ProximityRecovery.StartNewSession))
        "receipt" -> ProximityState.Completed(exchanges = 1, declined = false)
        else -> error("Unknown nearby visual state: $kind")
    })
}
