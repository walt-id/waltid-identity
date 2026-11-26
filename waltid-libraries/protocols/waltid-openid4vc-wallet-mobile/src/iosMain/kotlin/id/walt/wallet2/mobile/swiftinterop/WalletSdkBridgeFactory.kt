package id.walt.wallet2.mobile.swiftinterop

import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletEvent
import id.walt.wallet2.mobile.WalletFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Factory for creating [WalletSdkBridge] instances with iOS storage and key dependencies.
 */
public class WalletSdkBridgeFactory private constructor(
    private val createDependencies: suspend (WalletConfiguration) -> WalletSdkBridgeDependencies,
) {
    /** Opens the platform wallet with the optional app-owned NFC host adapter. */
    public constructor(nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null) : this({ config ->
        val wallet = WalletFactory(nfcHostPlatformAdapter).open(config)
        WalletSdkBridgeDependencies(MobileWalletSdkBridgeOperations(wallet), wallet.events)
    })

    /**
     * Creates an iOS wallet bridge from the supplied configuration.
     */
    public suspend fun create(
        configuration: WalletBridgeConfiguration = WalletBridgeConfiguration(),
    ): WalletBridgeResult<WalletSdkBridge> =
        walletBridgeCall {
            val dependencies = createDependencies(configuration.toMobileWalletConfig())
            WalletSdkBridge.forOperations(dependencies.operations, dependencies.eventFlow)
        }

    internal companion object {
        internal fun forOperationsFactory(
            createOperations: suspend (WalletConfiguration) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory = WalletSdkBridgeFactory { config ->
            WalletSdkBridgeDependencies(createOperations(config), emptyFlow())
        }
    }
}

internal data class WalletSdkBridgeDependencies(
    val operations: WalletSdkBridgeOperations,
    val eventFlow: Flow<WalletEvent>,
)
