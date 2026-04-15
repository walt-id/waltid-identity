package id.walt.test.integration.tests.issuer2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.resolver.LocalResolver
import id.walt.issuer2.OSSIssuer2FeatureCatalog
import id.walt.issuer2.ProfileDetails
import id.walt.issuer2.ProfileSummary
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.CredentialProfilesConfig
import id.walt.issuer2.config.OSSIssuer2ServiceConfig
import id.walt.issuer2.issuerModule
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the waltid-issuer-api2 service.
 * 
 * These tests verify the OpenID4VCI 1.0 implementation including:
 * - Metadata endpoints (.well-known)
 * - Profile management
 * - Pre-authorized code flow
 * - Authorization code flow
 */
@TestMethodOrder(OrderAnnotation::class)
class Issuer2IntegrationTest {

    companion object {
        private const val TEST_HOST = "127.0.0.1"
        private const val TEST_PORT = 17051
        private const val BASE_URL = "http://$TEST_HOST:$TEST_PORT"

        private const val SD_JWT_CREDENTIAL_CONFIG_ID = "identity_credential"
        private const val TEST_PROFILE_ID = "test-identity"

        private val holderKey: Key = runBlocking {
            KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
        }

        private val holderDid: String = runBlocking {
            DidKeyRegistrar().registerByKey(holderKey, DidKeyCreateOptions()).did
        }

        private val testIssuerKey: Key = runBlocking {
            KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
        }

        private val testIssuerKeyJson: JsonObject = runBlocking {
            val jwk = testIssuerKey.exportJWKObject()
            buildJsonObject {
                put("type", "jwk")
                put("jwk", jwk)
            }
        }

        private val testIssuerDid: String = runBlocking {
            DidKeyRegistrar().registerByKey(testIssuerKey, DidKeyCreateOptions()).did
        }

        private fun createTestProfilesConfig(baseUrl: String): CredentialProfilesConfig {
            return CredentialProfilesConfig(
                profiles = listOf(
                    CredentialProfileConfig(
                        profileId = TEST_PROFILE_ID,
                        name = "Test Identity Credential",
                        credentialConfigurationId = SD_JWT_CREDENTIAL_CONFIG_ID,
                        issuerKey = testIssuerKeyJson,
                        issuerDid = testIssuerDid,
                        credentialData = Json.parseToJsonElement("""
                            {
                                "given_name": "John",
                                "family_name": "Doe",
                                "birthdate": "1990-01-15"
                            }
                        """.trimIndent()).jsonObject,
                    )
                ),
                credentialConfigurations = mapOf(
                    SD_JWT_CREDENTIAL_CONFIG_ID to CredentialConfiguration(
                        format = CredentialFormat.SD_JWT_VC,
                        scope = SD_JWT_CREDENTIAL_CONFIG_ID,
                        vct = "$baseUrl/identity_credential",
                    )
                )
            )
        }
    }

    @Order(0)
    @Test
    fun testMetadataEndpoints() {
        E2ETest(TEST_HOST, TEST_PORT, true).testBlock(
            features = listOf(OSSIssuer2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("issuer-service", OSSIssuer2ServiceConfig(
                    baseUrl = BASE_URL,
                    tokenKey = testIssuerKeyJson
                ))
                ConfigManager.preloadConfig("profiles", createTestProfilesConfig(BASE_URL))
            },
            init = {
                DidService.apply {
                    minimalInit()
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::issuerModule
        ) {
            val http = testHttpClient()

            // Test Credential Issuer Metadata
            val credentialIssuerMetadata = testAndReturn("Get Credential Issuer Metadata") {
                http.get("/.well-known/openid-credential-issuer").body<CredentialIssuerMetadata>()
            }

            test("Verify Credential Issuer Metadata") {
                assertEquals(BASE_URL, credentialIssuerMetadata.credentialIssuer)
                assertEquals("$BASE_URL/credential", credentialIssuerMetadata.credentialEndpoint)
                assertNotNull(credentialIssuerMetadata.credentialConfigurationsSupported)
                assertTrue(credentialIssuerMetadata.credentialConfigurationsSupported.containsKey(SD_JWT_CREDENTIAL_CONFIG_ID))
            }

            // Test Authorization Server Metadata
            val authServerMetadata = testAndReturn("Get Authorization Server Metadata") {
                http.get("/.well-known/oauth-authorization-server").body<AuthorizationServerMetadata>()
            }

            test("Verify Authorization Server Metadata") {
                assertEquals(BASE_URL, authServerMetadata.issuer)
                assertNotNull(authServerMetadata.tokenEndpoint)
                assertNotNull(authServerMetadata.authorizationEndpoint)
                assertTrue(authServerMetadata.grantTypesSupported?.contains("urn:ietf:params:oauth:grant-type:pre-authorized_code") == true)
            }

            // Test OpenID Configuration
            val openIdConfig = testAndReturn("Get OpenID Configuration") {
                http.get("/.well-known/openid-configuration").body<AuthorizationServerMetadata>()
            }

            test("Verify OpenID Configuration matches Auth Server Metadata") {
                assertEquals(authServerMetadata.issuer, openIdConfig.issuer)
                assertEquals(authServerMetadata.tokenEndpoint, openIdConfig.tokenEndpoint)
            }
        }
    }

    @Order(1)
    @Test
    fun testListProfiles() {
        val testPort = TEST_PORT + 1
        val testBaseUrl = "http://$TEST_HOST:$testPort"

        E2ETest(TEST_HOST, testPort, true).testBlock(
            features = listOf(OSSIssuer2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("issuer-service", OSSIssuer2ServiceConfig(
                    baseUrl = testBaseUrl,
                    tokenKey = testIssuerKeyJson
                ))
                ConfigManager.preloadConfig("profiles", createTestProfilesConfig(testBaseUrl))
            },
            init = {
                DidService.apply {
                    minimalInit()
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::issuerModule
        ) {
            val http = testHttpClient()

            // List profiles
            val profiles = testAndReturn("List Profiles") {
                http.get("/profiles").body<List<ProfileSummary>>()
            }

            test("Verify profiles list") {
                assertEquals(1, profiles.size)
                assertEquals(TEST_PROFILE_ID, profiles[0].profileId)
                assertEquals("Test Identity Credential", profiles[0].name)
                assertEquals(SD_JWT_CREDENTIAL_CONFIG_ID, profiles[0].credentialConfigurationId)
            }

            // Get specific profile
            val profileDetails = testAndReturn("Get Profile Details") {
                http.get("/profiles/$TEST_PROFILE_ID").body<ProfileDetails>()
            }

            test("Verify profile details") {
                assertEquals(TEST_PROFILE_ID, profileDetails.profileId)
                assertEquals(testIssuerDid, profileDetails.issuerDid)
                assertNotNull(profileDetails.credentialConfiguration)
                assertEquals(CredentialFormat.SD_JWT_VC, profileDetails.credentialConfiguration?.format)
            }
        }
    }

    @Order(2)
    @Test
    fun testPreAuthorizedCodeFlow() {
        val testPort = TEST_PORT + 2
        val testBaseUrl = "http://$TEST_HOST:$testPort"

        E2ETest(TEST_HOST, testPort, true).testBlock(
            features = listOf(OSSIssuer2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("issuer-service", OSSIssuer2ServiceConfig(
                    baseUrl = testBaseUrl,
                    tokenKey = testIssuerKeyJson
                ))
                ConfigManager.preloadConfig("profiles", createTestProfilesConfig(testBaseUrl))
            },
            init = {
                DidService.apply {
                    minimalInit()
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::issuerModule
        ) {
            val http = testHttpClient()

            // Create credential offer with pre-authorized code
            val offerRequest = buildJsonObject {
                put("authMethod", "PRE_AUTHORIZED")
                put("expiresInSeconds", 300)
            }

            val offerResponse = testAndReturn("Create Pre-Authorized Credential Offer") {
                http.post("/profiles/$TEST_PROFILE_ID/offers") {
                    setBody(offerRequest)
                }.body<JsonObject>()
            }

            test("Verify offer response") {
                assertNotNull(offerResponse["sessionId"])
                assertNotNull(offerResponse["credentialOfferUri"])
            }

            val sessionId = offerResponse["sessionId"]?.jsonPrimitive?.content!!

            // Resolve credential offer
            val credentialOffer = testAndReturn("Resolve Credential Offer") {
                http.get("/credential-offer?id=$sessionId").body<CredentialOffer>()
            }

            test("Verify credential offer") {
                assertEquals(testBaseUrl, credentialOffer.credentialIssuer)
                assertNotNull(credentialOffer.grants?.preAuthorizedCode)
                assertTrue(credentialOffer.credentialConfigurationIds.contains(SD_JWT_CREDENTIAL_CONFIG_ID))
            }

            val preAuthCode = credentialOffer.grants?.preAuthorizedCode?.preAuthorizedCode!!

            // Exchange pre-authorized code for token
            val tokenResponse = testAndReturn("Exchange Pre-Auth Code for Token") {
                val tokenReq = TokenRequest.PreAuthorizedCode(preAuthorizedCode = preAuthCode)
                http.submitForm(
                    "/token",
                    parametersOf(tokenReq.toHttpParameters())
                ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
            }

            test("Verify token response") {
                assertNotNull(tokenResponse.accessToken)
            }

            val accessToken = tokenResponse.accessToken!!

            // Get nonce for credential request
            val nonceResponse = testAndReturn("Get Nonce") {
                http.post("/nonce").body<JsonObject>()
            }

            val nonce = tokenResponse.cNonce ?: nonceResponse["c_nonce"]?.jsonPrimitive?.content!!

            // Build proof of possession
            val proof = runBlocking {
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    keyId = holderKey.getKeyId(),
                    keyJwk = holderKey.getPublicKey().exportJWKObject(),
                ).build(holderKey)
            }

            // Request credential
            val credentialRequest = buildJsonObject {
                put("credential_configuration_id", SD_JWT_CREDENTIAL_CONFIG_ID)
                putJsonObject("proofs") {
                    putJsonArray("jwt") {
                        add(proof.jwt!!)
                    }
                }
            }

            val credentialResponse = testAndReturn("Request Credential") {
                http.post("/credential") {
                    bearerAuth(accessToken)
                    setBody(credentialRequest)
                }.body<JsonObject>()
            }

            test("Verify credential response") {
                val credentials = credentialResponse["credentials"]?.jsonArray
                assertNotNull(credentials)
                assertTrue(credentials.isNotEmpty())

                val credential = credentials.first().jsonObject["credential"]?.jsonPrimitive?.content
                assertNotNull(credential)
                assertTrue(credential.contains("~"), "SD-JWT should contain disclosures separator")
            }
        }
    }
}
