package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalletInteractionClassifierTest {
    @Test
    fun classifiesCredentialOffersWithoutKeepingWhitespace() {
        val result = assertIs<WalletInteractionClassification.Supported>(
            classifyWalletInteraction("  openid-credential-offer://issuer.example?offer=1  "),
        )

        assertEquals(WalletInteractionKind.CredentialOffer, result.kind)
        assertEquals("openid-credential-offer://issuer.example?offer=1", result.normalizedInput)
    }

    @Test
    fun classifiesPresentationRequestsCaseInsensitively() {
        val result = assertIs<WalletInteractionClassification.Supported>(
            classifyWalletInteraction("OPENID4VP://verifier.example?request=1"),
        )

        assertEquals(WalletInteractionKind.PresentationRequest, result.kind)
    }

    @Test
    fun distinguishesMalformedAndUnsupportedInput() {
        assertIs<WalletInteractionClassification.Invalid>(classifyWalletInteraction("not a wallet link"))
        assertIs<WalletInteractionClassification.Invalid>(classifyWalletInteraction("  "))
        assertIs<WalletInteractionClassification.Unsupported>(classifyWalletInteraction("https://example.com"))
        assertIs<WalletInteractionClassification.Unsupported>(classifyWalletInteraction("openid://callback"))
    }
}
