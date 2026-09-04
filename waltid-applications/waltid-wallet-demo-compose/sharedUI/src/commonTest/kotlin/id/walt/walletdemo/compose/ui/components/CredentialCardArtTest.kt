package id.walt.walletdemo.compose.ui.components

import id.walt.walletdemo.compose.logic.CredentialCardDisplayData
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialCardArtTest {

    @Test
    fun pendingMetadataArtDoesNotShowConstructedFallback() {
        assertFalse(showConstructedCardArtOverlay(CredentialCardMetadataArtState.Pending))
        assertFalse(showConstructedCardArtOverlay(CredentialCardMetadataArtState.Ready))
    }

    @Test
    fun constructedFallbackShowsWhenArtIsMissingOrRejected() {
        assertTrue(showConstructedCardArtOverlay(CredentialCardMetadataArtState.Absent))
        assertTrue(showConstructedCardArtOverlay(CredentialCardMetadataArtState.Rejected))
    }

    @Test
    fun storedCredentialLogoIsPreservedInCardArt() {
        val art = CredentialCardDisplayData(
            id = "credential-id",
            title = "Example credential",
            credentialType = null,
            format = "jwt_vc_json",
            issuer = "Example issuer",
            holderName = null,
            validity = null,
            portrait = null,
            logoUri = "https://issuer.example/credential.png",
            logoAltText = "Credential logo",
        ).toCardArt()

        assertEquals("https://issuer.example/credential.png", art.logoUri)
        assertEquals("Credential logo", art.logoAltText)
    }

    @Test
    fun offeredCredentialLogoIsPreservedInCardArt() {
        val art = WalletDemoMetadataDisplay(
            name = "Example credential",
            logoUri = "https://issuer.example/credential.png",
            logoAltText = "Credential logo",
        ).toCardArt(id = "credential-configuration-id", fallbackName = "Fallback")

        assertEquals("https://issuer.example/credential.png", art.logoUri)
        assertEquals("Credential logo", art.logoAltText)
    }

    @Test
    fun credentialLogoUsesHttpsMetadataAndOtherwiseFallsBack() {
        assertEquals(
            CredentialCardLogoSource.Metadata("https://issuer.example/credential.png"),
            credentialCardLogoSource("https://issuer.example/credential.png"),
        )
        assertEquals(CredentialCardLogoSource.BundledWalt, credentialCardLogoSource("http://issuer.example/logo.png"))
        assertEquals(
            CredentialCardLogoSource.Metadata("https://issuer.example/logo.svg"),
            credentialCardLogoSource("https://issuer.example/logo.svg"),
        )
        assertEquals(CredentialCardLogoSource.BundledWalt, credentialCardLogoSource(null))
    }

    @Test
    fun credentialLogoUsesTrimmedAltTextOrCredentialName() {
        assertEquals(
            "Credential logo",
            credentialCardLogoContentDescription("Example credential", "  Credential logo  "),
        )
        assertEquals(
            "Example credential logo",
            credentialCardLogoContentDescription("Example credential", "  "),
        )
    }
}
