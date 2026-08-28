package id.walt.walletdemo.compose.ui.components

import kotlin.test.Test
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
}
