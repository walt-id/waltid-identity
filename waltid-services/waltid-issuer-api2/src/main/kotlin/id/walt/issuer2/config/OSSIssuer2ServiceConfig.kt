package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthProviderConfiguration(
    val name: String,
    val authorizeUrl: String,
    val accessTokenUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val defaultScopes: List<String> = listOf("openid", "profile"),
)

@Serializable
data class OSSIssuer2ServiceConfig(
    val baseUrl: String,
    val tokenKey: JsonObject? = null,
    val defaultNotifications: NotificationsConfig? = null,
    val authProviderConfiguration: AuthProviderConfiguration? = null,
) : WaltConfig()
