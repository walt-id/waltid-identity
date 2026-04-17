package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

/**
 * Resolves offered credentials against issuer metadata.
 * Implements credential configuration resolution as per §4.1.1 and §11.2.3 of OpenID4VCI 1.0.
 */
object OfferedCredentialResolver {

    /**
     * Represents a resolved credential offer with its configuration
     */
    @Serializable
    data class ResolvedCredentialOffer(
        val credentialConfigurationId: String,
        val configuration: CredentialConfiguration,
    )

    /**
     * Resolves credential configuration IDs from the offer against the issuer metadata
     * 
     * @param offer The credential offer containing configuration IDs
     * @param metadata The credential issuer metadata containing supported configurations
     * @return List of resolved credential offers with their configurations
     * @throws IllegalArgumentException if any configuration ID is not found in metadata
     */
    fun resolveOfferedCredentials(
        offer: CredentialOffer,
        metadata: CredentialIssuerMetadata,
    ): List<ResolvedCredentialOffer> {
        val configurationIds = offer.credentialConfigurationIds

        if (configurationIds.isEmpty()) {
            log.warn { "Credential offer contains no credential configuration IDs" }
            return emptyList()
        }

        val supportedConfigurations = metadata.credentialConfigurationsSupported

        return configurationIds.mapNotNull { configId ->
            val configuration = supportedConfigurations[configId]

            if (configuration == null) {
                log.error { "Credential configuration ID '$configId' not found in issuer metadata" }
                throw IllegalArgumentException(
                    "Credential configuration ID '$configId' is not supported by the issuer. " +
                            "Supported IDs: ${supportedConfigurations.keys.joinToString()}"
                )
            }

            log.debug { "Resolved credential configuration: $configId -> ${configuration.format}" }
            ResolvedCredentialOffer(configId, configuration)
        }
    }

    /**
     * Resolves a single credential configuration by ID
     * 
     * @param configurationId The credential configuration ID to resolve
     * @param metadata The credential issuer metadata
     * @return The credential configuration
     * @throws IllegalArgumentException if configuration ID is not found
     */
    fun resolveCredentialConfiguration(
        configurationId: String,
        metadata: CredentialIssuerMetadata,
    ): CredentialConfiguration {
        val configuration = metadata.credentialConfigurationsSupported[configurationId]
            ?: throw IllegalArgumentException(
                "Credential configuration ID '$configurationId' is not supported by the issuer. " +
                        "Supported IDs: ${metadata.credentialConfigurationsSupported.keys.joinToString()}"
            )

        log.debug { "Resolved credential configuration: $configurationId" }
        return configuration
    }

    /**
     * Validates that all offered credentials are supported by the issuer
     * 
     * @param offer The credential offer
     * @param metadata The credential issuer metadata
     * @return true if all configurations are supported, false otherwise
     */
    fun validateOfferedCredentials(
        offer: CredentialOffer,
        metadata: CredentialIssuerMetadata,
    ): Boolean {
        val configurationIds = offer.credentialConfigurationIds
        val supportedIds = metadata.credentialConfigurationsSupported.keys

        return configurationIds.all { it in supportedIds }
    }

    /**
     * Gets display information for a resolved credential
     * 
     * @param resolvedOffer The resolved credential offer
     * @return Display name or null if not available
     */
    fun getDisplayName(resolvedOffer: ResolvedCredentialOffer): String? {
        return resolvedOffer.configuration.display?.firstOrNull()?.name
    }

    /**
     * Gets supported proof types for a credential configuration
     * 
     * @param configuration The credential configuration
     * @return List of supported proof type IDs
     */
    fun getSupportedProofTypes(configuration: CredentialConfiguration): List<String> {
        return configuration.proofTypesSupported?.keys?.toList() ?: emptyList()
    }

    /**
     * Gets supported cryptographic binding methods for a credential configuration
     * 
     * @param configuration The credential configuration
     * @return List of supported binding methods
     */
    fun getSupportedBindingMethods(configuration: CredentialConfiguration): Set<Any> {
        return configuration.cryptographicBindingMethodsSupported ?: emptySet()
    }
}
