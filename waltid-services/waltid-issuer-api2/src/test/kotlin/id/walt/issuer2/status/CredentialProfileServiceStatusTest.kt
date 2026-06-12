package id.walt.issuer2.status

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.service.CredentialProfileService
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CredentialProfileService with credential status
 */
class CredentialProfileServiceStatusTest {

    private val testIssuerKey = buildJsonObject {
        put("type", "jwk")
        putJsonObject("jwk") {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "test")
            put("y", "test")
        }
    }

    private val testCredentialData = buildJsonObject {
        put("@context", "https://www.w3.org/2018/credentials/v1")
        put("type", "VerifiableCredential")
        putJsonObject("credentialSubject") {
            put("id", "did:example:123")
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun createTestMetadataConfig(
        credentialConfigurationIds: List<String> = emptyList()
    ): Issuer2MetadataConfig {
        val configurations = credentialConfigurationIds.associateWith { id ->
            buildJsonObject {
                put("format", when {
                    id.contains("sd_jwt") || id.contains("dc+sd-jwt") -> "dc+sd-jwt"
                    id.contains("mso") || id.contains("iso") -> "mso_mdoc"
                    else -> "jwt_vc_json"
                })
            }
        }
        return Issuer2MetadataConfig(
            credentialConfigurations = configurations
        )
    }

    @Test
    fun `CredentialProfileService should load profile with credentialStatus`() {
        val status = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#123")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "123")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val profileConfig = CredentialProfileConfig(
            name = "Test Profile with Status",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("test-profile-with-status" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("TestCredential_jwt_vc_json")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.getProfile("test-profile-with-status")

        assertNotNull(profile.credentialStatus)
        assertEquals("BitstringStatusListEntry", profile.credentialStatus?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("revocation", profile.credentialStatus?.jsonObject?.get("statusPurpose")?.jsonPrimitive?.content)
    }

    @Test
    fun `CredentialProfileService should load profile without credentialStatus`() {
        val profileConfig = CredentialProfileConfig(
            name = "Test Profile without Status",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("test-profile-without-status" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("TestCredential_jwt_vc_json")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.getProfile("test-profile-without-status")

        assertNull(profile.credentialStatus)
    }

    @Test
    fun `CredentialProfileService should list all profiles including those with status`() {
        val profileWithStatus = CredentialProfileConfig(
            name = "Profile With Status",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData,
            credentialStatus = buildJsonObject {
                put("id", "https://issuer.example.com/status/1#123")
                put("type", "BitstringStatusListEntry")
            }
        )

        val profileWithoutStatus = CredentialProfileConfig(
            name = "Profile Without Status",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf(
                "profile-with-status" to profileWithStatus,
                "profile-without-status" to profileWithoutStatus
            )
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("TestCredential_jwt_vc_json")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profiles = service.listProfiles()

        assertEquals(2, profiles.size)
        assertTrue(profiles.any { it.profileId == "profile-with-status" && it.credentialStatus != null })
        assertTrue(profiles.any { it.profileId == "profile-without-status" && it.credentialStatus == null })
    }

    @Test
    fun `CredentialProfileService should resolve profile by credential configuration ID`() {
        val profileConfig = CredentialProfileConfig(
            name = "Unique Config",
            credentialConfigurationId = "UniqueCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = buildJsonObject {
                put("id", "https://issuer.example.com/status/unique#1")
                put("type", "BitstringStatusListEntry")
            }
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("unique-profile" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("UniqueCredential_jwt_vc_json")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.resolveProfileByCredentialConfigurationId("UniqueCredential_jwt_vc_json")

        assertNotNull(profile.credentialStatus)
        assertEquals("unique-profile", profile.profileId)
    }

    @Test
    fun `CredentialProfileService should support TokenStatusList format`() {
        val status = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 12345)
                put("uri", "https://issuer.example.com/status/1")
            }
        }

        val profileConfig = CredentialProfileConfig(
            name = "SD-JWT Profile with Token Status",
            credentialConfigurationId = "TestCredential_dc_sd_jwt",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("sdjwt-with-status" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("TestCredential_dc_sd_jwt")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.getProfile("sdjwt-with-status")

        assertNotNull(profile.credentialStatus)
        val statusList = profile.credentialStatus?.jsonObject?.get("status_list")?.jsonObject
        assertNotNull(statusList)
        assertEquals(12345, statusList["idx"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `CredentialProfileService should support mDoc TokenStatusList format`() {
        val status = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 94567)
                put("uri", "https://issuer.example.com/status/mdoc/1")
            }
        }

        val profileConfig = CredentialProfileConfig(
            name = "mDoc Profile with Token Status",
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            issuerKey = testIssuerKey,
            issuerDid = null,
            credentialData = buildJsonObject {
                putJsonObject("org.iso.18013.5.1") {
                    put("family_name", "Smith")
                    put("given_name", "John")
                }
            },
            credentialStatus = status
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("mdoc-with-status" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("org.iso.18013.5.1.mDL")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.getProfile("mdoc-with-status")

        assertNotNull(profile.credentialStatus)
        val statusList = profile.credentialStatus?.jsonObject?.get("status_list")?.jsonObject
        assertNotNull(statusList)
        assertEquals(94567, statusList["idx"]?.jsonPrimitive?.content?.toInt())
        assertEquals("https://issuer.example.com/status/mdoc/1", statusList["uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun `CredentialProfileService should throw exception for missing profile`() {
        val profilesConfig = Issuer2ProfilesConfig(profiles = emptyMap())
        val metadataConfig = createTestMetadataConfig()

        val service = CredentialProfileService(profilesConfig, metadataConfig)

        assertFailsWith<NotFoundException> {
            service.getProfile("non-existent-profile")
        }
    }

    @Test
    fun `CredentialProfile should preserve all status fields after conversion`() {
        val complexStatus = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#123")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "123")
            put("statusListCredential", "https://issuer.example.com/status/1")
            putJsonObject("additionalProperties") {
                put("customField", "customValue")
            }
        }

        val profileConfig = CredentialProfileConfig(
            name = "Complex Status Profile",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData,
            credentialStatus = complexStatus
        )

        val profilesConfig = Issuer2ProfilesConfig(
            profiles = mapOf("complex-status-profile" to profileConfig)
        )

        val metadataConfig = createTestMetadataConfig(
            credentialConfigurationIds = listOf("TestCredential_jwt_vc_json")
        )

        val service = CredentialProfileService(profilesConfig, metadataConfig)
        val profile = service.getProfile("complex-status-profile")

        val statusObj = profile.credentialStatus?.jsonObject
        assertNotNull(statusObj)
        assertEquals("https://issuer.example.com/status/1#123", statusObj["id"]?.jsonPrimitive?.content)
        assertEquals("BitstringStatusListEntry", statusObj["type"]?.jsonPrimitive?.content)
        assertEquals("revocation", statusObj["statusPurpose"]?.jsonPrimitive?.content)
        assertEquals("123", statusObj["statusListIndex"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example.com/status/1", statusObj["statusListCredential"]?.jsonPrimitive?.content)
    }
}
