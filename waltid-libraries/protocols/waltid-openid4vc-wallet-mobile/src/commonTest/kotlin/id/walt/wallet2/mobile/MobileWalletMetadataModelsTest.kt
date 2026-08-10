package id.walt.wallet2.mobile

import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileWalletMetadataModelsTest {
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
