package id.walt.issuer2.service

import id.walt.issuer2.config.CredentialProfileConfigProvider
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.domain.CredentialProfile
import io.ktor.server.plugins.NotFoundException

class CredentialProfileService(
    private val profileConfigProvider: CredentialProfileConfigProvider,
    private val metadataConfig: Issuer2MetadataConfig,
) {
    fun listProfiles(): List<CredentialProfile> = profileConfigProvider.list()

    fun getProfile(profileId: String): CredentialProfile =
        profileConfigProvider.get(profileId)
            ?: throw NotFoundException("Credential profile not found: $profileId")

    fun resolveProfile(profileId: String): CredentialProfile {
        val profile = getProfile(profileId)
        require(metadataConfig.credentialConfigurations.containsKey(profile.credentialConfigurationId)) {
            "Credential profile $profileId references unsupported credential configuration ${profile.credentialConfigurationId}"
        }
        return profile
    }
}
