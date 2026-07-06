package id.walt.wallet2

import id.walt.commons.config.WaltConfig
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
) : WaltConfig()

@Serializable
data class WalletAttestationConfig(
    val attesterUrl: String,
    val requestBody: JsonObject,
)
