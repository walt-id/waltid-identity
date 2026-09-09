package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.runner.IssuerCredentialOfferDelivery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IssuerCredentialOfferDeliveryTest {
    @Test
    fun twoClientsReceiveFreshOffersAtTheSameEndpointWithoutObservingRunning() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val endpoint = "https://suite.example/test/multiple-clients/credential_offer"
        val delivered = mutableListOf<Pair<String, String>>()
        val log = mutableListOf(waitEvent("client-1"))

        assertTrue(delivery.deliverIfRequested(log) {
            delivered += endpoint to "fresh-offer-1"
            // The first HTTP delivery completes client 1 and starts client 2 synchronously.
            log += waitEvent("client-2")
        })
        assertTrue(delivery.deliverIfRequested(log) { delivered += endpoint to "fresh-offer-2" })
        assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
        assertEquals(listOf(endpoint to "fresh-offer-1", endpoint to "fresh-offer-2"), delivered)
    }

    @Test
    fun repeatedPollsAndUnrelatedLogUpdatesDoNotDeliverAnotherOffer() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val log = listOf(waitEvent("client-1"))
        assertTrue(delivery.deliverIfRequested(log) {})
        repeat(3) {
            assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
        }
        val updatedLog = log + TestLogEntry(id = "tx-code", src = "VCIWaitForTxCode")
        assertFalse(delivery.deliverIfRequested(updatedLog) { error("Not a new offer request") })
    }

    @Test
    fun missingWaitEventOrEventIdDoesNotTriggerDelivery() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        assertFalse(delivery.deliverIfRequested(emptyList()) { error("No request") })
        assertFalse(delivery.deliverIfRequested(listOf(TestLogEntry(src = "VCIWaitForCredentialOffer"))) {
            error("No request ID")
        })
    }

    @Test
    fun failedDeliveryDoesNotMarkRequestAsDelivered() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val log = listOf(waitEvent("client-1"))
        assertFailsWith<IllegalStateException> {
            delivery.deliverIfRequested(log) { error("Delivery failed") }
        }
        assertTrue(delivery.deliverIfRequested(log) {})
        assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
    }

    private fun waitEvent(id: String) = TestLogEntry(id = id, src = "VCIWaitForCredentialOffer")
}
