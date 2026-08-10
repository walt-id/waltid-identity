package id.walt.wallet2.mobile

/**
 * App-facing input for starting an OpenID4VCI issuance session.
 *
 * Exactly one of [offerUrl] or [offerJson] must be provided. [offerUrl] is used for deep-link /
 * QR offers (`openid-credential-offer://…`). [offerJson] is used when the offer arrives as an
 * inline Credential Offer object, including Digital Credentials API `CREATE_CREDENTIAL` handoffs.
 *
 * The selected [keyId] is used for DPoP, holder binding, and credential proof creation. When it is
 * omitted, the wallet's first persisted key is selected. [did] is only required when the issuer
 * requires DID binding rather than JWK or COSE-key binding.
 *
 * @property offerUrl Credential-offer URL to resolve, or null when [offerJson] is supplied.
 * @property offerJson Inline Credential Offer JSON object, or null when [offerUrl] is supplied.
 * @property clientId OAuth client identifier sent to the authorization server.
 * @property redirectUri Exact callback URI registered for authorization-code issuance.
 * @property keyId Optional identifier of the holder key selected for DPoP and credential proofs.
 * @property did Optional holder DID URL used when the credential configuration requires DID binding.
 */
public data class MobileWalletIssuanceRequest(
    public val offerUrl: String? = null,
    public val offerJson: String? = null,
    public val clientId: String = "eudiw-abca",
    public val redirectUri: String = "openid://",
    public val keyId: String? = null,
    public val did: String? = null,
) {
    init {
        val url = offerUrl?.trim()?.takeIf { it.isNotEmpty() }
        val json = offerJson?.trim()?.takeIf { it.isNotEmpty() }
        require((url != null) xor (json != null)) {
            "Exactly one of offerUrl or offerJson must be provided"
        }
    }
}
