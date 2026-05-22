package id.walt.issuer2.config

import id.walt.issuer2.domain.CredentialProfile

class CredentialProfileConfigProvider(
    private val profilesConfig: Issuer2ProfilesConfig,
) {
    fun list(): List<CredentialProfile> =
        profilesConfig.profiles.map { (profileId, config) -> config.toDomain(profileId) }

    fun get(profileId: String): CredentialProfile? =
        profilesConfig.profiles[profileId]?.toDomain(profileId)
}

private fun CredentialProfileConfig.toDomain(profileId: String): CredentialProfile =
    CredentialProfile(
        profileId = profileId,
        name = name,
        version = version,
        credentialConfigurationId = credentialConfigurationId,
        issuerKeyId = issuerKeyId,
        issuerDid = issuerDid,
        credentialData = credentialData,
        mapping = mapping,
        selectiveDisclosure = selectiveDisclosure,
        idTokenClaimsMapping = idTokenClaimsMapping,
        mdocNamespacesDataMapping = mdocNamespacesDataMapping,
        webhookUrl = webhookUrl,
    )
