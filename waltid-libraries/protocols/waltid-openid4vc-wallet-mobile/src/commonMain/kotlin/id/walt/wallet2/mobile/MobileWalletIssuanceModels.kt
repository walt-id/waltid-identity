package id.walt.wallet2.mobile

/** Mobile client policy for using DPoP during an issuance session. */
public enum class MobileWalletIssuanceDpopMode {
    /** Do not include DPoP authorization bindings or proofs. */
    DISABLED,

    /** Use DPoP when the authorization server advertises supported algorithms. */
    IF_SUPPORTED,
}

/**
 * App-facing input for starting an OpenID4VCI issuance session.
 *
 * The selected [keyId] is used for DPoP, holder binding, and credential proof creation. When it is
 * omitted, the wallet's first persisted key is selected. [did] is only required when the issuer
 * requires DID binding rather than JWK or COSE-key binding.
 *
 * @property offerUrl Credential-offer URL or inline credential offer to resolve.
 * @property clientId OAuth client identifier sent to the authorization server.
 * @property redirectUri Exact callback URI registered for authorization-code issuance.
 * @property keyId Optional identifier of the holder key selected for DPoP and credential proofs.
 * @property did Optional holder DID URL used when the credential configuration requires DID binding.
 * @property dpopMode Client-selected DPoP policy for the issuance session.
 */
public data class MobileWalletIssuanceRequest(
    public val offerUrl: String,
    public val clientId: String = "eudiw-abca",
    public val redirectUri: String = "openid://",
    public val keyId: String? = null,
    public val did: String? = null,
    public val dpopMode: MobileWalletIssuanceDpopMode = MobileWalletIssuanceDpopMode.IF_SUPPORTED,
) {
}
