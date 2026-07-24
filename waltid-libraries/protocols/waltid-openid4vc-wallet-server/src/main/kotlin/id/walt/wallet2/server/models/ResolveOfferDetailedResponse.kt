package id.walt.wallet2.server.models

import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.offers.TxCode
import id.walt.wallet2.handlers.WalletOfferResolution
import io.ktor.http.Url
import kotlinx.serialization.Serializable

/**
 * Enriched, UI-facing response for the isolated `credentials/receive/resolve-offer` endpoint.
 *
 * Carries the stateless offer summary (grant type, endpoints, pre-authorized code, transaction-code
 * requirement) together with issuer display metadata and offered-credential display/claims so a
 * consent-first wallet UI can render an issuer/credential preview from a single resolve call.
 */
@Serializable
data class ResolveOfferDetailedResponse(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val grantType: String? = null,
    val preAuthorizedCode: String? = null,
    val txCodeRequired: Boolean,
    val credentialEndpoint: Url,
    val tokenEndpoint: Url? = null,
    val nonceEndpoint: Url? = null,
    val issuer: OfferIssuerMetadata,
    val offeredCredentials: List<OfferedCredentialMetadata>,
    val transactionCode: OfferTransactionCodeRequirement? = null,
)

/** Typed credential issuer metadata for issuance-review UIs. */
@Serializable
data class OfferIssuerMetadata(
    val credentialIssuer: String,
    val display: OfferMetadataDisplay? = null,
)

/** Localized display metadata normalized from issuer/credential protocol metadata. */
@Serializable
data class OfferMetadataDisplay(
    val name: String? = null,
    val locale: String? = null,
    val logoUri: String? = null,
    val logoAltText: String? = null,
    val description: String? = null,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
)

/** Typed metadata for one credential configuration referenced by an offer (issuer-supported config, not credential data). */
@Serializable
data class OfferedCredentialMetadata(
    val configurationId: String,
    val format: String,
    val scope: String? = null,
    val vct: String? = null,
    val doctype: String? = null,
    val display: OfferMetadataDisplay? = null,
    val claims: List<OfferClaimMetadata> = emptyList(),
)

/** Display metadata for one claim declared by an offered credential configuration. */
@Serializable
data class OfferClaimMetadata(
    val path: List<String>,
    val mandatory: Boolean? = null,
    val displayName: String? = null,
)

/** Permitted transaction-code input character class. */
enum class OfferTransactionCodeInputMode { numeric, text }

/** Transaction-code metadata a wallet UI uses to collect an issuer-delivered code. */
@Serializable
data class OfferTransactionCodeRequirement(
    val inputMode: OfferTransactionCodeInputMode,
    val length: Int? = null,
    val description: String? = null,
)

/**
 * Projects a [WalletOfferResolution] into the serializable [ResolveOfferDetailedResponse].
 *
 * @param preferredLocales BCP 47 language tags used to pick localized display values.
 */
fun WalletOfferResolution.toDetailedResponse(
    preferredLocales: List<String> = emptyList(),
): ResolveOfferDetailedResponse = ResolveOfferDetailedResponse(
    credentialIssuer = summary.credentialIssuer,
    credentialConfigurationIds = summary.credentialConfigurationIds,
    grantType = summary.grantType,
    preAuthorizedCode = summary.preAuthorizedCode,
    txCodeRequired = summary.txCodeRequired,
    credentialEndpoint = summary.credentialEndpoint,
    tokenEndpoint = summary.tokenEndpoint,
    nonceEndpoint = summary.nonceEndpoint,
    issuer = OfferIssuerMetadata(
        credentialIssuer = issuerMetadata.credentialIssuer,
        display = issuerMetadata.display.selectPreferredByLocale(preferredLocales) { it.locale }?.toOfferDisplay(),
    ),
    offeredCredentials = offeredCredentials.map { offered ->
        val configuration = offered.configuration
        val metadata = configuration.credentialMetadata
        OfferedCredentialMetadata(
            configurationId = offered.credentialConfigurationId,
            format = configuration.format.value,
            scope = configuration.scope,
            vct = configuration.vct,
            doctype = configuration.doctype,
            display = metadata?.display.selectPreferredByLocale(preferredLocales) { it.locale }?.toOfferDisplay(),
            claims = metadata?.claims.orEmpty().map { claim ->
                OfferClaimMetadata(
                    path = claim.path,
                    mandatory = claim.mandatory,
                    displayName = claim.display.selectPreferredByLocale(preferredLocales) { it.locale }?.name,
                )
            },
        )
    },
    transactionCode = transactionCode?.toOfferRequirement(),
)

private fun IssuerDisplay.toOfferDisplay(): OfferMetadataDisplay =
    OfferMetadataDisplay(
        name = name,
        locale = locale,
        logoUri = logo?.uri,
        logoAltText = logo?.altText,
    )

private fun CredentialDisplay.toOfferDisplay(): OfferMetadataDisplay =
    OfferMetadataDisplay(
        name = name,
        locale = locale,
        logoUri = logo?.uri,
        logoAltText = logo?.altText,
        description = description,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImage?.uri,
        textColor = textColor,
    )

private fun TxCode.toOfferRequirement(): OfferTransactionCodeRequirement {
    val expectedLength = length
    require(expectedLength == null || expectedLength > 0) { "Transaction code length must be positive when provided" }
    val mode = when (inputMode ?: "numeric") {
        "numeric" -> OfferTransactionCodeInputMode.numeric
        "text" -> OfferTransactionCodeInputMode.text
        else -> throw IllegalArgumentException("Unsupported transaction code input mode: $inputMode")
    }
    return OfferTransactionCodeRequirement(
        inputMode = mode,
        length = expectedLength,
        description = description?.takeIf { it.isNotBlank() },
    )
}
