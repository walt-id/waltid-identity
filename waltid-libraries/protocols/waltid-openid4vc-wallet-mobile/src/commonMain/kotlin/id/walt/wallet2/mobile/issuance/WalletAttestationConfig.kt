package id.walt.wallet2.mobile.issuance

import id.walt.wallet2.data.Wallet

/**
 * OAuth 2.0 client-attestation configuration used during mobile issuance.
 *
 * The wallet uses this configuration to request a client attestation JWT from the
 * enterprise client-attester service and attach the resulting proof to token requests.
 *
 * @property baseUrl Base URL of the deployment that hosts the attester service.
 * @property attesterPath Path to the attester endpoint, relative to [baseUrl].
 * @property bearerToken Optional bearer token for protected attester endpoints.
 * @property hostHeader Optional `Host` header override for tunneled local tests.
 */
public data class WalletAttestationConfig(
    public val baseUrl: String,
    public val attesterPath: String,
    public val bearerToken: String = "",
    public val hostHeader: String = "",
) {
    /** Redacts deployment credentials from log output. */
    override fun toString(): String = "WalletAttestationConfig(<redacted>)"
}

/**
 * Opt-in Enterprise Wallet Provider used for wallet instance and key attestations.
 *
 * @property baseUrl Origin of the Enterprise API, without a service path.
 * @property servicePath Wallet-provider target, `organization.tenant.service`.
 * @property bearerToken Optional bearer token for the wallet-provider API. Empty sends no Authorization header.
 * @property hostHeader Optional `Host` header override for tunneled local tests.
 * @property composition `wia`, `ka`, or `wia_ka`. Must match a composition that provider is configured to mint.
 */
public data class WalletProviderConfig(
    public val baseUrl: String,
    public val servicePath: String,
    public val bearerToken: String = "",
    public val hostHeader: String = "",
    public val composition: Composition = Composition.WalletInstanceAndKey,
) {
    /** Attestations to obtain from this deployment. */
    public enum class Composition(internal val wireValue: String) {
        /** Wallet instance attestation for authorization-server authentication. */
        WalletInstance("wia"),
        /** Credential holder key attestation. */
        Key("ka"),
        /** Both wallet instance and key attestations. */
        WalletInstanceAndKey("wia_ka"),
    }

    /** Identifies the selected attestation composition without exposing service credentials. */
    override fun toString(): String = "WalletProviderConfig(composition=$composition)"
}
