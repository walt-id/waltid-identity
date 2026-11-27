package id.walt.wallet2.mobile.persistence

import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider

/**
 * Selects the database key source and optional credential and DID stores.
 * The wallet opens SQLCipher for signing-identity state and pending issuance even with both store overrides.
 *
 * @property databaseKeySource Owner of the SQLCipher key used for the encrypted local wallet database.
 * @property credentialStore Optional credential-store override. `null` uses the encrypted SQLDelight store.
 * @property didStore Optional DID-store override. `null` uses the encrypted SQLDelight store.
 */
public data class WalletPersistence(
    public val databaseKeySource: DatabaseKeySource = DatabaseKeySource.Managed,
    public val credentialStore: WalletCredentialStore? = null,
    public val didStore: WalletDidStore? = null,
)

/**
 * Selects who owns the encrypted wallet database key.
 */
public sealed interface DatabaseKeySource {
    /**
     * Uses platform-protected storage to create, load, and delete the database key.
     */
    public data object Managed : DatabaseKeySource

    /**
     * Uses key material supplied by application code.
     *
     * @property provider Provider that returns SQLCipher key material for this wallet database.
     */
    public data class Provided(
        public val provider: DatabaseEncryptionKeyProvider,
    ) : DatabaseKeySource
}
