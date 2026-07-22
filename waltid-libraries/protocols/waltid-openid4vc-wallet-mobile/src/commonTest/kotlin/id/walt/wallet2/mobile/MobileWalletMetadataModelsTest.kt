package id.walt.wallet2.mobile

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.ClaimDisplay
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.offers.TxCode
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.handlers.IssuancePreviewHandle
import id.walt.wallet2.handlers.WalletOfferPreviewResult
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MobileWalletMetadataModelsTest {
    @Test
    fun offerPreviewMapsActualMetadataWithLocaleFallbacks() {
        val credential = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            scope = "pid",
            vct = "urn:example:pid",
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(name = "Personalausweis", locale = "de"),
                    CredentialDisplay(
                        name = "US Person ID",
                        locale = "en",
                        description = "Government-issued identity credential",
                    ),
                ),
                claims = listOf(
                    ClaimDescription(
                        path = listOf("given_name"),
                        mandatory = true,
                        display = listOf(ClaimDisplay(name = "Given name", locale = "en")),
                    )
                ),
            ),
        )
        val issuer = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            display = listOf(
                IssuerDisplay(name = "German issuer", locale = "de"),
                IssuerDisplay(name = "British issuer", locale = "en-GB"),
                IssuerDisplay(name = "English issuer", locale = "en"),
            ),
            credentialConfigurationsSupported = mapOf("pid" to credential),
        )

        val result = WalletOfferPreviewResult(
            previewHandle = IssuancePreviewHandle("metadata-locale-preview"),
            issuerMetadata = issuer,
            offeredCredentials = listOf(
                OfferedCredentialResolver.ResolvedCredentialOffer("pid", credential)
            ),
            transactionCode = TxCode(inputMode = "text", length = 8, description = "Check your email"),
        ).toMobileOfferResolution(listOf("en-AU", "de"))

        assertEquals("English issuer", result.issuer.display?.name)
        assertEquals("US Person ID", result.offeredCredentials.single().display?.name)
        assertEquals("Government-issued identity credential", result.offeredCredentials.single().display?.description)
        assertEquals("Given name", result.offeredCredentials.single().claims.single().displayName)
        assertEquals("urn:example:pid", result.offeredCredentials.single().vct)
        assertEquals(MobileWalletTransactionCodeInputMode.Text, result.transactionCode?.inputMode)
    }

    @Test
    fun localeLookupPreservesScriptAndRemovesExtensionsProgressively() {
        val issuer = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            display = listOf(
                IssuerDisplay(name = "Simplified Chinese", locale = "zh-Hans"),
                IssuerDisplay(name = "Traditional Chinese", locale = "zh-Hant"),
                IssuerDisplay(name = "German", locale = "de-DE"),
            ),
            credentialConfigurationsSupported = emptyMap(),
        )
        val preview = WalletOfferPreviewResult(
            previewHandle = IssuancePreviewHandle("metadata-script-preview"),
            issuerMetadata = issuer,
            offeredCredentials = emptyList(),
            transactionCode = null,
        )

        assertEquals(
            "Traditional Chinese",
            preview.toMobileOfferResolution(listOf("zh-Hant-TW")).issuer.display?.name,
        )
        assertEquals(
            "German",
            preview.toMobileOfferResolution(listOf("de-DE-u-co-phonebk")).issuer.display?.name,
        )
    }

    @Test
    fun localeLookupFallsBackToUnlocalizedThenFirstEntry() {
        fun resolve(display: List<IssuerDisplay>): String? = WalletOfferPreviewResult(
            previewHandle = IssuancePreviewHandle("metadata-fallback-preview"),
            issuerMetadata = CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                display = display,
                credentialConfigurationsSupported = emptyMap(),
            ),
            offeredCredentials = emptyList(),
            transactionCode = null,
        ).toMobileOfferResolution(listOf("en-AU")).issuer.display?.name

        assertEquals(
            "Default",
            resolve(listOf(IssuerDisplay(name = "German", locale = "de"), IssuerDisplay(name = "Default"))),
        )
        assertEquals(
            "German",
            resolve(listOf(IssuerDisplay(name = "German", locale = "de"), IssuerDisplay(name = "French", locale = "fr"))),
        )
    }

    @Test
    fun transactionCodeMetadataMapsProtocolDefaultsAndConstraints() {
        val result = TxCode(
            inputMode = null,
            length = 6,
            description = "Enter the code from your issuer",
        ).toMobileRequirement()

        assertEquals(MobileWalletTransactionCodeInputMode.Numeric, result.inputMode)
        assertEquals(6, result.length)
        assertEquals("Enter the code from your issuer", result.description)
    }

    @Test
    fun invalidTransactionCodeMetadataIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            TxCode(inputMode = "numeric", length = 0).toMobileRequirement()
        }
        assertFailsWith<IllegalArgumentException> {
            TxCode(inputMode = "unsupported").toMobileRequirement()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun verifierMetadataUsesPreferredLocalizedValuesConsistently() {
        val result = ClientMetadata(
            clientName = "Default verifier",
            clientNameI18n = mapOf("de" to "Deutscher Verifizierer", "en-US" to "US Verifier"),
            logoUri = "https://verifier.example/default.png",
            logoUriI18n = mapOf("de-DE" to "https://verifier.example/de.png"),
            clientUriI18n = mapOf("de" to "https://verifier.example/de"),
            policyUriI18n = mapOf("de" to "https://verifier.example/de/privacy"),
            tosUriI18n = mapOf("de" to "https://verifier.example/de/terms"),
        ).toMobileVerifierMetadata(listOf("de-AT"))

        assertEquals("Deutscher Verifizierer", result.display?.name)
        assertEquals("de", result.display?.locale)
        assertEquals("https://verifier.example/default.png", result.display?.logoUri)
        assertEquals("https://verifier.example/de", result.clientUri)
        assertEquals("https://verifier.example/de/privacy", result.policyUri)
        assertEquals("https://verifier.example/de/terms", result.termsOfServiceUri)
    }

    @Test
    fun responseEncryptionMapsToTypedMobileStates() {
        assertEquals(
            MobileWalletResponseEncryption.NotRequired,
            null.toMobileResponseEncryption(),
        )
        assertEquals(
            MobileWalletResponseEncryption.Required(
                keyManagementAlgorithm = "ECDH-ES",
                contentEncryptionAlgorithm = "A256GCM",
                verifierKeyId = "verifier-key-1",
                verifierKeyThumbprint = "thumbprint-1",
            ),
            ResponseEncryption.Metadata(
                keyManagementAlgorithm = "ECDH-ES",
                contentEncryptionAlgorithm = "A256GCM",
                verifierKeyId = "verifier-key-1",
                verifierKeyThumbprint = "thumbprint-1",
            ).toMobileResponseEncryption(),
        )
    }
}
