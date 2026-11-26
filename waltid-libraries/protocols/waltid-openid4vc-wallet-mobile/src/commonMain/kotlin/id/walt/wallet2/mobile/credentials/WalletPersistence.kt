package id.walt.wallet2.mobile.credentials

import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider

/**
 * Wallet-local persistence configuration.
 *
 * @property databaseKey Owner of the SQLCipher key used for the encrypted local wallet database.
 * @property credentialStore Optional credential-store override. `null` uses the encrypted SQLDelight store.
 * @property didStore Optional DID-store override. `null` uses the encrypted SQLDelight store.
 */
public data class WalletPersistence(
    public val databaseKey: WalletDatabaseKey = WalletDatabaseKey.Managed,
    public val credentialStore: WalletCredentialStore? = null,
    public val didStore: WalletDidStore? = null,
)

/**
 * Selects who owns the encrypted wallet database key.
 */
public sealed interface WalletDatabaseKey {
    /**
     * Uses platform-protected storage to create, load, and delete the database key.
     */
    public data object Managed : WalletDatabaseKey

    /**
     * Uses key material supplied by application code.
     *
     * @property provider Provider that returns SQLCipher key material for this wallet database.
     */
    public data class Provided(
        public val provider: DatabaseEncryptionKeyProvider,
    ) : WalletDatabaseKey
}
