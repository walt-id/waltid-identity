package id.walt.walletdemo.compose.logic

data class WalletDemoOfferPreview(
    val credentialIssuer: String,
    val offeredCredentials: List<String>,
    val transactionCodeRequired: Boolean,
    val issuerMetadataJson: String? = null,
)

fun WalletDemoOfferPreview.offeredCredentialDetails(): List<CredentialDetails> =
    offeredCredentials.mapIndexed { index, offeredCredential ->
        CredentialSummary(
            id = "offer-preview-$index",
            format = "Credential offer",
            issuer = null,
            label = CredentialDisplayVocabulary.readableCredentialType(offeredCredential)
                ?: offeredCredential.ifBlank { "Credential" },
        ).toCredentialDetails()
    }
