package id.walt.walletdemo.compose.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createMockDemoWallet
import id.walt.walletdemo.compose.ui.WalletDemoApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        WalletDemoApp(
            WalletDemoController(createMockDemoWallet())
        )
    }
}
