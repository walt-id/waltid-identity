package id.walt.wallet2.mobile

import id.walt.openid4vci.metadata.issuer.ClaimDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.offers.TxCode
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.handlers.WalletIssuanceOfferPreview
import id.walt.wallet2.handlers.WalletOfferPreviewResult
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Localized display metadata normalized from issuer, credential, or verifier protocol metadata.
 *
 * URI values are untrusted protocol input. Applications decide whether and how to load them and
 * must not treat display metadata as evidence of issuer or verifier trust.
 *
 * @property name Best localized human-readable name.
 * @property locale BCP 47 language tag associated with the selected display entry.
 * @property logoUri Issuer- or verifier-provided logo URI.
 * @property logoAltText Accessible alternative text for the logo.
 * @property description Human-readable credential description.
 * @property backgroundColor Suggested credential background color.
 * @property backgroundImageUri Suggested credential background image URI.
 * @property textColor Suggested credential text color.
 */
@OptIn(ExperimentalObjCName::class)
public data class MobileWalletMetadataDisplay(
    public val name: String?,
    public val locale: String?,
    public val logoUri: String?,
    public val logoAltText: String?,
    @ObjCName("descriptionText")
    public val description: String? = null,
    public val backgroundColor: String? = null,
    public val backgroundImageUri: String? = null,
    public val textColor: String? = null,
)

/**
 * Typed credential issuer metadata needed by mobile review interfaces.
 *
 * @property credentialIssuer Canonical credential issuer identifier from issuer metadata.
 * @property display Best localized display entry selected using [MobileWalletConfig.preferredLocales].
 */
public data class MobileWalletIssuerMetadata(
    public val credentialIssuer: String,
    public val display: MobileWalletMetadataDisplay?,
)

/**
 * Display metadata for one claim declared by an offered credential configuration.
 *
 * @property path Claim path relative to the credential root.
 * @property mandatory Whether the issuer declares that the claim is always included.
 * @property displayName Best localized human-readable claim name when available.
 */
public data class MobileWalletCredentialClaimMetadata(
    public val path: List<String>,
    public val mandatory: Boolean?,
    public val displayName: String?,
)

/**
 * Typed metadata for one credential configuration referenced by an offer.
 *
 * This describes the issuer's supported configuration before issuance; it is not credential data.
 *
 * @property configurationId Credential configuration identifier referenced by the offer.
 * @property format OpenID4VCI credential format.
 * @property scope Authorization scope associated with the configuration.
 * @property vct SD-JWT VC type identifier when present.
 * @property doctype ISO mdoc document type when present.
 * @property display Best localized credential display entry.
 * @property claims Claims declared by the credential configuration.
 */
public data class MobileWalletOfferedCredentialMetadata(
    public val configurationId: String,
    public val format: String,
    public val scope: String?,
    public val vct: String?,
    public val doctype: String?,
    public val display: MobileWalletMetadataDisplay?,
    public val claims: List<MobileWalletCredentialClaimMetadata>,
)

/** Input modes defined for an OpenID4VCI transaction code. */
public enum class MobileWalletTransactionCodeInputMode {
    Numeric,
    Text,
}

/**
 * Transaction-code metadata that a mobile wallet uses to collect issuer-delivered input.
 *
 * @property inputMode Permitted input character class; omitted protocol values default to numeric.
 * @property length Exact expected character count when the issuer provides one.
 * @property description Issuer-provided guidance for obtaining or entering the code.
 */
@OptIn(ExperimentalObjCName::class)
public data class MobileWalletTransactionCodeRequirement(
    public val inputMode: MobileWalletTransactionCodeInputMode,
    public val length: Int?,
    @ObjCName("descriptionText")
    public val description: String?,
)

/**
 * Result of resolving and retaining an OpenID4VCI credential offer for review.
 *
 * [MobileWallet.receive] reuses the retained resolution for the same wallet and offer, so the
 * metadata reviewed here and the offer accepted by the user belong to the same resolution.
 *
 * @property previewHandle Opaque handle required to accept or discard this reviewed offer.
 * @property issuer Typed issuer metadata selected for the configured locale preferences.
 * @property offeredCredentials Metadata for every credential configuration referenced by the offer.
 * @property transactionCode Input requirement when the issuer requires a separately delivered code.
 */
public data class MobileWalletOfferResolution(
    public val previewHandle: MobileWalletIssuancePreviewHandle,
    public val issuer: MobileWalletIssuerMetadata,
    public val offeredCredentials: List<MobileWalletOfferedCredentialMetadata>,
    public val transactionCode: MobileWalletTransactionCodeRequirement?,
)

/**
 * Typed OpenID4VP verifier metadata needed by mobile presentation-review interfaces.
 *
 * These values are supplied by the verifier and do not establish trust on their own.
 *
 * @property display Best localized verifier name and logo.
 * @property clientUri Verifier information-page URI.
 * @property policyUri Verifier privacy-policy URI.
 * @property termsOfServiceUri Verifier terms-of-service URI.
 */
public data class MobileWalletVerifierMetadata(
    public val display: MobileWalletMetadataDisplay?,
    public val clientUri: String?,
    public val policyUri: String?,
    public val termsOfServiceUri: String?,
)

/**
 * Response-encryption state selected for an OpenID4VP presentation request.
 *
 * This metadata describes how the wallet will protect the authorization response. It does not
 * establish verifier trust and does not expose cryptographic key material.
 */
public sealed interface MobileWalletResponseEncryption {
    /** Whether response encryption is required for the reviewed request. */
    public val isRequired: Boolean

    /** JWE `alg` value when encryption is required. */
    public val keyManagementAlgorithm: String?

    /** JWE `enc` value when encryption is required. */
    public val contentEncryptionAlgorithm: String?

    /** Verifier-provided identifier of the selected encryption key. */
    public val verifierKeyId: String?

    /** RFC 7638 thumbprint of the selected verifier encryption key. */
    public val verifierKeyThumbprint: String?

    /** The reviewed request does not require an encrypted authorization response. */
    public data object NotRequired : MobileWalletResponseEncryption {
        override val isRequired: Boolean = false
        override val keyManagementAlgorithm: String? = null
        override val contentEncryptionAlgorithm: String? = null
        override val verifierKeyId: String? = null
        override val verifierKeyThumbprint: String? = null
    }

    /**
     * The reviewed request requires an encrypted authorization response.
     *
     * @property keyManagementAlgorithm JWE `alg` value selected by the protocol implementation.
     * @property contentEncryptionAlgorithm JWE `enc` value selected by the protocol implementation.
     * @property verifierKeyId Verifier-provided identifier of the selected encryption key.
     * @property verifierKeyThumbprint RFC 7638 thumbprint of the selected verifier encryption key.
     */
    public data class Required(
        override val keyManagementAlgorithm: String,
        override val contentEncryptionAlgorithm: String,
        override val verifierKeyId: String?,
        override val verifierKeyThumbprint: String,
    ) : MobileWalletResponseEncryption {
        override val isRequired: Boolean = true
    }
}

internal fun WalletOfferPreviewResult.toMobileOfferResolution(
    preferredLocales: List<String>,
): MobileWalletOfferResolution = MobileWalletOfferResolution(
    previewHandle = MobileWalletIssuancePreviewHandle(previewHandle.value),
    issuer = MobileWalletIssuerMetadata(
        credentialIssuer = issuerMetadata.credentialIssuer,
        display = issuerMetadata.display.selectPreferred(preferredLocales)?.toMobileDisplay(),
    ),
    offeredCredentials = offeredCredentials.map { offeredCredential ->
        val configuration = offeredCredential.configuration
        val metadata = configuration.credentialMetadata
        MobileWalletOfferedCredentialMetadata(
            configurationId = offeredCredential.credentialConfigurationId,
            format = configuration.format.value,
            scope = configuration.scope,
            vct = configuration.vct,
            doctype = configuration.doctype,
            display = metadata?.display.selectPreferred(preferredLocales)?.toMobileDisplay(),
            claims = metadata?.claims.orEmpty().map { claim ->
                MobileWalletCredentialClaimMetadata(
                    path = claim.path,
                    mandatory = claim.mandatory,
                    displayName = claim.display.selectPreferred(preferredLocales)?.name,
                )
            },
        )
    },
    transactionCode = transactionCode?.toMobileRequirement(),
)

/**
 * Projects the review data retained by a durable issuance session into the legacy mobile offer
 * result without resolving the offer a second time. The session preview deliberately exposes only
 * display-safe fields, so fields not retained by the session remain absent here.
 */
internal fun WalletIssuanceOfferPreview.toMobileOfferResolution(): MobileWalletOfferResolution =
    MobileWalletOfferResolution(
        issuer = MobileWalletIssuerMetadata(
            credentialIssuer = issuer.identifier,
            display = MobileWalletMetadataDisplay(
                name = issuer.name,
                locale = issuer.locale,
                logoUri = issuer.logoUri,
                logoAltText = issuer.logoAltText,
            ),
        ),
        offeredCredentials = credentials.map { credential ->
            MobileWalletOfferedCredentialMetadata(
                configurationId = credential.configurationId,
                format = credential.format,
                scope = null,
                vct = null,
                doctype = null,
                display = MobileWalletMetadataDisplay(
                    name = credential.name,
                    locale = null,
                    logoUri = credential.logoUri,
                    logoAltText = null,
                    description = credential.descriptionText,
                ),
                claims = emptyList(),
            )
        },
        transactionCode = transactionCode?.let {
            val mode = when (it.inputMode ?: "numeric") {
                "numeric" -> MobileWalletTransactionCodeInputMode.Numeric
                "text" -> MobileWalletTransactionCodeInputMode.Text
                else -> throw IllegalArgumentException("Unsupported transaction code input mode: ${it.inputMode}")
            }
            MobileWalletTransactionCodeRequirement(
                inputMode = mode,
                length = it.length,
                description = it.descriptionText,
            )
        },
    )

@OptIn(ExperimentalSerializationApi::class)
internal fun ClientMetadata.toMobileVerifierMetadata(
    preferredLocales: List<String>,
): MobileWalletVerifierMetadata {
    val name = selectLocalizedValue(clientName, clientNameI18n, preferredLocales)
    val logo = selectLocalizedValue(logoUri, logoUriI18n, preferredLocales)
    return MobileWalletVerifierMetadata(
        display = if (name.value == null && logo.value == null) {
            null
        } else {
            MobileWalletMetadataDisplay(
                name = name.value,
                locale = if (name.value != null) name.locale else logo.locale,
                logoUri = logo.value,
                logoAltText = null,
            )
        },
        clientUri = selectLocalizedValue(clientUri, clientUriI18n, preferredLocales).value,
        policyUri = selectLocalizedValue(policyUri, policyUriI18n, preferredLocales).value,
        termsOfServiceUri = selectLocalizedValue(tosUri, tosUriI18n, preferredLocales).value,
    )
}

internal fun TxCode.toMobileRequirement(): MobileWalletTransactionCodeRequirement {
    val expectedLength = length
    require(expectedLength == null || expectedLength > 0) { "Transaction code length must be positive when provided" }
    val mode = when (inputMode ?: "numeric") {
        "numeric" -> MobileWalletTransactionCodeInputMode.Numeric
        "text" -> MobileWalletTransactionCodeInputMode.Text
        else -> throw IllegalArgumentException("Unsupported transaction code input mode: $inputMode")
    }
    return MobileWalletTransactionCodeRequirement(
        inputMode = mode,
        length = expectedLength,
        description = description?.takeIf { it.isNotBlank() },
    )
}

internal fun ResponseEncryption.Metadata?.toMobileResponseEncryption(): MobileWalletResponseEncryption =
    this?.let {
        MobileWalletResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    } ?: MobileWalletResponseEncryption.NotRequired

private fun IssuerDisplay.toMobileDisplay(): MobileWalletMetadataDisplay =
    MobileWalletMetadataDisplay(
        name = name,
        locale = locale,
        logoUri = logo?.uri,
        logoAltText = logo?.altText,
    )

private fun CredentialDisplay.toMobileDisplay(): MobileWalletMetadataDisplay =
    MobileWalletMetadataDisplay(
        name = name,
        locale = locale,
        logoUri = logo?.uri,
        logoAltText = logo?.altText,
        description = description,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImage?.uri,
        textColor = textColor,
    )

private data class LocalizedValue(
    val locale: String?,
    val value: String?,
)

private fun selectLocalizedValue(
    base: String?,
    localized: Map<String, String>,
    preferredLocales: List<String>,
): LocalizedValue = buildList {
    base?.takeIf { it.isNotBlank() }?.let { add(LocalizedValue(locale = null, value = it)) }
    localized.forEach { (locale, value) ->
        value.takeIf { it.isNotBlank() }?.let { add(LocalizedValue(locale = locale, value = it)) }
    }
}.selectPreferred(preferredLocales) ?: LocalizedValue(locale = null, value = null)

private fun <T> List<T>?.selectPreferred(
    preferredLocales: List<String>,
    locale: (T) -> String?,
): T? {
    val entries = this.orEmpty()
    if (entries.isEmpty()) return null
    val preferences = preferredLocales.mapNotNull(::normalizeLocale).distinct()

    preferences.forEach { preferred ->
        localeLookupTags(preferred).forEach { candidate ->
            entries.firstOrNull { normalizeLocale(locale(it)) == candidate }?.let { return it }
        }
    }
    return entries.firstOrNull { locale(it).isNullOrBlank() } ?: entries.first()
}

private fun List<IssuerDisplay>?.selectPreferred(preferredLocales: List<String>): IssuerDisplay? =
    selectPreferred(preferredLocales, IssuerDisplay::locale)

private fun List<CredentialDisplay>?.selectPreferred(preferredLocales: List<String>): CredentialDisplay? =
    selectPreferred(preferredLocales, CredentialDisplay::locale)

private fun List<ClaimDisplay>?.selectPreferred(preferredLocales: List<String>): ClaimDisplay? =
    selectPreferred(preferredLocales, ClaimDisplay::locale)

private fun List<LocalizedValue>.selectPreferred(preferredLocales: List<String>): LocalizedValue? =
    selectPreferred(preferredLocales, LocalizedValue::locale)

private fun normalizeLocale(locale: String?): String? = locale
    ?.trim()
    ?.replace('_', '-')
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

private fun localeLookupTags(locale: String): List<String> = buildList {
    val subtags = locale.split('-').filter(String::isNotEmpty).toMutableList()
    while (subtags.isNotEmpty()) {
        add(subtags.joinToString("-"))
        subtags.removeAt(subtags.lastIndex)
        if (subtags.lastOrNull()?.length == 1) subtags.removeAt(subtags.lastIndex)
    }
}
