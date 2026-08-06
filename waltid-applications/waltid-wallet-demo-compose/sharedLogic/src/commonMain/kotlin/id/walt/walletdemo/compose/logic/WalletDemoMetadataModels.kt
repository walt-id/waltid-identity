package id.walt.walletdemo.compose.logic

data class WalletDemoMetadataDisplay(
    val name: String?,
    val logoUri: String?,
    val logoAltText: String?,
    val description: String? = null,
)

data class WalletDemoIssuerMetadata(
    val credentialIssuer: String,
    val display: WalletDemoMetadataDisplay?,
)

data class WalletDemoCredentialClaimMetadata(
    val path: List<String>,
    val mandatory: Boolean?,
    val displayName: String?,
)

data class WalletDemoCredentialClaimDisplay(
    val label: String,
    val inclusion: String,
)

data class WalletDemoCredentialClaimDisplayGroup(
    val title: String,
    val claims: List<WalletDemoCredentialClaimDisplay>,
)

data class WalletDemoOfferedCredentialMetadata(
    val configurationId: String,
    val format: String,
    val scope: String?,
    val vct: String?,
    val doctype: String?,
    val display: WalletDemoMetadataDisplay?,
    val claims: List<WalletDemoCredentialClaimMetadata>,
)

fun WalletDemoOfferedCredentialMetadata.claimDisplayGroups(): List<WalletDemoCredentialClaimDisplayGroup> {
    val entries = claims.mapIndexed { index, claim ->
        val semantics = MdocClaimDisplaySemantics.describe(format = format, path = claim.path)
        OfferClaimDisplayEntry(
            group = semantics?.group,
            sortOrder = semantics?.sortOrder ?: Int.MAX_VALUE,
            sourceOrder = index,
            display = WalletDemoCredentialClaimDisplay(
                label = claim.displayName?.takeIf { it.isNotBlank() }
                    ?: semantics?.label
                    ?: CredentialDisplayVocabulary.humanizedClaimLabel(claim.path.lastOrNull().orEmpty()),
                inclusion = if (claim.mandatory == true) "Always included" else "May be included",
            ),
        )
    }
    return entries
        .groupBy { it.group }
        .entries
        .sortedBy { it.key?.order ?: 0 }
        .map { (group, claims) ->
            WalletDemoCredentialClaimDisplayGroup(
                title = group?.title ?: "Credential claims",
                claims = claims
                    .sortedWith(compareBy(OfferClaimDisplayEntry::sortOrder, OfferClaimDisplayEntry::sourceOrder))
                    .map(OfferClaimDisplayEntry::display),
            )
        }
}

private data class OfferClaimDisplayEntry(
    val group: MdocClaimGroup?,
    val sortOrder: Int,
    val sourceOrder: Int,
    val display: WalletDemoCredentialClaimDisplay,
)

enum class WalletDemoTransactionCodeInputMode {
    Numeric,
    Text,
}

data class WalletDemoTransactionCodeRequirement(
    val inputMode: WalletDemoTransactionCodeInputMode,
    val length: Int?,
    val description: String?,
) {
    fun normalizeInput(value: String): String {
        val normalized = when (inputMode) {
            WalletDemoTransactionCodeInputMode.Numeric -> value.filter { it in '0'..'9' }
            WalletDemoTransactionCodeInputMode.Text -> value
        }
        return length?.let(normalized::take) ?: normalized
    }

    fun accepts(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            (length == null || normalized.length == length) &&
            (inputMode != WalletDemoTransactionCodeInputMode.Numeric || normalized.all { it in '0'..'9' })
    }
}

data class WalletDemoVerifierMetadata(
    val display: WalletDemoMetadataDisplay?,
    val clientUri: String?,
    val policyUri: String?,
    val termsOfServiceUri: String?,
)
