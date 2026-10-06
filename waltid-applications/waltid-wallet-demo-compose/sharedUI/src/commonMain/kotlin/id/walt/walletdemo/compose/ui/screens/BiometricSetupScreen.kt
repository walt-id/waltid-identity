package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.DemoBiometricResult
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletFooter
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Cancelling an OS prompt leaves the optional choice with the user, without resaving the PIN. */
@Composable
internal fun BiometricSetupScreen(
    setup: WalletAuthState.BiometricSetup,
    busy: Boolean,
    available: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag(WalletUiTestTags.BiometricSetup),
        bottomBar = {
            WalletFooter(feedback = if (busy || setup.error != null) ({
                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Authenticating…", style = MaterialTheme.typography.bodyMedium)
                }
                setup.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }) else null) {
            WalletActions(
                primary = if (available) WalletAction(stringResource(Res.string.pin_biometric_retry), onRetry,
                    !busy, WalletUiTestTags.BiometricSetupRetry)
                else WalletAction(stringResource(Res.string.pin_biometric_continue), onContinue,
                    !busy, WalletUiTestTags.BiometricSetupContinue),
                secondary = if (available) WalletAction(stringResource(Res.string.pin_biometric_continue), onContinue,
                    !busy, WalletUiTestTags.BiometricSetupContinue) else null,
            )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(Res.string.pin_biometric_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(when (setup.outcome) {
                DemoBiometricResult.Cancelled -> Res.string.pin_biometric_cancelled
                DemoBiometricResult.Unavailable -> Res.string.pin_biometric_unavailable
                DemoBiometricResult.LockedOut -> Res.string.pin_biometric_locked_out
                DemoBiometricResult.Failed -> Res.string.pin_biometric_failed
                else -> Res.string.pin_biometric_ready
            }), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
