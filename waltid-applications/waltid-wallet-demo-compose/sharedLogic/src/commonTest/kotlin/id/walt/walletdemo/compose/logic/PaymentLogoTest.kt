package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentLogoTest {
    @Test fun onlyExplicitHttpsNavigationIsOffered() {
        assertNotNull(paymentWebsite("https://shop.example/payments?q=1"))
        for (uri in listOf("http://shop.example", "javascript:alert(1)", "data:image/png;base64,AA==",
            "https://user:password@shop.example", "https://shop.example/with space", "https://shop.example/\u0000")) {
            assertNull(paymentWebsite(uri))
        }
    }

    @Test fun invalidOrUnrequestedLogosAreHidden() = runTest {
        for (source in listOf("https://shop.example/logo.png", "", "data:image/png;base64,AA==",
            "data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,invalid!",
            "data:image/png;base64," + "A".repeat(700_000))) {
            assertNull(loadPaymentLogo(source))
        }
        for (source in listOf("http://shop.example/logo.png", "https://127.0.0.1/logo.png", "https://localhost/logo.png")) {
            assertNull(loadPaymentLogo(source, allowRemote = true))
        }
        // A known PNG signature is accepted here; platform decoding separately rejects corrupt/oversized images.
        assertNotNull(loadPaymentLogo("data:image/png;base64,iVBORw0KGgo="))
    }

    @Test fun remoteImagesAreExplicitBoundedAndDoNotFollowRedirects() = runTest {
        val png = Base64.Default.decode("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFUlEQVR4nGNgKPhAGhrVMKph+GoAADKWYBBGgv6DAAAAAElFTkSuQmCC")
        var requests = 0
        var body = png
        var status = HttpStatusCode.OK
        val createClient = { HttpClient(MockEngine) {
            followRedirects = false
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    requests++
                    respond(body, status, headersOf(HttpHeaders.Location, "https://redirect.example/logo"))
                }
            }
        } }
        val source = "https://shop.example/logo.png"
        assertNull(loadPaymentLogo(source, false, createClient))
        assertEquals(0, requests)
        assertContentEquals(png, loadPaymentLogo(source, true, createClient))
        status = HttpStatusCode.Found
        assertNull(loadPaymentLogo(source, true, createClient))
        assertEquals(2, requests, "A redirect must not send another request")
        status = HttpStatusCode.OK
        body = png + ByteArray(524_289)
        assertNull(loadPaymentLogo(source, true, createClient), "Bound the body even without Content-Length")
    }

}
