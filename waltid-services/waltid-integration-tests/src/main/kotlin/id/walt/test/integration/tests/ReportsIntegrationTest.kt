@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.test.integration.loadJsonResource
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.klogging.Klogging
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

private val jwtCredential = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

private val simplePresentationRequestPayload =
    loadResource("presentation/openbadgecredential-presentation-request.json")

@TestMethodOrder(OrderAnnotation::class)
class ReportsIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var credentialId: String? = null
    }

    @Order(0)
    @Test
    fun shouldGetFrequentCredentialsWhenEmpty() = runTest {
        val frequentCredentials = defaultWalletApi.getFrequentCredentials()
        assertNotNull(frequentCredentials)
        logger.info("Frequent credentials (empty wallet): ${frequentCredentials.size}")
    }

    @Order(1)
    @Test
    fun shouldIssueAndClaimCredential() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(jwtCredential)
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl)
        assertNotNull(claimedCredentials)
        assertTrue(claimedCredentials.isNotEmpty())
        credentialId = claimedCredentials[0].id
    }

    @Order(2)
    @Test
    fun shouldUseCredentialInPresentation() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        
        val verificationUrl = verifierApi.verify(simplePresentationRequestPayload)
        val verificationId = Url(verificationUrl).parameters.getOrFail("state")
        
        val resolvedPresentationOfferString = defaultWalletApi.resolvePresentationRequest(verificationUrl)
        val presentationDefinition = Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")
        
        val matchedCredentials = defaultWalletApi.matchCredentialsForPresentationDefinition(presentationDefinition)
        assertTrue(matchedCredentials.isNotEmpty(), "Should have matching credentials")
        
        val defaultDidString = defaultWalletApi.getDefaultDid().did
        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(defaultDidString, resolvedPresentationOfferString, listOf(credentialId!!))
        )
        
        logger.info("Credential used in presentation")
    }

    @Order(3)
    @Test
    fun shouldGetFrequentCredentialsAfterUsage() = runTest {
        val frequentCredentials = defaultWalletApi.getFrequentCredentials()
        assertNotNull(frequentCredentials)
        logger.info("Frequent credentials after usage: ${frequentCredentials.size}")
    }

    @Order(4)
    @Test
    fun shouldRespectLimitParameter() = runTest {
        val frequentCredentials = defaultWalletApi.getFrequentCredentials(limit = 1)
        assertNotNull(frequentCredentials)
        assertTrue(frequentCredentials.size <= 1, "Should respect limit parameter")
    }
}
