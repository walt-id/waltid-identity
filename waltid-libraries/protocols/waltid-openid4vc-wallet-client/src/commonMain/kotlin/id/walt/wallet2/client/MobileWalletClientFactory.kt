package id.walt.wallet2.client

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class MobileWalletConfig(
    val walletId: String = Uuid.random().toString(),
    val preferHardwareKeys: Boolean = true,
    val defaultKeyType: KeyType = KeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val onEvent: suspend (WalletSessionEvent) -> Unit = {},
)

expect class MobileWalletClientFactory {
    fun create(config: MobileWalletConfig = MobileWalletConfig()): NativeWalletClient
}
