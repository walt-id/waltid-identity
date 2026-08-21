package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createIosDemoWallet
import id.walt.walletdemo.compose.logic.createIosDemoPinStore
import id.walt.walletdemo.compose.logic.createIosDemoBiometricAuthenticator
import platform.UIKit.UIViewController

private var iosController: WalletDemoController? = null
private var pendingDeepLink: String? = null

/**
 * Hosts the Compose demo in a `UIViewController` for the SwiftUI app.
 *
 * @param appGroupIdentifier App Group holding the encrypted wallet database, shared with the
 * document-provider extension.
 * @param keychainAccessGroup Build-expanded shared Keychain access group. Without it the wallet's
 * signing key lands in a group the extension cannot read, so the extension would see the credential
 * and then fail to sign for it.
 * @param onDigitalCredentialRegistryChanged Called from Kotlin whenever the wallet's credential set
 * changed and its desired Apple registration state was re-published. The host reconciles Apple's
 * `IdentityDocumentProviderRegistrationStore` here; the wallet cannot, since only the app process may
 * call `IdentityDocumentServices`, and Apple's `performRegistrationUpdates()` runs only periodically.
 * Without it an issued or deleted credential reaches the platform no earlier than the next foreground
 * transition. Not a suspending callback: Kotlin/Native cannot export a suspending function type to
 * Swift, and the host has nothing to report back anyway - the credential change is committed before
 * this runs, so the host starts its own task and the wallet does not wait for Apple's store.
 * @throws IllegalArgumentException When [appGroupIdentifier] or [keychainAccessGroup] is empty. This
 * demo ships a document-provider extension, so falling back to process-local storage would produce a
 * wallet that looks healthy and is invisible to the extension - a failure that otherwise only appears
 * during a presentation on a device. A build that genuinely has no App Group capability has to fail
 * here instead.
 */
fun walletDemoViewController(
    appGroupIdentifier: String,
    keychainAccessGroup: String,
    onDigitalCredentialRegistryChanged: () -> Unit,
    walletId: String = "default",
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
    transactionDataProfilesUrl: String = "",
    biometricEnabled: Boolean = true,
): UIViewController {
    val config = DemoWalletConfig(
        walletId = walletId,
        attestationBaseUrl = attestationBaseUrl,
        attestationAttesterPath = attestationAttesterPath,
        attestationBearerToken = attestationBearerToken,
        attestationHostHeader = attestationHostHeader,
        transactionDataProfilesUrl = transactionDataProfilesUrl,
        biometricEnabled = biometricEnabled,
    )
    require(appGroupIdentifier.isNotEmpty()) {
        "This demo ships a document-provider extension and requires an App Group"
    }
    require(keychainAccessGroup.isNotEmpty()) {
        "This demo ships a document-provider extension and requires a shared Keychain access group"
    }
    val crossProcessAccess = MobileWalletCrossProcessAccess(
        appGroupIdentifier = appGroupIdentifier,
        keychainAccessGroup = keychainAccessGroup,
    )
    val controller = WalletDemoController(
        wallet = createIosDemoWallet(
            config = config,
            crossProcessAccess = crossProcessAccess,
            onDigitalCredentialRegistryChanged = { onDigitalCredentialRegistryChanged() },
        ),
        pinStore = createIosDemoPinStore(config.walletId),
        biometricAuthenticator = createIosDemoBiometricAuthenticator(),
    )
    iosController = controller
    pendingDeepLink?.let(controller::handleDeepLink)
    pendingDeepLink = null
    return ComposeUIViewController {
        WalletDemoApp(controller)
    }
}

fun handleWalletDemoDeepLink(url: String) {
    val controller = iosController
    if (controller == null) {
        pendingDeepLink = url
    } else {
        controller.handleDeepLink(url)
    }
}
