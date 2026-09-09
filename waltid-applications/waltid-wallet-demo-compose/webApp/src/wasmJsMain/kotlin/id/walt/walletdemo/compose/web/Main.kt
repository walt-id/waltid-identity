package id.walt.walletdemo.compose.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.walletapi2.WalletApi2BrowserSessionStore
import id.walt.walletdemo.compose.logic.walletapi2.WalletApi2Session
import id.walt.walletdemo.compose.logic.walletapi2.createWalletApi2DemoWallet
import id.walt.walletdemo.compose.logic.walletapi2.establishWalletApi2Session
import id.walt.walletdemo.compose.logic.walletapi2.webIssuanceRedirectUri
import id.walt.walletdemo.compose.ui.WalletDemoApp
import id.walt.walletdemo.compose.ui.WalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletDemoTheme
import id.walt.walletdemo.compose.ui.screens.AccountAuthScreen
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        var branding by remember { mutableStateOf<WalletDemoBranding?>(null) }
        LaunchedEffect(Unit) { branding = loadWebBranding() }
        val currentBranding = branding ?: return@ComposeViewport
        WalletDemoTheme(currentBranding) {
            WebWalletRoot(branding = currentBranding)
        }
    }
}

@Composable
private fun WebWalletRoot(branding: WalletDemoBranding) {
    var session by remember { mutableStateOf(WalletApi2BrowserSessionStore.load()) }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val current = session
    if (current == null) {
        AccountAuthScreen(
            isBusy = isBusy,
            error = error,
            onLogin = { email, password ->
                scope.launch {
                    isBusy = true
                    error = null
                    runCatching { establishWalletApi2Session(email, password, register = false) }
                        .onSuccess { session = it }
                        .onFailure { error = it.message ?: "Login failed" }
                    isBusy = false
                }
            },
            onRegister = { email, password ->
                scope.launch {
                    isBusy = true
                    error = null
                    runCatching { establishWalletApi2Session(email, password, register = true) }
                        .onSuccess { session = it }
                        .onFailure { error = it.message ?: "Registration failed" }
                    isBusy = false
                }
            },
        )
        return
    }

    WebWalletSession(
        session = current,
        branding = branding,
        onSignOut = {
                scope.launch {
                    WalletApi2BrowserSessionStore.signOut(current)
                    session = null
                }
        },
    )
}

@Composable
private fun WebWalletSession(
    session: WalletApi2Session,
    branding: WalletDemoBranding,
    onSignOut: () -> Unit,
) {
    val redirectUri = remember { webIssuanceRedirectUri() }
    val controller = remember(session.token, session.walletId) {
        WalletDemoController(
            wallet = createWalletApi2DemoWallet(
                baseUrl = session.baseUrl,
                token = session.token,
                walletId = session.walletId,
                redirectUri = redirectUri,
                onWalletIdChanged = WalletApi2BrowserSessionStore::updateWalletId,
            ),
            pinStore = InMemoryDemoPinStore(),
            skipPin = true,
            issuanceRedirectUri = redirectUri,
            signingProtectionMode = WalletDemoSigningProtectionMode.Disabled,
        )
    }

    LaunchedEffect(controller) {
        val href = window.location.href
        if (href.contains("code=")) {
            controller.handleDeepLink(href)
            clearAuthorizationCallbackFromAddressBar()
        }
    }

    WalletDemoApp(controller, branding = branding, onSignOut = onSignOut)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun clearAuthorizationCallbackFromAddressBar() {
    window.history.replaceState(null, "", window.location.pathname)
}
