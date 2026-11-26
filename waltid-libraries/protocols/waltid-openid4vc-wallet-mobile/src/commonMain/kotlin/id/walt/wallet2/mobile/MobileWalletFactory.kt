package id.walt.wallet2.mobile

/**
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
public expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults use the stable `default` wallet identifier and P-256 key material.
     */
    public suspend fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}
