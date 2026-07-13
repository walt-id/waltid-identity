package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal object WalletComposeE2EHelper {
    const val PIN = "1234"
    const val WALLET_READY_TIMEOUT = 60_000L
    const val UI_ELEMENT_TIMEOUT = 30_000L
    const val CREDENTIAL_OPERATION_TIMEOUT = 90_000L
    const val VERIFIER_POLLING_TIMEOUT = 30_000L
    const val QUICK_STATUS_CHECK_TIMEOUT = 5_000L
    const val POST_PRESENT_DELAY = 5_000L
    private const val STALE_RETRY_COUNT = 3
    private const val STALE_RETRY_DELAY_MS = 50L

    private val statusPrefixes = listOf(
        "Wallet ready",
        "Starting wallet",
        "Bootstrapping wallet",
        "Receiving credential",
        "Received",
        "Receive failed",
        "Bootstrap failed",
        "Presenting credential",
        "Presentation sent",
        "Presentation finished",
        "Present failed",
    )

    fun launchAndUnlock(context: Context, device: UiDevice) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)
        unlock(device)
    }

    fun unlock(device: UiDevice) {
        val pinInput = waitForResource(device, "wallet.pinInput", UI_ELEMENT_TIMEOUT)
        assertNotNull("PIN input not found", pinInput)
        pinInput!!.setText(PIN)

        waitForResource(device, "wallet.pinConfirmationInput", 2_000L)?.setText(PIN)

        val submit = waitForResource(device, "wallet.pinSubmitButton", UI_ELEMENT_TIMEOUT)
        assertNotNull("PIN submit button not found", submit)
        submit!!.click()

        assertTrue(
            "Wallet did not become ready after unlock. Latest status: ${latestStatus(device)}",
            waitForStatus(
                device = device,
                timeoutMs = WALLET_READY_TIMEOUT,
                matcher = { it == "Wallet ready" },
                failurePrefixes = listOf("Bootstrap failed")
            )
        )
    }

    fun sendDeepLink(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun clickByTag(device: UiDevice, tag: String) {
        val node = waitForResource(device, tag, UI_ELEMENT_TIMEOUT)
        assertNotNull("$tag not found", node)
        node!!.clickableAncestorOrSelf()?.click() ?: node.click()
    }

    fun waitForResource(device: UiDevice, tag: String, timeoutMs: Long): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = device.findObject(By.res(tag))
            if (node != null) return node
            Thread.sleep(500)
        }
        return null
    }

    /**
     * Reads a UiObject2's text safely, retrying on StaleObjectException.
     * Compose can recompose (and thus recycle) a node between the moment
     * findObject() returns it and the moment .text is read, especially
     * during fast status transitions. Returns null if the node stays
     * stale/unreachable after retrying, so callers can treat it as "no
     * reading available yet" rather than crashing the test.
     */
    private fun UiObject2.textOrNullIfStale(): String? {
        repeat(STALE_RETRY_COUNT) { attempt ->
            try {
                return this.text
            } catch (e: StaleObjectException) {
                if (attempt == STALE_RETRY_COUNT - 1) return null
                Thread.sleep(STALE_RETRY_DELAY_MS)
            }
        }
        return null
    }

    fun latestStatus(device: UiDevice): String {
        try {
            val tagged = device.findObject(By.res("wallet.status"))
            val taggedText = tagged?.textOrNullIfStale()
            if (taggedText != null) return taggedText

            for (prefix in statusPrefixes) {
                val obj = device.findObject(By.textStartsWith(prefix)) ?: continue
                val text = obj.textOrNullIfStale() ?: continue
                return text
            }
        } catch (e: StaleObjectException) {
            // The queried node tree itself shifted mid-lookup; treat as "unknown for now"
            // rather than failing the whole polling loop.
        }
        return "UNKNOWN"
    }

    fun waitForStatus(
        device: UiDevice,
        timeoutMs: Long,
        matcher: (String) -> Boolean,
        failurePrefixes: List<String>,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = latestStatus(device)
            if (matcher(status)) return true
            if (failurePrefixes.any { status.startsWith(it) }) return false
            Thread.sleep(500)
        }
        return false
    }

    private fun UiObject2.clickableAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }
}
