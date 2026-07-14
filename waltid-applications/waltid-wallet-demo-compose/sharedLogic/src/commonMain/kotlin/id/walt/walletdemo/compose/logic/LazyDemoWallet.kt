package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LazyDemoWallet(
    private val createWallet: suspend () -> DemoWallet,
) : DemoWallet {
    private val mutex = Mutex()
    private var wallet: DemoWallet? = null

    private suspend fun wallet(): DemoWallet =
        wallet ?: mutex.withLock {
            wallet ?: createWallet().also { wallet = it }
        }

    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        wallet().bootstrap()

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        wallet().listCredentials()

    override suspend fun receive(offerUrl: String): List<String> =
        wallet().receive(offerUrl)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        wallet().present(requestUrl, did)
}
