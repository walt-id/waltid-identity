@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.test.integration.loadJsonResource
import id.walt.webwallet.service.credentials.CredentialFilterObject
import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

private const val testOrderOfferUrlErrorMessage = "The offer URL should be set - test order?"
private const val testOrderCredentialIdErrorMessage = "Credential ID should be set - test order?"

private val jwtCredential = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

@TestMethodOrder(OrderAnnotation::class)
class CredentialRejectIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var offerUrl: String? = null
        var credentialId: String? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredentialForRejection() = runTest {
        offerUrl = issuerApi.issueJwtCredential(jwtCredential)
        assertNotNull(offerUrl)
    }

    @Order(1)
    @Test
    fun shouldClaimCredentialWithRequireUserInput() = runTest {
        assertNotNull(offerUrl, testOrderOfferUrlErrorMessage)
        val claimedCredentials = defaultWalletApi.claimCredential(
            offerUrl = offerUrl!!,
            requireUserInput = true
        )
        assertNotNull(claimedCredentials).also {
            assertEquals(1, it.size)
            credentialId = it[0].id
        }
    }

    @Order(2)
    @Test
    fun shouldShowCredentialAsPending() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val pendingCredentials = defaultWalletApi.listCredentials(
            CredentialFilterObject.default.copy(showPending = true)
        )
        assertTrue(
            pendingCredentials.any { it.id == credentialId },
            "Credential should be in pending list"
        )
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNotNull(credential.pending, "Credential should have pending flag set")
    }

    @Order(3)
    @Test
    fun shouldRejectCredentialWithNote() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val rejectionNote = "Test rejection note - credential not needed"
        defaultWalletApi.rejectCredential(credentialId!!, rejectionNote)
        logger.info("Credential '$credentialId' rejected with note: $rejectionNote")
    }

    @Order(4)
    @Test
    fun shouldNotShowRejectedCredentialInDefaultList() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val credentials = defaultWalletApi.listCredentials()
        assertTrue(
            credentials.none { it.id == credentialId },
            "Rejected credential should not appear in default credential list"
        )
    }

    @Order(5)
    @Test
    fun shouldShowRejectedCredentialInDeletedList() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val deletedCredentials = defaultWalletApi.listCredentials(
            CredentialFilterObject.default.copy(showDeleted = true)
        )
        assertTrue(
            deletedCredentials.any { it.id == credentialId },
            "Rejected credential should appear in deleted list"
        )
        val credential = deletedCredentials.first { it.id == credentialId }
        assertNotNull(credential.deletedOn, "Rejected credential should have deletedOn set")
    }

    @Order(6)
    @Test
    fun shouldRestoreRejectedCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.restoreCredential(credentialId!!)
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNull(credential.deletedOn, "Restored credential should not have deletedOn")
    }

    @Order(7)
    @Test
    fun shouldRejectCredentialWithoutNote() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.rejectCredential(credentialId!!)
        logger.info("Credential '$credentialId' rejected without note")
        val deletedCredentials = defaultWalletApi.listCredentials(
            CredentialFilterObject.default.copy(showDeleted = true)
        )
        assertTrue(
            deletedCredentials.any { it.id == credentialId },
            "Rejected credential should appear in deleted list"
        )
    }

    @Order(8)
    @Test
    fun shouldPermanentlyDeleteRejectedCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.deleteCredential(credentialId!!, permanent = true)
        val allCredentials = defaultWalletApi.listCredentials(
            CredentialFilterObject.default.copy(showDeleted = true)
        )
        assertTrue(
            allCredentials.none { it.id == credentialId },
            "Permanently deleted credential should not appear anywhere"
        )
    }
}
