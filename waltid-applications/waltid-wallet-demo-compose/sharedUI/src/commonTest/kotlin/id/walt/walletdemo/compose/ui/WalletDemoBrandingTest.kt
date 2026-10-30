package id.walt.walletdemo.compose.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoBrandingTest {
    @Test
    fun overlayReplacesTitleAndHexColors() {
        val branded = WalletDemoBranding(onPrimary = Color.Black).overlay(
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
              "onPrimary": "#102030",
              "secondary": "#213243",
              "onSecondary": "#324354",
              "primaryContainer": "#435465",
              "onPrimaryContainer": "#546576"
            }
            """.trimIndent(),
        )
        assertEquals(
            WalletDemoBranding(
                appTitle = "Acme Wallet",
                primary = Color(0xFF010203),
                onPrimary = Color(0xFF102030),
                secondary = Color(0xFF213243),
                onSecondary = Color(0xFF324354),
                primaryContainer = Color(0xFF435465),
                onPrimaryContainer = Color(0xFF546576),
            ),
            branded,
        )
    }

    @Test
    fun overlayJsonIgnoresMalformedPayload() {
        assertEquals(WalletDemoBranding(), WalletDemoBranding().overlayJson("{not json"))
    }
}
