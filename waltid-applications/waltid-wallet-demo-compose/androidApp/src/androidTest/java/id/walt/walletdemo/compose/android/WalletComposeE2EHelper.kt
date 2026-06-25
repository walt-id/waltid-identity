package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.uiautomator.By
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

    fun setText(device: UiDevice, tag: String, value: String) {
        val field = waitForResource(device, tag, UI_ELEMENT_TIMEOUT)
        assertNotNull("$tag not found", field)
        field!!.setText(value)
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

    fun latestStatus(device: UiDevice): String {
        val tagged = device.findObject(By.res("wallet.status"))
        if (tagged?.text != null) return tagged.text

        for (prefix in statusPrefixes) {
            val obj = device.findObject(By.textStartsWith(prefix))
            if (obj != null) return obj.text
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
