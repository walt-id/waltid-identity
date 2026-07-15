package id.walt.walletdemo.compose.logic

val WalletDemoUiState.isBusy: Boolean
    get() = session is WalletSessionState.Bootstrapping ||
        operation is WalletOperationState.ResolvingOffer ||
        operation is WalletOperationState.Receiving ||
        operation is WalletOperationState.ResolvingPresentation ||
        operation is WalletOperationState.Presenting

val WalletDemoUiState.isError: Boolean
    get() = isErrorFor(selectedTab)

val WalletDemoUiState.isStatusBusy: Boolean
    get() = session is WalletSessionState.Bootstrapping ||
        (operation.belongsTo(selectedTab) && operation.isBusyOperation)

val WalletDemoUiState.receiveUrlEntryEnabled: Boolean
    get() = !isBusy && !receiveCompleted

val WalletDemoUiState.receiveActionEnabled: Boolean
    get() = session is WalletSessionState.Ready &&
        requestDrafts.offerUrl.isNotBlank() &&
        (!requestDrafts.txCodeRequired || requestDrafts.txCode.isNotBlank()) &&
        receiveUrlEntryEnabled

val WalletDemoUiState.presentationUrlEntryEnabled: Boolean
    get() = !isBusy && presentationPreview == null && !presentationCompleted

val WalletDemoUiState.presentationPreviewActionEnabled: Boolean
    get() = (session as? WalletSessionState.Ready)?.credentials?.isNotEmpty() == true &&
        requestDrafts.presentationRequestUrl.isNotBlank() &&
        presentationUrlEntryEnabled

val WalletDemoUiState.presentationReviewEnabled: Boolean
    get() = !isBusy && presentationPreview != null && !presentationCompleted

val WalletDemoUiState.statusText: String
    get() = statusText(selectedTab)

fun WalletDemoUiState.statusText(tab: WalletDemoTab): String =
    operation.statusTextFor(tab)
        ?: tabStatusText(tab)
        ?: session.statusText(auth)

private fun WalletDemoUiState.isErrorFor(tab: WalletDemoTab): Boolean =
    session is WalletSessionState.Failed ||
        (operation is WalletOperationState.Failed && operation.belongsTo(tab))

private fun WalletDemoUiState.tabStatusText(tab: WalletDemoTab): String? =
    when (tab) {
        WalletDemoTab.Credentials -> null
        WalletDemoTab.Receive -> if (receiveCompleted && lastReceivedCredentialIds.isNotEmpty()) {
            WalletDisplayText.receivedCredentials(lastReceivedCredentialIds.size)
        } else {
            null
        }
        WalletDemoTab.Present -> when {
            presentationCompleted -> WalletDisplayText.PresentationSent
            presentationPreview != null -> WalletDisplayText.ReviewPresentationRequest
            else -> null
        }
    }

private fun WalletSessionState.statusText(auth: WalletAuthState): String =
    when (this) {
        WalletSessionState.NotBootstrapped -> when (auth) {
            is WalletAuthState.Setup -> WalletDisplayText.SetupPin
            is WalletAuthState.Login -> WalletDisplayText.UnlockPin
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
            WalletOperationState.Receiving -> WalletDisplayText.ReceivingCredential
            WalletOperationState.ResolvingPresentation -> WalletDisplayText.ResolvingPresentation
            WalletOperationState.Presenting -> WalletDisplayText.PresentingCredential
            is WalletOperationState.Succeeded -> message
            is WalletOperationState.Failed -> message
        }
    }

private fun WalletOperationState.belongsTo(tab: WalletDemoTab): Boolean =
    when (this) {
        WalletOperationState.Idle -> false
        WalletOperationState.ResolvingOffer,
        WalletOperationState.Receiving -> tab == WalletDemoTab.Receive
        WalletOperationState.ResolvingPresentation,
        WalletOperationState.Presenting,
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
        -> true
        WalletOperationState.Idle,
        is WalletOperationState.Succeeded,
        is WalletOperationState.Failed,
        -> false
    }
