@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.test.integration.loadJsonResource
import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
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

@TestMethodOrder(OrderAnnotation::class)
class SilentExchangeIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var offerUrl: String? = null
        var defaultDid: String? = null
        var initialCredentialCount: Int = 0
    }

    @Order(0)
    @Test
    fun shouldGetDefaultDid() = runTest {
        val did = defaultWalletApi.getDefaultDid()
        assertNotNull(did)
        defaultDid = did.did
        logger.info("Default DID: $defaultDid")
    }

    @Order(1)
    @Test
    fun shouldCountInitialCredentials() = runTest {
        val credentials = defaultWalletApi.listCredentials()
        initialCredentialCount = credentials.size
        logger.info("Initial credential count: $initialCredentialCount")
    }

    @Order(2)
    @Test
    fun shouldIssueCredentialForSilentClaim() = runTest {
        offerUrl = issuerApi.issueJwtCredential(jwtCredential)
        assertNotNull(offerUrl)
        logger.info("Credential offer URL created")
    }

    @Order(3)
    @Test
    fun shouldSilentlyClaimCredential() = runTest {
        assertNotNull(offerUrl, "Offer URL should be set - test order?")
        assertNotNull(defaultDid, "Default DID should be set - test order?")
        
        val claimedCount = defaultWalletApi.silentClaim(defaultDid!!, offerUrl!!)
        assertEquals(1, claimedCount, "Should have silently claimed 1 credential")
        logger.info("Silently claimed $claimedCount credential(s)")
    }

    @Order(4)
    @Test
    fun shouldVerifyCredentialWasClaimed() = runTest {
        val credentials = defaultWalletApi.listCredentials()
        assertEquals(
            initialCredentialCount + 1,
            credentials.size,
            "Should have one more credential after silent claim"
        )
    }

    @Order(5)
    @Test
    fun shouldSilentlyClaimMultipleCredentials() = runTest {
        assertNotNull(defaultDid, "Default DID should be set - test order?")
        
        val offer1 = issuerApi.issueJwtCredential(jwtCredential)
        val offer2 = issuerApi.issueJwtCredential(jwtCredential)
        
        val claimed1 = defaultWalletApi.silentClaim(defaultDid!!, offer1)
        val claimed2 = defaultWalletApi.silentClaim(defaultDid!!, offer2)
        
        assertEquals(1, claimed1, "First silent claim should return 1")
        assertEquals(1, claimed2, "Second silent claim should return 1")
        
        val credentials = defaultWalletApi.listCredentials()
        assertTrue(
            credentials.size >= initialCredentialCount + 3,
            "Should have at least 3 more credentials after all silent claims"
        )
    }
}
