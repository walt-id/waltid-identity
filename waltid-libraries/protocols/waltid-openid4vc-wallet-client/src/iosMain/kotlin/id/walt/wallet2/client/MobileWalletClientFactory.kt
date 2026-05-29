package id.walt.wallet2.client

import id.walt.wallet2.client.db.WalletClientDatabase
import id.walt.wallet2.client.keys.IosPlatformKeyProvider
import id.walt.wallet2.client.stores.DriverFactory
import id.walt.wallet2.client.stores.HardwareKeyStore
import id.walt.wallet2.client.stores.SqlDelightCredentialStore
import id.walt.wallet2.client.stores.SqlDelightDidStore

actual class MobileWalletClientFactory {
    actual fun create(config: MobileWalletConfig): NativeWalletClient {
        val driver = DriverFactory().createDriver("wallet_${config.walletId}")
        val db = WalletClientDatabase(driver)
        val queries = db.walletClientQueries

        val keyProvider = IosPlatformKeyProvider(useSecureElement = config.preferHardwareKeys)
        val keyStore = HardwareKeyStore(keyProvider, queries)
        val credentialStore = SqlDelightCredentialStore(queries)
        val didStore = SqlDelightDidStore(queries)

        return NativeWalletClient(
            walletId = config.walletId,
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            keyGenerator = { keyType -> keyProvider.generateKey(keyType) },
            attestationConfig = config.attestationConfig,
            onEvent = config.onEvent,
        )
    }
}
