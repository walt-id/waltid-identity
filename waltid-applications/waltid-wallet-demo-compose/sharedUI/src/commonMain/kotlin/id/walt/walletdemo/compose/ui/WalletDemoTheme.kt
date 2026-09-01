package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Demo-only white-label tokens. Edit the defaults on [WalletDemoBranding] to rebrand the Compose
 * wallet demo. Hosts can also pass a custom instance into [WalletDemoTheme] / [WalletDemoApp].
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
)

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
