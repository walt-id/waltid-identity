package id.walt.openid4vci.metadata.issuer

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Credential Issuer Metadata (OpenID4VCI 1.0).
 */
@Serializable
data class CredentialIssuerMetadata(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("authorization_servers")
    val authorizationServers: List<String>? = null,
    @SerialName("credential_endpoint")
    val credentialEndpoint: String,
    @SerialName("nonce_endpoint")
    val nonceEndpoint: String? = null,
    @SerialName("deferred_credential_endpoint")
    val deferredCredentialEndpoint: String? = null,
    @SerialName("notification_endpoint")
    val notificationEndpoint: String? = null,
    @SerialName("credential_request_encryption")
    val credentialRequestEncryption: CredentialRequestEncryption? = null,
    @SerialName("credential_response_encryption")
    val credentialResponseEncryption: CredentialResponseEncryption? = null,
    @SerialName("batch_credential_issuance")
    val batchCredentialIssuance: BatchCredentialIssuance? = null,
    @SerialName("display")
    val display: List<IssuerDisplay>? = null,
    @SerialName("credential_configurations_supported")
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration>,
) {
    init {
        require(credentialIssuer.isNotBlank()) {
            "Credential issuer must not be blank"
        }
        validateIssuerUrl(credentialIssuer)
        validateEndpointUrl("credential_endpoint", credentialEndpoint)
        deferredCredentialEndpoint?.let { validateEndpointUrl("deferred_credential_endpoint", it) }
        notificationEndpoint?.let { validateEndpointUrl("notification_endpoint", it) }
        nonceEndpoint?.let { validateEndpointUrl("nonce_endpoint", it) }

        require(credentialConfigurationsSupported.isNotEmpty()) {
            "credential_configurations_supported must not be empty"
        }
        require(credentialConfigurationsSupported.keys.all { it.isNotBlank() }) {
            "credential_configurations_supported keys must not be blank"
        }
        require(credentialConfigurationsSupported.all { (id, config) -> config.id.isNotBlank() && config.id == id }) {
            "credential_configurations_supported entries must use matching non-blank ids"
        }
        authorizationServers?.let { servers ->
            require(servers.isNotEmpty()) {
                "authorization_servers must not be empty"
            }
            servers.forEach { validateIssuerUrl(it) }
        }
        display?.let { entries ->
            require(entries.isNotEmpty()) {
                "display must not be empty"
            }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "display entries must not duplicate locales"
            }
        }
    }

    /**
     * Returns the list of Authorization Server issuer identifiers to use for discovery.
     * If none are declared, the Credential Issuer acts as the Authorization Server.
     */
    fun authorizationServerIssuers(): List<String> =
        authorizationServers?.takeIf { it.isNotEmpty() } ?: listOf(credentialIssuer)

    /**
     * Returns true if the provided authorization_server value is declared for this issuer.
     */
    fun isAuthorizationServerDeclared(server: String?): Boolean =
        server == null || authorizationServerIssuers().contains(server)

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            credentialConfigurationsSupported: Map<String, CredentialConfiguration>,
            credentialEndpointPath: String = "/credential",
            deferredCredentialEndpointPath: String? = "/credential_deferred",
            notificationEndpointPath: String? = "/notification",
            nonceEndpointPath: String? = "/nonce",
            authorizationServers: List<String>? = null,
            credentialRequestEncryption: CredentialRequestEncryption? = null,
            credentialResponseEncryption: CredentialResponseEncryption? = null,
            batchCredentialIssuance: BatchCredentialIssuance? = null,
            display: List<IssuerDisplay>? = null,
        ): CredentialIssuerMetadata {
            val normalized = baseUrl.trimEnd('/')
            return CredentialIssuerMetadata(
                credentialIssuer = normalized,
                credentialEndpoint = normalized + credentialEndpointPath,
                credentialConfigurationsSupported = credentialConfigurationsSupported,
                authorizationServers = authorizationServers,
                deferredCredentialEndpoint = deferredCredentialEndpointPath?.let { normalized + it },
                notificationEndpoint = notificationEndpointPath?.let { normalized + it },
                nonceEndpoint = nonceEndpointPath?.let { normalized + it },
                credentialRequestEncryption = credentialRequestEncryption,
                credentialResponseEncryption = credentialResponseEncryption,
                batchCredentialIssuance = batchCredentialIssuance,
                display = display,
            )
        }

        private fun validateIssuerUrl(issuer: String) {
            val url = Url(issuer)
            require(url.protocol == URLProtocol.HTTPS) {
                "Credential issuer must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Credential issuer must include a host"
            }
            require(url.parameters.isEmpty()) {
                "Credential issuer must not include query parameters"
            }
            require(url.fragment.isEmpty()) {
                "Credential issuer must not include fragment components"
            }
        }

        private fun validateEndpointUrl(fieldName: String, value: String) {
            require(value.isNotBlank()) {
                "Credential issuer $fieldName must not be blank"
            }
            val url = Url(value)
            require(url.protocol == URLProtocol.HTTPS) {
                "Credential issuer $fieldName must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Credential issuer $fieldName must include a host"
            }
        }
    }
}
