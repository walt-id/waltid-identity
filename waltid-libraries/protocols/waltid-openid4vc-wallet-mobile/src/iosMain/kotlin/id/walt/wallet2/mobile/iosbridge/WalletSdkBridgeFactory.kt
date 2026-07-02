package id.walt.wallet2.mobile.iosbridge

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual class WalletSdkBridgeFactory actual constructor() {
    private var createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations = { config ->
        MobileWalletSdkBridgeOperations(MobileWalletFactory().create(config))
    }

    private constructor(
        createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
    ) : this() {
        this.createOperations = createOperations
    }

    actual fun create(
        configuration: WalletBridgeConfiguration,
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

    actual companion object {
        internal actual fun forOperationsFactory(
            createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory =
            WalletSdkBridgeFactory(createOperations)
    }
}
