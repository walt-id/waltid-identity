package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertIsoMdlCredentialPayload
import id.walt.issuer2.testsupport.assertJwtVcJsonCredentialPayload
import id.walt.issuer2.testsupport.assertRefreshToken
import id.walt.issuer2.testsupport.assertSdJwtVcCredentialPayload
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.createIssuer2ClientAttestationTestMaterial
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.referencedOfferUri
import id.walt.openid4vci.offers.AuthenticationMethod
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2PreAuthorizedWalletFlowTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun walletCanCompletePreAuthorizedByReferenceOfferWithoutTxCode() = testApplication {
        val scenario = Issuer2CredentialScenarios.openBadgeCredential
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, createdOffer.authMethod)
        assertNull(createdOffer.issuerStateMode)
        assertNull(createdOffer.txCodeValue)
        val credentialOfferUri = createdOffer.referencedOfferUri()
        assertTrue(
            credentialOfferUri.contains("/openid4vci/credential-offer?id=${createdOffer.offerId}"),
            "Expected by-reference offer URI for session ${createdOffer.offerId}, got $credentialOfferUri",
        )
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertEquals(listOf(scenario.credentialConfigurationId), resolvedOffer.offer.credentialConfigurationIds)

        val preAuthorizedGrant = assertNotNull(resolvedOffer.offer.grants?.preAuthorizedCode)
        assertNull(preAuthorizedGrant.txCode)

        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertRefreshToken(tokenResponse)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

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
        assertJwtVcJsonCredentialPayload(credentialResponse.body<JsonObject>())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")

        val replay = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, replay.status, replay.bodyAsText())
        assertFalse("credentials" in replay.body<JsonObject>())
    }

    @Test
    fun walletCanCompletePreAuthorizedSdJwtVcOfferWithoutTxCode() = testApplication {
        val scenario = Issuer2CredentialScenarios.identitySdJwt
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, createdOffer.authMethod)
        assertNull(createdOffer.issuerStateMode)
        assertNull(createdOffer.txCodeValue)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertEquals(listOf(scenario.credentialConfigurationId), resolvedOffer.offer.credentialConfigurationIds)
        assertNull(assertNotNull(resolvedOffer.offer.grants?.preAuthorizedCode).txCode)

        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertRefreshToken(tokenResponse)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val credentialPayload = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            includeDidInProof = false,
        )
        assertSdJwtVcCredentialPayload(
            credentialPayload = credentialPayload,
            expectedVctSuffix = "/${scenario.credentialConfigurationId}",
            expectedDisclosureKeys = setOf("birthdate"),
            expectedClaims = mapOf(
                "family_name" to "Doe",
                "given_name" to "John",
            ),
        )
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
    }

    @Test
    fun rejectedProofKeyReturnsNoCredentialAndLeavesSessionOpen() = testApplication {
        var acceptedPublicJwk: JsonObject? = null
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, publicJwk ->
                acceptedPublicJwk = publicJwk
                false
            }
        )
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.identitySdJwt,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata, includeDidInProof = false)

        val response = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        val responseBody = response.body<JsonObject>()
        assertEquals("invalid_proof", responseBody["error"]?.jsonPrimitive?.content)
        assertFalse("credentials" in responseBody)
        val publicJwk = assertNotNull(acceptedPublicJwk)
        assertTrue(setOf("d", "p", "q", "dp", "dq", "qi", "oth").none { it in publicJwk })
        val session = client.getSession(createdOffer.offerId)
        assertEquals(IssuanceSessionStatus.ACTIVE, session.status)
        assertFalse(session.isClosed)
    }

    @Test
    fun concurrentCredentialRequestsClaimSessionOnce() = testApplication {
        val acceptanceStarted = CompletableDeferred<Unit>()
        val releaseAcceptance = CompletableDeferred<Unit>()
        val acceptanceCalls = AtomicInteger()
        installIssuer2WithConfigFiles(
            credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
                if (acceptanceCalls.incrementAndGet() == 1) {
                    acceptanceStarted.complete(Unit)
                    releaseAcceptance.await()
                }
                true
            }
        )
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.identitySdJwt,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val requestBody = credentialRequest(
            credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
            proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata, includeDidInProof = false),
        )

        suspend fun requestCredential() = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val responses = coroutineScope {
            val first = async { requestCredential() }
            acceptanceStarted.await()
            val second = try {
                requestCredential()
            } finally {
                releaseAcceptance.complete(Unit)
            }
            listOf(first.await(), second)
        }

        val successful = responses.single { it.status == HttpStatusCode.OK }
        assertSdJwtVcCredentialPayload(
            credentialPayload = successful.body(),
            expectedVctSuffix = "/${Issuer2CredentialScenarios.identitySdJwt.credentialConfigurationId}",
            expectedDisclosureKeys = setOf("birthdate"),
            expectedClaims = mapOf(
                "family_name" to "Doe",
                "given_name" to "John",
            ),
        )
        val rejected = responses.single { it.status == HttpStatusCode.BadRequest }
        assertFalse("credentials" in rejected.body<JsonObject>())
        assertEquals(1, acceptanceCalls.get())
        val session = client.getSession(createdOffer.offerId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertTrue(session.isClosed)
    }

    @Test
    fun walletCanRefreshClientAuthenticatedPreAuthorizedAccessToken() = testApplication {
        val scenario = Issuer2CredentialScenarios.identitySdJwt
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        installIssuer2WithConfigFiles { serviceConfig ->
            serviceConfig.copy(
                clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            )
        }
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(
            client = client,
            attestationAssembler = clientAttestation.attestationAssembler,
        )

        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        val refreshToken = assertRefreshToken(tokenResponse)

        val refreshedTokenResponse = walletFlow.refreshAccessToken(
            resolvedOffer = resolvedOffer,
            refreshToken = refreshToken,
        )

        assertBearerAccessToken(refreshedTokenResponse)
        val rotatedRefreshToken = assertRefreshToken(refreshedTokenResponse)
        assertNotEquals(refreshToken, rotatedRefreshToken)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")
    }

    @Test
    fun walletCanCompletePreAuthorizedMdocOfferWithoutTxCode() = testApplication {
        val scenario = Issuer2CredentialScenarios.isoMdl
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, createdOffer.authMethod)
        assertNull(createdOffer.issuerStateMode)
        assertNull(createdOffer.txCodeValue)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertEquals(listOf(scenario.credentialConfigurationId), resolvedOffer.offer.credentialConfigurationIds)
        assertNull(assertNotNull(resolvedOffer.offer.grants?.preAuthorizedCode).txCode)

        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertRefreshToken(tokenResponse)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val credentialPayload = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            includeDidInProof = false,
        )
        assertIsoMdlCredentialPayload(credentialPayload)
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
    }
}
