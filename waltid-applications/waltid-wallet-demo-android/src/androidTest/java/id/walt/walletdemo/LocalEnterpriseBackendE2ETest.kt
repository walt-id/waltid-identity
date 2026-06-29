package id.walt.walletdemo

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import id.walt.mobile.test.backend.LocalEnterpriseTestBackend
import io.ktor.client.*
import io.ktor.client.engine.android.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI test for the wallet demo app against local enterprise backend.
 *
 * Tests the full user flow: launch app → receive credential → present credential
 * Uses UIAutomator for UI interaction and shared LocalEnterpriseTestBackend for backend operations.
 *
 * This is an E2E test (slow, requires UI automation + local infrastructure) - runs locally only.
 * Requires: enterprise stack running + ngrok tunnel
 */
@RunWith(AndroidJUnit4::class)
class LocalEnterpriseBackendE2ETest {

    companion object {
        private const val DEFAULT_ADMIN_EMAIL = "admin@walt.id"
        private const val DEFAULT_ADMIN_PASSWORD = "admin123456"
        private const val DEFAULT_ORG = "waltid"
        private const val DEFAULT_TENANT = "waltid-tenant01"
        private const val DEFAULT_ISSUER_PROFILE = "issuer2.mdl-profile"
        private const val DEFAULT_VERIFIER = "verifier2"

        // Timeouts (aligned with EUDI test for consistency)
        private const val WALLET_READY_TIMEOUT = 60_000L
        private const val UI_ELEMENT_TIMEOUT = 30_000L
        private const val CREDENTIAL_OPERATION_TIMEOUT = 90_000L
        private const val VERIFIER_POLLING_TIMEOUT = 30_000L
        private const val QUICK_STATUS_CHECK_TIMEOUT = 5_000L
        private const val POST_PRESENT_DELAY = 5_000L
    }

    private val httpClient = HttpClient(Android)

    @Test
    fun receiveAndPresentAgainstLocalEnterpriseBackend() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        requireExplicitLocalEnterpriseRun(args)
        val config = loadLocalEnterpriseConfig(args)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val backend = prepareBackend(config)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(
                device = device,
                timeoutMs = WALLET_READY_TIMEOUT,
                matcher = { it == "Wallet ready" },
                failurePrefixes = listOf("Bootstrap failed")
            )
        )

        val offerIntent = Intent(Intent.ACTION_VIEW, Uri.parse(backend.offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(offerIntent)

        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForOfferUrlInUi(device, timeoutMs = UI_ELEMENT_TIMEOUT)
        )

        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = UI_ELEMENT_TIMEOUT)
        assertNotNull("Receive button not found", receiveButton)
        receiveButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }

        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        val presentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(backend.verifierSession.bootstrapAuthorizationRequestUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentIntent)

        val startedWithoutTap = waitForStatus(
            device = device,
            timeoutMs = QUICK_STATUS_CHECK_TIMEOUT,
            matcher = {
                it.startsWith("Presenting credential") ||
                    it.startsWith("Presentation sent") ||
                    it.startsWith("Presentation finished")
            },
            failurePrefixes = listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = UI_ELEMENT_TIMEOUT)
            assertNotNull("Present button not found", presentButton)
            presentButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }
        }

        Thread.sleep(POST_PRESENT_DELAY)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed in app. Latest status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )
        assertTrue(
            "Wallet app is no longer in foreground after presentation flow",
            device.currentPackageName == context.packageName
        )

        val verifierStatus = LocalEnterpriseTestBackend.waitForVerifierSuccess(
            config,
            backend.verifierSession.sessionId,
            httpClient,
            timeoutMs = VERIFIER_POLLING_TIMEOUT
        )
        assertTrue(
            "Verifier session was not SUCCESSFUL. Last status: $verifierStatus",
            verifierStatus == "SUCCESSFUL"
        )
    }

    @Test
    fun credentialsPersistAcrossAppRestart() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        requireExplicitLocalEnterpriseRun(args)
        val config = loadLocalEnterpriseConfig(args)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val backend = prepareBackend(config)

        // --- Phase 1: Launch, bootstrap, receive credential ---
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(device, WALLET_READY_TIMEOUT, { it == "Wallet ready" }, listOf("Bootstrap failed"))
        )

        val offerIntent = Intent(Intent.ACTION_VIEW, Uri.parse(backend.offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(offerIntent)
        assertTrue("Offer URL did not appear", waitForOfferUrlInUi(device, UI_ELEMENT_TIMEOUT))

        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = UI_ELEMENT_TIMEOUT)
        assertNotNull("Receive button not found", receiveButton)
        receiveButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }

        assertTrue(
            "Receive did not complete. Status: ${latestStatus(device)}",
            waitForStatus(device, CREDENTIAL_OPERATION_TIMEOUT, { it.startsWith("Received") }, listOf("Receive failed", "Bootstrap failed"))
        )

        // --- Phase 2: Kill app process (send to background first, then use am kill) ---
        device.pressHome()
        Thread.sleep(1_000)
        device.executeShellCommand("am kill ${context.packageName}")
        Thread.sleep(2_000)

        // --- Phase 3: Relaunch and verify credentials survived ---
        val relaunchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(relaunchIntent)

        assertTrue(
            "Wallet did not become ready after restart",
            waitForStatus(device, WALLET_READY_TIMEOUT, { it == "Wallet ready" }, listOf("Bootstrap failed"))
        )

        assertTrue(
            "Credentials not displayed after restart — persistence failed",
            device.findObject(By.text("No credentials")) == null
        )

        // --- Phase 4: Present from persisted credential ---
        val presentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(backend.verifierSession.bootstrapAuthorizationRequestUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentIntent)

        val startedWithoutTap = waitForStatus(
            device, QUICK_STATUS_CHECK_TIMEOUT,
            { it.startsWith("Presenting credential") || it.startsWith("Presentation sent") || it.startsWith("Presentation finished") },
            listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = UI_ELEMENT_TIMEOUT)
            assertNotNull("Present button not found after restart", presentButton)
            presentButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }
        }

        Thread.sleep(POST_PRESENT_DELAY)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed after restart. Status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )

        val verifierStatus = LocalEnterpriseTestBackend.waitForVerifierSuccess(
            config,
            backend.verifierSession.sessionId,
            httpClient,
            timeoutMs = VERIFIER_POLLING_TIMEOUT
        )
        assertTrue(
            "Verifier session was not SUCCESSFUL after restart. Status: $verifierStatus",
            verifierStatus == "SUCCESSFUL"
        )
    }

    private data class PreparedBackend(
        val offerUrl: String,
        val verifierSession: LocalEnterpriseTestBackend.VerifierSession,
    )

    private fun requireExplicitLocalEnterpriseRun(args: android.os.Bundle) {
        check(args.getString("e2e_local_enterprise") == "true") {
            """
            LocalEnterpriseBackendE2ETest is local-only and requires a provisioned Enterprise stack.
            Run it through waltid-wallet-demo-android/scripts/e2e-local-enterprise.sh.
            The script passes e2e_local_enterprise=true, validates quickstart resources, and configures emulator networking.
            """.trimIndent()
        }
    }

    private fun loadLocalEnterpriseConfig(args: android.os.Bundle): LocalEnterpriseTestBackend.BackendConfig {
        val ngrokDomain = args.getString("e2e_host_alias_domain")
            ?: error("Missing required instrumentation arg: e2e_host_alias_domain. Set HOST_ALIAS_DOMAIN in scripts/e2e.env.")

        return LocalEnterpriseTestBackend.BackendConfig(
            apiBaseUrl = args.getString("e2e_api_base_url") ?: "https://$ngrokDomain",
            ngrokBaseUrl = "https://$ngrokDomain",
            apiHostHeader = args.getString("e2e_api_host_header"),
            adminEmail = args.getString("e2e_admin_email") ?: DEFAULT_ADMIN_EMAIL,
            adminPassword = args.getString("e2e_admin_password") ?: DEFAULT_ADMIN_PASSWORD,
            org = args.getString("e2e_org") ?: DEFAULT_ORG,
            tenant = args.getString("e2e_tenant") ?: DEFAULT_TENANT,
            issuerProfile = args.getString("e2e_issuer_profile") ?: DEFAULT_ISSUER_PROFILE,
            verifier = args.getString("e2e_verifier") ?: DEFAULT_VERIFIER,
        )
    }

    private suspend fun prepareBackend(config: LocalEnterpriseTestBackend.BackendConfig): PreparedBackend {
        try {
            val token = LocalEnterpriseTestBackend.getAdminToken(config, httpClient)
            val offerUrl = LocalEnterpriseTestBackend.createPreAuthorizedOffer(config, token, httpClient)
            val verifierSession = LocalEnterpriseTestBackend.createVerifierSession(config, token, httpClient)
            return PreparedBackend(offerUrl, verifierSession)
        } catch (e: Throwable) {
            throw AssertionError(
                """
                Local Enterprise E2E environment is not ready.
                Expected quickstart baseline: org=${config.org}, tenant=${config.tenant}, issuerProfile=${config.issuerProfile}, verifier=${config.verifier}.
                From waltid-enterprise-quickstart run docker compose up, then cd cli && npm install.
                Provision with HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system,
                then HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all.
                For an existing database, rerun the --setup-all command.
                Create mobile helper resources explicitly with scripts/e2e-local-enterprise.sh --prepare-only.
                Then rerun this test through scripts/e2e-local-enterprise.sh so ngrok, issuer2-noattest, and verifier2-mobile are checked first.
                Original error: ${e.message}
                """.trimIndent(),
                e
            )
        }
    }

    private fun waitForClickableButton(device: UiDevice, text: String, timeoutMs: Long): UiObject2? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val candidates = device.findObjects(By.text(text))
            val preferred = candidates.maxByOrNull { it.visibleBounds.bottom }
            if (preferred != null) {
                val clickable = preferred.clickableAncestorOrSelf()
                if (clickable != null) return clickable
                if (text != "Present") return preferred
            }
            if (text == "Present") {
                device.swipe(540, 2000, 540, 900, 24)
            }
            Thread.sleep(500)
        }
        return null
    }

    private fun waitForOfferUrlInUi(device: UiDevice, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.textStartsWith("openid-credential-offer://")) != null) return true
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

    private fun latestStatus(device: UiDevice): String {
        val prefixes = listOf(
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
        for (prefix in prefixes) {
            val obj = device.findObject(By.textStartsWith(prefix))
            if (obj != null) return obj.text
        }
        return "UNKNOWN"
    }

    private fun waitForStatus(
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
}
