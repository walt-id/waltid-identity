package id.walt.walletdemo.compose.logic

fun WalletDemoPresentationCredentialOption.resolvedCardTitle(): String =
    resolveCardTitle(
        format = format,
        credentialDataJson = credentialDataJson,
        displayName = presentationDisplayName(format = format, metadataJson = metadataJson, storedLabel = label),
        fallback = format,
    )

internal fun presentationDisplayName(
    format: String,
    metadataJson: String?,
    storedLabel: String?,
): String? =
    StoredCredentialMetadataParser.credentialDisplay(metadataJson, platformPreferredLocales())?.name
        ?: storedLabel?.trim()?.takeIf { it.isNotBlank() && !it.equals(format, ignoreCase = true) }

fun CredentialSummary.toCredentialDetails(): CredentialDetails =
    CredentialDisplayNormalizer.toDetails(this, platformPreferredLocales())

internal fun WalletDemoPresentationCredentialOption.toCredentialSummary(): CredentialSummary =
    CredentialSummary(
        id = selection.id,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        addedAt = null,
        credentialDataJson = credentialDataJson,
        metadataJson = metadataJson,
    )
fun WalletDemoPresentationCredentialOption.toCredentialDetails(preferredLocales: List<String> = platformPreferredLocales()): CredentialDetails {
    val parsed = CredentialDisplayNormalizer.toDetails(toCredentialSummary(), preferredLocales)
    val requestedGroup = toRequestedDisclosureGroup(preferredLocales)

    return parsed.copy(groups = listOfNotNull(requestedGroup) + parsed.groups)
}

fun WalletDemoPresentationCredentialOption.toRequestedDisclosureGroup(preferredLocales: List<String> = platformPreferredLocales()): ClaimGroup? {
    val metadata = StoredCredentialMetadataParser.claims(metadataJson, preferredLocales, format)
    val requestedItems = disclosures.mapIndexed { index, disclosure ->
        disclosure.toClaimItem(index, format, metadata)
    }

    return requestedItems
        .takeIf { it.isNotEmpty() }
        ?.let { ClaimGroup(id = "requested", title = CredentialDisplayVocabulary.RequestedDisclosuresTitle, items = it) }
}

internal fun WalletDemoPresentationDisclosure.toClaimItem(
    index: Int,
    format: String,
    metadata: List<WalletDemoCredentialClaimMetadata>,
): ClaimItem {
    val claimPath = ClaimPath.disclosure(index = index, rawPath = path, format = format)
    return ClaimItem(
        path = claimPath.itemPath,
        pathComponents = claimPath.components,
        label = label,
        value = CredentialDisplayNormalizer.toDisclosureValue(valueJson, displayValue, claimPath, format),
        rawValue = valueJson,
        roles = CredentialDisplayVocabulary.roles(claimPath),
        labelSource = ClaimLabelSource.Request,
    ).withClaimMetadata(metadata, format, disclosurePathExpression(path, format))
}
