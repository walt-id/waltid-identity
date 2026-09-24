package id.walt.wallet2.mobile

/**
 * Credential offer input for [MobileWalletIssuanceRequest].
 *
 * Prefer [Uri] for deep-link / QR offers (`openid-credential-offer://…`).
 * Prefer [InlineJson] when the offer arrives as an inline Credential Offer object,
 * including Digital Credentials API `CREATE_CREDENTIAL` handoffs.
 */
public sealed interface MobileWalletCredentialOffer {
    /** Discover configurations from issuer metadata for wallet-initiated authorization. */
    public data class Issuer(
        public val credentialIssuer: String,
        public val credentialConfigurationIds: List<String>,
    ) : MobileWalletCredentialOffer {
        init {
            require(credentialIssuer.isNotBlank())
            require(credentialConfigurationIds.isNotEmpty() && credentialConfigurationIds.none(String::isBlank))
        }
    }
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
 * The selected [keyId] is used for OAuth/DPoP and as the default single-instance holder key.
 * Accepted credential selections may choose different holder keys without changing the OAuth key. When it is
 * omitted, the wallet's active signing identity is selected. [did] is only required when the issuer
 * requires DID binding rather than JWK or COSE-key binding.
 *
 * @property offer Credential offer as a URI or inline JSON object.
 * @property clientId OAuth client identifier sent to the authorization server.
 * @property redirectUri Exact callback URI registered for authorization-code issuance.
 * @property keyId Optional identifier of the holder key selected for DPoP and credential proofs.
 * @property did Optional holder DID URL used when the credential configuration requires DID binding.
 * @property keyPolicy Minimum host/issuer profile policy; requires an identity created under that retained policy.
 */
public data class MobileWalletIssuanceRequest(
    public val offer: MobileWalletCredentialOffer,
    public val clientId: String = "eudiw-abca",
    public val redirectUri: String = "openid://",
    public val keyId: String? = null,
    public val did: String? = null,
    public val keyPolicy: id.walt.wallet2.mobile.identity.SigningIdentityKeyPolicy = id.walt.wallet2.mobile.identity.SigningIdentityKeyPolicy.GeneralPurpose,
)

/** One credential instance, backed by an existing platform wallet key. */
public data class MobileWalletHolderBinding(
    public val keyId: String,
    public val did: String? = null,
)

/** Accepted configuration/dataset and the holder keys for its requested instances. */
public data class MobileWalletCredentialSelection(
    public val credentialConfigurationId: String,
    public val holderBindings: List<MobileWalletHolderBinding>,
    public val credentialIdentifier: String? = null,
)

internal fun List<MobileWalletCredentialSelection>.toLibrarySelections(): List<id.walt.wallet2.handlers.WalletCredentialSelection> =
    map { selection ->
        id.walt.wallet2.handlers.WalletCredentialSelection(
            selection.credentialConfigurationId,
            selection.credentialIdentifier,
            selection.holderBindings.map { id.walt.wallet2.handlers.CredentialHolderBinding(it.keyId, it.did) },
        )
    }
