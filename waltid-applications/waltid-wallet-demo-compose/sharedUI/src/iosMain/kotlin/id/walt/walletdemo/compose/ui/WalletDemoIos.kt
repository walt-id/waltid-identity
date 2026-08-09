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
 * document-provider extension. Empty disables cross-process access, which is what a build without the
 * App Group capability gets.
 * @param keychainAccessGroup Build-expanded shared Keychain access group. Must be non-empty together
 * with [appGroupIdentifier]: without it the wallet's signing key lands in a group the extension cannot
 * read, so the extension would see the credential and then fail to sign for it.
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
    val crossProcessAccess = if (appGroupIdentifier.isNotEmpty() && keychainAccessGroup.isNotEmpty()) {
        MobileWalletCrossProcessAccess(
            appGroupIdentifier = appGroupIdentifier,
            keychainAccessGroup = keychainAccessGroup,
        )
    } else {
        null
    }
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
