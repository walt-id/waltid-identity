package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceTextEquals
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceEnabled
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EudiPublicBackendE2ETest {

    @Test
    fun promptsForTransactionCodeAndRetriesAfterIncorrectCode() = runBlocking {
        val offer = EudiTestBackend.generateOffer()
        val transactionCode = requireNotNull(offer.txCode) {
            "EUDI backend did not return a transaction code"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        assertResourceTextEquals(
            device = device,
            tag = "wallet.offerInput",
            expected = offer.offerUrl,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "EUDI offer URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.receiveButton")
        assertNotNull(
            "Transaction-code input did not appear. Latest status: ${latestStatus(device)}",
            waitForResource(device, "wallet.txCodeInput", CREDENTIAL_OPERATION_TIMEOUT),
        )

        setTextByTag(device, "wallet.txCodeInput", incorrectCodeFor(transactionCode))
        assertTrue(
            "Receive button did not enable after entering a transaction code",
            waitForResourceEnabled(device, "wallet.receiveButton", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Incorrect transaction code was not rejected. Latest status: ${latestStatus(device)}",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Receive failed") },
                failurePrefixes = emptyList(),
            ),
        )

        setTextByTag(device, "wallet.txCodeInput", transactionCode)
        assertTrue(
            "Receive button did not re-enable after correcting the transaction code",
            waitForResourceEnabled(device, "wallet.receiveButton", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Receive did not succeed after correcting the transaction code. Latest status: ${latestStatus(device)}",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
    }

    private fun incorrectCodeFor(code: String): String {
        require(code.isNotEmpty()) { "Transaction code must not be empty" }
        val replacement = if (code.last() == '0') '1' else '0'
        return code.dropLast(1) + replacement
    }
}
