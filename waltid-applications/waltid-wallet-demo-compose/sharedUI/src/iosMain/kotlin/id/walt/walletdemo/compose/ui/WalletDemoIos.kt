package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.walletdemo.compose.logic.WalletDemoClientConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createIosWalletDemoClient
import platform.UIKit.UIViewController

private var iosController: WalletDemoController? = null

fun walletDemoViewController(
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
): UIViewController {
    val controller = WalletDemoController(
        createIosWalletDemoClient(
            WalletDemoClientConfig(
                attestationBaseUrl = attestationBaseUrl,
                attestationAttesterPath = attestationAttesterPath,
                attestationBearerToken = attestationBearerToken,
                attestationHostHeader = attestationHostHeader,
            )
        )
    )
    iosController = controller
    return ComposeUIViewController {
        WalletDemoApp(controller)
    }
}

fun handleWalletDemoDeepLink(url: String) {
    iosController?.handleDeepLink(url)
}
