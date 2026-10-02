package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletLinkKind
import id.walt.walletdemo.compose.logic.ResolvedWalletLink
import id.walt.walletdemo.compose.logic.WalletLinkException
import id.walt.walletdemo.compose.logic.resolveWalletLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.QrScannerDialog
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActionBar
import id.walt.walletdemo.compose.ui.components.WalletSection
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletSymbol

/** One entry point for camera and manual links; decoding never grants consent. */
@Composable
internal fun WalletScanScreen(
    onBack: () -> Unit,
    onOpen: (String, WalletLinkKind) -> Unit,
    initialInput: String = "",
    resolveLink: suspend (String) -> ResolvedWalletLink = { resolveWalletLink(it) },
) {
    var input by rememberSaveable { mutableStateOf(initialInput) }
    var scannerVisible by rememberSaveable { mutableStateOf(false) }
    var dispatched by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val kind = WalletLinkKind.classify(input)
    val clipboard = LocalClipboardManager.current
    fun open(value: String) {
        if (dispatched || resolving) return
        resolving = true
        resolutionError = null
        scope.launch {
            try {
                val resolved = resolveLink(value)
                currentCoroutineContext().ensureActive()
                dispatched = true
                onOpen(resolved.url, resolved.kind)
            } catch (error: CancellationException) { throw error }
            catch (error: WalletLinkException) { resolutionError = error.message }
            catch (_: Exception) { resolutionError = "Could not open this link. Check your connection and try again." }
            finally { resolving = false }
        }
    }
    SystemBackHandler(onBack = onBack, enabled = true)
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag(WalletUiTestTags.FlowBack)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to wallet")
                }
                Text("Scan or paste", style = MaterialTheme.typography.titleLarge)
            }
        },
        bottomBar = {
            WalletActionBar(primary = WalletAction(
                label = when {
                    resolving -> "Opening link…"
                    resolutionError != null -> "Try again"
                    kind == WalletLinkKind.AuthorizationCallback -> "Continue sign-in"
                    else -> "Continue"
                },
                onClick = { open(input) },
                enabled = !resolving && !dispatched && kind in setOf(WalletLinkKind.Offer,
                    WalletLinkKind.Presentation, WalletLinkKind.AuthorizationCallback, WalletLinkKind.Web),
                testTag = WalletUiTestTags.ScanContinue,
                icon = if (resolutionError != null) WalletSymbol.Retry else WalletSymbol.Next,
            ))
        },
        modifier = Modifier.imePadding().testTag(WalletUiTestTags.ScanScreen),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Scan a QR code or paste a link to receive or share credentials.",
                style = MaterialTheme.typography.bodyLarge)
            WalletSection {
                FlowRow(Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
                    OutlinedButton(onClick = { scannerVisible = true }, enabled = !resolving, modifier = Modifier.testTag(WalletUiTestTags.ScanCamera)) {
                        WalletIcon(WalletSymbol.Scan, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use camera")
                    }
                    OutlinedButton(onClick = { input = clipboard.getText()?.text.orEmpty(); resolutionError = null }, enabled = !resolving) {
                        WalletIcon(WalletSymbol.Paste, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste link")
                    }
                }
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it; resolutionError = null }, enabled = !resolving,
                label = { Text("Credential offer or request") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ScanInput),
                maxLines = 4,
                trailingIcon = if (input.isNotEmpty()) {{
                    IconButton(onClick = { input = ""; resolutionError = null }, enabled = !resolving) { Icon(Icons.Filled.Close, "Clear link") }
                }} else null,
            )
            val explanation = when (kind) {
                WalletLinkKind.FidoHybrid -> "This is a passkey sign-in code. Use your device's system camera to scan it. This wallet cannot complete FIDO hybrid sign-in."
                WalletLinkKind.Unsupported -> "This code is not a supported credential offer or sharing request. Check the link or scan another code."
                else -> null
            }
            explanation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (resolving) CircularProgressIndicator(Modifier.size(24.dp))
            resolutionError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (scannerVisible) QrScannerDialog(
        onDismiss = { scannerVisible = false },
        onCodeScanned = { value ->
            scannerVisible = false
            input = value.trim()
            val scannedKind = WalletLinkKind.classify(value)
            if (scannedKind in setOf(WalletLinkKind.Offer, WalletLinkKind.Presentation, WalletLinkKind.Web)) open(value)
        },
    )
}
