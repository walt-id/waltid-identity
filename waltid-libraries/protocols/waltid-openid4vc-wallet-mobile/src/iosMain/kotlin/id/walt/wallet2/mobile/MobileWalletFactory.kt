package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.persistence.stores.HardwareKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

/**
 * iOS [MobileWallet] factory backed by Keychain/Secure Enclave and a native SQLDelight database.
 */
actual class MobileWalletFactory {
    /**
     * Creates an iOS mobile wallet using the default [IosWalletSecurityConfig].
     */
    actual fun create(config: MobileWalletConfig): MobileWallet =
        create(config, IosWalletSecurityConfig())

    /**
     * Creates an iOS mobile wallet with explicit iOS security settings.
     *
     * @param config Shared wallet configuration.
     * @param iosConfig iOS-specific key storage settings.
     */
    fun create(
        config: MobileWalletConfig,
        iosConfig: IosWalletSecurityConfig,
    ): MobileWallet {
        val driver = DriverFactory().createDriver("wallet_${config.walletId}")
        val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries

        val keyProvider = IosPlatformKeyProvider(useSecureElement = iosConfig.useSecureElement)
        val keyStore = HardwareKeyStore(keyProvider, queries)
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
}
