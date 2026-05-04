package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.OIDC
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OidcExternalRoleExtractionConfiguration(
    val enabled: Boolean = false,
    val realmRolesClaimPath: String = "realm_access.roles",
    val clientRolesClaimPath: String = "resource_access",
    val clientId: String? = null,
)

@Serializable
@SerialName("oidc-config")
data class OidcAuthConfiguration(
    /** EITHER provide the discovery URL for automatic configuration */
    val openIdConfigurationUrl: Url? = null,
    /** OR provide the configuration manually. */
    var openIdConfiguration: OIDC.OpenIdConfiguration? = null,

    val clientId: String,
    val clientSecret: String,

    /** The callback URI registered with the IdP */
    val callbackUri: String,

    /** Enables PKCE. Highly recommended for all clients. */
    val pkceEnabled: Boolean = true,

    /** Default redirect URL after successful login (fallback if client doesn't specify redirect_to) */
    val redirectAfterLogin: Url? = null,

    /** Allowed redirect URL patterns for client-specified redirects (redirect_to parameter). Empty = no dynamic redirects allowed. */
    val allowedRedirectUrls: List<String> = emptyList(),

    /** The claim to use for the unique account identifier. 'sub' is the standard. */
    val accountIdentifierClaim: String = "sub",

    /** Optional extraction of external roles from OIDC ID token claims */
    val externalRoleExtraction: OidcExternalRoleExtractionConfiguration = OidcExternalRoleExtractionConfiguration(),
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

    /**
     * Validates if a client-specified redirect URL is allowed.
     * Returns the validated URL or null if not allowed.
     */
    fun validateRedirectUrl(redirectTo: String?): Url? {
        if (redirectTo == null) return null
        if (allowedRedirectUrls.isEmpty()) return null

        val requestedUrl = try {
            Url(redirectTo)
        } catch (e: Exception) {
            return null
        }

        for (pattern in allowedRedirectUrls) {
            if (matchesPattern(requestedUrl, pattern)) {
                return requestedUrl
            }
        }

        return null
    }

    private fun matchesPattern(url: Url, pattern: String): Boolean {
        if (url.toString() == pattern || url.toString().trimEnd('/') == pattern.trimEnd('/')) {
            return true
        }

        val patternUrl = try {
            Url(pattern.replace("*", "__WILDCARD__"))
        } catch (e: Exception) {
            return false
        }

        if (url.protocol != patternUrl.protocol) return false

        val patternHost = patternUrl.host.replace("__WILDCARD__", "*")
        if (patternHost.startsWith("*.")) {
            val suffix = patternHost.drop(1)
            if (!url.host.endsWith(suffix) && url.host != suffix.drop(1)) return false
        } else if (patternHost != url.host) {
            return false
        }

        if (patternUrl.port != url.port) return false

        val patternPath = patternUrl.encodedPath.replace("__WILDCARD__", "*")
        if (patternPath.endsWith("/*")) {
            val prefix = patternPath.dropLast(1)
            if (!url.encodedPath.startsWith(prefix)) return false
        } else if (patternPath != "*" && patternPath != url.encodedPath) {
            return false
        }

        return true
    }

    override fun authMethod() = OIDC
}
