@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2

import id.walt.commons.config.WaltConfig
import id.walt.openid4vp.clientidprefix.ClientIdX509TrustRegistry
import id.walt.trust.model.TrustedEntityType
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.trust.WalletIssuanceTrust
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OSS Wallet Service configuration, loaded from the service's HOCON config file.
 *
 * Example `wallet.conf`:
 * ```hocon
 * wallet-service {
 *     publicBaseUrl = "http://localhost:4000"
 *     attestationConfig {
 *         attesterUrl = "https://wallet-provider.example.com/wallet-instance-attestation/jwk"
 *         requestBody {
 *             jwk = "{{public_jwk}}"
 *         }
 *     }
 *     # Required for OpenID4VP x509_san_dns / x509_hash clients. Empty fails those prefixes closed.
 *     # Append more PEM strings to trust additional verifiers. Demo material from verifier-service.conf:
 *     # clientIdTrust {
 *     #     x509TrustAnchors = [
 *     #         """-----BEGIN CERTIFICATE-----
 *     #         MIIBVzCB/aADAgECAggNKZAvUrtimzAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2
 *     #         ...
 *     #         -----END CERTIFICATE-----"""
 *     #     ]
 *     # }
 *     # Or decide x509 clients through a trust registry. x509TrustAnchors then apply only to
 *     # verifier_attestation x5c chains:
 *     # clientIdTrust {
 *     #     trustRegistry {
 *     #         resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain"
 *     #     }
 *     # }
 *     # Store a received credential only if a trust registry trusts the X.509 chain that signed it:
 *     # issuanceTrust {
 *     #     trustRegistry {
 *     #         resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain"
 *     #     }
 *     # }
 * }
 * ```
 */
@Serializable
data class OSSWallet2ServiceConfig(
    /** Public-facing base URL for this wallet service. */
    val publicBaseUrl: Url,
    val attestationConfig: WalletAttestationConfig? = null,
    val clientIdTrust: ClientIdTrustConfig = ClientIdTrustConfig(),
    /** Issuer trust for credentials received through OpenID4VCI. Off unless [IssuanceTrustConfig.trustRegistry] is set. */
    val issuanceTrust: IssuanceTrustConfig = IssuanceTrustConfig(),
    /**
     * `transaction_data` types this wallet understands, e.g. `["payment"]`.
     *
     * OpenID4VP 1.0 5.1 requires a wallet to reject any request containing an unrecognised
     * `transaction_data` type, so the default of none is the safe behaviour - but it has to be
     * configurable, otherwise the feature can never be turned on.
     */
    val transactionDataTypes: Set<String> = emptySet(),
) : WaltConfig()

/**
 * Verifier client-ID trust used for OpenID4VP Request Object authentication.
 *
 * @property x509TrustAnchors PEM-encoded trust anchors for `x509_san_dns` and `x509_hash` clients while
 *   [trustRegistry] is not set, and for `verifier_attestation` `x5c` chains. Append additional PEMs to trust
 *   more verifiers. Without these or [trustRegistry], the x509 prefixes fail closed.
 * @property trustRegistry decides `x509_san_dns` and `x509_hash` clients through a trust registry. Those
 *   prefixes then ignore [x509TrustAnchors].
 * @property trustedVerifierAttestationIssuers DIDs allowed to issue verifier attestation JWTs.
 * @property preRegisteredClients Metadata for `pre-registered` client IDs such as Verifier2's default `verifier2`.
 */
@Serializable
data class ClientIdTrustConfig(
    val x509TrustAnchors: List<String> = emptyList(),
    val trustedVerifierAttestationIssuers: Set<String> = emptySet(),
    val preRegisteredClients: Map<String, ClientMetadata> = emptyMap(),
    val trustRegistry: ClientIdTrustRegistryConfig? = null,
)

/**
 * The trust registry resolver that decides `x509_san_dns` and `x509_hash` Request Objects. A chain is
 * trusted when the resolver answers `TRUSTED` for one of [expectedEntityTypes], from an accepted source
 * that is fresh.
 *
 * @property resolveCertificateChainUrl the full URL of a `resolve/certificate-chain` endpoint, for example an
 *   Enterprise `{base}/v1/{target}/trust-registry-api/resolve/certificate-chain`. A resolver on a loopback or
 *   private address must be allowed in `network-fetch-guard.conf`. The wallet verifies no certificate signature
 *   in the chain, so the resolver must validate the path itself, as the Enterprise Trust Registry does
 *   (see [ClientIdX509TrustRegistry.evaluator]).
 * @property headers sent with every request, for example `Authorization = "Bearer ..."`.
 * @property expectedEntityTypes the registry roles a verifier's chain may match.
 * @property requireAuthenticatedSource additionally require an authenticated (signed and trusted) source.
 */
@Serializable
data class ClientIdTrustRegistryConfig(
    val resolveCertificateChainUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedEntityTypes: Set<TrustedEntityType> = ClientIdX509TrustRegistry.RELYING_PARTY_ENTITY_TYPES,
    val requireAuthenticatedSource: Boolean = false,
) {
    /** Header values can hold credentials, and configurations are logged. */
    override fun toString(): String =
        "ClientIdTrustRegistryConfig(resolveCertificateChainUrl=$resolveCertificateChainUrl, headers=${headers.keys}, " +
            "expectedEntityTypes=$expectedEntityTypes, requireAuthenticatedSource=$requireAuthenticatedSource)"
}

/**
 * Issuer trust for credentials received through OpenID4VCI, for every wallet of this service. Without
 * [trustRegistry] a wallet stores what an issuer returns without checking the issuer. With it, every
 * credential of a response must be signed by a key certified by an X.509 chain that the registry trusts
 * as one of the entity types configured for the credential's signed type, or nothing of the response is
 * stored and issuance fails with `ISSUER_NOT_TRUSTED`. DID- or HTTPS-key-signed credentials have no chain
 * and are rejected, so leave [trustRegistry] unset for issuers that sign that way.
 *
 * @property credentialTypes rules from a signed SD-JWT VC `vct` or mdoc doctype to the registry roles allowed
 *   to issue it. The default maps the EUDI PID to `PID_PROVIDER`.
 * @property otherCredentialTypes the roles for every other credential, including W3C VCs. Empty rejects them.
 */
@Serializable
data class IssuanceTrustConfig(
    val trustRegistry: IssuanceTrustRegistryConfig? = null,
    val credentialTypes: List<IssuerEntityTypeRuleConfig> = WalletIssuanceTrust.DEFAULT_CREDENTIAL_TYPES.map {
        IssuerEntityTypeRuleConfig(it.vcts, it.doctypes, it.entityTypes)
    },
    val otherCredentialTypes: Set<TrustedEntityType> = WalletIssuanceTrust.DEFAULT_OTHER_CREDENTIAL_TYPES,
)

/** Credentials of these signed types (exact match) must be issued by one of [entityTypes]. */
@Serializable
data class IssuerEntityTypeRuleConfig(
    val vcts: Set<String> = emptySet(),
    val doctypes: Set<String> = emptySet(),
    val entityTypes: Set<TrustedEntityType>,
)

/**
 * The trust registry resolver that decides credential issuers.
 *
 * @property resolveCertificateChainUrl the full URL of a `resolve/certificate-chain` endpoint, for example an
 *   Enterprise `{base}/v1/{target}/trust-registry-api/resolve/certificate-chain`. A resolver on a loopback or
 *   private address must be allowed in `network-fetch-guard.conf`. The wallet sends the chain that verified
 *   the credential signature and checks no certificate signature in it, so the resolver must validate the
 *   path itself, as the Enterprise Trust Registry does.
 * @property headers sent with every request, for example `Authorization = "Bearer ..."`.
 * @property requireAuthenticatedSource additionally require an authenticated (signed and trusted) source.
 */
@Serializable
data class IssuanceTrustRegistryConfig(
    val resolveCertificateChainUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val requireAuthenticatedSource: Boolean = false,
) {
    /** Header values can hold credentials, and configurations are logged. */
    override fun toString(): String =
        "IssuanceTrustRegistryConfig(resolveCertificateChainUrl=$resolveCertificateChainUrl, headers=${headers.keys}, " +
            "requireAuthenticatedSource=$requireAuthenticatedSource)"
}

@Serializable
data class WalletAttestationConfig(
    val attesterUrl: String,
    val requestBody: JsonObject,
)
