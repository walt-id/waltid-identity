//package id.walt.issuer2.openid4vci
//
//import id.walt.commons.config.ConfigManager
//import id.walt.commons.config.WaltConfig
//import id.walt.commons.featureflag.FeatureManager
//import id.walt.commons.web.modules.AuthenticationServiceModule
//import id.walt.issuer2.config.AuthenticationServiceConfig
//import id.walt.issuer2.config.Issuer2MetadataConfig
//import id.walt.issuer2.config.Issuer2ProfilesConfig
//import id.walt.issuer2.config.Issuer2ServiceConfig
//import id.walt.issuer2.config.registerIssuer2ConfigDecoders
//import id.walt.issuer2.issuer2Module
//import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
//import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
//import id.walt.openid4vci.CredentialFormat
//import id.walt.openid4vci.CryptographicBindingMethod
//import id.walt.openid4vci.GrantType
//import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
//import id.walt.openid4vci.metadata.issuer.SigningAlgId
//import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
//import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
//import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
//import io.ktor.client.HttpClient
//import io.ktor.client.call.body
//import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
//import io.ktor.client.request.get
//import io.ktor.http.HttpStatusCode
//import io.ktor.serialization.kotlinx.json.json
//import io.ktor.server.application.install
//import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
//import io.ktor.server.testing.ApplicationTestBuilder
//import io.ktor.server.testing.testApplication
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.JsonObject
//import org.junit.jupiter.api.AfterEach
//import org.junit.jupiter.api.Test
//import java.nio.file.Files
//import java.nio.file.Path
//import kotlin.reflect.KClass
//import kotlinx.coroutines.runBlocking
//import kotlin.test.assertEquals
//import kotlin.test.assertFalse
//import kotlin.test.assertNotNull
//import kotlin.test.assertNull
//
//class Issuer2MetadataEndpointTest {
//
//    private val json = Json {
//        ignoreUnknownKeys = true
//        explicitNulls = false
//        encodeDefaults = false
//    }
//
//    @AfterEach
//    fun clearConfig() {
//        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }
//        ConfigManager.preclear()
//        FeatureManager.preclear()
//    }
//
//    @Test
//    fun shouldHaveValidMetadataEndpointsFromConfigFiles() = testApplication {
//        installIssuer2WithConfigFiles()
//        val client = apiClient()
//
//        val credentialIssuerMetadataRaw = client.get("/.well-known/openid-credential-issuer/openid4vci")
//        assertEquals(HttpStatusCode.OK, credentialIssuerMetadataRaw.status)
//        val credentialIssuerMetadata = credentialIssuerMetadataRaw.body<CredentialIssuerMetadata>()
//
//        val authorizationServerMetadataRaw = client.get("/.well-known/oauth-authorization-server/openid4vci")
//        assertEquals(HttpStatusCode.OK, authorizationServerMetadataRaw.status)
//        val authorizationServerMetadata = authorizationServerMetadataRaw.body<AuthorizationServerMetadata>()
//        val authorizationServerMetadataJson = client
//            .get("/.well-known/oauth-authorization-server/openid4vci")
//            .body<JsonObject>()
//
//        val jwtVcIssuerMetadataRaw = client.get("/.well-known/jwt-vc-issuer/openid4vci")
//        assertEquals(HttpStatusCode.OK, jwtVcIssuerMetadataRaw.status)
//        val jwtVcIssuerMetadata = jwtVcIssuerMetadataRaw.body<JWTVCIssuerMetadata>()
//
//        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/openid-credential-issuer/openid4vci").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/oauth-authorization-server/openid4vci").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/jwt-vc-issuer/openid4vci").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/vct/$SD_JWT_INTERNAL_CONFIG_ID").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/openid-credential-issuer").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/oauth-authorization-server").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/jwt-vc-issuer").status)
//        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/vct").status)
//
//        assertNull(credentialIssuerMetadata.authorizationServers)
//        assertEquals(ISSUER_BASE_URL, credentialIssuerMetadata.credentialIssuer)
//        assertEquals("$ISSUER_BASE_URL/credential", credentialIssuerMetadata.credentialEndpoint)
//        assertEquals("$ISSUER_BASE_URL/nonce", credentialIssuerMetadata.nonceEndpoint)
//        assertEquals(setOf("en-US", "de-DE"), credentialIssuerMetadata.display?.mapNotNull { it.locale }?.toSet())
//
//        assertEquals(ISSUER_BASE_URL, authorizationServerMetadata.issuer)
//        assertEquals("$ISSUER_BASE_URL/authorize", authorizationServerMetadata.authorizationEndpoint)
//        assertEquals("$ISSUER_BASE_URL/token", authorizationServerMetadata.tokenEndpoint)
//        assertEquals("$ISSUER_BASE_URL/jwks", authorizationServerMetadata.jwksUri)
//        assertNull(authorizationServerMetadata.pushedAuthorizationRequestEndpoint)
//        assertNull(authorizationServerMetadata.codeChallengeMethodsSupported)
//        assertEquals(setOf("attest_jwt_client_auth"), authorizationServerMetadata.tokenEndpointAuthMethodsSupported)
//        assertEquals(setOf("ES256"), authorizationServerMetadata.clientAttestationSigningAlgValuesSupported)
//        assertEquals(setOf("ES256"), authorizationServerMetadata.clientAttestationPopSigningAlgValuesSupported)
//        assertEquals(setOf("ES256"), authorizationServerMetadata.dpopSigningAlgValuesSupported)
//        assertEquals(true, authorizationServerMetadata.preAuthorizedGrantAnonymousAccessSupported)
//        assertEquals(
//            setOf(GrantType.AuthorizationCode.value, GrantType.PreAuthorizedCode.value),
//            authorizationServerMetadata.grantTypesSupported,
//        )
//        assertFalse("code_challenge_methods_supported" in authorizationServerMetadataJson)
//
//        assertEquals(ISSUER_BASE_URL, jwtVcIssuerMetadata.issuer)
//        assertEquals("$ISSUER_BASE_URL/jwks", jwtVcIssuerMetadata.jwksUri)
//        assertNull(jwtVcIssuerMetadata.jwks)
//
//        assertJwtVcJsonConfiguration(credentialIssuerMetadata)
//        assertMdocConfiguration(credentialIssuerMetadata)
//        assertSdJwtVcConfiguration(credentialIssuerMetadata)
//        assertConfiguredCredentialScenariosAreAdvertised(credentialIssuerMetadata)
//        assertSelfHostedSdJwtVcTypeMetadata(client, credentialIssuerMetadata)
//        assertExternalSdJwtVcVctPassThrough(credentialIssuerMetadata)
//    }
//
//    private fun assertConfiguredCredentialScenariosAreAdvertised(
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        Issuer2CredentialScenarios.configured.forEach { scenario ->
//            val configuration = assertNotNull(
//                credentialIssuerMetadata.credentialConfigurationsSupported[scenario.credentialConfigurationId],
//                "Expected metadata for configured issuer2 credential ${scenario.credentialConfigurationId}",
//            )
//            assertEquals(scenario.format, configuration.format.value)
//        }
//    }
//
//    private suspend fun assertSelfHostedSdJwtVcTypeMetadata(
//        client: HttpClient,
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        val internalSdJwtConfiguration = assertNotNull(
//            credentialIssuerMetadata.credentialConfigurationsSupported[SD_JWT_INTERNAL_CONFIG_ID],
//            "Expected credential configuration for $SD_JWT_INTERNAL_CONFIG_ID",
//        )
//        val publishedVct = assertNotNull(internalSdJwtConfiguration.vct)
//
//        val vctTypeMetadataRaw = client.get("/.well-known/vct/$SD_JWT_INTERNAL_CONFIG_ID")
//        assertEquals(HttpStatusCode.OK, vctTypeMetadataRaw.status)
//        val vctTypeMetadata = vctTypeMetadataRaw.body<SdJwtVcTypeMetadataDraft04>()
//        assertEquals(publishedVct, vctTypeMetadata.vct)
//        assertEquals(SD_JWT_INTERNAL_CONFIG_ID, vctTypeMetadata.name)
//        assertEquals("$SD_JWT_INTERNAL_CONFIG_ID Verifiable Credential", vctTypeMetadata.description)
//    }
//
//    private fun assertExternalSdJwtVcVctPassThrough(
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        val externalSdJwtConfiguration = assertNotNull(
//            credentialIssuerMetadata.credentialConfigurationsSupported[SD_JWT_EXTERNAL_CONFIG_ID],
//            "Expected credential configuration for $SD_JWT_EXTERNAL_CONFIG_ID",
//        )
//        assertEquals(CredentialFormat.SD_JWT_VC.value, externalSdJwtConfiguration.format.value)
//        assertEquals(EXTERNAL_SD_JWT_VCT, externalSdJwtConfiguration.vct)
//    }
//
//    private fun assertJwtVcJsonConfiguration(
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        val universityDegreeConfiguration = assertNotNull(
//            credentialIssuerMetadata.credentialConfigurationsSupported[UNIVERSITY_DEGREE_CONFIG_ID],
//            "Expected credential configuration for $UNIVERSITY_DEGREE_CONFIG_ID",
//        )
//        assertEquals(CredentialFormat.JWT_VC_JSON.value, universityDegreeConfiguration.format.value)
//        assertEquals(
//            setOf(SigningAlgId.Jose("ES256")),
//            universityDegreeConfiguration.credentialSigningAlgValuesSupported,
//        )
//        assertEquals(
//            setOf(
//                CryptographicBindingMethod.Jwk,
//                CryptographicBindingMethod.DidKey,
//                CryptographicBindingMethod.DidWeb,
//                CryptographicBindingMethod.DidJwk,
//                CryptographicBindingMethod.DidEbsi,
//            ),
//            universityDegreeConfiguration.cryptographicBindingMethodsSupported,
//        )
//        assertEquals(
//            setOf("VerifiableCredential", "UniversityDegree"),
//            universityDegreeConfiguration.credentialDefinition?.type?.toSet(),
//        )
//        assertNotNull(universityDegreeConfiguration.proofTypesSupported?.get("jwt"))
//    }
//
//    private fun assertMdocConfiguration(
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        val mdocConfiguration = assertNotNull(
//            credentialIssuerMetadata.credentialConfigurationsSupported[MDOC_CONFIG_ID],
//            "Expected credential configuration for $MDOC_CONFIG_ID",
//        )
//        assertEquals(CredentialFormat.MSO_MDOC.value, mdocConfiguration.format.value)
//        assertEquals(
//            setOf(SigningAlgId.CoseValue(-7), SigningAlgId.CoseValue(-9)),
//            mdocConfiguration.credentialSigningAlgValuesSupported,
//        )
//        assertEquals(setOf(CryptographicBindingMethod.CoseKey), mdocConfiguration.cryptographicBindingMethodsSupported)
//        assertEquals(MDOC_CONFIG_ID, mdocConfiguration.doctype)
//        assertEquals(MDOC_CONFIG_ID, mdocConfiguration.scope)
//    }
//
//    private fun assertSdJwtVcConfiguration(
//        credentialIssuerMetadata: CredentialIssuerMetadata,
//    ) {
//        val sdJwtConfiguration = assertNotNull(
//            credentialIssuerMetadata.credentialConfigurationsSupported[SD_JWT_INTERNAL_CONFIG_ID],
//            "Expected credential configuration for $SD_JWT_INTERNAL_CONFIG_ID",
//        )
//        assertEquals(CredentialFormat.SD_JWT_VC.value, sdJwtConfiguration.format.value)
//        assertEquals(
//            setOf(SigningAlgId.Jose("ES256")),
//            sdJwtConfiguration.credentialSigningAlgValuesSupported,
//        )
//        assertEquals(setOf(CryptographicBindingMethod.Jwk), sdJwtConfiguration.cryptographicBindingMethodsSupported)
//        assertEquals(SD_JWT_INTERNAL_CONFIG_ID, sdJwtConfiguration.scope)
//        assertEquals(INTERNAL_SD_JWT_VCT, sdJwtConfiguration.vct)
//        assertNotNull(sdJwtConfiguration.proofTypesSupported?.get("jwt"))
//    }
//
//    private fun ApplicationTestBuilder.installIssuer2WithConfigFiles() {
//        loadIssuer2ConfigFiles()
//        application {
//            install(ServerContentNegotiation) {
//                json(json)
//            }
//            runBlocking { issuer2AuthenticationPluginAmendment() }
//            AuthenticationServiceModule.run { enable() }
//            issuer2Module(withPlugins = true)
//        }
//    }
//
//    private fun ApplicationTestBuilder.apiClient() = createClient {
//        followRedirects = false
//        install(ClientContentNegotiation) {
//            json(json)
//        }
//    }
//
//    private fun loadIssuer2ConfigFiles() {
//        ConfigManager.preclear()
//        FeatureManager.preclear()
//        registerIssuer2ConfigDecoders()
//        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }
//
//        val configDir = issuer2ConfigDir()
//        configFiles.forEach { (id, type) ->
//            System.setProperty("config.file.$id", configDir.resolve("$id.conf").toString())
//            ConfigManager.registerConfig(id, type)
//        }
//        ConfigManager.loadConfigs()
//    }
//
//    private fun issuer2ConfigDir(): Path =
//        listOf(
//            Path.of("config"),
//            Path.of("waltid-services/waltid-issuer-api2/config"),
//            Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
//        )
//            .map { it.toAbsolutePath().normalize() }
//            .firstOrNull { Files.isRegularFile(it.resolve("issuer-service.conf")) }
//            ?: error("Could not locate waltid-issuer-api2 config directory")
//
//    private companion object {
//        const val ISSUER_AUTHORITY_BASE_URL = "http://localhost:7002"
//        const val OPENID4VCI_PREFIX = "/openid4vci"
//        const val ISSUER_BASE_URL = "$ISSUER_AUTHORITY_BASE_URL/openid4vci"
//        const val UNIVERSITY_DEGREE_CONFIG_ID = "UniversityDegree_jwt_vc_json"
//        const val MDOC_CONFIG_ID = "org.iso.18013.5.1.mDL"
//        const val SD_JWT_INTERNAL_CONFIG_ID = "identity_credential"
//        const val SD_JWT_EXTERNAL_CONFIG_ID = "my_custom_vct_dc+sd-jwt"
//        const val INTERNAL_SD_JWT_VCT = "$ISSUER_BASE_URL/$SD_JWT_INTERNAL_CONFIG_ID"
//        const val EXTERNAL_SD_JWT_VCT = "https://example.com/my_custom_vct"
//
//        val configFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
//            "issuer-service" to Issuer2ServiceConfig::class,
//            "authentication-service" to AuthenticationServiceConfig::class,
//            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
//            "issuer2-profiles" to Issuer2ProfilesConfig::class,
//        )
//    }
//}
