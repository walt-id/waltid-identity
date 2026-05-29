package id.walt.walletdemo

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalEnterpriseBackendInstrumentedTest {

    private data class BackendConfig(
        val apiBaseUrl: String,
        val ngrokBaseUrl: String,
        val apiHostHeader: String?,
        val adminEmail: String,
        val adminPassword: String,
        val org: String,
        val tenant: String,
        val issuerProfile: String,
        val verifier: String,
    ) {
        val tenantPath: String get() = "$org.$tenant"
    }

    private data class VerifierSession(
        val sessionId: String,
        val bootstrapAuthorizationRequestUrl: String,
    )

    companion object {
        private const val DEFAULT_ADMIN_EMAIL = "admin@walt.id"
        private const val DEFAULT_ADMIN_PASSWORD = "admin123456"
        private const val DEFAULT_ORG = "waltid"
        private const val DEFAULT_TENANT = "waltid-tenant01"
        private const val DEFAULT_ISSUER_PROFILE = "issuer2.mdl-profile"
        private const val DEFAULT_VERIFIER = "verifier2"
    }

    @Test
    fun receiveAndPresentAgainstLocalEnterpriseBackend() {
        val args = InstrumentationRegistry.getArguments()
        val ngrokDomain = args.getString("e2e_host_alias_domain")
            ?: error("Missing required instrumentation arg: e2e_host_alias_domain")

        val config = BackendConfig(
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val token = getAdminToken(config)
        val offerUrl = createPreAuthorizedOffer(config, token)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(
                device = device,
                timeoutMs = 120_000,
                matcher = { it == "Wallet ready" },
                failurePrefixes = listOf("Bootstrap failed")
            )
        )

        val offerIntent = Intent(Intent.ACTION_VIEW, Uri.parse(offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(offerIntent)

        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForOfferUrlInUi(device, timeoutMs = 30_000)
        )

        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = 30_000)
        assertNotNull("Receive button not found", receiveButton)
        receiveButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }

        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = 220_000,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        val verifierSession = createVerifierSession(config, token)
        val presentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verifierSession.bootstrapAuthorizationRequestUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentIntent)

        val startedWithoutTap = waitForStatus(
            device = device,
            timeoutMs = 5_000,
            matcher = {
                it.startsWith("Presenting credential") ||
                    it.startsWith("Presentation sent") ||
                    it.startsWith("Presentation finished")
            },
            failurePrefixes = listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = 30_000)
            assertNotNull("Present button not found", presentButton)
            presentButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }
        }

        Thread.sleep(8_000)
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

        val verifierStatus = waitForVerifierSuccess(config, verifierSession.sessionId, timeoutMs = 220_000)
        assertTrue(
            "Verifier session was not SUCCESSFUL. Last status: $verifierStatus",
            verifierStatus == "SUCCESSFUL"
        )
    }

    @Test
    fun credentialsPersistAcrossAppRestart() {
        val args = InstrumentationRegistry.getArguments()
        val ngrokDomain = args.getString("e2e_host_alias_domain")
            ?: error("Missing required instrumentation arg: e2e_host_alias_domain")

        val config = BackendConfig(
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val token = getAdminToken(config)
        val offerUrl = createPreAuthorizedOffer(config, token)

        // --- Phase 1: Launch, bootstrap, receive credential ---
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(device, 120_000, { it == "Wallet ready" }, listOf("Bootstrap failed"))
        )

        val offerIntent = Intent(Intent.ACTION_VIEW, Uri.parse(offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(offerIntent)
        assertTrue("Offer URL did not appear", waitForOfferUrlInUi(device, 30_000))

        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = 30_000)
        assertNotNull("Receive button not found", receiveButton)
        receiveButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }

        assertTrue(
            "Receive did not complete. Status: ${latestStatus(device)}",
            waitForStatus(device, 220_000, { it.startsWith("Received") }, listOf("Receive failed", "Bootstrap failed"))
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
            waitForStatus(device, 120_000, { it == "Wallet ready" }, listOf("Bootstrap failed"))
        )

        assertTrue(
            "Credentials not displayed after restart — persistence failed",
            device.findObject(By.text("No credentials")) == null
        )

        // --- Phase 4: Present from persisted credential ---
        val verifierSession = createVerifierSession(config, token)
        val presentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verifierSession.bootstrapAuthorizationRequestUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentIntent)

        val startedWithoutTap = waitForStatus(
            device, 5_000,
            { it.startsWith("Presenting credential") || it.startsWith("Presentation sent") || it.startsWith("Presentation finished") },
            listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = 30_000)
            assertNotNull("Present button not found after restart", presentButton)
            presentButton!!.visibleBounds.let { device.click(it.centerX(), it.centerY()) }
        }

        Thread.sleep(8_000)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed after restart. Status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )

        val verifierStatus = waitForVerifierSuccess(config, verifierSession.sessionId, timeoutMs = 220_000)
        assertTrue(
            "Verifier session was not SUCCESSFUL after restart. Status: $verifierStatus",
            verifierStatus == "SUCCESSFUL"
        )
    }

    private fun getAdminToken(config: BackendConfig): String {
        val body = JSONObject()
            .put("email", config.adminEmail)
            .put("password", config.adminPassword)
            .toString()

        val response = httpJson(
            url = "${config.apiBaseUrl}/auth/account/emailpass",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json") + ngrokWarningHeader(config.apiBaseUrl),
            body = body
        )
        return response.optString("token")
            .takeIf { it.isNotBlank() }
            ?: error("Auth response missing token: $response")
    }

    private fun createPreAuthorizedOffer(config: BackendConfig, token: String): String {
        val response = httpJson(
            url = "${config.ngrokBaseUrl}/v2/${config.tenantPath}.${config.issuerProfile}/issuer-service-api/credentials/offers",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
                "ngrok-skip-browser-warning" to "true",
            ),
            body = JSONObject().put("authMethod", "PRE_AUTHORIZED").toString()
        )
        return response.optString("credentialOffer")
            .takeIf { it.isNotBlank() }
            ?: error("Offer response missing credentialOffer: $response")
    }

    private fun createVerifierSession(config: BackendConfig, token: String): VerifierSession {
        val payload = JSONObject()
            .put("flow_type", "cross_device")
            .put(
                "core_flow",
                JSONObject().put(
                    "dcql_query",
                    JSONObject().put(
                        "credentials",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "my_mdl")
                                .put("format", "mso_mdoc")
                                .put("meta", JSONObject().put("doctype_value", "org.iso.18013.5.1.mDL"))
                                .put(
                                    "claims",
                                    JSONArray()
                                        .put(JSONObject().put("path", JSONArray().put("org.iso.18013.5.1").put("family_name")))
                                        .put(JSONObject().put("path", JSONArray().put("org.iso.18013.5.1").put("given_name")))
                                )
                        )
                    )
                )
            )

        val response = httpJson(
            url = "${config.ngrokBaseUrl}/v1/${config.tenantPath}.${config.verifier}/verifier2-service-api/verification-session/create",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
                "ngrok-skip-browser-warning" to "true",
            ),
            body = payload.toString()
        )

        val sessionId = response.optString("sessionId")
        val requestUrl = response.optString("bootstrapAuthorizationRequestUrl")
        require(sessionId.isNotBlank()) { "Verifier response missing sessionId: $response" }
        require(requestUrl.isNotBlank()) { "Verifier response missing bootstrapAuthorizationRequestUrl: $response" }
        return VerifierSession(sessionId = sessionId, bootstrapAuthorizationRequestUrl = requestUrl)
    }

    private fun waitForVerifierSuccess(config: BackendConfig, sessionId: String, timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastStatus = "UNKNOWN"

        while (System.currentTimeMillis() < deadline) {
            val token = getAdminToken(config)
            val headers = mutableMapOf(
                "Authorization" to "Bearer $token",
            )
            if (!config.apiHostHeader.isNullOrBlank()) {
                headers["Host"] = config.apiHostHeader
            }
            if (config.apiBaseUrl.startsWith("https://")) {
                headers["ngrok-skip-browser-warning"] = "true"
            }
            val response = httpJson(
                url = "${config.apiBaseUrl}/v1/${config.tenantPath}.${config.verifier}.$sessionId/verifier2-service-api/verification-session/info",
                method = "GET",
                headers = headers
            )

            lastStatus = response.optJSONObject("session")?.optString("status") ?: "UNKNOWN"
            when (lastStatus.uppercase(Locale.ROOT)) {
                "SUCCESSFUL" -> return "SUCCESSFUL"
                "FAILED", "ERROR", "EXPIRED" -> return lastStatus
            }

            Thread.sleep(2_000)
        }

        return lastStatus
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

    private fun ngrokWarningHeader(baseUrl: String): Map<String, String> =
        if (baseUrl.startsWith("https://")) mapOf("ngrok-skip-browser-warning" to "true") else emptyMap()

    private fun httpJson(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val content = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        require(status in 200..299) { "HTTP $status from $url: $content" }
        return if (content.isBlank()) JSONObject() else JSONObject(content)
    }
}
