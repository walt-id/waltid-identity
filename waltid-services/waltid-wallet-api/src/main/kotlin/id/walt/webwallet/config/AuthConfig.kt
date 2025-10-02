package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class AuthConfig(
    val encryptionKey: String,
    val signKey: String,
    val tokenKey: String,
    val issTokenClaim: String,
    val audTokenClaim: String? = null,
    val tokenLifetime: String,
    val refreshTokenLifetime: String? = null,
    val idleTimeoutMinutes: Int? = null,
) : WalletConfig()
