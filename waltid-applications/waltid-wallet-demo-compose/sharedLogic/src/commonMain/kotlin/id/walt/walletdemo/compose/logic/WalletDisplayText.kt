package id.walt.walletdemo.compose.logic

internal enum class WalletDeepLinkScheme(val scheme: String) {
    CredentialOffer("openid-credential-offer"),
    PresentationRequest("openid4vp"),
    AuthorizationCallback("openid"),
    ;

    companion object {
        fun parse(rawUrl: String): WalletDeepLinkScheme? {
            val scheme = rawUrl.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
                ?: return null
            entries.firstOrNull { it.scheme == scheme }?.let { return it }
            val isHttp = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
            val hasCode = rawUrl.substringAfter('?', "").split('&', '#').any { it.startsWith("code=") }
            return if (isHttp && hasCode) AuthorizationCallback else null
        }
    }
}

internal object WalletDisplayText {
    const val ReviewPresentationRequest = "Review presentation request"
    const val ReviewPresentationError = "Review presentation error"
    const val StartingWallet = "Starting wallet..."
    const val ResolvingCredentialOffer = "Resolving credential offer..."
    const val ReviewCredentialOffer = "Review credential offer"
    const val CredentialOfferDeclined = "Credential offer declined"
    const val ReceivingCredential = "Receiving credential..."
    const val ResolvingPresentation = "Resolving presentation..."
    const val PresentingCredential = "Presenting credential..."
    const val DecliningPresentation = "Declining presentation..."
    const val SetupPin = "Set up a PIN to unlock the wallet"
    const val UnlockPin = "Enter PIN to unlock the wallet"
    const val WalletNotReady = "Wallet not ready"
    const val BootstrappingWallet = "Bootstrapping wallet..."
    const val WalletReady = "Wallet ready"
    const val PresentationSent = "Presentation sent"
    const val VerifierNotified = "Verifier notified"
    const val PresentationReviewCancelled = "Presentation review cancelled"
    const val PresentationRejected = "Presentation rejected"
    const val PresentationFinishedWithoutVerifierConfirmation = "Presentation finished without verifier confirmation"
    const val RejectionFinishedWithoutVerifierConfirmation = "Rejection finished without verifier confirmation"
    const val PresentationContinuationFailed = "Could not deliver the verifier response"
    const val ReceiveFailed = "Receive failed"
    const val PreviewFailed = "Preview failed"
    const val PresentFailed = "Present failed"
    const val RejectFailed = "Reject failed"
    const val BootstrapFailed = "Bootstrap failed"
    const val ResetWalletFailed = "Reset wallet failed"
    const val SigningProtectionChangeFailed = "Signing protection change failed"
    const val DeleteCredentialFailed = "Delete credential failed"
    const val InvalidOfferUrl = "invalid offer URL"
    const val InvalidRequestUrl = "invalid request URL"
    const val SelectCredentialForEveryRequest = "select a credential for every requested credential"
    const val PinMustContain4To8Digits = "PIN must contain 4 to 8 digits"
    const val PinConfirmationDoesNotMatch = "PIN confirmation does not match"
    const val WrongPin = "Wrong PIN"
    const val UnlockWithBiometrics = "Unlock the wallet"
    const val EnableBiometricUnlock = "Enable biometric unlock"
    const val BiometricUnlockNotAuthorized = "Biometric unlock was not authorized. Use the PIN instead."
    const val BiometricNotEnrolled = "Set up a strong biometric in device settings, then try again."
    const val BiometricUnavailable = "Strong biometric authentication is not available on this device."
    const val SigningProtectionUnsupported = "This signing protection is not supported on this device."
    const val ReceivedCredentialsUnavailable = "received credentials are not available locally"
    const val UnexpectedError = "Unexpected error"

    fun receivedCredentials(count: Int): String = "Received $count credential(s)"

    fun failure(prefix: String, reason: String): String = "$prefix: $reason"

    fun failure(prefix: String, error: Throwable): String =
        failure(prefix, error.message ?: error::class.simpleName ?: UnexpectedError)

    fun biometricSigningUnavailable(
        availability: WalletDemoSigningProtectionAvailability,
        canChooseNoBiometricSigning: Boolean,
    ): String {
        val (reason, recovery) = when (availability) {
            WalletDemoSigningProtectionAvailability.Available -> return ""
            WalletDemoSigningProtectionAvailability.BiometricNotEnrolled ->
                "no strong biometric is enrolled" to "you enroll a strong biometric"
            WalletDemoSigningProtectionAvailability.BiometricUnavailable ->
                "strong biometric authentication is unavailable" to
                    "strong biometric authentication becomes available"
            WalletDemoSigningProtectionAvailability.Unsupported ->
                "the device cannot currently authorize it" to
                    "this device can authorize biometric signing"
        }
        val alternative = if (canChooseNoBiometricSigning) {
            " or you choose no biometric signing in Settings."
        } else {
            ". Biometric signing is required by app configuration."
        }
        return "This wallet uses biometric signing, but $reason. " +
            "Issuance and presentation signing will fail until $recovery$alternative"
    }
}
