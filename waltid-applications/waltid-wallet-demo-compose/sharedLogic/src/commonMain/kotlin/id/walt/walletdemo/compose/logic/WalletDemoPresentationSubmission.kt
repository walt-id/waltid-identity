package id.walt.walletdemo.compose.logic

/** One rendered online review. The controller rejects it if request, choices or wallet changed. */
data class WalletDemoPresentationSubmission(
    val previewHandle: WalletDemoPresentationPreviewHandle,
    val walletKeyId: String,
    val walletContext: Long,
    val selection: WalletDemoSharingSelection,
    val statusReviewEpoch: Long,
)

fun WalletDemoUiState.presentationSubmission(reviewedStatus: Map<String, String>): WalletDemoPresentationSubmission? {
    val ready = session as? WalletSessionState.Ready ?: return null
    val preview = presentationPreview ?: return null
    return WalletDemoPresentationSubmission(preview.previewHandle, ready.keyId, credentialStatusContext, WalletDemoSharingSelection(
        selectedPresentationCredentialOptions.toSet(),
        selectedPresentationDisclosureOptions.forSelectedCredentials(selectedPresentationCredentialOptions).toSet(),
        paymentReview.consent?.revision, reviewedStatus.toMap(),
    ), credentialStatuses.reviewEpoch)
}
