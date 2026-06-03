package id.walt.issuer2.service

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.domain.CredentialProfile
import io.ktor.server.plugins.NotFoundException

class CredentialProfileService(
    private val profilesConfig: Issuer2ProfilesConfig,
    private val metadataConfig: Issuer2MetadataConfig,
) {
    fun listProfiles(): List<CredentialProfile> =
        profilesConfig.profiles.map { (profileId, config) -> config.toDomain(profileId) }
            .map(::validateProfile)

    fun getProfile(profileId: String): CredentialProfile =
        profilesConfig.profiles[profileId]
            ?.toDomain(profileId)
            ?.let(::validateProfile)
            ?: throw NotFoundException("Credential profile not found: $profileId")

    fun resolveProfile(profileId: String): CredentialProfile =
        getProfile(profileId)

    private fun validateProfile(profile: CredentialProfile): CredentialProfile {
        require(profile.profileId.isNotBlank()) { "Credential profile id must not be blank" }
        require(!profile.profileId.contains('.')) { "Credential profile id must not contain '.' character" }
        require(profile.name.isNotBlank()) { "Credential profile name must not be blank" }
        require(profile.credentialConfigurationId.isNotBlank()) {
            "Credential profile credentialConfigurationId must not be blank"
        }
        require(profile.issuerKey.isNotEmpty()) { "Credential profile issuerKey must not be empty" }
        require(profile.issuerKey["type"] != null) { "Credential profile issuerKey must contain a key type" }
        require(profile.issuerDid?.isNotBlank() != false) {
            "Credential profile issuerDid must not be blank when provided"
        }
        require(metadataConfig.credentialConfigurations.containsKey(profile.credentialConfigurationId)) {
            "Credential profile ${profile.profileId} references unsupported credential configuration " +
                    profile.credentialConfigurationId
        }
        return profile
    }

    private fun CredentialProfileConfig.toDomain(profileId: String): CredentialProfile =
        CredentialProfile(
            profileId = profileId,
            name = name,
            credentialConfigurationId = credentialConfigurationId,
            issuerKey = issuerKey,
            issuerDid = issuerDid,
            credentialData = credentialData,
            mapping = mapping,
            selectiveDisclosure = selectiveDisclosure,
            idTokenClaimsMapping = idTokenClaimsMapping,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            x5Chain = x5Chain,
            webhookUrl = webhookUrl,
        )
}