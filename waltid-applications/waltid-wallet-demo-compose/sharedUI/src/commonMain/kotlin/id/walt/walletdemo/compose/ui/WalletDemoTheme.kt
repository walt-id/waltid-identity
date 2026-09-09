package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import id.walt.walletdemo.compose.ui.components.CssColorParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Demo-only white-label tokens. Edit the defaults on [WalletDemoBranding] to rebrand the Compose
 * wallet demo. Hosts can also pass a custom instance into [WalletDemoTheme] / [WalletDemoApp].
 *
 * Web also loads these tokens at runtime from `branding.json` (see the webApp README).
 *
 * Launcher names stay in platform manifests: Android `app_name` and iOS `CFBundleDisplayName`.
 */
data class WalletDemoBranding(
    val appTitle: String = "walt.id Wallet",
    val primary: Color = Color(0xFF0573F0),
    val onPrimary: Color = Color.White,
    val secondary: Color = Color(0xFFADC6FF),
    val onSecondary: Color = Color(0xFF002E69),
    val primaryContainer: Color = Color(0xFFD8E2FF),
    val onPrimaryContainer: Color = Color(0xFF002E69),
) {
    fun overlay(
        appTitle: String? = null,
        primary: String? = null,
        onPrimary: String? = null,
        secondary: String? = null,
        onSecondary: String? = null,
        primaryContainer: String? = null,
        onPrimaryContainer: String? = null,
    ): WalletDemoBranding = copy(
        appTitle = appTitle?.trim()?.takeIf { it.isNotEmpty() } ?: this.appTitle,
        primary = parseBrandColor(primary) ?: this.primary,
        onPrimary = parseBrandColor(onPrimary) ?: this.onPrimary,
        secondary = parseBrandColor(secondary) ?: this.secondary,
        onSecondary = parseBrandColor(onSecondary) ?: this.onSecondary,
        primaryContainer = parseBrandColor(primaryContainer) ?: this.primaryContainer,
        onPrimaryContainer = parseBrandColor(onPrimaryContainer) ?: this.onPrimaryContainer,
    )

    fun overlayJson(raw: String): WalletDemoBranding = runCatching {
        val obj = BrandingJson.parseToJsonElement(raw).jsonObject
        fun field(name: String): String? = obj[name]?.jsonPrimitive?.contentOrNull
        overlay(
            appTitle = field("appTitle"),
            primary = field("primary"),
            onPrimary = field("onPrimary"),
            secondary = field("secondary"),
            onSecondary = field("onSecondary"),
            primaryContainer = field("primaryContainer"),
            onPrimaryContainer = field("onPrimaryContainer"),
        )
    }.getOrDefault(this)
}

val LocalWalletDemoBranding = staticCompositionLocalOf { WalletDemoBranding() }

@Composable
fun WalletDemoTheme(
    branding: WalletDemoBranding = WalletDemoBranding(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWalletDemoBranding provides branding) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = branding.primary,
                onPrimary = branding.onPrimary,
                secondary = branding.secondary,
                onSecondary = branding.onSecondary,
                primaryContainer = branding.primaryContainer,
                onPrimaryContainer = branding.onPrimaryContainer,
            ),
            content = content,
        )
    }
}

private val BrandingJson = Json { ignoreUnknownKeys = true }

private fun parseBrandColor(value: String?): Color? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = when {
        raw.startsWith("#") || raw.startsWith("rgb", ignoreCase = true) -> raw
        else -> "#$raw"
    }
    val parsed = CssColorParser.parse(normalized) ?: return null
    return Color(
        red = parsed.red / 255f,
        green = parsed.green / 255f,
        blue = parsed.blue / 255f,
        alpha = parsed.alpha / 255f,
    )
}
