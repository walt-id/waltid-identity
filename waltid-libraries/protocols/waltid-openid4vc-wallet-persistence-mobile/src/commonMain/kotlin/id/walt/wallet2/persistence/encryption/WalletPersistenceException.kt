package id.walt.wallet2.persistence.encryption

/**
 * Base exception for mobile wallet persistence encryption failures.
 */
public sealed class WalletPersistenceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * The encrypted wallet database or stored database key could not be unlocked.
     */
    public class DatabaseUnlockFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' database could not be unlocked", cause)

    /**
     * Encrypted persistence is unavailable or incorrectly linked/configured on this platform.
     */
    public class EncryptionConfigurationFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' encrypted persistence is not configured correctly", cause)

    /**
     * A wallet database exists but the SDK-managed key needed to open it is missing.
     */
    public class DatabaseKeyMissing(walletId: String) :
        WalletPersistenceException("Wallet '$walletId' database exists but its SDK-managed database key is missing")
}
