package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt

/**
 * Key protection for this wallet.
 * @property policy Authorization required for newly created keys. Existing keys retain their policy.
 * @property prompt Text presented for protected signing operations.
 */
public data class MobileWalletKeyAuthorization(
    public val policy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
    public val prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
)
