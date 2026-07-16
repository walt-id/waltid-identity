package id.walt.walletdemo.compose.logic

internal enum class WalletDeepLinkScheme(val scheme: String) {
    CredentialOffer("openid-credential-offer"),
    PresentationRequest("openid4vp"),
    ;

    companion object {
        fun parse(rawUrl: String): WalletDeepLinkScheme? {
            val scheme = rawUrl.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
                ?: return null
            return entries.firstOrNull { it.scheme == scheme }
        }
    }
}

internal object WalletDisplayText {
    const val ReviewPresentationRequest = "Review presentation request"
    const val StartingWallet = "Starting wallet..."
    const val ResolvingCredentialOffer = "Resolving credential offer..."
    const val ReceivingCredential = "Receiving credential..."
    const val ResolvingPresentation = "Resolving presentation..."
    const val PresentingCredential = "Presenting credential..."
    const val SetupPin = "Set up a PIN to unlock the wallet"
    const val UnlockPin = "Enter PIN to unlock the wallet"
    const val WalletNotReady = "Wallet not ready"
    const val BootstrappingWallet = "Bootstrapping wallet..."
    const val WalletReady = "Wallet ready"
    const val PresentationSent = "Presentation sent"
    const val PresentationReviewCancelled = "Presentation review cancelled"
    const val PresentationFinishedWithoutVerifierConfirmation = "Presentation finished without verifier confirmation"
    const val ReceiveFailed = "Receive failed"
    const val PreviewFailed = "Preview failed"
    const val PresentFailed = "Present failed"
    const val BootstrapFailed = "Bootstrap failed"
    const val InvalidOfferUrl = "invalid offer URL"
    const val InvalidRequestUrl = "invalid request URL"
    const val SelectCredentialForEveryRequest = "select a credential for every requested credential"
    const val PinMustContain4To8Digits = "PIN must contain 4 to 8 digits"
    const val PinConfirmationDoesNotMatch = "PIN confirmation does not match"
    const val WrongPin = "Wrong PIN"
    const val ReceivedCredentialsUnavailable = "received credentials are not available locally"
    const val UnexpectedError = "Unexpected error"

    fun receivedCredentials(count: Int): String = "Received $count credential(s)"

    fun failure(prefix: String, reason: String): String = "$prefix: $reason"

    fun failure(prefix: String, error: Throwable): String =
        failure(prefix, error.message ?: error::class.simpleName ?: UnexpectedError)
}
