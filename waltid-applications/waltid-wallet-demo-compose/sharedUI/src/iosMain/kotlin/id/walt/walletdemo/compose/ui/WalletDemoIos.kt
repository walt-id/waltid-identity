package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createIosDemoWallet
import id.walt.walletdemo.compose.logic.createIosDemoPinStore
import platform.UIKit.UIViewController

private var iosController: WalletDemoController? = null
private var pendingDeepLink: String? = null

fun walletDemoViewController(
    walletId: String = "default",
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
    transactionDataProfilesUrl: String = "",
): UIViewController {
    val config = DemoWalletConfig(
        walletId = walletId,
        attestationBaseUrl = attestationBaseUrl,
        attestationAttesterPath = attestationAttesterPath,
        attestationBearerToken = attestationBearerToken,
        attestationHostHeader = attestationHostHeader,
        transactionDataProfilesUrl = transactionDataProfilesUrl,
    )
    val controller = WalletDemoController(
        wallet = createIosDemoWallet(config),
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
