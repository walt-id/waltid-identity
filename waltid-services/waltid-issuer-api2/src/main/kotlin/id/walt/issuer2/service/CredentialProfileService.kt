package id.walt.issuer2.service

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.mdoc.MsoData
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray

class CredentialProfileService(
    private val profilesConfig: Issuer2ProfilesConfig,
    private val metadataConfig: Issuer2MetadataConfig,
    private val preallocatedStatusEnabled: Boolean = true,
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

    fun resolveProfileByCredentialConfigurationId(credentialConfigurationId: String): CredentialProfile {
        val profiles = listProfiles().filter { it.credentialConfigurationId == credentialConfigurationId }
        require(profiles.size == 1) {
            "Expected exactly one credential profile for credentialConfigurationId '$credentialConfigurationId', " +
                    "found ${profiles.size}"
        }
        return profiles.single()
    }

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
        requireMsoDataOnlyForMdoc(profile.credentialConfigurationId, profile.msoData)
        prepareCredentialStatus(profile.credentialConfigurationId, profile.credentialStatus, profile.mapping)
        return profile
    }

    /** Validate format-specific configuration and normalize per-copy values for stored sessions. */
    fun prepareCredentialStatus(configurationId: String, value: JsonElement?, mapping: JsonObject?): JsonElement? {
        if (value !is JsonArray) return value
        val format = metadataConfig.credentialConfigurations[configurationId]
            ?.jsonObject?.get("format")?.jsonPrimitive?.content?.let(CredentialFormat::fromValue)
        if (format == null) {
            require(!CredentialStatusConfiguration.isPreallocated(value)) { "Unknown credential format for preallocated status" }
            return value
        }
        return when (val configuration = CredentialStatusConfiguration.parse(value, format, mapping)) {
            is CredentialStatusConfiguration.Fixed -> configuration.value
            is CredentialStatusConfiguration.Preallocated -> {
                require(preallocatedStatusEnabled) { "Preallocated credentialStatus requires Redis persistence" }
                JsonArray(configuration.groups.map { it.statuses })
            }
        }
    }

    fun requireMsoDataOnlyForMdoc(credentialConfigurationId: String, msoData: MsoData?) {
        if (msoData == null || msoData.isEmpty()) return
        msoData.requireNonBlankFields()
        val format = metadataConfig.credentialConfigurations[credentialConfigurationId]
            ?.jsonObject?.get("format")?.jsonPrimitive?.content
        require(format == CredentialFormat.MSO_MDOC.value) {
            "msoData is only supported for mso_mdoc credential profiles"
        }
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
            authorizedTransactionDataTypes = authorizedTransactionDataTypes,
            msoData = msoData,
            x5Chain = x5Chain,
            notifications = notifications,
            credentialStatus = credentialStatus,
        )
}
