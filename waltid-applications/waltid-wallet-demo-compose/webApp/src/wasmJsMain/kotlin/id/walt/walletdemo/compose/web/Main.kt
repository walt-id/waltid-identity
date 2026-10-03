package id.walt.walletdemo.compose.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
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
import id.walt.walletdemo.compose.ui.installWalletImageLoader
import id.walt.walletdemo.compose.ui.WalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletDemoTheme
import id.walt.walletdemo.compose.ui.screens.AccountAuthScreen
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installWalletImageLoader()
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

    fun authenticate(email: String, password: String, register: Boolean) {
        if (isBusy) return
        isBusy = true
        error = null
        scope.launch {
            try {
                session = establishWalletApi2Session(email, password, register)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Sign in failed. Try again." }
            finally { isBusy = false }
        }
    }

    val current = session
    if (current == null) {
        AccountAuthScreen(
            isBusy = isBusy,
            error = error,
            onLogin = { email, password -> authenticate(email, password, register = false) },
            onRegister = { email, password -> authenticate(email, password, register = true) },
        )
        return
    }

    WebWalletSession(
        session = current,
        branding = branding,
        onSignOut = {
            WalletApi2BrowserSessionStore.clearIfCurrent(current)
            session = null
            error = null
            scope.launch { WalletApi2BrowserSessionStore.signOut(current) }
        },
        onSessionExpired = {
            if (session === current) {
                WalletApi2BrowserSessionStore.clearIfCurrent(current)
                session = null
                error = "Your session has expired. Sign in again to continue."
            }
        },
    )
}

@Composable
private fun WebWalletSession(
    session: WalletApi2Session,
    branding: WalletDemoBranding,
    onSignOut: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val redirectUri = remember { webIssuanceRedirectUri() }
    val sessionScope = rememberCoroutineScope()
    val expireSession by rememberUpdatedState(onSessionExpired)
    val controller = remember(session.token, session.walletId) {
        WalletDemoController(
            wallet = createWalletApi2DemoWallet(
                baseUrl = session.baseUrl,
                token = session.token,
                walletId = session.walletId,
                redirectUri = redirectUri,
                onWalletIdChanged = { WalletApi2BrowserSessionStore.updateWalletIdIfCurrent(session, it) },
                onSessionExpired = { expireSession() },
            ),
            pinStore = InMemoryDemoPinStore(),
            skipPin = true,
            scope = sessionScope,
            issuanceRedirectUri = redirectUri,
            signingProtectionMode = WalletDemoSigningProtectionMode.Disabled,
        )
    }

    LaunchedEffect(controller) {
        val href = window.location.href
        if (isAuthorizationCallbackHref(href)) {
            controller.handleDeepLink(href)
            clearAuthorizationCallbackFromAddressBar()
        }
    }

    WalletDemoApp(
        controller, branding = branding, onSignOut = onSignOut,
        resetWalletDescription = "This deletes the current wallet, including its keys and credentials, from the server and creates an empty wallet. Your account remains signed in.",
    )
}

private fun isAuthorizationCallbackHref(href: String): Boolean {
    val query = href.substringAfter('?', "").substringBefore('#')
    return query.split('&').any { parameter ->
        parameter.startsWith("code=") || parameter.startsWith("error=")
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun clearAuthorizationCallbackFromAddressBar() {
    window.history.replaceState(null, "", window.location.pathname)
}
