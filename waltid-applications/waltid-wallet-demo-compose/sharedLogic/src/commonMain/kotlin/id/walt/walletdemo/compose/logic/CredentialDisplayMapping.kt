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

fun WalletDemoPresentationCredentialOption.toCredentialDetails(preferredLocales: List<String> = platformPreferredLocales()): CredentialDetails {
    val summary = CredentialSummary(
        id = selection.id,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        addedAt = null,
        credentialDataJson = credentialDataJson,
        metadataJson = metadataJson,
    )
    val parsed = CredentialDisplayNormalizer.toDetails(summary, preferredLocales)
    val requestedGroup = toRequestedDisclosureGroup(preferredLocales)

    return parsed.copy(groups = listOfNotNull(requestedGroup) + parsed.groups)
}

fun WalletDemoPresentationCredentialOption.toRequestedDisclosureGroup(preferredLocales: List<String> = platformPreferredLocales()): ClaimGroup? {
    val metadata = StoredCredentialMetadataParser.claims(metadataJson, preferredLocales)
    val requestedItems = disclosures.mapIndexed { index, disclosure ->
        val path = ClaimPath.disclosure(index = index, rawPath = disclosure.path, format = format)
        ClaimItem(
            path = path.itemPath,
            pathComponents = path.components,
            label = disclosure.label,
            value = CredentialDisplayNormalizer.toDisclosureValue(
                valueJson = disclosure.valueJson,
                displayValue = disclosure.displayValue,
                path = path,
                format = format,
            ),
            rawValue = disclosure.valueJson,
            roles = CredentialDisplayVocabulary.roles(path),
            labelSource = ClaimLabelSource.Request,
        ).withClaimMetadata(metadata, format, disclosurePathExpression(disclosure.path, format))
    }

    return requestedItems
        .takeIf { it.isNotEmpty() }
        ?.let { ClaimGroup(id = "requested", title = CredentialDisplayVocabulary.RequestedDisclosuresTitle, items = it) }
}
