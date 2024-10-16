package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.OIDC.resolveConfiguration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("oidc-config")
data class OidcAuthConfiguration(
    val openIdConfigurationUrl: String? = null,
    var openIdConfiguration: OIDC.OpenIdConfiguration = OIDC.OpenIdConfiguration.INVALID,

    val clientId: String,
    val clientSecret: String,

    val accountIdentifierClaim: String = "sub",
) : AuthMethodConfiguration {
    fun check() {
        require(openIdConfiguration != OIDC.OpenIdConfiguration.INVALID || openIdConfigurationUrl != null) { "Either openIdConfiguration or openIdConfigurationUrl have to be provided!" }
    }

    suspend fun init() {
        check()

        if (openIdConfiguration == OIDC.OpenIdConfiguration.INVALID) {
            // TODO: move to cache
            openIdConfiguration = resolveConfiguration(openIdConfigurationUrl!!)
        }
    }

    init {
        runBlocking { init() }
    }

    override fun authMethod() = OIDC
}
