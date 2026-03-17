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
class HistoryEventLogIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var offerUrl: String? = null
        var credentialId: String? = null
        var eventId: Int? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredentialToGenerateEvents() = runTest {
        offerUrl = issuerApi.issueJwtCredential(jwtCredential)
        assertNotNull(offerUrl)
    }

    @Order(1)
    @Test
    fun shouldClaimCredentialToGenerateEvents() = runTest {
        assertNotNull(offerUrl, "Offer URL should be set - test order?")
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl!!)
        assertNotNull(claimedCredentials).also {
            assertTrue(it.isNotEmpty(), "Should have claimed at least one credential")
            credentialId = it[0].id
        }
    }

    @Order(2)
    @Test
    fun shouldListEventLogs() = runTest {
        val eventLogs = defaultWalletApi.listEventLogs()
        assertNotNull(eventLogs)
        assertTrue(eventLogs.items.isNotEmpty(), "Should have at least one event log after claiming credential")
        logger.info("Found ${eventLogs.count} event logs")
        eventId = eventLogs.items.firstOrNull()?.id
    }

    @Order(3)
    @Test
    fun shouldListEventLogsWithLimit() = runTest {
        val eventLogs = defaultWalletApi.listEventLogs(limit = 1)
        assertNotNull(eventLogs)
        assertTrue(eventLogs.items.size <= 1, "Should respect limit parameter")
    }

    @Order(4)
    @Test
    fun shouldListEventLogsWithSorting() = runTest {
        val eventLogsAsc = defaultWalletApi.listEventLogs(sortOrder = "ASC")
        val eventLogsDesc = defaultWalletApi.listEventLogs(sortOrder = "DESC")
        assertNotNull(eventLogsAsc)
        assertNotNull(eventLogsDesc)
        if (eventLogsAsc.items.size > 1 && eventLogsDesc.items.size > 1) {
            assertTrue(
                eventLogsAsc.items.first().id != eventLogsDesc.items.first().id ||
                        eventLogsAsc.items.size == 1,
                "Sorting should affect order when there are multiple events"
            )
        }
    }

    @Order(5)
    @Test
    fun shouldListHistory() = runTest {
        val history = defaultWalletApi.listHistory()
        assertNotNull(history)
        logger.info("Found ${history.size} history entries")
    }

    @Order(6)
    @Test
    fun shouldDeleteEventLog() = runTest {
        assertNotNull(eventId, "Event ID should be set - test order?")
        defaultWalletApi.deleteEventLog(eventId!!)
        logger.info("Deleted event log with ID: $eventId")
    }

    @Order(7)
    @Test
    fun shouldVerifyEventLogDeleted() = runTest {
        assertNotNull(eventId, "Event ID should be set - test order?")
        val eventLogs = defaultWalletApi.listEventLogs()
        assertTrue(
            eventLogs.items.none { it.id == eventId },
            "Deleted event log should not appear in list"
        )
    }
}
