package id.walt.issuer2.service

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.openid4vci.CredentialFormat
import id.walt.credentials.issuance.MdlAgeValidation
import id.walt.credentials.issuance.MdlLoginClaimMapping
import id.walt.mdoc.issuance.MdlAge
import id.walt.mdoc.issuance.MsoData
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CredentialProfileService(
    private val profilesConfig: Issuer2ProfilesConfig,
    private val metadataConfig: Issuer2MetadataConfig,
    private val preallocatedStatusEnabled: Boolean = true,
) {
    private val preparedStatusByProfile = profilesConfig.profiles.mapValues { (_, profile) ->
        prepareCredentialStatus(profile.credentialConfigurationId, profile.credentialStatus, profile.mapping)
    }

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
        val profiles = profilesConfig.profiles.filterValues { it.credentialConfigurationId == credentialConfigurationId }
        require(profiles.size == 1) {
            "Expected exactly one credential profile for credentialConfigurationId '$credentialConfigurationId', " +
                    "found ${profiles.size}"
        }
        return getProfile(profiles.keys.single())
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
        validateMdlAge(
            profile.credentialConfigurationId, profile.credentialData, profile.mapping,
            profile.idTokenClaimsMapping, profile.msoData,
        )
        return profile
    }

    internal fun validateMdlAge(
        configurationId: String,
        credentialData: JsonObject,
        mapping: JsonObject?,
        idTokenClaimsMapping: Map<String, String>?,
        msoData: MsoData?,
        canUseLoginClaims: Boolean = true,
        explicitBirthYear: JsonElement? = null,
    ) {
        if (isMdl(configurationId)) {
            MdlAgeValidation.validateConfiguration(
                credentialData = credentialData,
                mapping = mapping,
                loginClaimDestinations = if (canUseLoginClaims) idTokenClaimsMapping.orEmpty().values else emptyList(),
                validFrom = msoData?.validFrom,
                explicitBirthYear = explicitBirthYear,
            )
        }
    }

    internal fun claimsDestination(
        configurationId: String,
        credentialData: JsonObject,
        claimsMapping: Map<String, String>,
    ): JsonObject =
        if (isMdl(configurationId)) {
            MdlLoginClaimMapping.forDestinations(claimsMapping.values).prepareDestination(credentialData)
        } else {
            credentialData
        }

    internal fun isMdl(configurationId: String): Boolean =
        credentialFormat(configurationId) == CredentialFormat.MSO_MDOC &&
            metadataConfig.credentialConfigurations[configurationId]
                ?.jsonObject?.get("doctype")?.jsonPrimitive?.content == MdlAge.DOC_TYPE

    internal fun statusForIssuance(
        profile: CredentialProfile,
        statusOverride: JsonElement? = null,
        mappingOverride: JsonObject? = null,
    ): JsonElement? {
        if (statusOverride != null) {
            return prepareCredentialStatus(profile.credentialConfigurationId, statusOverride, mappingOverride ?: profile.mapping)
        }
        val status = preparedStatusByProfile.getValue(profile.profileId)
        if (mappingOverride != null && CredentialStatusConfiguration.isPreallocated(status)) {
            val format = requireNotNull(credentialFormat(profile.credentialConfigurationId))
            CredentialStatusConfiguration.validateMapping(format, mappingOverride)
        }
        return status
    }

    private fun prepareCredentialStatus(configurationId: String, value: JsonElement?, mapping: JsonObject?): JsonElement? {
        if (value !is JsonArray) return value
        val format = credentialFormat(configurationId)
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

    private fun credentialFormat(configurationId: String): CredentialFormat? =
        metadataConfig.credentialConfigurations[configurationId]
            ?.jsonObject?.get("format")?.jsonPrimitive?.content?.let(CredentialFormat::fromValue)

    fun requireMsoDataOnlyForMdoc(credentialConfigurationId: String, msoData: MsoData?) {
        if (msoData == null || msoData.isEmpty()) return
        msoData.requireNonBlankFields()
        require(credentialFormat(credentialConfigurationId) == CredentialFormat.MSO_MDOC) {
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
