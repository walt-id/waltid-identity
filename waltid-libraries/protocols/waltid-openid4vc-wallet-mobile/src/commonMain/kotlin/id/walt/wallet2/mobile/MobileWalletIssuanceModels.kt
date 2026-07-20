package id.walt.wallet2.mobile

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
 */
public data class MobileWalletIssuanceRequest(
    public val offerUrl: String,
    public val clientId: String = "eudiw-abca",
    public val redirectUri: String = "openid://",
    public val keyId: String? = null,
    public val did: String? = null,
) {
    /** Returns a diagnostic description with offer, key, and DID values redacted. */
    override fun toString(): String =
        "MobileWalletIssuanceRequest(offerUrl=<redacted>, clientId=$clientId, " +
            "redirectUri=$redirectUri, keyId=${keyId?.let { "<redacted>" }}, did=${did?.let { "<redacted>" }})"
}
