package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.test.runTest
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
}
