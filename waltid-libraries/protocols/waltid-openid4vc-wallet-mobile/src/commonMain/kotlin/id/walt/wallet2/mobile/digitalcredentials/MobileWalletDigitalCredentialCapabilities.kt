package id.walt.wallet2.mobile.digitalcredentials

/** Protocol identifiers understood by platform Digital Credentials APIs. */
public object MobileWalletDigitalCredentialProtocols {
    /** Unsigned OpenID4VP Digital Credentials protocol identifier. */
    public const val OPENID4VP_UNSIGNED: String = "openid4vp-v1-unsigned"
    /** Signed OpenID4VP Digital Credentials protocol identifier. Not supported. */
    public const val OPENID4VP_SIGNED: String = "openid4vp-v1-signed"
    /** Multi-signed OpenID4VP Digital Credentials protocol identifier. Not supported. */
    public const val OPENID4VP_MULTISIGNED: String = "openid4vp-v1-multisigned"
    /** ISO 18013-7 Annex C mobile-document protocol identifier. */
    public const val ISO_MDOC_ANNEX_C: String = "org-iso-mdoc"
    /** Canonical OpenID4VCI Digital Credentials issuance protocol identifier. */
    public const val OPENID4VCI_V1: String = "openid4vci-v1"
}

/**
 * Credential formats exposed through the platform Digital Credentials integration.
 *
 * @property identifier Stable credential format identifier reported in capability metadata, never
 * the enum-entry name.
 */
public enum class MobileWalletDigitalCredentialFormat(public val identifier: String) {
    MDOC("mso_mdoc"),
    SD_JWT_VC("dc+sd-jwt"),
}

/**
 * Authentication applied to a Digital Credentials request.
 *
 * @property identifier Stable identifier reported in capability metadata.
 */
public enum class MobileWalletDigitalCredentialRequestProtection(public val identifier: String) {
    UNSIGNED("unsigned"),
    SIGNED("signed"),
    MULTISIGNED("multisigned"),
    READER_AUTHENTICATED("reader_authenticated"),
}

/**
 * Protection applied to the response returned to the requesting platform.
 *
 * @property identifier Stable identifier reported in capability metadata.
 */
public enum class MobileWalletDigitalCredentialResponseProtection(public val identifier: String) {
    UNENCRYPTED("unencrypted"),
    JWE("jwe"),
    HPKE("hpke"),
}

/**
 * Runtime support for one protocol/format/protection combination.
 *
 * @property protocol Platform Digital Credentials protocol identifier.
 * @property credentialFormats Credential formats accepted for this protocol.
 * @property requestProtection Authentication applied to the request.
 * @property responseProtection Protection applied to the response.
 * @property supported Whether this exact combination is currently available.
 * @property unsupportedReason Human-readable reason when [supported] is false.
 */
public data class MobileWalletDigitalCredentialCapability(
    public val protocol: String,
    public val credentialFormats: List<MobileWalletDigitalCredentialFormat>,
    public val requestProtection: List<MobileWalletDigitalCredentialRequestProtection>,
    public val responseProtection: List<MobileWalletDigitalCredentialResponseProtection>,
    public val supported: Boolean,
    public val unsupportedReason: String? = null,
)

/**
 * Truthful platform capability snapshot, including runtime and registration availability.
 *
 * @property platform Platform integration that produced this snapshot.
 * @property platformAvailable Whether the required platform API is available.
 * @property minimumOsVersion Lowest OS version supported by the platform integration.
 * @property registrationAvailable Whether metadata registration can currently be used.
 * @property capabilities Per-protocol capability details.
 */
public data class MobileWalletDigitalCredentialCapabilities(
    public val platform: String,
    public val platformAvailable: Boolean,
    public val minimumOsVersion: String,
    public val registrationAvailable: Boolean,
    public val capabilities: List<MobileWalletDigitalCredentialCapability>,
)
