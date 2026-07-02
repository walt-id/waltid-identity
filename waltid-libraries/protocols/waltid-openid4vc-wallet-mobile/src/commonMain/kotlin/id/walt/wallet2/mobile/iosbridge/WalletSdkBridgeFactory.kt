package id.walt.wallet2.mobile.iosbridge

import id.walt.wallet2.mobile.MobileWalletConfig

expect class WalletSdkBridgeFactory {
    constructor()

    fun create(
        configuration: WalletBridgeConfiguration = WalletBridgeConfiguration(),
    ): WalletBridgeResult<WalletSdkBridge>

    companion object {
        internal fun forOperationsFactory(
            createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory
    }
}
