@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2

import id.walt.commons.config.WaltConfig
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
 * }
 * ```
 */
@Serializable
data class OSSWallet2ServiceConfig(
    /** Public-facing base URL for this wallet service. */
    val publicBaseUrl: Url,
    val attestationConfig: WalletAttestationConfig? = null,
    val clientIdTrust: ClientIdTrustConfig = ClientIdTrustConfig(),
) : WaltConfig()

@Serializable
data class ClientIdTrustConfig(
    val x509TrustAnchors: List<String> = emptyList(),
    val trustedVerifierAttestationIssuers: Set<String> = emptySet(),
    val preRegisteredClients: Map<String, ClientMetadata> = emptyMap(),
)

@Serializable
data class WalletAttestationConfig(
    val attesterUrl: String,
    val requestBody: JsonObject,
)
