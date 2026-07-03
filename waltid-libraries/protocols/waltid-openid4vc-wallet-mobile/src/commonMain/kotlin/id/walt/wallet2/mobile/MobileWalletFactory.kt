package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore
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

internal fun createMobileWallet(
    config: MobileWalletConfig,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformKeyProvider,
): MobileWallet {
    val queries = db.walletPersistenceQueries
    val keyStore = PlatformKeyStore(keyProvider, queries)
    val credentialStore = SqlDelightCredentialStore(queries)
    val didStore = SqlDelightDidStore(queries)

    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        keyGenerator = { keyType -> keyProvider.generateKey(keyType) },
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        onEvent = config.onEvent,
    )
}
