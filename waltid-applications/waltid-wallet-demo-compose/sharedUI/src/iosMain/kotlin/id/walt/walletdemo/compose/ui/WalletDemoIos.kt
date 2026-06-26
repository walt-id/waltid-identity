package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.walletdemo.compose.logic.WalletDemoClientConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createIosWalletDemoClient
import platform.UIKit.UIViewController

private var iosController: WalletDemoController? = null
private var pendingDeepLink: String? = null

fun walletDemoViewController(
    walletId: String = "default",
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
): UIViewController {
    val controller = WalletDemoController(
        createIosWalletDemoClient(
            WalletDemoClientConfig(
                walletId = walletId,
                attestationBaseUrl = attestationBaseUrl,
                attestationAttesterPath = attestationAttesterPath,
                attestationBearerToken = attestationBearerToken,
                attestationHostHeader = attestationHostHeader,
            )
        )
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
