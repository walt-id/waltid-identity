package id.walt.issuer2.openid4vci

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.openid4vci.handlers.credential.JwtUtils
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.sdjwt.SDJwt
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration tests verifying that credential status is correctly embedded in issued credentials.
 * Tests cover JWT VC, SD-JWT VC, and mDoc credential formats.
 */
class Issuer2CredentialStatusIntegrationTest {

    companion object {
        const val JWT_VC_PROFILE_ID = "universityDegree"
        const val SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
        const val MDOC_PROFILE_ID = "isoPhotoId"
    }

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    // ==================== JWT VC Tests ====================

    @Test
    fun issuedJwtVcCredentialShouldContainCredentialStatusWhenProvidedViaRuntimeOverride() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val credentialStatus = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#94567")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "94567")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val offerRequest = CredentialOfferCreateRequest(
            profileId = JWT_VC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialStatus = credentialStatus
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")

        val responseBody = credentialResponse.body<JsonObject>()
        val jwtCredential = extractJwtCredential(responseBody)
        val vcPayload = decodeJwtPayload(jwtCredential)

        val embeddedStatus = vcPayload["vc"]?.jsonObject?.get("credentialStatus")?.jsonObject
        assertNotNull(embeddedStatus, "Credential should contain credentialStatus")
        assertEquals("BitstringStatusListEntry", embeddedStatus["type"]?.jsonPrimitive?.content)
        assertEquals("revocation", embeddedStatus["statusPurpose"]?.jsonPrimitive?.content)
        assertEquals("94567", embeddedStatus["statusListIndex"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example.com/status/1", embeddedStatus["statusListCredential"]?.jsonPrimitive?.content)
    }

    @Test
    fun issuedJwtVcCredentialShouldContainStatusList2021EntryWhenProvided() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val credentialStatus = buildJsonObject {
            put("id", "https://issuer.example.com/status/2#500")
            put("type", "StatusList2021Entry")
            put("statusPurpose", "suspension")
            put("statusListIndex", "500")
            put("statusListCredential", "https://issuer.example.com/status/2")
        }

        val offerRequest = CredentialOfferCreateRequest(
            profileId = JWT_VC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialStatus = credentialStatus
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())

        val responseBody = credentialResponse.body<JsonObject>()
        val jwtCredential = extractJwtCredential(responseBody)
        val vcPayload = decodeJwtPayload(jwtCredential)

        val embeddedStatus = vcPayload["vc"]?.jsonObject?.get("credentialStatus")?.jsonObject
        assertNotNull(embeddedStatus, "Credential should contain credentialStatus")
        assertEquals("StatusList2021Entry", embeddedStatus["type"]?.jsonPrimitive?.content)
        assertEquals("suspension", embeddedStatus["statusPurpose"]?.jsonPrimitive?.content)
    }

    @Test
    fun issuedJwtVcCredentialShouldNotContainCredentialStatusWhenNotProvided() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val offerRequest = CredentialOfferCreateRequest(
            profileId = JWT_VC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())

        val responseBody = credentialResponse.body<JsonObject>()
        val jwtCredential = extractJwtCredential(responseBody)
        val vcPayload = decodeJwtPayload(jwtCredential)

        val embeddedStatus = vcPayload["vc"]?.jsonObject?.get("credentialStatus")
        assertNull(embeddedStatus, "Credential should not contain credentialStatus when not configured")
    }

    @Test
    fun credentialStatusFromRuntimeOverrideShouldTakePrecedenceOverProfileDefault() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val overrideStatus = buildJsonObject {
            put("id", "https://override.example.com/status/override#999")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "suspension")
            put("statusListIndex", "999")
            put("statusListCredential", "https://override.example.com/status/override")
        }

        val offerRequest = CredentialOfferCreateRequest(
            profileId = JWT_VC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialStatus = overrideStatus
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status)

        val responseBody = credentialResponse.body<JsonObject>()
        val jwtCredential = extractJwtCredential(responseBody)
        val vcPayload = decodeJwtPayload(jwtCredential)

        val embeddedStatus = vcPayload["vc"]?.jsonObject?.get("credentialStatus")?.jsonObject
        assertNotNull(embeddedStatus)
        assertEquals("999", embeddedStatus["statusListIndex"]?.jsonPrimitive?.content)
        assertEquals("suspension", embeddedStatus["statusPurpose"]?.jsonPrimitive?.content)
        assertEquals("https://override.example.com/status/override", embeddedStatus["statusListCredential"]?.jsonPrimitive?.content)
    }

    // ==================== SD-JWT VC Tests ====================

    @Test
    fun issuedSdJwtVcCredentialShouldContainStatusClaimWhenProvided() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val credentialStatus = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 94567)
                put("uri", "https://issuer.example.com/status/sdjwt/1")
            }
        }

        val offerRequest = CredentialOfferCreateRequest(
            profileId = SD_JWT_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialStatus = credentialStatus
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")

        val responseBody = credentialResponse.body<JsonObject>()
        val sdJwtCredential = extractJwtCredential(responseBody)
        val sdJwtPayload = decodeSdJwtPayload(sdJwtCredential)

        val embeddedStatus = sdJwtPayload["status"]?.jsonObject?.get("status_list")?.jsonObject
        assertNotNull(embeddedStatus, "SD-JWT VC should contain status.status_list claim")
        assertEquals(94567L, embeddedStatus["idx"]?.jsonPrimitive?.long)
        assertEquals("https://issuer.example.com/status/sdjwt/1", embeddedStatus["uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun issuedSdJwtVcCredentialShouldNotContainStatusWhenNotProvided() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val offerRequest = CredentialOfferCreateRequest(
            profileId = SD_JWT_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())

        val responseBody = credentialResponse.body<JsonObject>()
        val sdJwtCredential = extractJwtCredential(responseBody)
        val sdJwtPayload = decodeSdJwtPayload(sdJwtCredential)

        val embeddedStatus = sdJwtPayload["status"]
        assertNull(embeddedStatus, "SD-JWT VC should not contain status claim when not configured")
    }

    // ==================== mDoc Tests ====================

    @Test
    fun issuedMdocCredentialShouldContainStatusWhenProvided() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val credentialStatus = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 12345)
                put("uri", "https://issuer.example.com/status/mdoc/1")
            }
        }

        val offerRequest = CredentialOfferCreateRequest(
            profileId = MDOC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialData = buildJsonObject {
                    putJsonObject("org.iso.23220.photoid.1") {
                        put("age_over_18", true)
                        put("issuing_country", "AT")
                        put("given_name_unicode", "Jane")
                        put("family_name_unicode", "Doe")
                        put("birth_date", "2003-12-21")
                        put("issuance_date", "2025-12-13")
                        put("issuing_authority_unicode", "Walt.id Issuer")
                        put("expiry_date", "2026-12-13")
                        put("portrait", "AQIDBAUGBwgJCgsMDQ4P")
                    }
                },
                credentialStatus = credentialStatus
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
    }

    @Test
    fun issuedMdocCredentialShouldSucceedWithoutStatus() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val offerRequest = CredentialOfferCreateRequest(
            profileId = MDOC_PROFILE_ID,
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                credentialData = buildJsonObject {
                    putJsonObject("org.iso.23220.photoid.1") {
                        put("age_over_18", true)
                        put("issuing_country", "AT")
                        put("given_name_unicode", "Jane")
                        put("family_name_unicode", "Doe")
                        put("birth_date", "2003-12-21")
                        put("issuance_date", "2025-12-13")
                        put("issuing_authority_unicode", "Walt.id Issuer")
                        put("expiry_date", "2026-12-13")
                        put("portrait", "AQIDBAUGBwgJCgsMDQ4P")
                    }
                }
            )
        )

        val createdOffer = client.createCredentialOffer(offerRequest)
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
    }

    // ==================== Helper Functions ====================

    private fun extractJwtCredential(responseBody: JsonObject): String {
        return responseBody["credentials"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("credential")
            ?.jsonPrimitive
            ?.content
            ?: error("Could not extract credential from response")
    }

    private fun decodeJwtPayload(jwt: String): JsonObject = JwtUtils.parseJWTPayload(jwt)

    private fun decodeSdJwtPayload(sdJwt: String): JsonObject = SDJwt.parse(sdJwt).fullPayload
}
