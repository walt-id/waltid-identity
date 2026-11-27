package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.issuance.CredentialReceiptAuthority
import id.walt.wallet2.issuance.CredentialReceiptTrust
import id.walt.wallet2.mobile.issuance.IssuanceConfiguration
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.wallet2.mobile.issuance.WalletProviderConfig
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import kotlinx.serialization.json.Json

/**
 * Issuance integrations supplied by a native Swift wallet.
 *
 * @property attestation Optional client-attestation configuration.
 * @property walletProvider Wallet provider for instance attestation and fallback key attestation.
 * @property issuerMetadataTrustResolver Host verifier for signed credential issuer metadata.
 * @property receiptAuthoritiesJson Encoded receipt authorities. An absent profile uses ordinary storage.
 */
public data class WalletBridgeIssuanceConfiguration(
    public val attestation: WalletAttestationConfig? = null,
    public val walletProvider: WalletProviderConfig? = null,
    public val issuerMetadataTrustResolver: WalletBridgeIssuerMetadataTrustResolver? = null,
    public val receiptAuthoritiesJson: String? = null,
)

/** Trusted signer details returned after Swift verifies signed Credential Issuer Metadata. */
public data class WalletBridgeIssuerMetadataSigner(
    /** Identifier of the trusted verification key, as reported by the trust resolver. */
    public val keyId: String?,
    /** JWS algorithm verified by the trust resolver. */
    public val algorithm: String,
    /** Authority category established by the trust resolver. */
    public val trustType: WalletBridgeIssuerMetadataSignerTrustType,
)

/** Authority category assigned by Swift after it verifies the signed metadata JWS. */
public enum class WalletBridgeIssuerMetadataSignerTrustType {
    /** The signer is the trusted credential issuer. */
    TrustedIssuer,
    /** The signer is a trusted delegate of the credential issuer. */
    TrustedDelegate,
}

/**
 * Swift-facing trust boundary for signed Credential Issuer Metadata.
 *
 * Implementations must verify the JWS and establish that its signer is authorized for the requested issuer.
 * Returning normally authorizes the metadata for wallet use.
 */
public interface WalletBridgeIssuerMetadataTrustResolver {
    /**
     * @param compactJwt Compact JWS returned by the Credential Issuer Metadata endpoint.
     * @param expectedCredentialIssuer Credential issuer for which signer authority must be established.
     * @return Trusted signer details used to retain metadata provenance.
     */
    public suspend fun verify(
        compactJwt: String,
        expectedCredentialIssuer: String,
    ): WalletBridgeIssuerMetadataSigner
}

internal fun WalletBridgeIssuanceConfiguration.toIssuanceConfiguration(): IssuanceConfiguration = IssuanceConfiguration(
    attestation = attestation,
    walletProvider = walletProvider,
    receiptTrust = receiptAuthoritiesJson?.let { CredentialReceiptTrust(Json.decodeFromString<List<CredentialReceiptAuthority>>(it)) },
    metadataTrustResolver = issuerMetadataTrustResolver?.let { bridgeResolver ->
        CredentialIssuerMetadataTrustResolver { compactJwt, expectedCredentialIssuer ->
            bridgeResolver.verify(compactJwt, expectedCredentialIssuer).toMetadataSigner()
        }
    },
)

private fun WalletBridgeIssuerMetadataSigner.toMetadataSigner(): MetadataSigner = MetadataSigner(
    keyId = keyId,
    algorithm = algorithm,
    trustType = when (trustType) {
        WalletBridgeIssuerMetadataSignerTrustType.TrustedIssuer -> MetadataSignerTrustType.TRUSTED_ISSUER
        WalletBridgeIssuerMetadataSignerTrustType.TrustedDelegate -> MetadataSignerTrustType.TRUSTED_DELEGATE
    },
)
