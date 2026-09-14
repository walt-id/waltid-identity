package id.walt.walletdemo.compose.logic

enum class WalletStatusKind {
    Busy,
    Info,
    Success,
    Error,
}

data class WalletStatusBanner(
    val message: String,
    val kind: WalletStatusKind,
    val occurrenceId: Long = 0,
) {
    val key: String get() = "$kind:$message:$occurrenceId"
}

val WalletDemoUiState.isBusy: Boolean
    get() = isAuthenticating ||
        isChangingSigningProtection ||
        session is WalletSessionState.Bootstrapping ||
        operation is WalletOperationState.ResolvingOffer ||
        operation is WalletOperationState.Receiving ||
        operation is WalletOperationState.ResolvingPresentation ||
        operation is WalletOperationState.Presenting ||
        operation is WalletOperationState.DecliningPresentation

val WalletDemoUiState.isError: Boolean
    get() = isErrorFor(selectedTab)

val WalletDemoUiState.isStatusBusy: Boolean
    get() = session is WalletSessionState.Bootstrapping ||
        (operation.belongsTo(selectedTab) && operation.isBusyOperation)

val WalletDemoUiState.receiveUrlEntryEnabled: Boolean
    get() = !isBusy && offerPreview == null

val WalletDemoUiState.receiveActionEnabled: Boolean
    get() = session is WalletSessionState.Ready &&
        requestDrafts.offerUrl.isNotBlank() &&
        receiveUrlEntryEnabled

val WalletDemoUiState.offerReviewEnabled: Boolean
    get() = !isBusy && offerPreview != null

val WalletDemoUiState.acceptOfferEnabled: Boolean
    get() = offerReviewEnabled && (offerPreview?.transactionCode?.accepts(requestDrafts.txCode) ?: true)

val WalletDemoUiState.presentationUrlEntryEnabled: Boolean
    get() = !isBusy && presentationReview == null

val WalletDemoUiState.presentationPreviewActionEnabled: Boolean
    get() = session is WalletSessionState.Ready &&
        requestDrafts.presentationRequestUrl.isNotBlank() &&
        presentationUrlEntryEnabled

val WalletDemoUiState.presentationReviewEnabled: Boolean
    get() = !isBusy && presentationReview != null

val WalletDemoUiState.statusText: String
    get() = statusText(selectedTab)

fun WalletDemoUiState.statusText(tab: WalletDemoTab): String =
    statusBanner(tab)?.message.orEmpty()

fun WalletDemoUiState.statusBanner(tab: WalletDemoTab = selectedTab): WalletStatusBanner? {
    val message = operation.statusTextFor(tab)
        ?: tabStatusText(tab)
        ?: session.statusText(auth)
        ?: return null
    val kind = when {
        isErrorFor(tab) -> WalletStatusKind.Error
        isStatusBusyFor(tab) -> WalletStatusKind.Busy
        isSuccessStatus(tab, message) -> WalletStatusKind.Success
        else -> WalletStatusKind.Info
    }
    return WalletStatusBanner(message = message, kind = kind, occurrenceId = statusOccurrenceId)
}

val WalletDemoUiState.isStatusVisible: Boolean
    get() {
        val banner = statusBanner() ?: return false
        return statusDismissedKey != banner.key
    }

val WalletDemoUiState.isStatusExpanded: Boolean
    get() = statusExpanded && statusBanner()?.kind == WalletStatusKind.Error && isStatusVisible

private fun WalletDemoUiState.isStatusBusyFor(tab: WalletDemoTab): Boolean =
    session is WalletSessionState.Bootstrapping ||
        (operation.belongsTo(tab) && operation.isBusyOperation)

private fun WalletDemoUiState.isSuccessStatus(tab: WalletDemoTab, message: String): Boolean =
    (operation is WalletOperationState.Succeeded && operation.belongsTo(tab)) ||
        message == WalletDisplayText.WalletReady ||
        message == WalletDisplayText.PresentationSent ||
        message.startsWith("Received ")

private fun WalletDemoUiState.isErrorFor(tab: WalletDemoTab): Boolean =
    session is WalletSessionState.Failed ||
        (operation is WalletOperationState.Failed && operation.belongsTo(tab))

private fun WalletDemoUiState.tabStatusText(tab: WalletDemoTab): String? =
    when (tab) {
        WalletDemoTab.Credentials -> null
        WalletDemoTab.Receive -> null
        WalletDemoTab.Present -> when {
            presentationReview is WalletDemoPresentationPreviewResult.Invalid -> WalletDisplayText.ReviewPresentationError
            presentationReview is WalletDemoPresentationPreviewResult.Ready -> WalletDisplayText.ReviewPresentationRequest
            else -> null
        }
    }

private fun WalletSessionState.statusText(auth: WalletAuthState): String? =
    when (this) {
        WalletSessionState.NotBootstrapped -> when (auth) {
            is WalletAuthState.Setup -> WalletDisplayText.SetupPin
            is WalletAuthState.Login -> WalletDisplayText.UnlockPin
            is WalletAuthState.StorageUnavailable -> auth.message
            WalletAuthState.Unlocked -> WalletDisplayText.WalletNotReady
        }
        WalletSessionState.Bootstrapping -> WalletDisplayText.BootstrappingWallet
        is WalletSessionState.Ready -> WalletDisplayText.WalletReady
        is WalletSessionState.Failed -> message
    }

private fun WalletOperationState.statusTextFor(tab: WalletDemoTab): String? =
    if (!belongsTo(tab)) {
        null
    } else {
        when (this) {
            WalletOperationState.Idle -> null
            WalletOperationState.ResolvingOffer -> WalletDisplayText.ResolvingCredentialOffer
            WalletOperationState.OfferPreview -> WalletDisplayText.ReviewCredentialOffer
            WalletOperationState.Receiving -> WalletDisplayText.ReceivingCredential
            WalletOperationState.ResolvingPresentation -> WalletDisplayText.ResolvingPresentation
            WalletOperationState.Presenting -> WalletDisplayText.PresentingCredential
            WalletOperationState.DecliningPresentation -> WalletDisplayText.DecliningPresentation
            is WalletOperationState.Succeeded -> message
            is WalletOperationState.Failed -> message
        }
    }

private fun WalletOperationState.belongsTo(tab: WalletDemoTab): Boolean =
    when (this) {
        WalletOperationState.Idle -> false
        WalletOperationState.ResolvingOffer,
        WalletOperationState.OfferPreview,
        WalletOperationState.Receiving -> tab == WalletDemoTab.Receive
        WalletOperationState.ResolvingPresentation,
        WalletOperationState.Presenting,
        WalletOperationState.DecliningPresentation,
        -> tab == WalletDemoTab.Present
        is WalletOperationState.Succeeded -> this.tab == null || this.tab == tab
        is WalletOperationState.Failed -> this.tab == null || this.tab == tab
    }

private val WalletOperationState.isBusyOperation: Boolean
    get() = when (this) {
        WalletOperationState.ResolvingOffer,
        WalletOperationState.Receiving,
        WalletOperationState.ResolvingPresentation,
        WalletOperationState.Presenting,
        WalletOperationState.DecliningPresentation,
        -> true
        WalletOperationState.Idle,
        WalletOperationState.OfferPreview,
        is WalletOperationState.Succeeded,
        is WalletOperationState.Failed,
        -> false
    }
