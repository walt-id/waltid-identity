package id.walt.walletdemo.compose.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoBrandingTest {
    @Test
    fun overlayReplacesTitleAndHexColors() {
        val branded = WalletDemoBranding().overlay(
            appTitle = "Acme Wallet",
            primary = "#112233",
            onPrimary = "ffffff",
        )
        assertEquals("Acme Wallet", branded.appTitle)
        assertEquals(Color(0xFF112233), branded.primary)
        assertEquals(Color.White, branded.onPrimary)
        assertEquals(WalletDemoBranding().secondary, branded.secondary)
    }

    @Test
    fun overlayIgnoresBlankAndInvalidValues() {
        val original = WalletDemoBranding()
        val branded = original.overlay(
            appTitle = "  ",
            primary = "not-a-color",
            secondary = null,
        )
        assertEquals(original, branded)
    }

    @Test
    fun overlayJsonMatchesMobileBrandTokens() {
        val branded = WalletDemoBranding().overlayJson(
            """
            {
              "appTitle": "Acme Wallet",
              "primary": "#010203",
              "onPrimary": "#FFFFFF",
              "secondary": "#ADC6FF",
              "onSecondary": "#002E69",
              "primaryContainer": "#D8E2FF",
              "onPrimaryContainer": "#002E69"
            }
            """.trimIndent(),
        )
        assertEquals("Acme Wallet", branded.appTitle)
        assertEquals(Color(0xFF010203), branded.primary)
        assertEquals(Color.White, branded.onPrimary)
    }

    @Test
    fun overlayJsonIgnoresMalformedPayload() {
        assertEquals(WalletDemoBranding(), WalletDemoBranding().overlayJson("{not json"))
    }
}
