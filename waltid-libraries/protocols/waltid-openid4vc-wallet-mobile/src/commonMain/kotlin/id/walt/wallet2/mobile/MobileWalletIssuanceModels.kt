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

/**
 * One credential instance, backed by an existing platform wallet key.
 *
 * @property keyId Existing wallet signing key identifier; constructing a binding does not generate a key.
 * @property did Holder DID associated with the key when the issuer requires DID binding.
 */
public data class MobileWalletHolderBinding(
    public val keyId: String,
    public val did: String? = null,
) {
    init {
        require(keyId.isNotBlank()) { "Holder key ID must not be blank" }
        require(did == null || did.isNotBlank()) { "Holder DID must not be blank" }
    }
}

/** Holder-key choice for one accepted configuration or dataset. */
public sealed interface MobileWalletCredentialHolders {
    /**
     * Uses keys already owned by the wallet; the SDK never cleans these up on an acceptance error.
     *
     * @property bindings Existing wallet keys for the requested credential instances.
     */
    public data class Existing(public val bindings: List<MobileWalletHolderBinding>) : MobileWalletCredentialHolders {
        init { require(bindings.isNotEmpty()) { "At least one holder binding is required" } }
    }

    /**
     * Creates distinct platform keys inside acceptance, using the wallet's configured key policy.
     *
     * @property count Number of credential instances for which to create distinct holder keys.
     */
    public data class NewKeys(public val count: Int) : MobileWalletCredentialHolders {
        init { require(count >= 1) { "At least one holder key must be requested" } }
    }
}

/**
 * Accepted configuration/dataset and its holder-key choice.
 *
 * New keys are prepared by the SDK, not the UI. Rejected or cancelled preparation removes them
 * before acceptance; after acceptance they remain wallet-owned even if the issuer outcome is uncertain.
 *
 * @property credentialConfigurationId Credential configuration identifier from the reviewed offer.
 * @property holders Existing bindings or an explicit number of new keys; never both.
 * @property credentialIdentifier Optional dataset identifier granted by the issuer; never invent one.
 */
public data class MobileWalletCredentialSelection(
    public val credentialConfigurationId: String,
    public val holders: MobileWalletCredentialHolders,
    public val credentialIdentifier: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) { "Credential configuration must not be blank" }
        require(credentialIdentifier == null || credentialIdentifier.isNotBlank()) { "Credential identifier must not be blank" }
    }
}
