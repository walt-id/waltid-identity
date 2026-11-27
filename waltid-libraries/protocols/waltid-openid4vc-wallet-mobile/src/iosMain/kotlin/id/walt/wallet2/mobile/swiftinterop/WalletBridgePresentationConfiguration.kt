package id.walt.wallet2.mobile.swiftinterop

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.consent.PaymentCredentialIssuer
import id.walt.wallet2.mobile.presentation.PresentationConfiguration
import id.walt.wallet2.mobile.presentation.TransactionDataProfile

/**
 * Verifier trust and consent profiles supplied by a native Swift wallet.
 *
 * @property clientIdTrustConfiguration Trust anchors and registered clients for verifier requests.
 * @property transactionDataProfiles Accepted transaction data profiles.
 * @property paymentCredentialIssuers Trusted payment attestation issuers.
 */
public data class WalletBridgePresentationConfiguration(
    public val clientIdTrustConfiguration: WalletBridgeClientIdTrustConfiguration = WalletBridgeClientIdTrustConfiguration(),
    public val transactionDataProfiles: List<TransactionDataProfile> = emptyList(),
    public val paymentCredentialIssuers: List<WalletBridgePaymentCredentialIssuer> = emptyList(),
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

internal fun WalletBridgePresentationConfiguration.toPresentationConfiguration(): PresentationConfiguration = PresentationConfiguration(
    verifierTrust = clientIdTrustConfiguration.toClientIdTrustConfiguration(),
    transactionDataProfiles = transactionDataProfiles,
    paymentCredentialIssuers = paymentCredentialIssuers.map { it.toCore() },
)
