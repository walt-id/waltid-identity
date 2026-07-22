package id.walt.walletdemo.compose.logic

fun CredentialSummary.toCredentialDetails(): CredentialDetails =
    CredentialDisplayNormalizer.toDetails(this)

fun WalletDemoPresentationCredentialOption.toCredentialDetails(): CredentialDetails {
    val summary = CredentialSummary(
        id = selection.id,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        addedAt = null,
        credentialDataJson = credentialDataJson,
    )
    val parsed = summary.toCredentialDetails()
    val requestedGroup = toRequestedDisclosureGroup()

    return parsed.copy(groups = listOfNotNull(requestedGroup) + parsed.groups)
}

fun WalletDemoPresentationCredentialOption.toRequestedDisclosureGroup(): ClaimGroup? {
    val requestedItems = disclosures.mapIndexed { index, disclosure ->
        val path = ClaimPath.disclosure(index = index, rawPath = disclosure.path, label = disclosure.label)
        ClaimItem(
            path = path.itemPath,
            pathComponents = path.components,
            label = disclosure.label,
            value = CredentialDisplayNormalizer.toDisclosureValue(
                valueJson = disclosure.valueJson,
                displayValue = disclosure.displayValue,
                path = path,
            ),
            rawValue = disclosure.valueJson,
            roles = CredentialDisplayVocabulary.roles(path),
        )
    }

    return requestedItems
        .takeIf { it.isNotEmpty() }
        ?.let { ClaimGroup(title = CredentialDisplayVocabulary.RequestedDisclosuresTitle, items = it) }
}
