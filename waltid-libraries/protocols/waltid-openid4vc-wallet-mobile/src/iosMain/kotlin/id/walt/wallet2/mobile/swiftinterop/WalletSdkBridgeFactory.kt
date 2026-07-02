package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WalletSdkBridgeFactory() {
    private var createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations = { config ->
        MobileWalletSdkBridgeOperations(MobileWalletFactory().create(config))
    }

    private constructor(
        createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
    ) : this() {
        this.createOperations = createOperations
    }

    fun create(
        configuration: WalletBridgeConfiguration = WalletBridgeConfiguration(),
    ): WalletBridgeResult<WalletSdkBridge> =
        try {
            val events = MutableSharedFlow<WalletBridgeEvent>(replay = 10)
            WalletBridgeResult.Success(
                WalletSdkBridge.forOperations(
                    operations = createOperations(
                        configuration.toMobileWalletConfig(
                            onEvent = { event -> events.emit(event.toWalletBridgeEvent()) }
                        )
                    ),
                    eventFlow = events.asSharedFlow(),
                )
            )
        } catch (throwable: Throwable) {
            WalletBridgeResult.Failure(WalletBridgeError.fromThrowable(throwable))
        }

    companion object {
        internal fun forOperationsFactory(
            createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory =
            WalletSdkBridgeFactory(createOperations)
    }
}
