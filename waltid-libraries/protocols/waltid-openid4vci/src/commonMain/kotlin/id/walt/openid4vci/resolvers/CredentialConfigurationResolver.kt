package id.walt.openid4vci.resolvers

import id.walt.openid4vci.responses.credential.CredentialConfiguration

/**
 * Resolves credential configurations by credential_configuration_id.
 * Default implementation can be backed by a map of credential_configurations_supported.
 */
fun interface CredentialConfigurationResolver {
    fun resolve(id: String): CredentialConfiguration?
}

class MapBackedCredentialConfigurationResolver(
    private val configurations: Map<String, CredentialConfiguration>
) : CredentialConfigurationResolver {
    override fun resolve(id: String): CredentialConfiguration? = configurations[id]
}
