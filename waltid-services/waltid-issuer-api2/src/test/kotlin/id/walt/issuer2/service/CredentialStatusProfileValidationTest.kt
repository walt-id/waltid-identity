package id.walt.issuer2.service

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.CredentialStatusConfigurationTest.Companion.tokenStatus
import id.walt.issuer2.testsupport.*
import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class CredentialStatusProfileValidationTest {
    @AfterEach
    fun clear() = clearIssuer2TestEnvironment()

    @Test
    fun hoconProfileLoadingPreservesFlatEntriesAndNestedW3cGroups() {
        loadIssuer2ConfigFiles()
        val configured = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val file = Files.createTempFile("issuer2-status-profile-", ".conf")
        try {
            for (example in listOf(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS,
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS,
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_W3C_STATUS,
            )) {
                val expected = configured.profiles.getValue(example.profileId).copy(
                    credentialStatus = example.runtimeOverrides!!.credentialStatus,
                    requireDistinctBatchHolderKeys = example.profileId == Issuer2CredentialScenarios.identitySdJwt.profileId,
                )
                Files.writeString(file, "profiles { " + example.profileId + " = " +
                    Json.encodeToString(CredentialProfileConfig.serializer(), expected) + " }")
                System.setProperty("config.file.issuer2-profiles", file.toString())
                val loaded = ConfigManager.loadConfig(
                    ConfigManager.ConfigData("issuer2-profiles", Issuer2ProfilesConfig::class), emptyArray(),
                ).getOrThrow() as Issuer2ProfilesConfig
                assertEquals(expected.credentialStatus, loaded.profiles.getValue(example.profileId).credentialStatus)
                val profile = CredentialProfileService(loaded, ConfigManager.getConfig()).getProfile(example.profileId)
                assertEquals(expected.credentialStatus, profile.credentialStatus)
                assertEquals(expected.requireDistinctBatchHolderKeys, profile.requireDistinctBatchHolderKeys)
            }
        } finally { Files.deleteIfExists(file) }
    }

    @Test
    fun invalidMdocStatusUrlIsRejectedWhenTheProfileLoads() {
        loadIssuer2ConfigFiles()
        val profiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val id = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_MDOC_STATUS.profileId
        val invalid = profiles.profiles.getValue(id).copy(
            credentialStatus = JsonArray(listOf(tokenStatus(7, "https://status.example.com:70000/list"))),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            CredentialProfileService(profiles.copy(profiles = mapOf(id to invalid)), ConfigManager.getConfig())
        }
        assertContains(error.message.orEmpty(), "status_list.uri")
    }

    @Test
    fun mappingOverrideIsCheckedAgainstTheEffectiveStatusConfiguration() {
        loadIssuer2ConfigFiles()
        val profiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val inventory = requireNotNull(example.runtimeOverrides?.credentialStatus)
        val config = profiles.profiles.getValue(example.profileId).copy(credentialStatus = inventory)
        val service = CredentialProfileService(profiles.copy(profiles = mapOf(example.profileId to config)), ConfigManager.getConfig())
        val profile = service.resolveProfileByCredentialConfigurationId(config.credentialConfigurationId)
        val mapping = buildJsonObject { put("jwt:status", "overwrite") }
        assertFailsWith<IllegalArgumentException> { service.statusForIssuance(profile, mappingOverride = mapping) }
        val fixed = tokenStatus(14)
        assertEquals(fixed, service.statusForIssuance(profile, statusOverride = fixed, mappingOverride = mapping))
        assertEquals(inventory, service.getProfile(profile.profileId).credentialStatus)
    }

    @Test
    fun deployedPreallocatedProfilesRequireRedis() {
        loadIssuer2ConfigFiles()
        val example = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS
        val configured = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val profiles = configured.copy(profiles = configured.profiles.mapValues { (id, profile) ->
            if (id == example.profileId) profile.copy(credentialStatus = example.runtimeOverrides!!.credentialStatus)
            else profile
        })
        val error = assertFailsWith<IllegalArgumentException> {
            Issuer2Module(ConfigManager.getConfig(), ConfigManager.getConfig(), profiles)
        }
        assertContains(error.message.orEmpty(), "Redis")
    }

    @Test
    fun deployedPreallocatedOverridesRequireRedisWhileFixedOverridesStillWork() = runTest {
        loadIssuer2ConfigFiles()
        val module = Issuer2Module(ConfigManager.getConfig(), ConfigManager.getConfig(), ConfigManager.getConfig())
        val fixed = module.credentialOfferService.createCredentialOffer(
            Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS, "fixed",
        )
        assertNotNull(fixed)
        val error = assertFailsWith<BadRequestException> {
            module.credentialOfferService.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PREALLOCATED_SD_JWT_STATUS, "preallocated",
            )
        }
        assertContains(error.message.orEmpty(), "Redis")
    }
}
