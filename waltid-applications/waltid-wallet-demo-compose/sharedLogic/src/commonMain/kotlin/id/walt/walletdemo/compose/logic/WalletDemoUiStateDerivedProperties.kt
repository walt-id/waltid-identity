package id.walt.walletdemo.compose.logic

val WalletDemoUiState.isBusy: Boolean
    get() = session is WalletSessionState.Bootstrapping ||
        operation is WalletOperationState.ResolvingOffer ||
        operation is WalletOperationState.Receiving ||
        operation is WalletOperationState.Presenting

val WalletDemoUiState.isError: Boolean
    get() = session is WalletSessionState.Failed ||
        operation is WalletOperationState.Failed

val WalletDemoUiState.statusText: String
    get() = when (operation) {
        WalletOperationState.Idle -> session.statusText(auth)
        WalletOperationState.ResolvingOffer -> "Resolving offer..."
        WalletOperationState.Receiving -> "Receiving credential..."
        WalletOperationState.Presenting -> "Presenting credential..."
        is WalletOperationState.Succeeded -> operation.message
        is WalletOperationState.Failed -> operation.message
    }

private fun WalletSessionState.statusText(auth: WalletAuthState): String =
    when (this) {
        WalletSessionState.NotBootstrapped -> when (auth) {
            is WalletAuthState.Setup -> "Set up a PIN to unlock the wallet"
            is WalletAuthState.Login -> "Enter PIN to unlock the wallet"
            WalletAuthState.Unlocked -> "Wallet not ready"
        }
        WalletSessionState.Bootstrapping -> "Bootstrapping wallet..."
        is WalletSessionState.Ready -> "Wallet ready"
        is WalletSessionState.Failed -> message
    }
