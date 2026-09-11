package id.walt.walletdemo.compose.logic

/** Only the metadata actually displayed by credential cards and their issuer overview. */
data class CredentialCardDisplayData(
    val id: String,
    val title: String,
    val issuer: String,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
    val logoUri: String? = null,
    val logoAltText: String? = null,
)

/** Card art does not require normalized claim groups or decoded claim images. */
fun CredentialSummary.toCardDisplayData(): CredentialCardDisplayData {
    val locales = platformPreferredLocales()
    return cardDisplayData(
        summary = this,
        issuerDisplay = StoredCredentialMetadataParser.issuerDisplay(metadataJson, locales),
        credentialDisplay = StoredCredentialMetadataParser.credentialDisplay(metadataJson, locales),
    )
}

fun CredentialDetails.toCardDisplayData(): CredentialCardDisplayData =
    cardDisplayData(summary, issuerDisplay, credentialDisplay)

private fun cardDisplayData(
    summary: CredentialSummary,
    issuerDisplay: WalletDemoMetadataDisplay?,
    credentialDisplay: WalletDemoMetadataDisplay?,
): CredentialCardDisplayData = CredentialCardDisplayData(
    id = summary.id,
    title = resolveCardTitle(summary.format, summary.credentialDataJson, credentialDisplay?.name, summary.label),
    issuer = issuerDisplay?.name?.takeIf { it.isNotBlank() }
        ?: summary.issuer?.takeIf { it.isNotBlank() }
        ?: CredentialDisplayText.Unknown,
    backgroundColor = credentialDisplay?.backgroundColor,
    backgroundImageUri = credentialDisplay?.backgroundImageUri,
    textColor = credentialDisplay?.textColor,
    logoUri = credentialDisplay?.logoUri,
    logoAltText = credentialDisplay?.logoAltText,
)
