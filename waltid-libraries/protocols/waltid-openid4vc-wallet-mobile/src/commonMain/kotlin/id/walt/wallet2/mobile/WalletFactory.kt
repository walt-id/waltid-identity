package id.walt.wallet2.mobile

/**
 * Platform factory that wires [Wallet] to Android or iOS storage and key infrastructure.
 */
public expect class WalletFactory {
    /**
     * Opens the configured wallet and its persistent storage.
     *
     * @param config Wallet configuration. Defaults use the stable `default` wallet identifier and P-256 key material.
     */
    public suspend fun open(config: WalletConfiguration = WalletConfiguration()): Wallet
}
