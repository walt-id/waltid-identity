package id.walt.walletdemo.compose.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.ComposeViewport
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.walletapi2.WalletApi2BrowserSessionStore
import id.walt.walletdemo.compose.logic.walletapi2.WalletApi2Session
import id.walt.walletdemo.compose.logic.walletapi2.createWalletApi2DemoWallet
import id.walt.walletdemo.compose.logic.walletapi2.establishWalletApi2Session
import id.walt.walletdemo.compose.logic.walletapi2.refreshWalletTargets
import id.walt.walletdemo.compose.logic.walletapi2.walletApiKind
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
    val kind = remember { walletApiKind() }
    val scope = rememberCoroutineScope()

    val current = session
    if (current == null) {
        AccountAuthScreen(
            isBusy = isBusy,
            error = error,
            allowRegister = kind.canRegister,
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
        onSessionChange = { session = it },
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
    onSessionChange: (WalletApi2Session?) -> Unit,
    onSignOut: () -> Unit,
) {
    val redirectUri = remember { webIssuanceRedirectUri() }
    var targets by remember(session.walletId, session.walletTargets) { mutableStateOf(session.walletTargets) }
    val controller = remember(session.token, session.walletId, session.kind, session.baseUrl) {
        WalletDemoController(
            wallet = createWalletApi2DemoWallet(
                baseUrl = session.baseUrl,
                token = session.token,
                walletId = session.walletId,
                redirectUri = redirectUri,
                kind = session.kind,
                onWalletIdChanged = WalletApi2BrowserSessionStore::updateWalletId,
            ),
            pinStore = InMemoryDemoPinStore(),
            skipPin = true,
            issuanceRedirectUri = redirectUri,
            signingProtectionMode = WalletDemoSigningProtectionMode.Disabled,
        )
    }

    LaunchedEffect(session.token, session.kind, session.baseUrl) {
        val refreshed = runCatching { refreshWalletTargets(session) }.getOrNull() ?: return@LaunchedEffect
        targets = refreshed
        if (refreshed != session.walletTargets) {
            val updated = session.copy(walletTargets = refreshed)
            WalletApi2BrowserSessionStore.save(updated)
            onSessionChange(updated)
        }
    }

    LaunchedEffect(controller) {
        val href = window.location.href
        if (isAuthorizationCallbackHref(href)) {
            controller.handleDeepLink(href)
            clearAuthorizationCallbackFromAddressBar()
        }
    }

    WalletDemoApp(
        controller,
        branding = branding,
        onSignOut = onSignOut,
        resetWalletDescription = "This deletes the current wallet, including its keys and credentials, from the server and creates an empty wallet. Your account remains signed in.",
        allowWalletReset = session.kind.canManageWallet,
        allowCredentialDelete = session.kind.canDeleteCredential,
        serverSettingsContent = if (targets.size > 1) {
            {
                WalletTargetPicker(
                    walletTargets = targets,
                    selectedWalletId = session.walletId,
                    onWalletSelected = { target ->
                        if (target == session.walletId) return@WalletTargetPicker
                        WalletApi2BrowserSessionStore.updateWalletId(target)
                        onSessionChange(session.copy(walletId = target, walletTargets = targets))
                    },
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun WalletTargetPicker(
    walletTargets: List<String>,
    selectedWalletId: String,
    onWalletSelected: (String) -> Unit,
) {
    Column(Modifier.selectableGroup().fillMaxWidth()) {
        walletTargets.forEach { target ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = target == selectedWalletId,
                        onClick = { onWalletSelected(target) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = target == selectedWalletId, onClick = null)
                Text(target)
            }
        }
    }
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
