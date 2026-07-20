package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import id.walt.issuer2.issuer2Module
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationSigningAlgorithms
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.getSplitValues
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

class Issuer2MetadataEndpointTest {

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
    fun shouldHaveValidMetadataEndpointsFromConfigFiles() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val credentialIssuerMetadataRaw = client.get("/.well-known/openid-credential-issuer/openid4vci")
        assertEquals(HttpStatusCode.OK, credentialIssuerMetadataRaw.status)
        val credentialIssuerMetadata = credentialIssuerMetadataRaw.body<CredentialIssuerMetadata>()

        val authorizationServerMetadataRaw = client.get("/.well-known/oauth-authorization-server/openid4vci")
        assertEquals(HttpStatusCode.OK, authorizationServerMetadataRaw.status)
        val authorizationServerMetadata = authorizationServerMetadataRaw.body<AuthorizationServerMetadata>()
        val authorizationServerMetadataJson = client
            .get("/.well-known/oauth-authorization-server/openid4vci")
            .body<JsonObject>()

        val jwtVcIssuerMetadataRaw = client.get("/.well-known/jwt-vc-issuer/openid4vci")
        assertEquals(HttpStatusCode.OK, jwtVcIssuerMetadataRaw.status)
        val jwtVcIssuerMetadata = jwtVcIssuerMetadataRaw.body<JWTVCIssuerMetadata>()

        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/openid-credential-issuer/openid4vci").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/oauth-authorization-server/openid4vci").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/jwt-vc-issuer/openid4vci").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/vct/$SD_JWT_INTERNAL_CONFIG_ID").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/openid-credential-issuer").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/oauth-authorization-server").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/jwt-vc-issuer").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$OPENID4VCI_PREFIX/.well-known/vct").status)

        assertNull(credentialIssuerMetadata.authorizationServers)
        assertEquals(ISSUER_BASE_URL, credentialIssuerMetadata.credentialIssuer)
        assertEquals("$ISSUER_BASE_URL/credential", credentialIssuerMetadata.credentialEndpoint)
        assertEquals("$ISSUER_BASE_URL/nonce", credentialIssuerMetadata.nonceEndpoint)
        assertEquals(setOf("en-US", "de-DE"), credentialIssuerMetadata.display?.mapNotNull { it.locale }?.toSet())

        assertEquals(ISSUER_BASE_URL, authorizationServerMetadata.issuer)
        assertEquals("$ISSUER_BASE_URL/authorize", authorizationServerMetadata.authorizationEndpoint)
        assertEquals("$ISSUER_BASE_URL/token", authorizationServerMetadata.tokenEndpoint)
        assertEquals("$ISSUER_BASE_URL/jwks", authorizationServerMetadata.jwksUri)
        assertEquals("$ISSUER_BASE_URL/par", authorizationServerMetadata.pushedAuthorizationRequestEndpoint)
        assertEquals(false, authorizationServerMetadata.requirePushedAuthorizationRequests)
        assertNull(authorizationServerMetadata.codeChallengeMethodsSupported)
        assertEquals(setOf("attest_jwt_client_auth"), authorizationServerMetadata.tokenEndpointAuthMethodsSupported)
        assertNull(authorizationServerMetadata.tokenEndpointAuthSigningAlgValuesSupported)
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            authorizationServerMetadata.clientAttestationSigningAlgValuesSupported,
        )
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            authorizationServerMetadata.clientAttestationPopSigningAlgValuesSupported,
        )
        assertEquals(setOf("ES256"), authorizationServerMetadata.dpopSigningAlgValuesSupported)
        assertEquals(true, authorizationServerMetadata.preAuthorizedGrantAnonymousAccessSupported)
        assertEquals(
            setOf(
                GrantType.AuthorizationCode.value,
                GrantType.PreAuthorizedCode.value,
                GrantType.RefreshToken.value,
            ),
            authorizationServerMetadata.grantTypesSupported,
        )
        assertFalse("code_challenge_methods_supported" in authorizationServerMetadataJson)

        assertEquals(ISSUER_BASE_URL, jwtVcIssuerMetadata.issuer)
        assertEquals("$ISSUER_BASE_URL/jwks", jwtVcIssuerMetadata.jwksUri)
        assertNull(jwtVcIssuerMetadata.jwks)

        assertJwtVcJsonConfiguration(credentialIssuerMetadata)
        assertMdocConfiguration(credentialIssuerMetadata)
        assertSdJwtVcConfiguration(credentialIssuerMetadata)
        assertCredentialEncryptionMetadata(credentialIssuerMetadata)
        assertConfiguredCredentialScenariosAreAdvertised(credentialIssuerMetadata)
        assertSdJwtCatalogConfigurations(credentialIssuerMetadata)
        assertSelfHostedSdJwtVcTypeMetadata(client, credentialIssuerMetadata)
    }

    @Test
    fun shouldServeSignedCredentialIssuerMetadataForBothRegisteredMediaTypes() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val metadataPath = "/.well-known/openid-credential-issuer/openid4vci"
        val unsignedMetadata = client.get(metadataPath) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<JsonObject>()
        val signingKey = KeyManager
            .resolveSerializedKey(ConfigManager.getConfig<Issuer2ServiceConfig>().ciTokenKey)
            .getPublicKey()

        listOf(
            CredentialIssuerMetadataJwt.MEDIA_TYPE,
            CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE,
        ).forEach { mediaType ->
            val response = client.get(metadataPath) {
                header(HttpHeaders.Accept, mediaType)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                ContentType.parse(mediaType),
                response.contentType()?.withoutParameters(),
            )
            assertTrue(HttpHeaders.Accept in response.headers.getSplitValues(HttpHeaders.Vary).orEmpty())

            val compactJws = response.bodyAsText()
            val decoded = compactJws.decodeJws()
            assertEquals(signingKey.keyType.jwsAlg, decoded.header[JwtHeaderParams.ALGORITHM]?.jsonPrimitive?.content)
            assertEquals(CredentialIssuerMetadataJwt.TYPE, decoded.header[JwtHeaderParams.TYPE]?.jsonPrimitive?.content)
            assertEquals(signingKey.getKeyId(), decoded.header[JwtHeaderParams.KEY_ID]?.jsonPrimitive?.content)
            val publicJwk = assertNotNull(decoded.header[JwtHeaderParams.JSON_WEB_KEY]?.jsonObject)
            assertEquals(signingKey.getKeyId(), publicJwk["kid"]?.jsonPrimitive?.content)
            assertEquals(signingKey.keyType.jwsAlg, publicJwk["alg"]?.jsonPrimitive?.content)
            assertEquals("sig", publicJwk["use"]?.jsonPrimitive?.content)
            assertFalse("d" in publicJwk)
            assertEquals(ISSUER_BASE_URL, decoded.payload[JwtPayloadClaims.ISSUER]?.jsonPrimitive?.content)
            assertEquals(ISSUER_BASE_URL, decoded.payload[JwtPayloadClaims.SUBJECT]?.jsonPrimitive?.content)
            assertNotNull(decoded.payload[JwtPayloadClaims.ISSUED_AT]?.jsonPrimitive?.content?.toLongOrNull())
            assertFalse(JwtPayloadClaims.EXPIRATION in decoded.payload)
            unsignedMetadata.forEach { (name, value) ->
                assertEquals(value, decoded.payload[name], "Signed payload must preserve metadata parameter $name")
            }
            assertTrue(signingKey.verifyJws(compactJws).isSuccess)
        }
    }

    @Test
    fun shouldServeJwtVcIssuerMetadataFromSpecWellKnownPathOnly() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val authorizationServerMetadata = client
            .get(AUTHORIZATION_SERVER_METADATA_PATH)
            .body<AuthorizationServerMetadata>()
        val jwtVcIssuerMetadataRaw = client.get(JWT_VC_ISSUER_METADATA_PATH)
        assertEquals(HttpStatusCode.OK, jwtVcIssuerMetadataRaw.status)
        val jwtVcIssuerMetadata = jwtVcIssuerMetadataRaw.body<JWTVCIssuerMetadata>()

        assertEquals(authorizationServerMetadata.issuer, jwtVcIssuerMetadata.issuer)
        assertEquals(authorizationServerMetadata.jwksUri, jwtVcIssuerMetadata.jwksUri)
        assertNull(jwtVcIssuerMetadata.jwks)

        assertEquals(HttpStatusCode.NotFound, client.get(NESTED_JWT_VC_ISSUER_METADATA_PATH).status)
    }

    private fun assertConfiguredCredentialScenariosAreAdvertised(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        Issuer2CredentialScenarios.configured.forEach { scenario ->
            val configuration = assertNotNull(
                credentialIssuerMetadata.credentialConfigurationsSupported[scenario.credentialConfigurationId],
                "Expected metadata for configured issuer2 credential ${scenario.credentialConfigurationId}",
            )
            assertEquals(scenario.format, configuration.format.value)
        }
    }

    private fun assertCredentialEncryptionMetadata(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        val requestEncryption = assertNotNull(credentialIssuerMetadata.credentialRequestEncryption)
        assertEquals(CredentialEncryptionProfile.encValuesSupported, requestEncryption.encValuesSupported)
        assertFalse(requestEncryption.encryptionRequired)
        assertNull(requestEncryption.zipValuesSupported)

        val key = assertNotNull(requestEncryption.jwks["keys"]?.jsonArray?.singleOrNull()).jsonObject
        assertEquals(CredentialEncryptionProfile.KEY_TYPE_EC, key["kty"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.CURVE_P256, key["crv"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.ALG_ECDH_ES, key["alg"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.KEY_USE_ENC, key["use"]?.jsonPrimitive?.content)
        assertNotNull(key["kid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(key["x"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(key["y"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNull(key["d"])

        val responseEncryption = assertNotNull(credentialIssuerMetadata.credentialResponseEncryption)
        assertEquals(CredentialEncryptionProfile.responseAlgValuesSupported, responseEncryption.algValuesSupported)
        assertEquals(CredentialEncryptionProfile.encValuesSupported, responseEncryption.encValuesSupported)
        assertFalse(responseEncryption.encryptionRequired)
        assertNull(responseEncryption.zipValuesSupported)
    }

    private suspend fun assertSelfHostedSdJwtVcTypeMetadata(
        client: HttpClient,
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        SD_JWT_CATALOG_CONFIG_IDS.forEach { credentialConfigurationId ->
            val configuration = assertNotNull(
                credentialIssuerMetadata.credentialConfigurationsSupported[credentialConfigurationId],
                "Expected credential configuration for $credentialConfigurationId",
            )
            val publishedVct = assertNotNull(configuration.vct)

            val vctTypeMetadataRaw = client.get("/.well-known/vct/$credentialConfigurationId")
            assertEquals(HttpStatusCode.OK, vctTypeMetadataRaw.status)
            val vctTypeMetadata = vctTypeMetadataRaw.body<SdJwtVcTypeMetadataDraft04>()
            assertEquals(publishedVct, vctTypeMetadata.vct)
            assertEquals(credentialConfigurationId, vctTypeMetadata.name)
            assertEquals("$credentialConfigurationId Verifiable Credential", vctTypeMetadata.description)
        }
    }

    private fun assertSdJwtCatalogConfigurations(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        SD_JWT_CATALOG_CONFIG_IDS.forEach { credentialConfigurationId ->
            val configuration = assertNotNull(
                credentialIssuerMetadata.credentialConfigurationsSupported[credentialConfigurationId],
                "Expected credential configuration for $credentialConfigurationId",
            )
            assertEquals(CredentialFormat.SD_JWT_VC.value, configuration.format.value)
            assertEquals(
                setOf(SigningAlgId.Jose("ES256")),
                configuration.credentialSigningAlgValuesSupported,
            )
            assertEquals(JWT_PROOF_BINDING_METHODS, configuration.cryptographicBindingMethodsSupported)
            assertEquals(credentialConfigurationId, configuration.scope)
            assertEquals("$ISSUER_BASE_URL/$credentialConfigurationId", configuration.vct)
            assertNotNull(configuration.proofTypesSupported?.get("jwt"))
        }
    }

    private fun assertJwtVcJsonConfiguration(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        val openBadgeConfiguration = assertNotNull(
            credentialIssuerMetadata.credentialConfigurationsSupported[OPEN_BADGE_CONFIG_ID],
            "Expected credential configuration for $OPEN_BADGE_CONFIG_ID",
        )
        assertEquals(CredentialFormat.JWT_VC_JSON.value, openBadgeConfiguration.format.value)
        assertEquals(
            setOf(SigningAlgId.Jose("ES256")),
            openBadgeConfiguration.credentialSigningAlgValuesSupported,
        )
        assertEquals(
            JWT_PROOF_BINDING_METHODS,
            openBadgeConfiguration.cryptographicBindingMethodsSupported,
        )
        assertEquals(
            setOf("VerifiableCredential", "OpenBadgeCredential"),
            openBadgeConfiguration.credentialDefinition?.type?.toSet(),
        )
        assertNotNull(openBadgeConfiguration.proofTypesSupported?.get("jwt"))
    }

    private fun assertMdocConfiguration(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        MDOC_CATALOG_CONFIG_IDS.forEach { (credentialConfigurationId, doctype) ->
            val mdocConfiguration = assertNotNull(
                credentialIssuerMetadata.credentialConfigurationsSupported[credentialConfigurationId],
                "Expected credential configuration for $credentialConfigurationId",
            )
            assertEquals(CredentialFormat.MSO_MDOC.value, mdocConfiguration.format.value)
            assertEquals(
                setOf(SigningAlgId.CoseValue(-7), SigningAlgId.CoseValue(-9)),
                mdocConfiguration.credentialSigningAlgValuesSupported,
            )
            assertEquals(
                setOf(CryptographicBindingMethod.CoseKey),
                mdocConfiguration.cryptographicBindingMethodsSupported,
            )
            assertEquals(doctype, mdocConfiguration.doctype)
            assertEquals(credentialConfigurationId, mdocConfiguration.scope)
        }
    }

    private fun assertSdJwtVcConfiguration(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ) {
        val sdJwtConfiguration = assertNotNull(
            credentialIssuerMetadata.credentialConfigurationsSupported[SD_JWT_INTERNAL_CONFIG_ID],
            "Expected credential configuration for $SD_JWT_INTERNAL_CONFIG_ID",
        )
        assertEquals(CredentialFormat.SD_JWT_VC.value, sdJwtConfiguration.format.value)
        assertEquals(
            setOf(SigningAlgId.Jose("ES256")),
            sdJwtConfiguration.credentialSigningAlgValuesSupported,
        )
        assertEquals(JWT_PROOF_BINDING_METHODS, sdJwtConfiguration.cryptographicBindingMethodsSupported)
        assertEquals(SD_JWT_INTERNAL_CONFIG_ID, sdJwtConfiguration.scope)
        assertEquals(INTERNAL_SD_JWT_VCT, sdJwtConfiguration.vct)
        assertNotNull(sdJwtConfiguration.proofTypesSupported?.get("jwt"))
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

    private companion object {
        const val ISSUER_AUTHORITY_BASE_URL = "http://localhost:7002"
        const val OPENID4VCI_PREFIX = "/openid4vci"
        const val ISSUER_BASE_URL = "$ISSUER_AUTHORITY_BASE_URL/openid4vci"
        const val AUTHORIZATION_SERVER_METADATA_PATH = "/.well-known/oauth-authorization-server/openid4vci"
        const val JWT_VC_ISSUER_METADATA_PATH = "/.well-known/jwt-vc-issuer/openid4vci"
        const val NESTED_JWT_VC_ISSUER_METADATA_PATH = "$OPENID4VCI_PREFIX/.well-known/jwt-vc-issuer"
        const val OPEN_BADGE_CONFIG_ID = "OpenBadgeCredential_jwt_vc_json"
        const val SD_JWT_INTERNAL_CONFIG_ID = "identity_credential"
        const val INTERNAL_SD_JWT_VCT = "$ISSUER_BASE_URL/$SD_JWT_INTERNAL_CONFIG_ID"

        val MDOC_CATALOG_CONFIG_IDS = listOf(
            "org.iso.18013.5.1.mDL" to "org.iso.18013.5.1.mDL",
            "org.iso.18013.5.1.mDL.aamva" to "org.iso.18013.5.1.mDL",
            "org.iso.23220.photoid.1" to "org.iso.23220.photoid.1",
            "eu.europa.ec.eudi.pid.1" to "eu.europa.ec.eudi.pid.1",
            "eu.europa.ec.av.1" to "eu.europa.ec.av.1",
            "at.gv.id-austria.2023.iso" to "at.gv.id-austria.2023.iso",
            "com.google.wallet.idcard.1" to "com.google.wallet.idcard.1",
        )

        val SD_JWT_CATALOG_CONFIG_IDS = listOf(
            "asit.tax-id-credential",
            "urn:eu.europa.ec.eudi:cor:1",
            "urn:eu.europa.ec.eudi:por:1",
            "urn:eudi:ehic:1",
            "urn:eudi:pid:1",
            SD_JWT_INTERNAL_CONFIG_ID,
        )

        val JWT_PROOF_BINDING_METHODS = setOf(
            CryptographicBindingMethod.Jwk,
            CryptographicBindingMethod.DidKey,
            CryptographicBindingMethod.DidWeb,
            CryptographicBindingMethod.DidJwk,
            CryptographicBindingMethod.DidEbsi,
        )

        val configFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
            "issuer-service" to Issuer2ServiceConfig::class,
            "authentication-service" to AuthenticationServiceConfig::class,
            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
            "issuer2-profiles" to Issuer2ProfilesConfig::class,
        )
    }
}
