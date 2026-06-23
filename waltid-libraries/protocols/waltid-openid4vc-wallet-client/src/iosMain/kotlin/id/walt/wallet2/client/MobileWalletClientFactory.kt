package id.walt.wallet2.client

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.persistence.stores.HardwareKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

actual class MobileWalletClientFactory {
    actual fun create(config: MobileWalletConfig): NativeWalletClient =
        create(config, IosWalletSecurityConfig())

    fun create(
        config: MobileWalletConfig,
        iosConfig: IosWalletSecurityConfig,
    ): NativeWalletClient {
        val driver = DriverFactory().createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries

        val keyProvider = IosPlatformKeyProvider(useSecureElement = iosConfig.useSecureElement)
        val keyStore = HardwareKeyStore(keyProvider, queries)
        val credentialStore = SqlDelightCredentialStore(queries)
        val didStore = SqlDelightDidStore(queries)

        return NativeWalletClient(
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
}
