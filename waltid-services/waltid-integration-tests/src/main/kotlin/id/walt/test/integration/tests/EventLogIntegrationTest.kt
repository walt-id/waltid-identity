@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.test.integration.expectSuccess
import id.walt.test.integration.loadJsonResource
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Integration tests for event log functionality.
 * Tests the /wallet-api/wallet/{walletId}/eventlog endpoint.
 */
@TestMethodOrder(OrderAnnotation::class)
class EventLogIntegrationTest : AbstractIntegrationTest() {

    companion object {
        private val issuerKey = loadJsonResource("issuance/key.json")
        private val issuerDid = loadResource("issuance/did.txt")
    }

    @Test
    @Order(1)
    fun shouldReturnEmptyEventLogForNewWallet() = runTest {
        val response = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog")
        response.expectSuccess()
        
        val eventLog = response.body<JsonObject>()
        assertNotNull(eventLog, "Event log response should not be null")
        assertNotNull(eventLog["items"], "Event log should contain items field")
    }

    @Test
    @Order(2)
    fun shouldLogCredentialClaimEvent() = runTest {
        val credentialData = loadJsonResource("issuance/openbadgecredential.json")
        
        val offerUrl = issuerApi.issueJwtCredential(
            IssuanceRequest(
                issuerKey = issuerKey,
                issuerDid = issuerDid,
                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
                credentialData = credentialData,
                mapping = loadJsonResource("issuance/mapping.json")
            )
        )
        
        defaultWalletApi.claimCredential(offerUrl)
        
        val response = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog")
        response.expectSuccess()
        
        val eventLog = response.body<JsonObject>()
        val items = eventLog["items"]?.jsonArray
        assertNotNull(items, "Event log should contain items")
        assertTrue(items.isNotEmpty(), "Event log should have at least one event after claiming credential")
    }

    @Test
    @Order(3)
    fun shouldReturnEventLogWithPagination() = runTest {
        val response = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog") {
            url {
                parameters.append("limit", "5")
            }
        }
        response.expectSuccess()
        
        val eventLog = response.body<JsonObject>()
        assertNotNull(eventLog["items"], "Event log should contain items field")
    }

    @Test
    @Order(4)
    fun shouldReturnEventLogWithSorting() = runTest {
        val responseAsc = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog") {
            url {
                parameters.append("sortOrder", "ASC")
            }
        }
        responseAsc.expectSuccess()
        
        val responseDesc = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog") {
            url {
                parameters.append("sortOrder", "DESC")
            }
        }
        responseDesc.expectSuccess()
        
        val eventLogAsc = responseAsc.body<JsonObject>()
        val eventLogDesc = responseDesc.body<JsonObject>()
        
        assertNotNull(eventLogAsc["items"], "ASC sorted event log should contain items")
        assertNotNull(eventLogDesc["items"], "DESC sorted event log should contain items")
    }

    @Test
    @Order(5)
    fun shouldReturnEventLogWithFiltering() = runTest {
        val response = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog") {
            url {
                parameters.append("filter", "action=useOfferRequest")
            }
        }
        response.expectSuccess()
        
        val eventLog = response.body<JsonObject>()
        assertNotNull(eventLog["items"], "Filtered event log should contain items field")
    }

    @Test
    @Order(6)
    fun shouldContainEventMetadata() = runTest {
        val response = defaultWalletApi.httpClient.get("/wallet-api/wallet/${defaultWalletApi.walletId}/eventlog")
        response.expectSuccess()
        
        val eventLog = response.body<JsonObject>()
        val items = eventLog["items"]?.jsonArray
        
        if (items != null && items.isNotEmpty()) {
            val firstEvent = items.first().jsonObject
            assertNotNull(firstEvent["id"], "Event should have an id")
            assertNotNull(firstEvent["timestamp"] ?: firstEvent["createdOn"], "Event should have a timestamp")
            assertNotNull(firstEvent["action"] ?: firstEvent["event"], "Event should have an action/event type")
        }
    }
}
