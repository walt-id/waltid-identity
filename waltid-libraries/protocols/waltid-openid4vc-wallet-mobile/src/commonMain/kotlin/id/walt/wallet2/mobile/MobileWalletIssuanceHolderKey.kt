package id.walt.wallet2.mobile

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy

/**
 * A separately persisted holder key for credential issuance.
 * Creating this key does not replace or activate the wallet's signing identity.
 */
public data class MobileWalletIssuanceHolderKey(
    public val keyId: String,
    public val did: String,
    /** Public JWK only; private key material remains in the platform key store. */
    public val publicJwk: String,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
)
