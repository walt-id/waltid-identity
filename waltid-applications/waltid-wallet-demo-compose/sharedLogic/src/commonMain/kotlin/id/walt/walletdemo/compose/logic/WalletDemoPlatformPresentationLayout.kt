package id.walt.walletdemo.compose.logic

/** Height policy for a presentation review launched by the operating system. */
enum class WalletDemoPlatformPresentationLayout {
    /** One credential and no decision or warning that benefits from a larger review surface. */
    Compact,

    /** A review with choices, consequential context, or reader-authentication state. */
    Expanded,
}

/**
 * Chooses a platform presentation container from typed review facts.
 *
 * This does not inspect protocol names or raw request JSON. A request is promoted only when the
 * shared review model says the person has a real choice or additional context to understand.
 */
fun WalletDemoSharingReview.platformPresentationLayout(): WalletDemoPlatformPresentationLayout {
    val hasCredentialChoice = credentialOptions.size != 1 ||
        credentialOptions.any { it.multiple } ||
        credentialRequirements.any { requirement -> requirement.options.size > 1 }
    val hasDisclosureChoice = credentialOptions.any { option ->
        option.disclosures.any { it.selectable }
    }
    val hasConsequentialContext = request.transactionData.isNotEmpty()
    val hasReaderAuthenticationState = request.readerTrust != null

    return if (
        hasCredentialChoice ||
        hasDisclosureChoice ||
        hasConsequentialContext ||
        hasReaderAuthenticationState
    ) {
        WalletDemoPlatformPresentationLayout.Expanded
    } else {
        WalletDemoPlatformPresentationLayout.Compact
    }
}
