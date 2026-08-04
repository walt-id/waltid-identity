package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresentationContinuationAndroidTest {
    @Test
    fun completesOnlyAfterSubmittedNavigationFinishes() {
        var completionCount = 0
        val client = FormPostWebViewClient(
            onCompleted = { completionCount++ },
            onFailed = {},
        )

        client.onPageStarted(null, "data:text/html,form", null)
        client.onPageStarted(null, "https://verifier.example/response", null)
        client.onPageFinished(null, "data:text/html,form")
        assertEquals(0, completionCount)

        client.onPageFinished(null, "https://verifier.example/response")
        client.onPageFinished(null, "https://verifier.example/response")
        assertEquals(1, completionCount)
    }
}
