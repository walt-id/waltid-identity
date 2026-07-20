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

data class WalletDemoOfferedCredentialMetadata(
    val configurationId: String,
    val format: String,
    val vct: String?,
    val doctype: String?,
    val display: WalletDemoMetadataDisplay?,
    val claims: List<WalletDemoCredentialClaimMetadata>,
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
