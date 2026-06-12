package id.walt.issuer2.profile

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import id.walt.issuer2.controller.openapi.Issuer2ManagementRoutesDocs
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.issuer2Module
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2ProfileEndpointTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @AfterEach
    fun clearConfig() {
        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }
        ConfigManager.preclear()
        FeatureManager.preclear()
    }

    @Test
    fun `configured profile issuer modes should match credential formats`() {
        loadIssuer2ConfigFiles()

        val metadata = ConfigManager.getConfig<Issuer2MetadataConfig>()
        val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>()

        profilesConfig.profiles.forEach { (profileId, profile) ->
            val metadataEntry = assertNotNull(
                metadata.credentialConfigurations[profile.credentialConfigurationId],
                "Expected metadata for profile $profileId credential configuration ${profile.credentialConfigurationId}",
            )
            val format = assertNotNull(
                metadataEntry.jsonObject["format"]?.jsonPrimitive?.contentOrNull,
                "Expected format for profile $profileId credential configuration ${profile.credentialConfigurationId}",
            )

            when (format) {
                JWT_VC_JSON_FORMAT -> {
                    assertEquals(
                        ISSUER_DID,
                        profile.issuerDid,
                        "Expected W3C JWT VC profile $profileId to use DID/JWK issuer mode",
                    )
                    assertNull(profile.x5Chain, "W3C JWT VC profile $profileId must not configure x5Chain")
                }

                MSO_MDOC_FORMAT -> {
                    assertNull(profile.issuerDid, "mDOC profile $profileId must not configure issuerDid")
                    assertEquals(
                        profilesConfig.defaultIssuerX5chain,
                        profile.x5Chain,
                        "Expected mDOC profile $profileId to use the default issuer x5 chain",
                    )
                }

                SD_JWT_VC_FORMAT -> {
                    val usesIssuerDid = !profile.issuerDid.isNullOrBlank()
                    val usesX5Chain = !profile.x5Chain.isNullOrEmpty()

                    assertTrue(
                        usesIssuerDid xor usesX5Chain,
                        "SD-JWT VC profile $profileId must configure exactly one issuer mode: issuerDid or x5Chain",
                    )
                    if (usesIssuerDid) {
                        assertEquals(ISSUER_DID, profile.issuerDid)
                    }
                    if (usesX5Chain) {
                        assertEquals(profilesConfig.defaultIssuerX5chain, profile.x5Chain)
                    }
                }

                else -> assertTrue(false, "Unsupported credential profile format for $profileId: $format")
            }
        }
    }

    @Test
    fun `configured profile defaults should resolve from hocon substitutions`() {
        loadIssuer2ConfigFiles()

        val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>()

        // issuer2-profiles.conf keeps shared issuer material at the top level and
        // injects it into profiles with HOCON substitutions like ${defaultIssuerKey}.
        // These assertions make sure the typed runtime config contains real values.
        assertDefaultIssuerKey(assertNotNull(profilesConfig.defaultIssuerKey), "defaultIssuerKey")
        assertEquals(ISSUER_DID, profilesConfig.defaultIssuerDid)
        assertEquals(1, profilesConfig.defaultIssuerX5chain.size)
        assertTrue(
            profilesConfig.defaultIssuerX5chain.single().contains("-----BEGIN CERTIFICATE-----"),
            "Expected defaultIssuerX5chain to contain PEM certificate data",
        )

        profilesConfig.profiles.forEach { (profileId, profile) ->
            assertNoUnresolvedSubstitutions(profileId, profile)
            assertDefaultIssuerKey(profile.issuerKey, "$profileId.issuerKey")
            profile.issuerDid?.let {
                assertEquals(ISSUER_DID, it, "Expected profile $profileId to resolve defaultIssuerDid")
            }
            profile.x5Chain?.let {
                assertEquals(
                    profilesConfig.defaultIssuerX5chain,
                    it,
                    "Expected profile $profileId to resolve defaultIssuerX5chain",
                )
            }
        }
    }

    @Test
    fun `should expose configured profiles`() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val profilesRaw = client.get("/issuer2/profiles")
        assertEquals(HttpStatusCode.OK, profilesRaw.status)
        val profilesResponseBody = profilesRaw.bodyAsText()
        assertIssuerKeyIsExposed(profilesResponseBody)
        val profiles = json.decodeFromString<List<CredentialProfile>>(profilesResponseBody)
        assertTrue(profiles.isNotEmpty(), "Expected at least one configured issuer2 profile")
        assertProfilesCoverCredentialMetadata(profiles)
        profiles.forEach(::assertProfileHasDefaultIssuerKey)

        profiles.forEach { listedProfile ->
            val configuredProfileRaw = client.get("/issuer2/profiles/${listedProfile.profileId}")
            assertEquals(HttpStatusCode.OK, configuredProfileRaw.status)
            val configuredProfileBody = configuredProfileRaw.bodyAsText()
            assertIssuerKeyIsExposed(configuredProfileBody)
            assertProfileJsonEquals(
                expected = listedProfile,
                actualBody = configuredProfileBody,
                "Expected configured profile ${listedProfile.profileId} to be retrievable by id",
            )
        }

        val universityDegreeProfile = assertNotNull(
            profiles.firstOrNull { it.profileId == UNIVERSITY_DEGREE_PROFILE_ID },
            "Expected configured profile $UNIVERSITY_DEGREE_PROFILE_ID",
        )
        assertW3CProfile(universityDegreeProfile)

        val profileRaw = client.get("/issuer2/profiles/$UNIVERSITY_DEGREE_PROFILE_ID")
        assertEquals(HttpStatusCode.OK, profileRaw.status)
        val profileResponseBody = profileRaw.bodyAsText()
        assertIssuerKeyIsExposed(profileResponseBody)
        assertProfileJsonEquals(universityDegreeProfile, profileResponseBody)

        val mdocPhotoIdProfile = assertNotNull(
            profiles.firstOrNull { it.profileId == ISO_PHOTO_ID_PROFILE_ID },
            "Expected configured profile $ISO_PHOTO_ID_PROFILE_ID",
        )
        assertMdocProfile(mdocPhotoIdProfile)

        val mdocMdlProfile = assertNotNull(
            profiles.firstOrNull { it.profileId == ISO_MDL_PROFILE_ID },
            "Expected configured profile $ISO_MDL_PROFILE_ID",
        )
        assertMdlProfile(mdocMdlProfile)

        assertSdJwtCatalogProfiles(profiles)
    }

    @Test
    fun `profile openapi examples should be selected from configured profiles`() {
        loadIssuer2ConfigFiles()

        val configuredProfiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
            .profiles
            .map { (profileId, profile) ->
                CredentialProfile(
                    profileId = profileId,
                    name = profile.name,
                    credentialConfigurationId = profile.credentialConfigurationId,
                    issuerKey = profile.issuerKey,
                    issuerDid = profile.issuerDid,
                    credentialData = profile.credentialData,
                    mapping = profile.mapping,
                    selectiveDisclosure = profile.selectiveDisclosure,
                    idTokenClaimsMapping = profile.idTokenClaimsMapping,
                    mDocNameSpacesDataMappingConfig = profile.mDocNameSpacesDataMappingConfig,
                    x5Chain = profile.x5Chain,
                    notifications = profile.notifications,
                )
            }
        val configuredProfilesById = configuredProfiles.associateBy { it.profileId }

        val examples = Issuer2ManagementRoutesDocs.selectProfileExamples(configuredProfiles)

        assertEquals(
            listOf(UNIVERSITY_DEGREE_PROFILE_ID, IDENTITY_SD_JWT_PROFILE_ID, ISO_PHOTO_ID_PROFILE_ID),
            examples.map { it.profileId },
            "Expected profile OpenAPI examples to cover W3C, SD-JWT VC, and mDOC defaults",
        )
        examples.forEach { example ->
            assertEquals(
                configuredProfilesById[example.profileId],
                example,
                "Expected OpenAPI example ${example.profileId} to be the exact loaded profile config",
            )
        }
    }

    private fun assertProfilesCoverCredentialMetadata(profiles: List<CredentialProfile>) {
        val metadataConfigurationIds = ConfigManager.getConfig<Issuer2MetadataConfig>().credentialConfigurations.keys
        val expectedScenarioProfileIds = Issuer2CredentialScenarios.configured.map { it.profileId }.toSet()
        assertEquals(
            metadataConfigurationIds,
            profiles.map { it.credentialConfigurationId }.toSet(),
            "Expected issuer2 profiles to cover every configured credential metadata entry",
        )
        assertEquals(
            metadataConfigurationIds.size,
            profiles.size,
            "Expected one issuer2 profile per configured credential metadata entry",
        )
        assertEquals(
            expectedScenarioProfileIds,
            profiles.map { it.profileId }.toSet(),
            "Expected configured issuer2 profiles to match the supported credential scenario matrix",
        )
    }

    private fun assertW3CProfile(profile: CredentialProfile) {
        assertEquals(UNIVERSITY_DEGREE_PROFILE_ID, profile.profileId)
        assertEquals("UniversityDegree", profile.name)
        assertEquals(UNIVERSITY_DEGREE_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertEquals(ISSUER_DID, profile.issuerDid)
        assertEquals(
            listOf("VerifiableCredential", "UniversityDegree"),
            profile.credentialData["type"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        val degree = assertNotNull(profile.credentialData["credentialSubject"]?.jsonObject?.get("degree")?.jsonObject)
        assertEquals("BachelorDegree", degree["type"]?.jsonPrimitive?.content)
        assertEquals("Bachelor of Science and Arts", degree["name"]?.jsonPrimitive?.content)
        assertEquals("<uuid>", profile.mapping?.get("id")?.jsonPrimitive?.content)
        assertNull(profile.idTokenClaimsMapping)
    }

    private fun assertMdocProfile(profile: CredentialProfile) {
        assertEquals(ISO_PHOTO_ID_PROFILE_ID, profile.profileId)
        assertEquals(ISO_PHOTO_ID_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertNull(profile.issuerDid, "Expected mDOC profile to be x5-chain based")
        assertNotNull(profile.x5Chain, "Expected mDOC profile to use issuer1 sample certificate")
        assertTrue(
            profile.credentialData.containsKey(ISO_PHOTO_ID_CONFIGURATION_ID),
            "Expected mDOC credential data to be namespaced by doctype",
        )
        val photoIdData = profile.credentialData[ISO_PHOTO_ID_CONFIGURATION_ID]?.jsonObject
        assertEquals(SAMPLE_PORTRAIT_BASE64, photoIdData?.get("portrait")?.jsonPrimitive?.content)
        assertEquals(
            "$.['$ISO_PHOTO_ID_CONFIGURATION_ID'].family_name_unicode",
            profile.idTokenClaimsMapping?.get("$.family_name"),
        )
        val entriesConfig = assertNotNull(
            profile.mDocNameSpacesDataMappingConfig
                ?.get(ISO_PHOTO_ID_CONFIGURATION_ID)
                ?.entriesConfigMap,
        )
        assertNotNull(entriesConfig["portrait"], "Expected portrait byte-string mapping")
    }

    private fun assertMdlProfile(profile: CredentialProfile) {
        assertEquals(ISO_MDL_PROFILE_ID, profile.profileId)
        assertEquals(ISO_MDL_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertNull(profile.issuerDid, "Expected mDL profile to be x5-chain based")
        assertNotNull(profile.x5Chain, "Expected mDL profile to use issuer1 sample certificate")

        val mdlData = profile.credentialData[ISO_MDL_NAMESPACE_ID]?.jsonObject
        assertEquals(SAMPLE_PORTRAIT_BASE64, mdlData?.get("portrait")?.jsonPrimitive?.content)
        assertEquals(2, mdlData?.get("driving_privileges")?.jsonArray?.size)
        assertEquals("Example Street 1, Vienna", mdlData?.get("resident_address")?.jsonPrimitive?.content)
        assertEquals("$.['$ISO_MDL_NAMESPACE_ID'].family_name", profile.idTokenClaimsMapping?.get("$.family_name"))
        assertEquals("$.['$ISO_MDL_NAMESPACE_ID'].resident_address", profile.idTokenClaimsMapping?.get("$.address"))

        val entriesConfig = assertNotNull(
            profile.mDocNameSpacesDataMappingConfig
                ?.get(ISO_MDL_NAMESPACE_ID)
                ?.entriesConfigMap,
        )
        assertNotNull(entriesConfig["portrait"], "Expected mDL portrait byte-string mapping")
        assertNotNull(entriesConfig["driving_privileges"], "Expected mDL nested driving privilege mapping")
    }

    private fun assertSdJwtCatalogProfiles(profiles: List<CredentialProfile>) {
        val profilesById = profiles.associateBy { it.profileId }

        SD_JWT_CATALOG_PROFILE_EXPECTATIONS.forEach { expectation ->
            val profile = assertNotNull(
                profilesById[expectation.profileId],
                "Expected configured profile ${expectation.profileId}",
            )

            assertEquals(expectation.name, profile.name)
            assertEquals(expectation.credentialConfigurationId, profile.credentialConfigurationId)
            assertEquals(
                expectation.sampleClaimValue,
                profile.credentialData[expectation.sampleClaim]?.jsonPrimitive?.contentOrNull,
                "Expected ${expectation.profileId} to expose catalog sample claim ${expectation.sampleClaim}",
            )
            assertEquals("<uuid>", profile.mapping?.get("id")?.jsonPrimitive?.content)
            assertNotNull(profile.selectiveDisclosure?.fields)

            if (expectation.usesIssuerDid) {
                assertEquals(ISSUER_DID, profile.issuerDid)
                assertNull(profile.x5Chain, "Expected ${expectation.profileId} to be DID/JWK based")
            } else {
                assertNull(profile.issuerDid, "Expected ${expectation.profileId} not to be DID based")
                assertNotNull(profile.x5Chain, "Expected ${expectation.profileId} to be x5-chain based")
            }
        }

        assertSdJwtProfile(assertNotNull(profilesById[IDENTITY_SD_JWT_PROFILE_ID]))
        assertSdJwtX5Profile(assertNotNull(profilesById[TAX_ID_SD_JWT_PROFILE_ID]))
    }

    private fun assertSdJwtProfile(profile: CredentialProfile) {
        assertEquals(IDENTITY_SD_JWT_PROFILE_ID, profile.profileId)
        assertEquals(IDENTITY_SD_JWT_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertEquals("Identity Credential", profile.name)
        assertEquals(ISSUER_DID, profile.issuerDid)
        assertEquals("John", profile.credentialData["given_name"]?.jsonPrimitive?.content)
        assertEquals("Doe", profile.credentialData["family_name"]?.jsonPrimitive?.content)
        assertEquals("<uuid>", profile.mapping?.get("id")?.jsonPrimitive?.content)
        assertNotNull(profile.selectiveDisclosure?.fields)
        assertEquals("$.given_name", profile.idTokenClaimsMapping?.get("$.given_name"))
        assertEquals("$.family_name", profile.idTokenClaimsMapping?.get("$.family_name"))
        assertEquals("$.email", profile.idTokenClaimsMapping?.get("$.email"))
        assertNull(profile.x5Chain, "Expected identity SD-JWT VC profile to be DID/JWK based")
    }

    private fun assertSdJwtX5Profile(profile: CredentialProfile) {
        assertEquals(TAX_ID_SD_JWT_PROFILE_ID, profile.profileId)
        assertEquals(TAX_ID_SD_JWT_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertEquals("Tax ID Credential", profile.name)
        assertNull(profile.issuerDid, "Expected x5-chain SD-JWT VC profile not to be DID based")
        assertNotNull(profile.x5Chain, "Expected x5-chain SD-JWT VC profile to use issuer1 sample certificate")
        assertEquals("91-287/6543", profile.credentialData["tax_number"]?.jsonPrimitive?.content)
        assertEquals("Musterfrau", profile.credentialData["registered_family_name"]?.jsonPrimitive?.content)
        assertEquals("$.registered_family_name", profile.idTokenClaimsMapping?.get("$.family_name"))
        assertEquals("$.registered_given_name", profile.idTokenClaimsMapping?.get("$.given_name"))
        assertEquals("$.birth_date", profile.idTokenClaimsMapping?.get("$.birthdate"))
    }

    private fun assertProfileHasDefaultIssuerKey(profile: CredentialProfile) {
        assertDefaultIssuerKey(profile.issuerKey, "${profile.profileId}.issuerKey")
    }

    private fun assertDefaultIssuerKey(issuerKey: JsonObject, context: String) {
        val jwk = assertNotNull(issuerKey["jwk"]?.jsonObject, "Expected $context.jwk")
        assertEquals("jwk", issuerKey["type"]?.jsonPrimitive?.content, "Expected $context.type")
        assertEquals(DEFAULT_ISSUER_KEY_D, jwk["d"]?.jsonPrimitive?.content, "Expected $context.jwk.d")
        assertEquals(DEFAULT_ISSUER_KEY_X, jwk["x"]?.jsonPrimitive?.content, "Expected $context.jwk.x")
        assertEquals(DEFAULT_ISSUER_KEY_Y, jwk["y"]?.jsonPrimitive?.content, "Expected $context.jwk.y")
    }

    private fun assertIssuerKeyIsExposed(responseBody: String) {
        assertTrue(responseBody.contains("issuerKey"), "Profile API should expose issuerKey in the first version")
        assertTrue(responseBody.contains(DEFAULT_ISSUER_KEY_D), "Profile API should expose private JWK values in the first version")
    }

    // Walk all profile fields that can contain strings or nested JSON. A plain "$"
    // is valid in JsonPath mappings; only "${" indicates an unresolved HOCON substitution.
    private fun assertNoUnresolvedSubstitutions(profileId: String, profile: CredentialProfileConfig) {
        assertNoUnresolvedSubstitution("$profileId.issuerKey", profile.issuerKey)
        assertNoUnresolvedSubstitution("$profileId.credentialData", profile.credentialData)
        profile.mapping?.let { assertNoUnresolvedSubstitution("$profileId.mapping", it) }
        profile.selectiveDisclosure?.let {
            assertNoUnresolvedSubstitution("$profileId.selectiveDisclosure", json.encodeToJsonElement(it))
        }
        profile.mDocNameSpacesDataMappingConfig?.forEach { (namespace, mapping) ->
            assertNoUnresolvedString("$profileId.mDocNameSpacesDataMappingConfig.key", namespace)
            assertNoUnresolvedSubstitution(
                "$profileId.mDocNameSpacesDataMappingConfig.$namespace",
                json.encodeToJsonElement(mapping),
            )
        }
        profile.idTokenClaimsMapping?.forEach { (claim, path) ->
            assertNoUnresolvedString("$profileId.idTokenClaimsMapping.key", claim)
            assertNoUnresolvedString("$profileId.idTokenClaimsMapping.$claim", path)
        }
        profile.issuerDid?.let { assertNoUnresolvedString("$profileId.issuerDid", it) }
        profile.x5Chain?.forEachIndexed { index, certificate ->
            assertNoUnresolvedString("$profileId.x5Chain[$index]", certificate)
        }
        profile.notifications?.webhook?.url?.let { assertNoUnresolvedString("$profileId.notifications.webhook.url", it) }
    }

    private fun assertNoUnresolvedSubstitution(context: String, value: JsonElement) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                assertNoUnresolvedSubstitution("$context.$key", child)
            }
            is JsonArray -> value.forEachIndexed { index, child ->
                assertNoUnresolvedSubstitution("$context[$index]", child)
            }
            is JsonPrimitive -> value.contentOrNull?.let { assertNoUnresolvedString(context, it) }
        }
    }

    private fun assertNoUnresolvedString(context: String, value: String) {
        assertFalse(value.contains("\${"), "Expected $context to have resolved HOCON substitutions, got: $value")
    }

    private data class SdJwtProfileExpectation(
        val profileId: String,
        val name: String,
        val credentialConfigurationId: String,
        val sampleClaim: String,
        val sampleClaimValue: String,
        val usesIssuerDid: Boolean = false,
    )

    private fun assertProfileJsonEquals(
        expected: CredentialProfile,
        actualBody: String,
        message: String? = null,
    ) {
        assertEquals(
            json.encodeToJsonElement(expected),
            json.parseToJsonElement(actualBody),
            message,
        )
    }

    private fun ApplicationTestBuilder.installIssuer2WithConfigFiles() {
        loadIssuer2ConfigFiles()
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            runBlocking { issuer2AuthenticationPluginAmendment() }
            AuthenticationServiceModule.run { enable() }
            issuer2Module(withPlugins = true)
        }
    }

    private fun ApplicationTestBuilder.apiClient() = createClient {
        install(ClientContentNegotiation) {
            json(json)
        }
    }

    private fun loadIssuer2ConfigFiles() {
        ConfigManager.preclear()
        FeatureManager.preclear()
        registerIssuer2ConfigDecoders()
        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }

        val configDir = issuer2ConfigDir()
        configFiles.forEach { (id, type) ->
            System.setProperty("config.file.$id", configDir.resolve("$id.conf").toString())
            ConfigManager.registerConfig(id, type)
        }
        ConfigManager.loadConfigs()
    }

    private fun issuer2ConfigDir(): Path =
        listOf(
            Path.of("config"),
            Path.of("waltid-services/waltid-issuer-api2/config"),
            Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
        )
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.isRegularFile(it.resolve("issuer-service.conf")) }
            ?: error("Could not locate waltid-issuer-api2 config directory")

    private companion object {
        const val UNIVERSITY_DEGREE_PROFILE_ID = "universityDegree"
        const val UNIVERSITY_DEGREE_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"
        const val ISO_PHOTO_ID_PROFILE_ID = "isoPhotoId"
        const val ISO_PHOTO_ID_CONFIGURATION_ID = "org.iso.23220.photoid.1"
        const val ISO_MDL_PROFILE_ID = "isoMdl"
        const val ISO_MDL_CONFIGURATION_ID = "org.iso.18013.5.1.mDL"
        const val ISO_MDL_NAMESPACE_ID = "org.iso.18013.5.1"
        const val IDENTITY_SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
        const val IDENTITY_SD_JWT_CONFIGURATION_ID = "identity_credential"
        const val TAX_ID_SD_JWT_PROFILE_ID = "taxIdCredentialSdJwt"
        const val TAX_ID_SD_JWT_CONFIGURATION_ID = "asit.tax-id-credential"
        const val JWT_VC_JSON_FORMAT = "jwt_vc_json"
        const val MSO_MDOC_FORMAT = "mso_mdoc"
        const val SD_JWT_VC_FORMAT = "dc+sd-jwt"
        const val SAMPLE_PORTRAIT_BASE64 = "AQIDBAUGBwgJCgsMDQ4P"
        const val DEFAULT_ISSUER_KEY_D = "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ"
        const val DEFAULT_ISSUER_KEY_X = "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM"
        const val DEFAULT_ISSUER_KEY_Y = "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
        const val ISSUER_DID =
            "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IkcwUklOQmlGLW9RVUQzZDVER25lZ1F1WGVuSTI5SkRhTUdvTXZpb0tSQk0iLCJ5IjoiZWQzZUZHczJwRXRycDd2QVo3QkxjYnJVdHBLa1lXQVQySlBVUUs0bE40RSJ9"

        val SD_JWT_CATALOG_PROFILE_EXPECTATIONS = listOf(
            SdJwtProfileExpectation(
                profileId = TAX_ID_SD_JWT_PROFILE_ID,
                name = "Tax ID Credential",
                credentialConfigurationId = TAX_ID_SD_JWT_CONFIGURATION_ID,
                sampleClaim = "tax_number",
                sampleClaimValue = "91-287/6543",
            ),
            SdJwtProfileExpectation(
                profileId = "certificateOfResidenceSdJwt",
                name = "Certificate of Residence",
                credentialConfigurationId = "urn:eu.europa.ec.eudi:cor:1",
                sampleClaim = "family_name",
                sampleClaimValue = "Musterfrau",
            ),
            SdJwtProfileExpectation(
                profileId = "powerOfRepresentationSdJwt",
                name = "Power of Representation",
                credentialConfigurationId = "urn:eu.europa.ec.eudi:por:1",
                sampleClaim = "legal_name",
                sampleClaimValue = "Musterfirma GmbH",
            ),
            SdJwtProfileExpectation(
                profileId = "ehicSdJwt",
                name = "European Health Insurance Card (EHIC)",
                credentialConfigurationId = "urn:eudi:ehic:1",
                sampleClaim = "social_security_number",
                sampleClaimValue = "1234 250888",
            ),
            SdJwtProfileExpectation(
                profileId = "eudiPidSdJwt",
                name = "EU PID Credential (SD-JWT VC)",
                credentialConfigurationId = "urn:eudi:pid:1",
                sampleClaim = "family_name",
                sampleClaimValue = "Musterfrau",
            ),
            SdJwtProfileExpectation(
                profileId = IDENTITY_SD_JWT_PROFILE_ID,
                name = "Identity Credential",
                credentialConfigurationId = IDENTITY_SD_JWT_CONFIGURATION_ID,
                sampleClaim = "given_name",
                sampleClaimValue = "John",
                usesIssuerDid = true,
            ),
        )

        val configFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
            "issuer-service" to Issuer2ServiceConfig::class,
            "authentication-service" to AuthenticationServiceConfig::class,
            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
            "issuer2-profiles" to Issuer2ProfilesConfig::class,
        )
    }
}
