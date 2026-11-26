@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.swiftinterop

import kotlinx.serialization.json.Json
import id.walt.wallet2.issuance.CredentialReceiptAuthority
import id.walt.wallet2.issuance.CredentialReceiptTrust
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.consent.PaymentCredentialIssuer
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialsConfig
import id.walt.wallet2.mobile.digitalcredentials.UnavailableMobileWalletCredentialRegistry
import id.walt.wallet2.mobile.identity.MobileWalletKeyAuthorization
import id.walt.wallet2.mobile.identity.SigningIdentityConfiguration
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceConfig
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.wallet2.mobile.issuance.WalletProviderConfig
import id.walt.wallet2.mobile.platformPreferredLocales
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationConfig
import id.walt.wallet2.mobile.presentation.MobileWalletTransactionDataProfile
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Configuration used when creating an iOS [WalletSdkBridge].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property persistence Wallet-local persistence configuration.
 * @property databaseKeyProvider Swift-owned database key provider used when [persistence] uses
 * [WalletBridgeDatabaseKeyConfiguration.Provided].
 * @property attestation Optional client-attestation configuration for issuers that require it.
 * @property issuerMetadataTrustResolver Optional Swift-owned verifier for signed Credential Issuer Metadata.
 * @property preferredLocales Ordered BCP 47 locale preferences used to select display metadata.
 * @property transactionDataProfiles Transaction data profiles this wallet accepts.
 * @property clientIdTrustConfiguration Trust anchors used to authenticate verifier Request Objects.
 * @property appGroupIdentifier Shared container used by the app and document-provider extension.
 * @property keychainAccessGroup Shared Keychain access group used for database and signing keys.
 * @property defaultKeyUseAuthorizationPolicy Default authorization policy for newly created wallet keys.
 * @property keyUseAuthorizationPrompt Prompt text used for protected signing operations.
 */
public data class WalletBridgeConfiguration(
    public val walletId: String = "default",
    public val persistence: WalletBridgePersistence = WalletBridgePersistence(),
    public val databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider? = null,
    public val attestation: WalletAttestationConfig? = null,
    /** Enterprise Wallet Provider. When set, it supplies WIA and is the fallback KA provider. */
    public val walletProvider: WalletProviderConfig? = null,
    /** Signed metadata is neither requested nor accepted when this trust resolver is absent. */
    public val issuerMetadataTrustResolver: WalletBridgeIssuerMetadataTrustResolver? = null,
    /** Optional independently configured receipt authorities encoded by the native façade. */
    public val receiptAuthoritiesJson: String? = null,
    public val preferredLocales: List<String> = platformPreferredLocales(),
    public val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    /** Independently configured payment attestation issuer keys. */
    public val paymentCredentialIssuers: List<WalletBridgePaymentCredentialIssuer> = emptyList(),
    public val clientIdTrustConfiguration: WalletBridgeClientIdTrustConfiguration = WalletBridgeClientIdTrustConfiguration(),
    /** Publish wallet metadata through the platform's credential registry. */
    public val registersDigitalCredentials: Boolean = true,
    public val appGroupIdentifier: String? = null,
    public val keychainAccessGroup: String? = null,
    public val defaultKeyUseAuthorizationPolicy: WalletBridgeKeyUseAuthorizationPolicy =
        WalletBridgeKeyUseAuthorizationPolicy.BiometricCurrentSet,
    public val keyUseAuthorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    /** Shared identity policy and trusted, explicitly registered recovery providers. */
    public val signingIdentity: SigningIdentityConfiguration = SigningIdentityConfiguration(),
)

/**
 * Verifier Request Object trust configuration exposed to the Swift wallet bridge.
 *
 * @property x509TrustAnchorsPem PEM-encoded trust anchors pinned by the hosting application.
 *   Required for OpenID4VP `x509_san_dns` and `x509_hash` clients; an empty list fails those
 *   prefixes closed. Append additional PEMs to trust more verifiers.
 * @property preRegisteredClientMetadataJson Explicit pre-registered client metadata, keyed by client ID.
 */
public data class WalletBridgeClientIdTrustConfiguration(
    public val x509TrustAnchorsPem: List<String> = emptyList(),
    public val preRegisteredClientMetadataJson: Map<String, String> = emptyMap(),
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

/**
 * Swift-friendly public verification material.
 * @property issuer Exact credential issuer identifier to trust.
 * @property publicJwkJson Independently configured public verification key as JWK JSON.
 * @property algorithm Allowed JWS algorithm identifier for this key.
 */
public data class WalletBridgePaymentCredentialIssuer(
    public val issuer: String,
    public val publicJwkJson: String,
    public val algorithm: String = "ES256",
) {
    internal fun toCore(): PaymentCredentialIssuer = PaymentCredentialIssuer(
        issuer, publicJwkJson, JwsAlgorithm.entries.first { it.identifier == algorithm },
    )
}

internal fun WalletBridgeClientIdTrustConfiguration.toClientIdTrustConfiguration(): ClientIdTrustConfiguration =
    ClientIdTrustConfiguration(
        x509TrustAnchors = InMemoryTrustStore(x509TrustAnchorsPem.map(X509CertificateUtil::parseCertificatePem)),
        preRegisteredClients = preRegisteredClientMetadataJson.mapValues { (clientId, metadataJson) ->
            ClientMetadata.fromJson(metadataJson).getOrElse { cause ->
                throw IllegalArgumentException("Malformed pre-registered client metadata for '$clientId'", cause)
            }
        },
    )

internal fun WalletBridgeConfiguration.toMobileWalletConfig(): MobileWalletConfig {
    require((appGroupIdentifier == null) == (keychainAccessGroup == null)) {
        "App Group and Keychain access group must be configured together"
    }
    return MobileWalletConfig(
        walletId = walletId,
        persistence = persistence.toMobileWalletPersistence(databaseKeyProvider),
        keyAuthorization = MobileWalletKeyAuthorization(
            policy = defaultKeyUseAuthorizationPolicy.toCorePolicy(),
            prompt = keyUseAuthorizationPrompt,
        ),
        signingIdentity = signingIdentity,
        issuance = MobileWalletIssuanceConfig(
            attestation = attestation,
            walletProvider = walletProvider,
            receiptTrust = receiptAuthoritiesJson?.let { CredentialReceiptTrust(Json.decodeFromString<List<CredentialReceiptAuthority>>(it)) },
            metadataTrustResolver = issuerMetadataTrustResolver?.let { bridgeResolver ->
                CredentialIssuerMetadataTrustResolver { compactJwt, expectedCredentialIssuer ->
                    bridgeResolver.verify(compactJwt, expectedCredentialIssuer).toMetadataSigner()
                }
            },
        ),
        presentation = MobileWalletPresentationConfig(
            verifierTrust = clientIdTrustConfiguration.toClientIdTrustConfiguration(),
            transactionDataProfiles = transactionDataProfiles,
            paymentCredentialIssuers = paymentCredentialIssuers.map { it.toCore() },
        ),
        digitalCredentials = MobileWalletDigitalCredentialsConfig(
            registry = if (registersDigitalCredentials) null else UnavailableMobileWalletCredentialRegistry,
            crossProcessAccess = appGroupIdentifier?.let { appGroup ->
                MobileWalletCrossProcessAccess(
                    appGroupIdentifier = appGroup,
                    keychainAccessGroup = requireNotNull(keychainAccessGroup),
                )
            },
        ),
        preferredLocales = preferredLocales,
    )
}

private fun WalletBridgeIssuerMetadataSigner.toMetadataSigner(): MetadataSigner = MetadataSigner(
    keyId = keyId,
    algorithm = algorithm,
    trustType = when (trustType) {
        WalletBridgeIssuerMetadataSignerTrustType.TrustedIssuer -> MetadataSignerTrustType.TRUSTED_ISSUER
        WalletBridgeIssuerMetadataSignerTrustType.TrustedDelegate -> MetadataSignerTrustType.TRUSTED_DELEGATE
    },
)
