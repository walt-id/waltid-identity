package id.walt.walletdemo.compose.logic

/** A display projection of the SDK preview; it never decides which claims are valid to submit. */
data class SharingInformationField(
    val item: ClaimItem,
    val included: Boolean,
    val alwaysIncluded: Boolean,
    val optionalSelections: Set<WalletDemoPresentationDisclosureSelection>,
    val selections: Set<WalletDemoPresentationDisclosureSelection>,
)

data class SharingInformationGroup(
    val options: List<WalletDemoPresentationCredentialOption>,
    val fields: List<SharingInformationField>,
) {
    val option: WalletDemoPresentationCredentialOption get() = options.first()
}

/** One credential can satisfy several queries. Show its actual union once, retaining query identities. */
fun WalletDemoSharingReview.informationToShare(
    credentials: Set<WalletDemoPresentationCredentialSelection>,
    disclosures: Set<WalletDemoPresentationDisclosureSelection>,
): List<SharingInformationGroup> = credentialOptions.filter { it.selection in credentials }
    .groupBy { it.credentialId }.values.map { options ->
        SharingInformationGroup(options, options.flatMap { it.informationFields(disclosures) }
            .groupBy { disclosurePathExpression(it.selections.first().path, options.first().format).segments }.values.map { fields ->
                val included = fields.any { it.included }
                val fixed = fields.any { it.alwaysIncluded || (it.included && it.optionalSelections.isEmpty()) }
                fields.first().copy(
                    included = included,
                    alwaysIncluded = fields.any { it.alwaysIncluded },
                    optionalSelections = if (fixed) emptySet() else fields.flatMap { it.optionalSelections }.toSet(),
                    selections = fields.flatMap { it.selections }.toSet(),
                )
            }.sortedBy { it.item.displayOrder ?: Int.MAX_VALUE })
    }

fun WalletDemoPresentationCredentialOption.informationFields(
    selectedDisclosures: Set<WalletDemoPresentationDisclosureSelection>,
): List<SharingInformationField> {
    val items = toRequestedDisclosureGroup()?.items.orEmpty()
    return disclosures.mapIndexedNotNull { index, disclosure ->
        val item = items.getOrNull(index) ?: return@mapIndexedNotNull null
        // Protocol metadata is not personal information; keep requested metadata visible.
        if (!disclosure.requested &&
            CredentialDisplayVocabulary.groupKind(ClaimPath.disclosure(index, disclosure.path, format), format) == ClaimGroupKind.Technical)
            return@mapIndexedNotNull null
        val selection = WalletDemoPresentationDisclosureSelection(queryId, credentialId, disclosure.path)
        SharingInformationField(item,
            included = !disclosure.selectivelyDisclosable || disclosure.required || selection in selectedDisclosures,
            alwaysIncluded = !disclosure.requested,
            optionalSelections = if (disclosure.selectable) setOf(selection) else emptySet(),
            selections = setOf(selection))
    }
}

/** Brief warning only for unavoidable personal information beyond the requested fields. */
fun WalletDemoPresentationCredentialOption.additionalInformationLabels(): List<String> =
    informationFields(emptySet()).filter { it.alwaysIncluded }.map { it.item.label }.distinct()
