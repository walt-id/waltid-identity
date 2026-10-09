package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowInsets
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

internal object WalletComposeE2EHelper {
    private val walletPackage: String get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: InstrumentationRegistry.getInstrumentation().targetContext.packageName
    private const val SIGNING_PROTECTION_MODE_EXTRA =
        "id.walt.walletdemo.compose.android.WALLET_SIGNING_PROTECTION_MODE"
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

    fun launchAndUnlock(context: Context, device: UiDevice, initializeSigningIdentity: Boolean = true) {
        launch(context)
        unlock(device, initializeSigningIdentity)
    }

    /** Uses the normal setup UI; the operator approves each native signing prompt. */
    fun launchAndCreateScaIdentity(context: Context, device: UiDevice) {
        launch(context, signingProtectionMode = "required")
        unlock(device, initializeSigningIdentity = false)
        clickByTag(device, "wallet.keySetupEdit.Storage")
        requireNotNull(device.wait(Until.findObject(By.text("Hardware required")), UI_ELEMENT_TIMEOUT)).click()
        clickByTag(device, "wallet.keySetupContinue")
        clickByTag(device, "wallet.keySetupEdit.Approval")
        requireNotNull(device.wait(Until.findObject(By.text("Current biometrics only")), UI_ELEMENT_TIMEOUT)).click()
        clickByTag(device, "wallet.keySetupContinue")
        println("SCA_OPERATOR: approve native key setup prompts")
        clickByTag(device, "wallet.keySetupContinue")
        assertNotNull("App key setup failed: ${foregroundWindowSnapshot(device)}",
            waitForResource(device, "wallet.scanButton", 180_000L))
    }

    fun receiveThroughApp(device: UiDevice, offerUrl: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        sendDeepLink(context, offerUrl, signingProtectionMode = "required")
        requireNotNull(waitForResource(device, "wallet.offerAcceptButton", CREDENTIAL_OPERATION_TIMEOUT))
        println("SCA_OPERATOR: approve native issuance prompts")
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue("App issuance failed: ${foregroundWindowSnapshot(device)}",
            waitForStatus(device, 180_000L, { it.startsWith("Received") }, listOf("Receive failed")))
    }

    fun launchExpectingSetupAndUnlock(context: Context, device: UiDevice) {
        launch(context)

        assertNotNull("PIN input not found on initial launch", waitForResource(device, "wallet.pinInput", UI_ELEMENT_TIMEOUT))
        assertTrue("PIN setup was not shown on initial launch",
            device.wait(Until.hasObject(By.text("Step 1 of 2")), UI_ELEMENT_TIMEOUT))
        unlock(device)
    }

    fun relaunchAndUnlock(context: Context, device: UiDevice) {
        launch(context)

        assertNotNull("PIN input not found after relaunch", waitForResource(device, "wallet.pinInput", UI_ELEMENT_TIMEOUT))
        assertTrue("PIN setup was shown after relaunch", !device.hasObject(By.text("Step 1 of 2")))
        unlock(device)
    }

    fun launch(context: Context, signingProtectionMode: String = "disabled") {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(SIGNING_PROTECTION_MODE_EXTRA, signingProtectionMode)
            }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)
    }

    fun unlock(device: UiDevice, initializeSigningIdentity: Boolean = true) {
        val pinInput = waitForResource(device, "wallet.pinInput", UI_ELEMENT_TIMEOUT)
            ?: throw AssertionError("PIN input not found. ${foregroundWindowSnapshot(device)}")
        val setup = device.hasObject(By.text("Step 1 of 2"))
        pinInput.setText(PIN)
        if (setup) {
            assertTrue(device.wait(Until.hasObject(By.text("Step 2 of 2")), UI_ELEMENT_TIMEOUT))
            val confirmation = requireNotNull(waitForResource(device, "wallet.pinConfirmationInput", UI_ELEMENT_TIMEOUT))
            confirmation.setText(PIN) // Matching confirmation advances directly to OS opt-in / signing setup.
        }
        if (initializeSigningIdentity) awaitWalletReady(device)
    }

    /** Settle the IME before locating a footer action; its bounds move when the keyboard closes. */
    fun dismissKeyboard(device: UiDevice) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun keyboardVisible(): Boolean {
            var visible = false
            instrumentation.runOnMainSync {
                visible = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            }
            return visible
        }
        if (!keyboardVisible()) return
        device.pressBack()
        val deadline = System.currentTimeMillis() + CLICK_VISIBLE_TIMEOUT
        while (keyboardVisible() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Keyboard did not close before the footer action", !keyboardVisible())
        device.waitForIdle()
    }

    fun awaitWalletReady(device: UiDevice) {
        val deadline = System.currentTimeMillis() + WALLET_READY_TIMEOUT
        var creationRequested = false
        while (System.currentTimeMillis() < deadline) {
            // A preceding modal window may leave API 34+ UiAutomation with stale roots.
            // Refresh accessibility only; never infer readiness from a timer or a status string.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            val status = latestStatus(device)
            if (device.hasObject(By.res("wallet.scanButton"))) return
            if (status.startsWith("Bootstrap failed")) break
            if (!creationRequested) {
                try {
                    if (device.hasObject(By.res("wallet.keySetupEdit.Storage"))) {
                        device.findObject(By.res("wallet.keySetupContinue"))?.let { button ->
                            if (button.isEnabled) {
                                button.click()
                                creationRequested = true
                            }
                        }
                    }
                } catch (_: StaleObjectException) {
                    // Re-query when Compose replaces the accessibility tree during setup.
                }
            }
            Thread.sleep(500)
        }
        fail("Wallet did not become ready after unlock. Latest status: ${latestStatus(device)}. " +
            foregroundWindowSnapshot(device))
    }

    /** Recreates the real Activity, without destroying the retained request model or app process. */
    fun recreateActivity(type: Class<out Activity>, device: UiDevice) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun resumed(): Activity? {
            var activity: Activity? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).singleOrNull { type.isInstance(it) }
            }
            return activity
        }
        val previous = requireNotNull(resumed()) { "${type.simpleName} is not resumed" }
        instrumentation.runOnMainSync { previous.recreate() }
        val deadline = System.currentTimeMillis() + UI_ELEMENT_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (resumed()?.let { it !== previous } == true) {
                device.waitForIdle()
                return
            }
            Thread.sleep(100)
        }
        fail("${type.simpleName} did not recreate. ${foregroundWindowSnapshot(device)}")
    }

    fun sendDeepLink(context: Context, url: String, signingProtectionMode: String = "disabled") {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url),
        ).setClassName(context.packageName, "id.walt.walletdemo.compose.android.MainActivity").apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(SIGNING_PROTECTION_MODE_EXTRA, signingProtectionMode)
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
        val initialNode = waitForVisibleResource(device, tag, CLICK_VISIBLE_TIMEOUT)
            ?: findResourceAfterScrolling(device, tag)
        if (initialNode == null) {
            fail("$tag not found.\n${visibleUiSnapshot(device)}")
            return
        }
        var node: UiObject2 = initialNode
        val deadline = System.currentTimeMillis() + CLICK_VISIBLE_TIMEOUT
        while (!node.isEnabled && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            node = findVisibleResource(device, tag) ?: node
        }
        if (!node.isEnabled) {
            fail("$tag is disabled.\n${visibleUiSnapshot(device)}")
        }
        device.waitForIdle()
        node.clickableAncestorOrSelf()?.click() ?: node.click()
        device.waitForIdle()
    }

    fun setTextByTag(device: UiDevice, tag: String, value: String) {
        val node = waitForVisibleResource(device, tag, CLICK_VISIBLE_TIMEOUT)
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

    /**
     * Asserts [substring] appears in some wallet text node, scrolling to look for it. For values the
     * review renders wrapped in surrounding label text, where an exact match would fail on content that
     * is in fact on screen.
     */
    fun assertTextContainingVisibleAfterScrolling(
        device: UiDevice,
        substring: String,
        message: String,
    ) {
        if (findTextContainingAfterScrolling(device, substring) != null) return
        fail("$message. Expected a node containing '$substring'.\n${visibleUiSnapshot(device)}")
    }

    /**
     * Asserts [substring] appears in the front-most window, whichever package owns it. Credential
     * Manager draws its prompt from Google Play services, so the wallet-scoped lookups above cannot
     * see it - and would report an empty screen rather than a missing value.
     */
    fun assertTextContainingVisibleInForegroundWindow(
        device: UiDevice,
        substring: String,
        message: String,
    ) {
        if (device.wait(Until.findObject(By.textContains(substring)), UI_ELEMENT_TIMEOUT) != null) return
        fail("$message. Expected a node containing '$substring'.\n${foregroundWindowSnapshot(device)}")
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
        var visibleTexts = node.visibleTexts()
        fun expectedContentIsVisible(): Boolean =
            label in visibleTexts && expectedValues.all { expected -> expected in visibleTexts }

        if (expectedContentIsVisible()) return
        repeat(6) {
            device.scrollDown()
            findVisibleResource(device, tag)?.let { visibleTexts = it.visibleTexts() }
            if (expectedContentIsVisible()) return
        }
        repeat(12) {
            device.scrollUp()
            findVisibleResource(device, tag)?.let { visibleTexts = it.visibleTexts() }
            if (expectedContentIsVisible()) return
        }

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

    private fun waitForVisibleResource(device: UiDevice, tag: String, timeoutMs: Long): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            findVisibleResource(device, tag)?.let { return it }
            Thread.sleep(200)
        }
        return null
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

    private fun findTextContainingAfterScrolling(device: UiDevice, substring: String): UiObject2? {
        findVisibleTextContaining(device, substring)?.let { return it }
        repeat(6) {
            device.scrollDown()
            findVisibleTextContaining(device, substring)?.let { return it }
        }
        repeat(12) {
            device.scrollUp()
            findVisibleTextContaining(device, substring)?.let { return it }
        }
        return null
    }

    private fun findVisibleTextContaining(device: UiDevice, substring: String): UiObject2? =
        device.findObjects(By.pkg(walletPackage).textContains(substring))
            .firstOrNull { it.isVisibleOn(device) }

    private fun findVisibleText(device: UiDevice, texts: List<String>): UiObject2? =
        device.findObjects(By.pkg(walletPackage).text(Pattern.compile(texts.joinToString("|") { Pattern.quote(it) })))
            .firstOrNull { it.isVisibleOn(device) }

    private fun findVisibleResource(device: UiDevice, tag: String): UiObject2? =
        device.findObjects(By.pkg(walletPackage).res(tag)).firstOrNull { it.isVisibleOn(device) }

    fun findResourceAfterScrolling(device: UiDevice, tag: String): UiObject2? {
        findVisibleResource(device, tag)?.let { return it }
        for (attempt in 0 until 24) {
            val more = device.scrollDown()
            findVisibleResource(device, tag)?.let { return it }
            if (!more) break
        }
        for (attempt in 0 until 24) {
            val more = device.scrollUp()
            findVisibleResource(device, tag)?.let { return it }
            if (!more) break
        }
        return null
    }

    /**
     * Waits for [tag] after a navigation or configuration change. Small landscape
     * viewports still need scrolling, but a single scroll pass is not enough when
     * Compose has not finished composing the restored destination.
     */
    fun waitForResourceAfterScrolling(
        device: UiDevice,
        tag: String,
        timeoutMs: Long = UI_ELEMENT_TIMEOUT,
    ): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var towardBottom = true
        var stepsInDirection = 0
        while (System.currentTimeMillis() < deadline) {
            try {
                findVisibleResource(device, tag)?.let { return it }
                device.findObject(By.res(tag))?.let { return it }
                if (towardBottom) device.scrollDown() else device.scrollUp()
                stepsInDirection++
                if (stepsInDirection == 6) {
                    towardBottom = !towardBottom
                    stepsInDirection = 0
                }
            } catch (_: StaleObjectException) {
                // Re-query the target and all scroll-container bounds after Compose replaces a node.
            }
            Thread.sleep(200)
        }
        return null
    }

    fun assertResourceVisibleAfterScrolling(
        device: UiDevice,
        tag: String,
        message: String,
        timeoutMs: Long = UI_ELEMENT_TIMEOUT,
    ) {
        if (waitForResourceAfterScrolling(device, tag, timeoutMs) != null) return
        fail("$message. Expected $tag.\n${visibleUiSnapshot(device)}")
    }

    private fun UiObject2.isVisibleOn(device: UiDevice): Boolean = runCatching {
        val bounds = visibleBounds
        bounds.width() > 0 &&
            bounds.height() > 0 &&
            bounds.right > 0 &&
            bounds.bottom > 0 &&
            bounds.left < device.displayWidth &&
            bounds.top < device.displayHeight
    }.getOrDefault(false)

    private fun claimTag(path: String): String =
        "wallet.claim.${path.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")}"

    /** Every card tag currently in the tree, sweeping the list so off-screen cards are included. */
    fun UiDevice.credentialCardTags(): Set<String> {
        if (hasObject(By.res("wallet.credentials.empty"))) return emptySet()
        val cardTag = Pattern.compile("wallet\\.credentialCard\\..*")
        val tags = mutableSetOf<String>()
        fun collect() {
            findObjects(By.res(cardTag))
                .mapNotNullTo(tags) { runCatching { it.resourceName }.getOrNull() }
        }
        collect()
        for (attempt in 0 until 24) {
            val more = scrollDown()
            collect()
            if (!more) break
        }
        for (attempt in 0 until 24) { if (!scrollUp()) break }
        return tags
    }

    internal fun UiDevice.scrollDown() = scrollContent(towardBottom = true)

    internal fun UiDevice.scrollUp() = scrollContent(towardBottom = false)

    private fun UiDevice.scrollContent(towardBottom: Boolean): Boolean {
        // Review actions are fixed below the scroll viewport, especially on compact devices.
        val viewport = findObjects(By.pkg(walletPackage).scrollable(true)).mapNotNull { node ->
            try { node.visibleBounds.let { node to it } } catch (_: StaleObjectException) { null }
        }.filter { (_, bounds) -> bounds.width() > 0 && bounds.height() > 0 }
            .maxByOrNull { (_, bounds) -> bounds.width().toLong() * bounds.height() }?.first ?: return false
        val bounds = viewport.visibleBounds
        fun visibleContent() = findObjects(By.pkg(walletPackage).text(Pattern.compile(".*"))).mapNotNull { node ->
            try {
                node.visibleBounds.takeIf { android.graphics.Rect.intersects(it, bounds) }
                    ?.let { "${node.text}:$it" }
            } catch (_: StaleObjectException) { null }
        }
        val before = visibleContent()
        val upper = bounds.top + bounds.height() * 15 / 100
        val lower = bounds.bottom - bounds.height() * 15 / 100
        swipe(bounds.centerX(), if (towardBottom) lower else upper,
            bounds.centerX(), if (towardBottom) upper else lower, 40)
        waitForIdle()
        // UiObject2.scroll reports false on this Compose/API 37 host even before the end.
        // Compare the actual visible content after a bounded gesture instead of trusting that event.
        return before != visibleContent()
    }

    fun latestStatus(device: UiDevice): String = try {
        device.findObject(By.res("wallet.status"))?.text
            ?: statusPrefixes.firstNotNullOfOrNull { prefix ->
                device.findObject(By.textStartsWith(prefix))?.text
            }
            ?: "UNKNOWN"
    } catch (_: StaleObjectException) {
        // Discard stale accessibility data so the next poll reads the current screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
        }
        "UNKNOWN"
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

    /**
     * Every text and content description in the front-most window. Dumped rather than walked, because
     * the node tree of a window owned by another package is not reachable through a package matcher.
     */
    fun foregroundWindowSnapshot(device: UiDevice): String {
        val hierarchy = ByteArrayOutputStream().use { out ->
            runCatching { device.dumpWindowHierarchy(out) }
            out.toString("UTF-8")
        }
        val texts = Regex("""(?:text|content-desc)="([^"]*)"""").findAll(hierarchy)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .take(120)
            .joinToString("\n")

        return """
            package=${device.currentPackageName}
            foregroundWindowTexts:
            ${texts.ifBlank { "<none>" }}
        """.trimIndent()
    }

    private fun visibleUiSnapshot(device: UiDevice): String {
        val nodes = device.findObjects(By.pkg(walletPackage))
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

    private fun UiObject2.visibleTexts(): List<String> =
        flatten().mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }

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
