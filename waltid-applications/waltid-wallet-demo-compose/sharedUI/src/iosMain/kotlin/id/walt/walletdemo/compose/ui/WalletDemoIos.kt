package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createIosDemoWallet
import id.walt.walletdemo.compose.logic.createIosDemoPinStore
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
 * @throws IllegalArgumentException When either value is empty. This demo ships a document-provider
 * extension, so falling back to process-local storage would produce a wallet that looks healthy and
 * is invisible to the extension - a failure that otherwise only appears during a presentation on a
 * device. A build that genuinely has no App Group capability has to fail here instead.
 */
fun walletDemoViewController(
    walletId: String = "default",
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
    transactionDataProfilesUrl: String = "",
    appGroupIdentifier: String = "",
    keychainAccessGroup: String = "",
): UIViewController {
    val config = DemoWalletConfig(
        walletId = walletId,
        attestationBaseUrl = attestationBaseUrl,
        attestationAttesterPath = attestationAttesterPath,
        attestationBearerToken = attestationBearerToken,
        attestationHostHeader = attestationHostHeader,
        transactionDataProfilesUrl = transactionDataProfilesUrl,
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
        wallet = createIosDemoWallet(config, crossProcessAccess),
        pinStore = createIosDemoPinStore(config.walletId),
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
