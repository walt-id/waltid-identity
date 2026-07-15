package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

internal object WalletComposeE2EHelper {
    const val PIN = "1234"
    const val WALLET_READY_TIMEOUT = 60_000L
    const val UI_ELEMENT_TIMEOUT = 30_000L
    private const val CLICK_VISIBLE_TIMEOUT = 3_000L
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
        "Resolving presentation",
        "Review presentation request",
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

    fun relaunchAndUnlock(context: Context, device: UiDevice) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertNotNull("PIN input not found after relaunch", waitForResource(device, "wallet.pinInput", UI_ELEMENT_TIMEOUT))
        assertNull(
            "PIN setup was shown after relaunch",
            waitForResource(device, "wallet.pinConfirmationInput", 2_000L),
        )
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
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url),
            context,
            MainActivity::class.java,
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(intent)
    }

    fun assertResourceTextEquals(
        device: UiDevice,
        tag: String,
        expected: String,
        timeoutMs: Long,
        message: String,
    ) {
        val node = waitForResource(device, tag, timeoutMs)
        if (node?.text != expected) {
            fail(
                """
                    $message
                    expected=$expected
                    actual=${node?.text ?: "<missing>"}
                    ${visibleUiSnapshot(device)}
                """.trimIndent()
            )
        }
    }

    fun clickByTag(device: UiDevice, tag: String) {
        val node = waitForResource(device, tag, CLICK_VISIBLE_TIMEOUT)
            ?: findVisibleResource(device, tag)
            ?: findResourceAfterScrolling(device, tag)
        if (node == null) {
            fail("$tag not found.\n${visibleUiSnapshot(device)}")
        }
        assertTrue("$tag is disabled", node!!.isEnabled)
        device.waitForIdle()
        node.clickableAncestorOrSelf()?.click() ?: node.click()
        device.waitForIdle()
    }

    fun setTextByTag(device: UiDevice, tag: String, value: String) {
        val node = waitForResource(device, tag, CLICK_VISIBLE_TIMEOUT)
            ?: findVisibleResource(device, tag)
            ?: findResourceAfterScrolling(device, tag)
        if (node == null) {
            fail("$tag not found.\n${visibleUiSnapshot(device)}")
        }
        assertTrue("$tag is disabled", node!!.isEnabled)
        node.setText(value)
        device.waitForIdle()
    }

    fun assertTextVisibleAfterScrolling(
        device: UiDevice,
        texts: List<String>,
        message: String,
    ) {
        if (findTextAfterScrolling(device, texts) != null) return
        fail("$message. Expected one of $texts.\n${visibleUiSnapshot(device)}")
    }

    fun assertClaimValueVisibleAfterScrolling(
        device: UiDevice,
        path: String,
        label: String,
        expectedValues: List<String>,
        message: String,
    ) {
        val tag = claimTag(path)
        val node = waitForResource(device, tag, CLICK_VISIBLE_TIMEOUT)
            ?: findVisibleResource(device, tag)
            ?: findResourceAfterScrolling(device, tag)
        if (node == null) {
            fail("$message. Claim row $tag not found.\n${visibleUiSnapshot(device)}")
            return
        }
        val visibleTexts = node.flatten()
            .mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
        val missingValues = expectedValues.filter { expected -> expected !in visibleTexts }
        if (label !in visibleTexts || missingValues.isNotEmpty()) {
            fail(
                """
                    $message.
                    claim=$tag
                    expectedLabel=$label
                    expectedValues=$expectedValues
                    visibleTexts=$visibleTexts
                    ${visibleUiSnapshot(device)}
                """.trimIndent()
            )
        }
    }

    fun waitForResource(device: UiDevice, tag: String, timeoutMs: Long): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = device.findObject(By.res(tag))
            if (node != null) return node
            findVisibleResource(device, tag)?.let { return it }
            Thread.sleep(500)
        }
        return null
    }

    fun waitForResourceEnabled(device: UiDevice, tag: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = device.findObject(By.res(tag)) ?: findVisibleResource(device, tag)
            if (node?.isEnabled == true) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun findTextAfterScrolling(device: UiDevice, texts: List<String>): UiObject2? {
        findVisibleText(device, texts)?.let { return it }
        repeat(6) {
            device.scrollDown()
            findVisibleText(device, texts)?.let { return it }
        }
        repeat(12) {
            device.scrollUp()
            findVisibleText(device, texts)?.let { return it }
        }
        return null
    }

    private fun findVisibleText(device: UiDevice, texts: List<String>): UiObject2? =
        device.findObjects(By.pkg("id.walt.walletdemo.compose"))
            .flatMap { it.flatten() }
            .firstOrNull { node ->
                runCatching { node.text?.trim() in texts }.getOrDefault(false)
            }

    private fun findVisibleResource(device: UiDevice, tag: String): UiObject2? =
        device.findObjects(By.pkg("id.walt.walletdemo.compose"))
            .flatMap { it.flatten() }
            .firstOrNull { node ->
                runCatching { node.resourceName == tag }.getOrDefault(false)
            }

    private fun findResourceAfterScrolling(device: UiDevice, tag: String): UiObject2? {
        repeat(6) {
            device.scrollDown()
            waitForResource(device, tag, 1_000L)?.let { return it }
            findVisibleResource(device, tag)?.let { return it }
        }
        repeat(12) {
            device.scrollUp()
            waitForResource(device, tag, 1_000L)?.let { return it }
            findVisibleResource(device, tag)?.let { return it }
        }
        return null
    }

    private fun claimTag(path: String): String =
        "wallet.claim.${path.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")}"

    private fun UiDevice.scrollDown() {
        swipe(
            displayWidth / 2,
            (displayHeight * 0.72).toInt(),
            displayWidth / 2,
            (displayHeight * 0.36).toInt(),
            24,
        )
        waitForIdle()
    }

    private fun UiDevice.scrollUp() {
        swipe(
            displayWidth / 2,
            (displayHeight * 0.36).toInt(),
            displayWidth / 2,
            (displayHeight * 0.72).toInt(),
            24,
        )
        waitForIdle()
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

    private fun visibleUiSnapshot(device: UiDevice): String {
        val roots = device.findObjects(By.pkg("id.walt.walletdemo.compose"))
        val nodes = roots
            .flatMap { it.flatten() }
            .distinctBy { node ->
                node.snapshotIdentity()
            }
            .take(80)
            .mapNotNull { it.describeForSnapshot() }
            .joinToString("\n")

        return """
            package=${device.currentPackageName}
            latestStatus=${latestStatus(device)}
            visibleWalletNodes:
            ${nodes.ifBlank { "<none>" }}
        """.trimIndent()
    }

    private fun UiObject2.flatten(): List<UiObject2> =
        listOf(this) + runCatching { children.flatMap { it.flatten() } }.getOrDefault(emptyList())

    private fun UiObject2.snapshotIdentity(): String =
        runCatching {
            listOf(
                resourceName.orEmpty(),
                text.orEmpty(),
                contentDescription.orEmpty(),
                visibleBounds.toShortString(),
            ).joinToString("|")
        }.getOrDefault("stale")

    private fun UiObject2.describeForSnapshot(): String? =
        runCatching {
            val text = text?.takeIf { it.isNotBlank() }?.let { " text='$it'" }.orEmpty()
            val res = resourceName?.takeIf { it.isNotBlank() }?.let { " res='$it'" }.orEmpty()
            val desc = contentDescription?.takeIf { it.isNotBlank() }?.let { " desc='$it'" }.orEmpty()
            "$className$res$text$desc bounds=${visibleBounds.toShortString()}"
        }.getOrNull()
}
