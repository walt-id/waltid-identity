package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.FidoQrHandoffResult
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.readPlainText
import id.walt.walletdemo.compose.ui.rememberFidoQrHandoff
import id.walt.walletdemo.compose.ui.rememberSystemCameraLauncher
import id.walt.walletdemo.compose.ui.rememberScannerHostActive
import id.walt.walletdemo.compose.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Scanning and manual input occupy the same sheet; decoding never grants consent. */
@Composable
internal fun WalletScanScreen(
    onBack: () -> Unit,
    onOpen: (String, WalletLinkKind) -> Unit,
    initialInput: String = "",
    resolveLink: suspend (String) -> ResolvedWalletLink = { resolveWalletLink(it) },
    clipboard: Clipboard = LocalClipboard.current,
    handoffFidoQr: ((String) -> FidoQrHandoffResult)? = rememberFidoQrHandoff(),
    scanner: @Composable (Modifier, (String) -> Unit) -> Unit = { modifier, onCode ->
        PlatformQrScanner(modifier, onCode)
    },
) {
    var input by rememberSaveable { mutableStateOf(initialInput) }
    var manual by rememberSaveable { mutableStateOf(initialInput.isNotEmpty()) }
    var dispatched by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var pendingFidoCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    val hostActive = rememberScannerHostActive()
    val launchCamera = rememberSystemCameraLauncher()
    val kind = WalletLinkKind.classify(input)
    val canOpen = kind in setOf(WalletLinkKind.Offer, WalletLinkKind.Presentation,
        WalletLinkKind.AuthorizationCallback, WalletLinkKind.Web) ||
        (kind == WalletLinkKind.FidoHybrid && handoffFidoQr != null)
    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        focus.clearFocus()
        onBack()
    }
    fun open(value: String) {
        if (closed || dispatched || resolving) return
        resolving = true
        resolutionError = null
        focus.clearFocus()
        if (WalletLinkKind.classify(value) == WalletLinkKind.FidoHybrid) {
            manual = true
            if (handoffFidoQr == null) { resolving = false; return }
            pendingFidoCode = value.trim()
            return
        }
        scope.launch {
            try {
                val resolved = resolveLink(value)
                currentCoroutineContext().ensureActive()
                if (closed) return@launch
                dispatched = true
                onOpen(resolved.url, resolved.kind)
            } catch (error: CancellationException) { throw error }
            catch (error: WalletLinkException) { manual = true; resolutionError = error.message }
            catch (_: Exception) { manual = true; resolutionError = "Could not open this link. Check your connection and try again." }
            finally { resolving = false }
        }
    }
    fun paste() {
        if (closed) return
        val originalInput = input
        pasting = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val text = clipboard.getClipEntry()?.readPlainText()
                currentCoroutineContext().ensureActive()
                if (input == originalInput) {
                    if (text != null) { input = text; resolutionError = null }
                    else resolutionError = "There is no text in the clipboard."
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (input == originalInput) resolutionError = "Could not paste this link. Paste it into the field and try again."
            } finally { pasting = false }
        }
    }
    LaunchedEffect(pendingFidoCode) {
        val code = pendingFidoCode ?: return@LaunchedEffect
        // Manual mode removes capture from composition before the system handler opens.
        withFrameNanos { }
        if (closed) return@LaunchedEffect
        val result = handoffFidoQr?.invoke(code) ?: FidoQrHandoffResult.Unavailable
        pendingFidoCode = null
        resolving = false
        when (result) {
            FidoQrHandoffResult.Opened -> close()
            FidoQrHandoffResult.InvalidCode -> resolutionError = "This FIDO QR code is invalid. Ask for a fresh QR code."
            FidoQrHandoffResult.Unavailable -> resolutionError = "No system handler is available for this FIDO QR code. Try your device's camera or another device."
            FidoQrHandoffResult.Blocked -> resolutionError = "Your device could not open this FIDO QR code. Try your device's camera or a fresh code."
        }
    }
    LaunchedEffect(manual, hostActive, resolving) {
        if (manual && hostActive && !resolving) { withFrameNanos { }; inputFocus.requestFocus() }
        else focus.clearFocus()
    }
    SystemBackHandler(onBack = ::close, enabled = true)
    ReviewScaffold(
        modifier = Modifier.testTag(WalletUiTestTags.ScanScreen), fillViewport = false,
        header = {
            WalletScreenHeader(if (manual) "Enter a link" else "Scan QR code") {
                launchCamera?.let { camera ->
                    WalletHeaderButton(onClick = {
                        focus.clearFocus()
                        try { camera() } catch (_: Exception) { resolutionError = "The camera app could not be opened. Use the scanner or enter a link." }
                    }, enabled = !resolving, modifier = Modifier.testTag(WalletUiTestTags.ScanCamera)) {
                        WalletIcon(WalletSymbol.Camera, "Open camera app")
                    }
                }
                WalletHeaderButton(onClick = { focus.clearFocus(); manual = !manual; resolutionError = null },
                    enabled = !resolving, modifier = Modifier.testTag("wallet.scanMode")) {
                    WalletIcon(if (manual) WalletSymbol.Scan else WalletSymbol.Manual,
                        if (manual) "Scan QR code" else "Enter a link")
                }
                WalletHeaderButton(onClick = ::close, modifier = Modifier.testTag(WalletUiTestTags.FlowBack)) {
                    WalletIcon(WalletSymbol.Decline, "Close scanner")
                }
            }
        },
        actions = if (manual) ({
            WalletActions(primary = WalletAction(
                label = if (resolving) "Opening link…" else if (resolutionError != null) "Try again"
                    else if (kind == WalletLinkKind.AuthorizationCallback) "Continue sign-in" else "Continue",
                onClick = { open(input) }, enabled = !resolving && !dispatched && canOpen,
                testTag = WalletUiTestTags.ScanContinue))
        }) else null,
    ) {
        if (manual) {
            OutlinedTextField(value = input, onValueChange = { input = it; resolutionError = null }, enabled = !resolving,
                label = { Text("Credential offer or request") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { open(input) }),
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).testTag(WalletUiTestTags.ScanInput), maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = ::paste, enabled = !resolving && !pasting, modifier = Modifier.testTag("wallet.scanPaste")) {
                        WalletIcon(WalletSymbol.Paste, "Paste link")
                    }
                })
            when (kind) {
                WalletLinkKind.FidoHybrid -> Text(
                    if (handoffFidoQr != null) "Continue in your device's system dialog to share a credential or use a passkey."
                    else "This is a digital credential or passkey request. Scan it with your device's Camera or Code Scanner.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                WalletLinkKind.Unsupported -> Text("This is not a supported credential offer or sharing request.", style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).testTag("wallet.scanPreview")) {
                // System permission prompts temporarily deactivate the app. Preserve the sheet's
                // geometry while releasing capture; resume scanning when the host becomes active.
                if (resolving || dispatched) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)) {
                            CircularProgressIndicator()
                            Text("Opening request…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (hostActive) {
                    scanner(Modifier.fillMaxSize()) { value ->
                        if (closed || resolving || dispatched || value.isBlank()) return@scanner
                        input = value.trim()
                        when (WalletLinkKind.classify(input)) {
                            WalletLinkKind.Offer, WalletLinkKind.Presentation, WalletLinkKind.Web, WalletLinkKind.AuthorizationCallback -> open(input)
                            WalletLinkKind.FidoHybrid -> if (handoffFidoQr != null) open(input) else manual = true
                            else -> manual = true
                        }
                    }
                } else {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("Camera paused", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        resolutionError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }
}
