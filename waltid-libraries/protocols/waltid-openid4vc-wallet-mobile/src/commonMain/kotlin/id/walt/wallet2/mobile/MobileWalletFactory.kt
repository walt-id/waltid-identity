package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import kotlin.uuid.Uuid

data class MobileWalletConfig(
    val walletId: String = Uuid.random().toString(),
    val defaultKeyType: KeyType = KeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val onEvent: suspend (WalletSessionEvent) -> Unit = {},
)

expect class MobileWalletFactory {
    fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}
