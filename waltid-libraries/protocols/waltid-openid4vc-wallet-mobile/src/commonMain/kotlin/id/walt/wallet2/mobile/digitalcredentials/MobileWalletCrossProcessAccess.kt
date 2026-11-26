package id.walt.wallet2.mobile.digitalcredentials

/**
 * Cross-process wallet access required by native document-provider extensions.
 *
 * @property appGroupIdentifier Apple App Group used to share wallet state with the extension.
 * @property keychainAccessGroup Keychain access group shared by the app and extension.
 */
public data class MobileWalletCrossProcessAccess(
    public val appGroupIdentifier: String,
    public val keychainAccessGroup: String,
)
