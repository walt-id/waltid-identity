package id.walt.wallet2.mobile.swiftinterop

import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Factory for creating [WalletSdkBridge] instances with iOS storage and key dependencies.
 */
public class WalletSdkBridgeFactory(
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
) {
    private var createDependencies: suspend (MobileWalletConfig, ClientIdTrustConfiguration) -> WalletSdkBridgeDependencies = { config, trustConfiguration ->
        val wallet = MobileWalletFactory(nfcHostPlatformAdapter).create(config, trustConfiguration)
        WalletSdkBridgeDependencies(
            operations = MobileWalletSdkBridgeOperations(wallet),
            eventFlow = wallet.events,
        )
    }

    private constructor(
        createOperations: suspend (MobileWalletConfig) -> WalletSdkBridgeOperations,
    ) : this() {
        this.createDependencies = { config, _ ->
            WalletSdkBridgeDependencies(
                operations = createOperations(config),
                eventFlow = emptyFlow(),
            )
        }
    }

    /**
     * Creates an iOS wallet bridge from the supplied configuration.
     */
    public suspend fun create(
        configuration: WalletBridgeConfiguration = WalletBridgeConfiguration(),
    ): WalletBridgeResult<WalletSdkBridge> =
        try {
            val dependencies = createDependencies(
                configuration.toMobileWalletConfig(),
                configuration.clientIdTrustConfiguration.toClientIdTrustConfiguration(),
            )
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
            createOperations: suspend (MobileWalletConfig) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory =
            WalletSdkBridgeFactory(createOperations)

        internal fun forOperationsFactoryWithTrust(
            createOperations: suspend (MobileWalletConfig, ClientIdTrustConfiguration) -> WalletSdkBridgeOperations,
        ): WalletSdkBridgeFactory =
            WalletSdkBridgeFactory().also { factory ->
                factory.createDependencies = { config, trustConfiguration ->
                    WalletSdkBridgeDependencies(
                        operations = createOperations(config, trustConfiguration),
                        eventFlow = emptyFlow(),
                    )
                }
            }
    }
}

internal data class WalletSdkBridgeDependencies(
    val operations: WalletSdkBridgeOperations,
    val eventFlow: Flow<MobileWalletEvent>,
)
