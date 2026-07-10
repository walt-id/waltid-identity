package id.walt.walletdemo.compose.logic

fun CredentialSummary.toCredentialDetails(): CredentialDetails =
    CredentialDisplayNormalizer.toDetails(this)

fun WalletDemoPresentationPreview.toVerifierDetails(): VerifierDetails =
    VerifierDetails(
        name = verifierName,
        clientId = clientId,
        responseUri = responseUri,
        state = state,
        nonce = nonce,
    )

fun WalletDemoPresentationCredentialOption.toCredentialDetails(): CredentialDetails {
    val summary = CredentialSummary(
        id = credentialId,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        addedAt = null,
        credentialDataJson = credentialDataJson,
    )
    val parsed = summary.toCredentialDetails()
    val requestedGroup = ClaimGroup(
        title = CredentialDisplayVocabulary.RequestedDisclosuresTitle,
        items = disclosures.mapIndexed { index, disclosure ->
            val path = ClaimPath.disclosure(index = index, rawPath = disclosure.path, label = disclosure.label)
            ClaimItem(
                path = path.itemPath,
                label = disclosure.label,
                value = CredentialDisplayNormalizer.toDisclosureValue(
                    valueJson = disclosure.valueJson,
                    displayValue = disclosure.displayValue,
                    path = path,
                ),
                rawValue = disclosure.valueJson,
                requested = true,
                shareable = disclosure.selectivelyDisclosable,
                roles = CredentialDisplayVocabulary.roles(path),
            )
        },
    )

    return parsed.copy(groups = listOf(requestedGroup) + parsed.groups)
}
