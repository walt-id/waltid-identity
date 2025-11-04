package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.OIDC
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("oidc-config")
data class OidcAuthConfiguration(
    /** EITHER provide the discovery URL for automatic configuration, e.g., "https://idp.example.com/realms/my-realm/.well-known/openid-configuration" */
    val openIdConfigurationUrl: Url? = null,
    /** OR provide the configuration manually. */
    var openIdConfiguration: OIDC.OpenIdConfiguration? = null,

    val clientId: String,
    val clientSecret: String,

    /**
    The URI where the Ktor app will handle the callback from the IdP. Must be registered with the IdP.
    e.g., "http://localhost:8080/auth/oidc/callback"
     */
    val callbackUri: String,

    /** Enables Proof Key for Code Exchange (PKCE). Highly recommended for all clients. */
    val pkceEnabled: Boolean = true,

    /** Where to redirect to after the IDP redirected us to the authnz callback? (usually a frontend URL) */
    val redirectAfterLogin: Url? = null,

    /** The claim in the user info response to use for the unique account identifier. 'sub' is the standard. */
    val accountIdentifierClaim: String = "sub",
) : AuthMethodConfiguration {

    @kotlinx.serialization.Transient
    private val mutex = Mutex()

    fun check() {
        require(openIdConfigurationUrl != null || openIdConfiguration != null) {
            "OidcAuthConfig requires either 'openIdConfigurationUrl' or a manual 'openIdConfiguration' object."
        }
    }

    init {
        check()
    }

    /**
     * Asynchronously and safely resolves the OpenID configuration.
     * If a discovery URL is provided, it fetches and caches the configuration.
     */
    suspend fun getOpenIdConfiguration(): OIDC.OpenIdConfiguration {
        openIdConfiguration?.let { return it }
        return mutex.withLock {
            // Re-check in case another coroutine resolved it while waiting for the lock
            openIdConfiguration ?: OIDC.resolveConfiguration(openIdConfigurationUrl!!).also {
                openIdConfiguration = it
            }
        }
    }

    override fun authMethod() = OIDC
}
