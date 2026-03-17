@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import io.klogging.Klogging
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class ErrorHandlingIntegrationTest : AbstractIntegrationTest(), Klogging {

    @Order(0)
    @Test
    fun shouldReturn404ForNonExistentCredential() = runTest {
        val response = defaultWalletApi.credentialApi.getCredentialRaw(
            defaultWalletApi.walletId,
            "non-existent-credential-id"
        )
        assertEquals(HttpStatusCode.NotFound, response.status)
        logger.info("Got expected 404 for non-existent credential")
    }

    @Order(1)
    @Test
    fun shouldReturn404ForNonExistentKey() = runTest {
        val response = defaultWalletApi.keysApi.loadRaw(
            defaultWalletApi.walletId,
            "non-existent-key-id"
        )
        assertEquals(HttpStatusCode.NotFound, response.status)
        logger.info("Got expected 404 for non-existent key")
    }

    @Order(2)
    @Test
    fun shouldHandleInvalidCredentialOffer() = runTest {
        val response = defaultWalletApi.exchangeApi.resolveCredentialOfferRaw(
            defaultWalletApi.walletId,
            "invalid-offer-url"
        )
        assertTrue(
            response.status == HttpStatusCode.BadRequest || 
            response.status == HttpStatusCode.InternalServerError,
            "Should return error for invalid offer URL"
        )
        logger.info("Got expected error for invalid credential offer: ${response.status}")
    }

    @Order(3)
    @Test
    fun shouldHandleInvalidPresentationRequest() = runTest {
        val response = defaultWalletApi.exchangeApi.resolvePresentationRequestRaw(
            defaultWalletApi.walletId,
            "invalid-presentation-request"
        )
        assertTrue(
            response.status == HttpStatusCode.BadRequest || 
            response.status == HttpStatusCode.InternalServerError,
            "Should return error for invalid presentation request"
        )
        logger.info("Got expected error for invalid presentation request: ${response.status}")
    }

    @Order(4)
    @Test
    fun shouldHandleInvalidDidMethod() = runTest {
        val response = defaultWalletApi.didsApi.createDidRaw(
            defaultWalletApi.walletId,
            method = "invalid-method"
        )
        assertTrue(
            response.status == HttpStatusCode.BadRequest || 
            response.status == HttpStatusCode.InternalServerError,
            "Should return error for invalid DID method"
        )
        logger.info("Got expected error for invalid DID method: ${response.status}")
    }

    @Order(5)
    @Test
    fun shouldHandleDeleteNonExistentCredential() = runTest {
        val response = defaultWalletApi.credentialApi.deleteCredentialRaw(
            defaultWalletApi.walletId,
            "non-existent-credential-id"
        )
        assertTrue(
            response.status == HttpStatusCode.NotFound || 
            response.status == HttpStatusCode.BadRequest ||
            response.status == HttpStatusCode.Accepted,
            "Should handle delete of non-existent credential gracefully"
        )
        logger.info("Delete non-existent credential response: ${response.status}")
    }

    @Order(6)
    @Test
    fun shouldHandleRestoreNonDeletedCredential() = runTest {
        val response = defaultWalletApi.credentialApi.restoreCredentialRaw(
            defaultWalletApi.walletId,
            "non-existent-credential-id"
        )
        assertTrue(
            response.status == HttpStatusCode.NotFound || 
            response.status == HttpStatusCode.BadRequest,
            "Should return error when restoring non-existent credential"
        )
        logger.info("Restore non-existent credential response: ${response.status}")
    }

    @Order(7)
    @Test
    fun shouldHandleInvalidCategoryName() = runTest {
        val response = defaultWalletApi.categoryApi.deleteCategoryRaw(
            defaultWalletApi.walletId,
            "non-existent-category"
        )
        assertTrue(
            response.status.isSuccess() || 
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.BadRequest,
            "Should handle delete of non-existent category"
        )
        logger.info("Delete non-existent category response: ${response.status}")
    }

    @Order(8)
    @Test
    fun shouldHandleInvalidEventLogId() = runTest {
        val response = defaultWalletApi.eventLogApi.deleteEventLogRaw(
            defaultWalletApi.walletId,
            eventId = -1
        )
        assertTrue(
            response.status.isSuccess() || 
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.BadRequest,
            "Should handle delete of invalid event log ID"
        )
        logger.info("Delete invalid event log response: ${response.status}")
    }
}
