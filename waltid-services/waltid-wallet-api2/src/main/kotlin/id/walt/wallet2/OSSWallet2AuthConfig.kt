package id.walt.wallet2

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.Serializable

/**
 * Configuration for the optional auth feature.
 *
 * Example `auth.conf`:
 * ```hocon
 * auth {
 *   # JWT signing secret for session tokens (HS256). Generate with: openssl rand -hex 32
 *   jwtSecret = "change-me-in-production"
 *   # Token expiry in seconds. Default: 86400 (24h)
 *   tokenExpirySeconds = 86400
 * }
 * ```
 */
@Serializable
data class OSSWallet2AuthConfig(
    val jwtSecret: String,
    val tokenExpirySeconds: Long = 86400L,
) : WaltConfig()
