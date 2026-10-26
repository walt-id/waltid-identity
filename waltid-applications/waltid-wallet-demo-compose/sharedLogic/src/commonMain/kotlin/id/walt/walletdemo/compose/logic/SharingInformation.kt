package id.walt.walletdemo.compose.logic

/** A display projection of the SDK preview; it never decides which claims are valid to submit. */
@ConsistentCopyVisibility
data class SharingInformationField internal constructor(
    val item: ClaimItem,
    internal val path: List<ClaimPathExpression.Segment>,
    val included: Boolean,
    val alwaysIncluded: Boolean,
    val optionalSelections: Set<WalletDemoPresentationDisclosureSelection>,
    val selections: Set<WalletDemoPresentationDisclosureSelection>,
)

/** Parsed once per option, independently of the user's changing selection. */
data class SharingCredentialInformation(
    val option: WalletDemoPresentationCredentialOption,
    val summary: CredentialCardDisplayData,
    val fields: List<SharingInformationField>,
) {
    val additionalInformationLabels: List<String> = fields.filter { it.alwaysIncluded }.map { it.item.label }.distinct()
}

data class SharingInformationGroup(
    val options: List<WalletDemoPresentationCredentialOption>,
    val summary: CredentialCardDisplayData,
    val fields: List<SharingInformationField>,
)

/** One credential can satisfy several queries. Show its actual union once, retaining query identities. */
fun List<SharingCredentialInformation>.informationToShare(
    credentials: Set<WalletDemoPresentationCredentialSelection>,
    disclosures: Set<WalletDemoPresentationDisclosureSelection>,
): List<SharingInformationGroup> = filter { it.option.selection in credentials }
    .groupBy { it.option.credentialId }.values.map { information ->
        SharingInformationGroup(information.map { it.option }, information.first().summary,
            information.flatMap { it.fields }.groupBy { it.path }.values.map { fields ->
                val included = fields.any { it.included || it.optionalSelections.any(disclosures::contains) }
                val fixed = fields.any { it.alwaysIncluded || (it.included && it.optionalSelections.isEmpty()) }
                fields.first().copy(
                    included = included,
                    alwaysIncluded = fields.any { it.alwaysIncluded },
                    optionalSelections = if (fixed) emptySet() else fields.flatMap { it.optionalSelections }.toSet(),
                    selections = fields.flatMap { it.selections }.toSet(),
                )
            }.sortedBy { it.item.displayOrder ?: Int.MAX_VALUE })
    }

fun WalletDemoPresentationCredentialOption.toSharingInformation(): SharingCredentialInformation {
    val details = toCredentialDetails()
    val items = details.groups.firstOrNull { it.id == "requested" }?.items.orEmpty()
    val fields = disclosures.mapIndexedNotNull { index, disclosure ->
        val item = items.getOrNull(index) ?: return@mapIndexedNotNull null
        val path = disclosurePathExpression(disclosure.path, format).segments
        if (!disclosure.requested && isCredentialSummaryClaim(path, format)) return@mapIndexedNotNull null
        val selection = WalletDemoPresentationDisclosureSelection(queryId, credentialId, disclosure.path)
        SharingInformationField(item, path,
            included = !disclosure.selectivelyDisclosable || disclosure.required,
            alwaysIncluded = !disclosure.requested,
            optionalSelections = if (disclosure.selectable) setOf(selection) else emptySet(),
            selections = setOf(selection))
    }
    return SharingCredentialInformation(this, details.toCardDisplayData(), fields)
}

/** Only the issuer/type already represented by the credential selector may be summarized away.
 * Technical grouping is not an omission policy: identifiers and custom nested claims stay visible. */
internal fun isCredentialSummaryClaim(path: List<ClaimPathExpression.Segment>, format: String): Boolean {
    val keys = path.map { (it as? ClaimPathExpression.Segment.Key)?.value ?: return false }
    return when (format) {
        "dc+sd-jwt", "vc+sd-jwt" -> keys == listOf("iss") || keys == listOf("vct")
        "jwt_vc", "jwt_vc_json", "jwt_vc_json-ld" ->
            keys == listOf("iss") || keys == listOf("vc", "issuer") || keys == listOf("vc", "type")
        "ldp_vc" -> keys == listOf("issuer") || keys == listOf("type")
        else -> false
    }
}
