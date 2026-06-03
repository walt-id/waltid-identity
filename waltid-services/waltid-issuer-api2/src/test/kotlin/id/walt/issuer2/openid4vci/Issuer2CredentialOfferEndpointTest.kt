package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import id.walt.issuer2.controller.dto.CredentialOfferCreateRequest
import id.walt.issuer2.controller.dto.CredentialOfferCreateResponse
import id.walt.issuer2.controller.dto.CredentialOfferRuntimeOverrides
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.issuer2Module
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.walt.sdjwt.SDMap
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class Issuer2CredentialOfferEndpointTest {

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
    fun shouldCreateCredentialOffersFromConfiguredProfiles() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        // Start at the management API boundary: profiles are config-backed and should be
        // discoverable before an operator can create offers from them.
        val profiles = client.listProfiles()
        assertFalse(profiles.isEmpty(), "Expected issuer2 config to expose credential profiles")

        val representativeProfiles = listOf(
            client.getProfile(UNIVERSITY_DEGREE_PROFILE_ID),
            client.getProfile(ISO_MDL_PROFILE_ID),
            client.getProfile(ISO_PHOTO_ID_PROFILE_ID),
            client.getProfile(IDENTITY_SD_JWT_PROFILE_ID),
            client.getProfile(CUSTOM_VCT_SD_JWT_PROFILE_ID),
        )

        representativeProfiles.forEach { profile ->
            assertConfiguredProfileCanCreateOffer(client, profile)
        }
    }

    @Test
    fun shouldCreateConfiguredCredentialOffersForSupportedModes() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val profile = client.getProfile(UNIVERSITY_DEGREE_PROFILE_ID)

        // These are the modes exposed to issuer clients. They should work from real
        // config, not only from a synthetic in-test profile.
        assertConfiguredOfferMode(
            client = client,
            profile = profile,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        )
        assertConfiguredOfferMode(
            client = client,
            profile = profile,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_VALUE,
        )
        assertConfiguredOfferMode(
            client = client,
            profile = profile,
            authMethod = AuthenticationMethod.AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        )
        assertConfiguredOfferMode(
            client = client,
            profile = profile,
            authMethod = AuthenticationMethod.AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_VALUE,
        )

        val authorizedWithIssuerState = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.AUTHORIZED,
                issuerStateMode = IssuerStateMode.INCLUDE,
                valueMode = CredentialOfferValueMode.BY_VALUE,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )
        assertEquals(authorizedWithIssuerState.offerId, authorizedWithIssuerState.inlineOffer().grants?.authorizationCode?.issuerState)

        val providedTxCode = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                txCode = TX_CODE,
                txCodeValue = TX_CODE_VALUE,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )
        assertEquals(TX_CODE_VALUE, providedTxCode.txCodeValue)
        assertEquals(TX_CODE, providedTxCode.resolveOffer(client).grants?.preAuthorizedCode?.txCode)

        val generatedTxCode = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                txCode = TX_CODE,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )
        assertEquals(TX_CODE.length, assertNotNull(generatedTxCode.txCodeValue).length)

        assertConfiguredCustomExpiry(client, profile)
        assertConfiguredNoExpiry(client, profile)
    }

    @Test
    fun shouldStoreConfiguredCredentialOfferSessions() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val profile = client.getProfile(ISO_PHOTO_ID_PROFILE_ID)

        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )

        // The session is the immutable issuance input snapshot. Credential issuance will
        // read from this later instead of re-reading mutable profile config.
        val session = client.getSession(response.offerId)
        assertEquals(response.offerId, session.sessionId)
        assertEquals(profile.profileId, session.profileId)
        assertEquals(profile.credentialConfigurationId, session.credentialConfigurationId)
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, session.authenticationMethod)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
        assertEquals(response.expiresAt, session.expiresAt.toEpochMilliseconds())
        assertEquals(response.resolveOffer(client), session.credentialOffer)
    }

    @Test
    fun shouldApplyRuntimeOverridesToConfiguredCredentialOfferSessions() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val profile = client.getProfile(IDENTITY_SD_JWT_PROFILE_ID)
        val selectiveDisclosure = SDMap.generateSDMap(listOf("given_name", "family_name"))

        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.AUTHORIZED,
                runtimeOverrides = CredentialOfferRuntimeOverrides(
                    subjectId = "did:example:subject",
                    issuerDid = "did:example:issuer-override",
                    credentialData = buildJsonObject {
                        put("given_name", "Jane")
                        put("family_name", "Doe")
                        put("birthdate", "2000-01-01")
                    },
                    mapping = buildJsonObject {
                        put("iat", "<timestamp>")
                    },
                    selectiveDisclosure = selectiveDisclosure,
                    idTokenClaimsMapping = mapOf(
                        "given_name" to "$.given_name",
                        "family_name" to "$.family_name",
                    ),
                    x5Chain = listOf("-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"),
                    webhookUrl = "https://issuer.example/webhooks/issuer2",
                ),
            )
        )

        // Runtime overrides are offer-scoped; they must be copied into the stored session
        // without changing the configured profile.
        val session = client.getSession(response.offerId)
        assertEquals("did:example:subject", session.subjectId)
        assertEquals("did:example:issuer-override", session.issuerDid)
        assertEquals("Jane", session.credentialData["given_name"]?.jsonPrimitive?.content)
        assertEquals("<timestamp>", session.mapping?.get("iat")?.jsonPrimitive?.content)
        val sessionSelectiveDisclosure = assertNotNull(session.selectiveDisclosure)
        assertNotNull(sessionSelectiveDisclosure.get("given_name"))
        assertNotNull(sessionSelectiveDisclosure.get("family_name"))
        assertEquals("$.given_name", session.idTokenClaimsMapping?.get("given_name"))
        assertEquals(listOf("-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"), session.x5Chain)
        assertEquals("https://issuer.example/webhooks/issuer2", session.webhookUrl)

        val unchangedProfile = client.getProfile(profile.profileId)
        assertEquals(profile.issuerDid, unchangedProfile.issuerDid)
        assertEquals(profile.credentialData, unchangedProfile.credentialData)
    }

    @Test
    fun shouldCreateCredentialOffersForDocumentedModes() = testApplication {
        installIssuer2()
        val client = apiClient()

        assertAuthorizedByReferenceOffer(client)
        assertAuthorizedByValueOffer(client)
        assertAuthorizedByValueOfferWithIssuerState(client)
        assertPreAuthorizedByReferenceOfferWithProvidedTxCode(client)
        assertPreAuthorizedByReferenceOfferWithGeneratedTxCode(client)
        assertPreAuthorizedByValueOffer(client)
        assertCustomExpiry(client)
        assertNoExpiry(client)
        assertRuntimeOverrides(client)
    }

    private suspend fun assertAuthorizedByReferenceOffer(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
            )
        )

        assertEquals(AuthenticationMethod.AUTHORIZED, response.authMethod)
        assertEquals(IssuerStateMode.OMIT, response.issuerStateMode)
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(response.credentialOffer)
        assertNull(offerRequest.credentialOffer)
        val credentialOfferUri = assertNotNull(offerRequest.credentialOfferUri)
        assertTrue(
            credentialOfferUri.endsWith("/openid4vci/credential-offer?id=${response.offerId}"),
            "Expected by-reference offer URI to point to the OSS issuer2 credential-offer endpoint",
        )
    }

    private suspend fun assertAuthorizedByValueOffer(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_VALUE,
            )
        )

        val offer = response.inlineOffer()
        assertEquals(ISSUER_BASE_URL, offer.credentialIssuer)
        assertEquals(listOf(CREDENTIAL_CONFIGURATION_ID), offer.credentialConfigurationIds)
        assertNull(offer.grants?.authorizationCode?.issuerState)
    }

    private suspend fun assertAuthorizedByValueOfferWithIssuerState(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.AUTHORIZED,
                issuerStateMode = IssuerStateMode.INCLUDE,
                valueMode = CredentialOfferValueMode.BY_VALUE,
            )
        )

        assertEquals(IssuerStateMode.INCLUDE, response.issuerStateMode)
        assertEquals(response.offerId, response.inlineOffer().grants?.authorizationCode?.issuerState)
    }

    private suspend fun assertPreAuthorizedByReferenceOfferWithProvidedTxCode(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                txCode = TX_CODE,
                txCodeValue = TX_CODE_VALUE,
            )
        )

        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, response.authMethod)
        assertEquals(IssuerStateMode.OMIT, response.issuerStateMode)
        assertEquals(TX_CODE_VALUE, response.txCodeValue)
        assertEquals(TX_CODE, response.resolveOffer(client).grants?.preAuthorizedCode?.txCode)
    }

    private suspend fun assertPreAuthorizedByReferenceOfferWithGeneratedTxCode(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                txCode = TX_CODE,
            )
        )

        val txCodeValue = assertNotNull(response.txCodeValue)
        assertEquals(6, txCodeValue.length)
        assertEquals(TX_CODE, response.resolveOffer(client).grants?.preAuthorizedCode?.txCode)
    }

    private suspend fun assertPreAuthorizedByValueOffer(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_VALUE,
            )
        )

        val offer = response.inlineOffer()
        assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)
        assertNull(offer.grants?.authorizationCode)
    }

    private suspend fun assertCustomExpiry(client: HttpClient) {
        val expectedNotBefore = Clock.System.now().plus(TWO_MINUTES_SECONDS.seconds).toEpochMilliseconds()
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.AUTHORIZED,
                expiresInSeconds = TWO_MINUTES_SECONDS,
            )
        )
        val expectedNotAfter = Clock.System.now().plus(TWO_MINUTES_SECONDS.seconds).toEpochMilliseconds()

        assertTrue(
            response.expiresAt in expectedNotBefore..expectedNotAfter,
            "Expected expiresAt to respect expiresInSeconds=$TWO_MINUTES_SECONDS",
        )
    }

    private suspend fun assertNoExpiry(client: HttpClient) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                expiresInSeconds = -1L,
            )
        )

        assertEquals(Instant.DISTANT_FUTURE.toEpochMilliseconds(), response.expiresAt)
        val session = client.get("/issuer2/sessions/${response.offerId}").body<IssuanceSession>()
        assertEquals(Instant.DISTANT_FUTURE, session.expiresAt)
    }

    private suspend fun assertRuntimeOverrides(client: HttpClient) {
        val selectiveDisclosure = SDMap.generateSDMap(listOf("credentialSubject.givenName"))
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = PROFILE_ID,
                authMethod = AuthenticationMethod.AUTHORIZED,
                runtimeOverrides = CredentialOfferRuntimeOverrides(
                    credentialData = buildJsonObject {
                        putJsonObject("credentialSubject") {
                            put("givenName", "Jane")
                            put("familyName", "Doe")
                        }
                    },
                    selectiveDisclosure = selectiveDisclosure,
                    webhookUrl = "https://issuer.example/webhooks/issuance",
                ),
            )
        )

        val session = client.get("/issuer2/sessions/${response.offerId}").body<IssuanceSession>()
        assertEquals("Jane", session.credentialData["credentialSubject"]?.jsonObject?.get("givenName")?.jsonPrimitive?.content)
        assertEquals("https://issuer.example/webhooks/issuance", session.webhookUrl)
        assertNotNull(session.selectiveDisclosure?.get("credentialSubject"))
    }

    private suspend fun assertConfiguredProfileCanCreateOffer(
        client: HttpClient,
        profile: CredentialProfile,
    ) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                valueMode = CredentialOfferValueMode.BY_REFERENCE,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )

        val offer = response.resolveOffer(client)
        assertEquals(CONFIGURED_ISSUER_BASE_URL, offer.credentialIssuer)
        assertEquals(listOf(profile.credentialConfigurationId), offer.credentialConfigurationIds)
        assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)

        val session = client.getSession(response.offerId)
        assertEquals(profile.profileId, session.profileId)
        assertEquals(profile.credentialConfigurationId, session.credentialConfigurationId)
    }

    private suspend fun assertConfiguredOfferMode(
        client: HttpClient,
        profile: CredentialProfile,
        authMethod: AuthenticationMethod,
        valueMode: CredentialOfferValueMode,
    ) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = authMethod,
                valueMode = valueMode,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )

        val offer = when (valueMode) {
            CredentialOfferValueMode.BY_VALUE -> response.inlineOffer()
            CredentialOfferValueMode.BY_REFERENCE -> response.resolveOffer(client)
        }
        assertEquals(CONFIGURED_ISSUER_BASE_URL, offer.credentialIssuer)
        assertEquals(listOf(profile.credentialConfigurationId), offer.credentialConfigurationIds)
        when (authMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> {
                assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)
                assertNull(offer.grants?.authorizationCode)
            }
            AuthenticationMethod.AUTHORIZED -> {
                assertNotNull(offer.grants?.authorizationCode)
                assertNull(offer.grants?.preAuthorizedCode)
            }
        }
    }

    private suspend fun assertConfiguredCustomExpiry(
        client: HttpClient,
        profile: CredentialProfile,
    ) {
        val expectedNotBefore = Clock.System.now().plus(TWO_MINUTES_SECONDS.seconds).toEpochMilliseconds()
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.AUTHORIZED,
                expiresInSeconds = TWO_MINUTES_SECONDS,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )
        val expectedNotAfter = Clock.System.now().plus(TWO_MINUTES_SECONDS.seconds).toEpochMilliseconds()

        assertTrue(response.expiresAt in expectedNotBefore..expectedNotAfter)
        assertEquals(response.expiresAt, client.getSession(response.offerId).expiresAt.toEpochMilliseconds())
    }

    private suspend fun assertConfiguredNoExpiry(
        client: HttpClient,
        profile: CredentialProfile,
    ) {
        val response = client.createCredentialOffer(
            CredentialOfferCreateRequest(
                profileId = profile.profileId,
                authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                expiresInSeconds = -1L,
                runtimeOverrides = runtimeOverridesForConfiguredProfile(profile.profileId),
            )
        )

        assertEquals(Instant.DISTANT_FUTURE.toEpochMilliseconds(), response.expiresAt)
        assertEquals(Instant.DISTANT_FUTURE, client.getSession(response.offerId).expiresAt)
    }

    private suspend fun HttpClient.listProfiles(): List<CredentialProfile> =
        get("/issuer2/profiles").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body()

    private suspend fun HttpClient.getProfile(profileId: String): CredentialProfile =
        get("/issuer2/profiles/$profileId").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body()

    private suspend fun HttpClient.getSession(sessionId: String): IssuanceSession =
        get("/issuer2/sessions/$sessionId").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body()

    private suspend fun HttpClient.createCredentialOffer(
        request: CredentialOfferCreateRequest,
    ): CredentialOfferCreateResponse {
        val response = post("/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return response.body()
    }

    private fun CredentialOfferCreateResponse.inlineOffer(): CredentialOffer {
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(credentialOffer)
        assertNull(offerRequest.credentialOfferUri)
        return assertNotNull(offerRequest.credentialOffer)
    }

    private suspend fun CredentialOfferCreateResponse.resolveOffer(client: HttpClient): CredentialOffer {
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(credentialOffer)
        offerRequest.credentialOffer?.let { return it }

        val credentialOfferUri = assertNotNull(offerRequest.credentialOfferUri)
        val url = Url(credentialOfferUri)
        val query = url.encodedQuery.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
        return client.get("${url.encodedPath}$query").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body()
    }

    private fun ApplicationTestBuilder.installIssuer2WithConfigFiles() {
        loadIssuer2ConfigFiles()
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            issuer2Module(withPlugins = true)
        }
    }

    private fun ApplicationTestBuilder.installIssuer2() {
        configureIssuer2Configs()
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            issuer2Module(withPlugins = true)
        }
    }

    private fun ApplicationTestBuilder.apiClient() = createClient {
        followRedirects = false
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

    private fun configureIssuer2Configs() {
        ConfigManager.preclear()
        FeatureManager.preclear()
        registerIssuer2ConfigDecoders()

        ConfigManager.preloadAndRegisterConfig(
            "issuer-service",
            Issuer2ServiceConfig(baseUrl = "http://localhost"),
        )
        ConfigManager.preloadAndRegisterConfig(
            "authentication-service",
            AuthenticationServiceConfig(enabled = false),
        )
        ConfigManager.preloadAndRegisterConfig(
            "credential-issuer-metadata",
            Issuer2MetadataConfig(
                credentialConfigurations = mapOf(
                    CREDENTIAL_CONFIGURATION_ID to json.parseToJsonElement(
                        """
                        {
                          "format": "jwt_vc_json",
                          "cryptographic_binding_methods_supported": ["jwk", "did:key", "did:jwk", "did:web"],
                          "credential_signing_alg_values_supported": ["ES256"],
                          "proof_types_supported": {
                            "jwt": {
                              "proof_signing_alg_values_supported": ["ES256"]
                            }
                          },
                          "credential_definition": {
                            "type": ["VerifiableCredential", "UniversityDegreeCredential"]
                          }
                        }
                        """.trimIndent()
                    ),
                ),
            )
        )
        ConfigManager.preloadAndRegisterConfig(
            "issuer2-profiles",
            Issuer2ProfilesConfig(
                profiles = mapOf(
                    PROFILE_ID to CredentialProfileConfig(
                        name = "University Degree",
                        credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                        issuerKey = defaultIssuerKey(),
                        credentialData = credentialData(),
                        mapping = credentialMapping(),
                    )
                ),
            )
        )
        ConfigManager.loadConfigs()
    }

    private fun defaultIssuerKey(): JsonObject = buildJsonObject {
        putJsonObject("jwk") {
            put("kty", "EC")
            put("d", "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ")
            put("crv", "P-256")
            put("x", "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM")
            put("y", "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E")
        }
        put("type", "jwk")
    }

    private fun credentialData(): JsonObject = buildJsonObject {
        put("@context", buildJsonArray {
            add("https://www.w3.org/2018/credentials/v1")
        })
        put("type", buildJsonArray {
            add("VerifiableCredential")
            add("UniversityDegreeCredential")
        })
        putJsonObject("credentialSubject") {
            put("id", "did:example:holder")
        }
    }

    private fun credentialMapping(): JsonObject = buildJsonObject {
        put("id", "<uuid>")
        putJsonObject("issuer") {
            put("id", "<issuerDid>")
        }
        put("issuanceDate", "<timestamp>")
    }

    private fun runtimeOverridesForConfiguredProfile(profileId: String): CredentialOfferRuntimeOverrides? =
        when (profileId) {
            UNIVERSITY_DEGREE_PROFILE_ID -> CredentialOfferRuntimeOverrides(
                credentialData = buildJsonObject {
                    putJsonObject("credentialSubject") {
                        put("id", "did:example:holder")
                        put("givenName", "Jane")
                        put("familyName", "Doe")
                    }
                },
            )

            ISO_MDL_PROFILE_ID -> CredentialOfferRuntimeOverrides(
                credentialData = buildJsonObject {
                    putJsonObject("org.iso.18013.5.1") {
                        put("family_name", "Doe")
                        put("given_name", "Jane")
                        put("resident_address", "Example Street 1, Vienna")
                    }
                },
            )

            else -> null
        }

    private companion object {
        const val PROFILE_ID = "universityDegree"
        const val CREDENTIAL_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"
        const val ISSUER_BASE_URL = "http://localhost/openid4vci"
        const val CONFIGURED_ISSUER_BASE_URL = "http://localhost:7003/openid4vci"
        const val UNIVERSITY_DEGREE_PROFILE_ID = "universityDegree"
        const val ISO_MDL_PROFILE_ID = "isoMdl"
        const val ISO_PHOTO_ID_PROFILE_ID = "isoPhotoId"
        const val IDENTITY_SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
        const val CUSTOM_VCT_SD_JWT_PROFILE_ID = "customVctSdJwt"
        const val TX_CODE_VALUE = "123456"
        const val TWO_MINUTES_SECONDS = 120L

        val TX_CODE = TxCode(
            inputMode = "numeric",
            length = 6,
            description = "Enter the PIN shown by the issuer",
        )

        val configFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
            "issuer-service" to Issuer2ServiceConfig::class,
            "authentication-service" to AuthenticationServiceConfig::class,
            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
            "issuer2-profiles" to Issuer2ProfilesConfig::class,
        )
    }
}
