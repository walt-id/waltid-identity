package id.walt.wallet2.mobile

/**
 * Credential offer input for [MobileWalletIssuanceRequest].
 *
 * Prefer [Uri] for deep-link / QR offers (`openid-credential-offer://…`).
 * Prefer [InlineJson] when the offer arrives as an inline Credential Offer object,
 * including Digital Credentials API `CREATE_CREDENTIAL` handoffs.
 */
public sealed interface MobileWalletCredentialOffer {
    /**
     * Deep-link or QR credential offer URI (`openid-credential-offer://…`).
     *
     * @property value Non-blank credential offer URI.
     */
    public data class Uri(public val value: String) : MobileWalletCredentialOffer {
        init {
            require(value.trim().isNotEmpty()) { "Credential offer URI must not be blank" }
        }
    }

    /**
     * Inline Credential Offer JSON object as a string (cross-language friendly).
     *
     * @property value Non-blank Credential Offer JSON object.
     */
    public data class InlineJson(public val value: String) : MobileWalletCredentialOffer {
        init {
            require(value.trim().isNotEmpty()) { "Inline credential offer JSON must not be blank" }
        }
    }
}

/**
 * App-facing input for starting an OpenID4VCI issuance session.
 *
 * The selected [keyId] is used for DPoP, holder binding, and credential proof creation. When it is
 * omitted, the wallet's first persisted key is selected. [did] is only required when the issuer
 * requires DID binding rather than JWK or COSE-key binding.
 *
 * @property offer Credential offer as a URI or inline JSON object.
 * @property clientId OAuth client identifier sent to the authorization server.
 * @property redirectUri Exact callback URI registered for authorization-code issuance.
 * @property keyId Optional identifier of the holder key selected for DPoP and credential proofs.
 * @property did Optional holder DID URL used when the credential configuration requires DID binding.
 */
public data class MobileWalletIssuanceRequest(
    public val offer: MobileWalletCredentialOffer,
    public val clientId: String = "eudiw-abca",
    public val redirectUri: String = "openid://",
    public val keyId: String? = null,
    public val did: String? = null,
)
