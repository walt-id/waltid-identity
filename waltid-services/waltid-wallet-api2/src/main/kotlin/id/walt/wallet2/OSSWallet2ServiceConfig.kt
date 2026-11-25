@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2

import id.walt.commons.config.WaltConfig
import id.walt.openid4vp.clientidprefix.ClientIdX509TrustRegistry
import id.walt.trust.model.TrustedEntityType
import id.walt.verifier.openid.models.authorization.ClientMetadata
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
 * }
 * ```
 */
@Serializable
data class OSSWallet2ServiceConfig(
    /** Public-facing base URL for this wallet service. */
    val publicBaseUrl: Url,
    val attestationConfig: WalletAttestationConfig? = null,
    val clientIdTrust: ClientIdTrustConfig = ClientIdTrustConfig(),
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
 *   private address must be allowed in `network-fetch-guard.conf`.
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

@Serializable
data class WalletAttestationConfig(
    val attesterUrl: String,
    val requestBody: JsonObject,
)
