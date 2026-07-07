package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Factory for creating [WalletSdkBridge] instances with iOS storage and key dependencies.
 */
public class WalletSdkBridgeFactory() {
    private var createDependencies: (MobileWalletConfig) -> WalletSdkBridgeDependencies = { config ->
        val wallet = MobileWalletFactory().create(config)
        WalletSdkBridgeDependencies(
            operations = MobileWalletSdkBridgeOperations(wallet),
            eventFlow = wallet.events,
        )
    }

    private constructor(
        createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
    ) : this() {
        this.createDependencies = { config ->
            WalletSdkBridgeDependencies(
                operations = createOperations(config),
                eventFlow = emptyFlow(),
            )
        }
    }

    /**
     * Creates an iOS wallet bridge from the supplied configuration.
     */
    public fun create(
        configuration: WalletBridgeConfiguration = WalletBridgeConfiguration(),
    ): WalletBridgeResult<WalletSdkBridge> =
        try {
            val dependencies = createDependencies(configuration.toMobileWalletConfig())
            WalletBridgeResult.Success(
                WalletSdkBridge.forOperations(
                    operations = dependencies.operations,
                    eventFlow = dependencies.eventFlow,
                )
            )
        } catch (throwable: Throwable) {
            WalletBridgeResult.Failure(WalletBridgeError.fromThrowable(throwable))
        }

    internal companion object {
        internal fun forOperationsFactory(
            createOperations: (MobileWalletConfig) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory =
            WalletSdkBridgeFactory(createOperations)
    }
}

internal data class WalletSdkBridgeDependencies(
    val operations: WalletSdkBridgeOperations,
    val eventFlow: Flow<MobileWalletEvent>,
)
