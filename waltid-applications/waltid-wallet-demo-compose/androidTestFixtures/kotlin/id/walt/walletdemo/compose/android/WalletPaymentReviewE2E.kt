package id.walt.walletdemo.compose.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoPayment
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.foregroundWindowSnapshot
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForAnyResourceAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceAfterScrolling
import org.junit.Assert.*

/** Real issuer-labelled review, shared by the URL and physical Credential Manager journeys. */
internal object WalletPaymentReviewE2E {
    fun review(device: UiDevice, payment: DemoPayment, capturePrefix: String) {
        fun text(value: String) = assertTextContainingVisibleAfterScrolling(device, value, "Missing visible payment field: $value")
        fun toggle(tag: String) {
            assertNotNull("Missing payment disclosure: $tag", waitForResourceAfterScrolling(device, tag, 10_000))
            clickByTag(device, tag)
        }
        val consent = waitForAnyResourceAfterScrolling(device, listOf("payment-consent", "payment-consent-blocked"), 60_000)
        if (consent == null) {
            fail("Payment consent is unavailable.\n${foregroundWindowSnapshot(device)}")
            return
        }
        assertEquals("Payment consent is blocked: ${consent.text}", "payment-consent", consent.resourceName)
        listOf("Confirm this payment", "Super Store", "merchant-001", "Payee ID", "Amount", "11.56", "EUR",
            "Check the payee, amounts, timing and recurring terms before confirming.").forEach(::text)
        assertFalse("Failed logos must leave no placeholder", device.hasObject(By.res("payment-payee-logo")))
        assertFalse(device.hasObject(By.text("Payee logo")))
        assertFalse(device.hasObject(By.text("Currency")))
        text("Super Store")
        WalletGalleryEvidence.capture(device, "$capturePrefix-main")
        if (payment != DemoPayment.MINIMAL) {
            toggle("payment-payee-details-toggle")
            text("https://example.com")
            device.findObject(By.text("https://example.com ↗")).click()
            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            assertTrue("Website must open outside the wallet", device.wait(Until.gone(By.pkg(packageName)), 15_000))
            device.pressBack()
            assertTrue("Browser return must retain payment review", device.wait(Until.hasObject(By.pkg(packageName)), 15_000))
            text("https://example.com")
            toggle("payment-payee-details-toggle")
            assertFalse(device.hasObject(By.text("https://example.com ↗")))
            val terms = if (payment == DemoPayment.SCHEDULED) listOf("Payment timing", "Execution date and time") else
                listOf("Payment timing", "First payment", "Last payment", "Monthly", "Payment terms",
                    "9007199254740993.01", "-1.23", "Annual percentage rate", "0.1234567890123456789")
            terms.forEach(::text)
            toggle("payment-details-toggle")
            listOf("Example Payments Ltd", "Estimated amount", "wal-1665-${payment.name.lowercase()}").forEach(::text)
            listOf("No", "Yes").forEach { assertTextVisibleAfterScrolling(device, listOf(it), "Missing localized Boolean: $it") }
            WalletGalleryEvidence.capture(device, "$capturePrefix-details")
            toggle("payment-details-toggle")
            assertFalse("Details must collapse without submitting", device.hasObject(By.text("Example Payments Ltd")))
        }
        listOf("Confirm payment", "Cancel payment").forEach(::text)
    }
}
