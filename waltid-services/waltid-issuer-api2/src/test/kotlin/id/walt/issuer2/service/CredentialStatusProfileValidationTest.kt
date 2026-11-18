package id.walt.issuer2.service

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.*
import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
                )
                Files.writeString(file, "profiles { " + example.profileId + " = " +
                    Json.encodeToString(CredentialProfileConfig.serializer(), expected) + " }")
                System.setProperty("config.file.issuer2-profiles", file.toString())
                val loaded = ConfigManager.loadConfig(
                    ConfigManager.ConfigData("issuer2-profiles", Issuer2ProfilesConfig::class), emptyArray(),
                ).getOrThrow() as Issuer2ProfilesConfig
                assertEquals(expected.credentialStatus, loaded.profiles.getValue(example.profileId).credentialStatus)
                assertEquals(expected.credentialStatus, CredentialProfileService(
                    loaded, ConfigManager.getConfig(),
                ).getProfile(example.profileId).credentialStatus)
            }
        } finally { Files.deleteIfExists(file) }
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
