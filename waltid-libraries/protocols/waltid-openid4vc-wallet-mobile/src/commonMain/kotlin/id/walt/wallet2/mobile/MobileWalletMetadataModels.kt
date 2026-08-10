package id.walt.wallet2.mobile

import id.walt.verifier.openid.models.authorization.ClientMetadata
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

internal fun ResponseEncryption.Metadata?.toMobileResponseEncryption(): MobileWalletResponseEncryption =
    this?.let {
        MobileWalletResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    } ?: MobileWalletResponseEncryption.NotRequired

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
