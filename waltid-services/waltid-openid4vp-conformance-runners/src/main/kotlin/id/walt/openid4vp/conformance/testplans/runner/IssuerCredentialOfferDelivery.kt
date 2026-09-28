package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry

internal class IssuerCredentialOfferDelivery {
    private val deliveredRequests = mutableSetOf<String>()

    suspend fun deliverIfRequested(log: List<TestLogEntry>, deliverFreshOffer: suspend () -> Unit): Boolean {
        // Multiple clients reuse the endpoint URL; each new wait event needs its own fresh offer.
        // Tracking events also works when RUNNING -> WAITING happens entirely between polls.
        val requestId = log.lastOrNull { it.src == "VCIWaitForCredentialOffer" }?.id ?: return false
        if (requestId in deliveredRequests) return false

        deliverFreshOffer()
        deliveredRequests += requestId
        return true
    }
}
