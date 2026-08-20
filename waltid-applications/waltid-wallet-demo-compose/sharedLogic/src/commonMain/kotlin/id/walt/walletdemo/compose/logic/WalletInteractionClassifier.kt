package id.walt.walletdemo.compose.logic

enum class WalletInteractionKind {
    CredentialOffer,
    PresentationRequest,
}

sealed interface WalletInteractionClassification {
    data class Supported(
        val kind: WalletInteractionKind,
        val normalizedInput: String,
    ) : WalletInteractionClassification

    data class Invalid(val message: String) : WalletInteractionClassification

    data class Unsupported(val message: String) : WalletInteractionClassification
}

fun classifyWalletInteraction(rawInput: String): WalletInteractionClassification {
    val normalizedInput = rawInput.trim()
    if (normalizedInput.isEmpty()) {
        return WalletInteractionClassification.Invalid("Enter or scan a QR code.")
    }

    val scheme = normalizedInput
        .substringBefore(':', missingDelimiterValue = "")
        .lowercase()
    if (scheme.isEmpty()) {
        return WalletInteractionClassification.Invalid("This QR code does not contain a valid wallet link.")
    }

    val kind = when (scheme) {
        WalletDeepLinkScheme.CredentialOffer.scheme -> WalletInteractionKind.CredentialOffer
        WalletDeepLinkScheme.PresentationRequest.scheme -> WalletInteractionKind.PresentationRequest
        else -> null
    }
    return kind?.let { WalletInteractionClassification.Supported(it, normalizedInput) }
        ?: WalletInteractionClassification.Unsupported(
            "This code is not a supported credential offer or presentation request.",
        )
}
