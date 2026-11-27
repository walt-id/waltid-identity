package id.walt.wallet2.mobile

import id.walt.webdatafetching.DataFetchingException
import id.walt.webdatafetching.ssrf.BlockedAddressException
import id.waltid.openid4vp.wallet.PresentationDeliveryUncertainException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WalletFailureTest {
    @Test
    fun callersCanDistinguishTimeoutRejectionAndUnknownDeliveryWithoutReadingDiagnostics() = runTest {
        val diagnostic = "private issuer diagnostic"
        val failures = mapOf(
            CancellationException(diagnostic) to WalletFailure.cancelled,
            HttpRequestTimeoutException("https://issuer.example", 2000L) to WalletFailure.timeout,
            DataFetchingException(diagnostic, IOException(diagnostic)) to WalletFailure.network,
            DataFetchingException(diagnostic, BlockedAddressException(diagnostic)) to WalletFailure.invalidInput,
            PresentationDeliveryUncertainException(IOException(diagnostic)) to WalletFailure.deliveryUncertain,
        )
        for ((cause, expected) in failures) {
            val classified = WalletFailure.classify(cause)
            assertEquals(expected, classified)
            assertFalse(diagnostic in classified.userMessage)
        }
        HttpClient(MockEngine { respond(diagnostic, HttpStatusCode.BadRequest) }) { expectSuccess = true }.use { client ->
            val rejected = assertFailsWith<ClientRequestException> { client.get("https://issuer.example") }
            assertEquals(WalletFailure.remoteResponse, WalletFailure.classify(rejected))
            assertEquals(WalletFailure.remoteResponse, WalletFailure.classify(DataFetchingException(diagnostic, rejected)))
        }
    }
}
