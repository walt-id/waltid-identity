package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollDown
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollUp
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** Shared, visible-label-only driver for portal2's mobile Simple mode. */
internal object Portal2DigitalCredentialE2EHelper {

    object Labels {
        const val PORTAL_TITLE = "Demo Portal"
        const val ISSUE_TAB = "Issue"
        const val VERIFY_TAB = "Verify"
        const val ISSUE_HEADING = "Choose what to issue"
        const val VERIFY_HEADING = "Choose what to verify"
        const val MDL_ISSUE_OPTION = "Mobile Drivers License"
        const val MDL_VERIFY_OPTION = "mDL over DC API"
        const val PRE_AUTHORIZED = "Pre-authorized"
        const val DC_API_DELIVERY = "Digital Credentials API"
        const val CREATE_OFFER = "Create Offer"
        const val VERIFY_OPENID4VP = "Verify with DC API (OpenID4VP)"
        const val VERIFY_ANNEX_C = "Verify with DC API (ISO 18013-7)"
        const val PRESENTATION_UNAVAILABLE = "Digital Credentials API presentation is not available"
        const val ISSUANCE_UNAVAILABLE = "Digital Credentials API issuance is not available"
        const val PRESENTATION_COMPLETED = "Completed successfully"
        const val ISSUANCE_COMPLETED = "COMPLETED"
    }

    data class DeviceCapabilities(
        val googlePlayServices: String,
        val chrome: String,
    ) {
        override fun toString(): String = "GMS=$googlePlayServices, Chrome=$chrome"
    }

    fun requirePresentationCapabilities(context: Context): DeviceCapabilities =
        requireDeviceCapabilities(
            context = context,
            minimumChromeMajor = 141,
            purpose = "Digital Credentials API presentation",
        )

    fun requireIssuanceCapabilities(context: Context): DeviceCapabilities =
        requireDeviceCapabilities(
            context = context,
            minimumChromeMajor = 143,
            purpose = "Digital Credentials API issuance",
        )

    private fun requireDeviceCapabilities(
        context: Context,
        minimumChromeMajor: Int,
        purpose: String,
    ): DeviceCapabilities {
        val gms = packageVersion(context, GMS_PACKAGE)
            ?: error("Portal2 DC API E2E requires $GMS_PACKAGE, but it is not installed or visible")
        val chrome = packageVersion(context, CHROME_PACKAGE)
            ?: error("Portal2 DC API E2E requires $CHROME_PACKAGE, but it is not installed or visible")
        val chromeMajor = chrome.substringBefore('.').toIntOrNull()
            ?: error("Could not parse the installed Chrome version '$chrome' for $purpose")
        check(chromeMajor >= minimumChromeMajor) {
            "$purpose requires Chrome $minimumChromeMajor+, but installed Chrome is $chrome (GMS=$gms)"
        }
        return DeviceCapabilities(gms, chrome)
    }

    fun openPortal(context: Context, device: UiDevice, capabilities: DeviceCapabilities) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL_URL))
                .setPackage(CHROME_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val title = device.wait(
            Until.findObject(By.pkg(CHROME_PACKAGE).text(Labels.PORTAL_TITLE)),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertNotNull(
            "portal2.demo did not load in Chrome ($capabilities). " +
                "Current package=${device.currentPackageName}; visible page text:\n${pageText(device)}",
            title,
        )
    }

    fun selectPortalTab(device: UiDevice, label: String, confirmedBy: String) {
        repeat(MAX_HYDRATION_ATTEMPTS) {
            clickPortalControl(device, label)
            if (device.wait(
                    Until.findObject(By.pkg(CHROME_PACKAGE).text(confirmedBy)),
                    HYDRATION_CONFIRM_TIMEOUT,
                ) != null
            ) return
        }
        error("Clicking portal tab '$label' never revealed '$confirmedBy'.\n${pageText(device)}")
    }

    fun clickPortalControl(device: UiDevice, label: String) {
        val control = findPortalControlAfterScrolling(device, label)
            ?: error("Portal control '$label' was not found.\n${pageText(device)}")
        assertTrue("Portal control '$label' is disabled.\n${pageText(device)}", control.isEnabled)
        control.click()
        device.waitForIdle(PAGE_SETTLE_TIMEOUT)
    }

    fun requirePortalCapability(
        device: UiDevice,
        actionLabel: String,
        unavailableText: String,
        capabilities: DeviceCapabilities,
        remediation: String,
    ) {
        val action = findPortalControlAfterScrolling(device, actionLabel)
        val unavailable = findPortalText(device, unavailableText, contains = true)
        assertNotNull(
            "Portal2 did not render '$actionLabel' ($capabilities).\n${pageText(device)}",
            action,
        )
        assertTrue(
            "Portal2 reports '$unavailableText' ($capabilities). $remediation\n${pageText(device)}",
            unavailable == null && requireNotNull(action).isEnabled,
        )
    }

    /** Chrome only shows this per-site prompt before the first browser-mediated call. */
    fun acceptChromeTrustPromptIfPresent(device: UiDevice) {
        val deadline = SystemClock.uptimeMillis() + TRUST_OR_PICKER_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            device.findObject(By.res("$CHROME_PACKAGE:id/positive_button"))?.let {
                it.click()
                device.waitForIdle()
                return
            }
            if (device.findObject(By.pkg(GMS_PACKAGE)) != null) return
            Thread.sleep(POLL_INTERVAL)
        }
    }

    fun drivePresentationPicker(device: UiDevice) {
        val picker = By.pkg(GMS_PACKAGE)
        val candidate = device.wait(
            Until.findObject(By.copy(picker).text(MDL_DOC_TYPE)),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull(
            "Credential Manager did not surface the mDL candidate.\n${foregroundText(device)}",
            candidate,
        )
        requireNotNull(candidate).interactiveAncestorOrSelf().click()
        confirmCredentialManagerIfAsked(device)
        confirmBrowserPresentationDisclosureIfAsked(device)

        assertNotNull(
            "Wallet sharing review did not open.\n${foregroundText(device)}",
            waitForResource(device, WalletDemoSharingReviewTestTags.Review, UI_ELEMENT_TIMEOUT),
        )
        clickPresentationShare(device)
    }

    fun driveIssuancePicker(device: UiDevice) {
        val picker = By.pkg(GMS_PACKAGE)
        val candidate = device.wait(
            Until.findObject(By.copy(picker).textContains("walt.id")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(
            Until.findObject(By.copy(picker).textContains("Wallet")),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull(
            "Credential Manager did not surface the wallet create option.\n${foregroundText(device)}",
            candidate,
        )
        requireNotNull(candidate).interactiveAncestorOrSelf().click()
        confirmCredentialManagerIfAsked(device)

        assertNotNull(
            "Wallet create-offer review did not open.\n${foregroundText(device)}",
            waitForResource(device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "Wallet create provider did not finish after accepting the portal offer.\n" +
                foregroundText(device),
            device.wait(Until.gone(By.res("wallet.offerReview")), UI_ELEMENT_TIMEOUT),
        )
    }

    fun awaitPortalCompletion(device: UiDevice): String {
        val completionLabels = listOf(Labels.PRESENTATION_COMPLETED, Labels.ISSUANCE_COMPLETED)
        val completed = awaitPortalText(device, completionLabels)
        assertNotNull(
            "Portal2 did not report one of $completionLabels.\n${pageText(device)}",
            completed,
        )
        return readSessionId(device)
    }

    suspend fun verifierSessionInfo(sessionId: String): JsonObject {
        HttpClient().use { client ->
            val response = client.get("$PORTAL_VERIFIER_BASE/verification-session/$sessionId/info") {
                accept(ContentType.Application.Json)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("HTTP ${response.status.value} from portal verifier session $sessionId: $body")
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    fun executedPolicyIds(element: JsonElement): Set<String> = buildSet {
        fun walk(current: JsonElement) {
            when (current) {
                is JsonObject -> {
                    current["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?.let(::add)
                    current.values.forEach(::walk)
                }
                is JsonArray -> current.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
    }

    fun failedPolicies(element: JsonElement): List<String> = buildList {
        fun walk(current: JsonElement, path: String) {
            when (current) {
                is JsonObject -> {
                    val id = current["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    if (id != null && current["success"]?.jsonPrimitive?.booleanOrNull == false) {
                        add("$path/$id: ${current["errors"]}")
                    }
                    current.forEach { (key, value) -> walk(value, "$path/$key") }
                }
                is JsonArray -> current.forEachIndexed { index, value -> walk(value, "$path[$index]") }
                else -> Unit
            }
        }
        walk(element, "")
    }

    fun snapshotCredentialCards(context: Context, device: UiDevice): Set<String> {
        WalletComposeE2EHelper.launchAndUnlock(context, device)
        clickByTag(device, "wallet.tab.credentials")
        return device.credentialCardTags()
    }

    fun assertNewMdlCredentialStored(
        context: Context,
        device: UiDevice,
        preexistingCardTags: Set<String>,
    ) {
        relaunchAndUnlock(context, device)
        clickByTag(device, "wallet.tab.credentials")
        val card = device.waitForNewCredentialCard(preexistingCardTags)
        if (card == null) {
            fail(
                "Wallet stored no new credential after portal issuance " +
                    "(before=$preexistingCardTags, after=${device.credentialCardTags()})",
            )
            return
        }
        val cardTag = requireNotNull(card.resourceName) { "Credential card is missing its test tag" }
        clickByTag(device, cardTag)
        assertNotNull(
            "Credential details did not open for $cardTag",
            waitForResource(
                device,
                cardTag.replace("wallet.credentialCard.", "wallet.credentialDetails."),
                UI_ELEMENT_TIMEOUT,
            ),
        )
        assertClaimValueVisibleAfterScrolling(
            device = device,
            path = "docType",
            label = "Doc type",
            expectedValues = listOf(MDL_DOC_TYPE),
            message = "Portal issuance stored a credential other than an mDL",
        )
    }

    private fun confirmCredentialManagerIfAsked(device: UiDevice) {
        val picker = By.pkg(GMS_PACKAGE)
        val confirm = device.wait(
            Until.findObject(By.copy(picker).res("continue_button")),
            OPTIONAL_CONFIRM_TIMEOUT,
        ) ?: device.wait(
            Until.findObject(By.copy(picker).text("Continue")),
            OPTIONAL_CONFIRM_TIMEOUT,
        )
        confirm?.interactiveAncestorOrSelf()?.click()
        device.waitForIdle()
    }

    private fun confirmBrowserPresentationDisclosureIfAsked(device: UiDevice) {
        val picker = By.pkg(GMS_PACKAGE)
        val disclosureTitle = By.copy(picker).textStartsWith("Share info with ")
        device.wait(
            Until.findObject(disclosureTitle),
            OPTIONAL_CONFIRM_TIMEOUT,
        ) ?: return
        repeat(MAX_PAGE_SCROLLS) {
            val confirmSelector = By.copy(picker).text("Agree and continue")
            device.findObject(confirmSelector)?.let {
                device.waitForIdle(PAGE_SETTLE_TIMEOUT)
                val settledConfirm = device.findObject(confirmSelector)
                    ?: error("Credential Manager presentation confirmation disappeared while settling")
                settledConfirm.interactiveAncestorOrSelf().click()
                assertTrue(
                    "Credential Manager presentation disclosure remained after confirmation.\n" +
                        foregroundText(device),
                    device.wait(Until.gone(disclosureTitle), UI_ELEMENT_TIMEOUT),
                )
                device.waitForIdle()
                return
            }
            device.scrollDown()
        }
        error(
            "Credential Manager presentation disclosure did not expose its confirmation.\n" +
                foregroundText(device),
        )
    }

    private fun clickPresentationShare(device: UiDevice) {
        repeat(MAX_PAGE_SCROLLS * 2) {
            device.findObject(By.res(WalletDemoSharingReviewTestTags.ShareButton))?.let { share ->
                assertTrue(
                    "${WalletDemoSharingReviewTestTags.ShareButton} is disabled.\n" +
                        foregroundText(device),
                    share.isEnabled,
                )
                share.interactiveAncestorOrSelf().click()
                device.waitForIdle()
                return
            }
            device.scrollDown()
        }
        error(
            "${WalletDemoSharingReviewTestTags.ShareButton} was not found after scrolling " +
                "the complete presentation review.\n${foregroundText(device)}",
        )
    }

    private fun findPortalControlAfterScrolling(device: UiDevice, label: String): UiObject2? {
        findPortalControl(device, label)?.let { return it }
        repeat(MAX_PAGE_SCROLLS) {
            device.scrollDown()
            findPortalControl(device, label)?.let { return it }
        }
        repeat(MAX_PAGE_SCROLLS * 2) {
            device.scrollUp()
            findPortalControl(device, label)?.let { return it }
        }
        return null
    }

    private fun findPortalControl(device: UiDevice, label: String): UiObject2? =
        (
            device.findObjects(By.pkg(CHROME_PACKAGE).text(label)) +
                device.findObjects(By.pkg(CHROME_PACKAGE).textStartsWith("$label "))
            )
            .mapNotNull { runCatching { it.controlAncestorOrSelf() }.getOrNull() }
            .firstOrNull()

    private fun findPortalText(device: UiDevice, text: String, contains: Boolean): UiObject2? =
        if (contains) {
            device.findObject(By.pkg(CHROME_PACKAGE).textContains(text))
        } else {
            device.findObject(By.pkg(CHROME_PACKAGE).text(text))
        }

    private fun awaitPortalText(device: UiDevice, texts: List<String>): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + CREDENTIAL_OPERATION_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            texts.firstNotNullOfOrNull { text ->
                findPortalText(device, text, contains = true)
            }?.let { return it }
            device.scrollDown()
            Thread.sleep(POLL_INTERVAL)
        }
        return null
    }

    private fun readSessionId(device: UiDevice): String {
        repeat(MAX_PAGE_SCROLLS * 2) {
            SESSION_ID_PATTERN.find(pageText(device))?.let { return it.value }
            device.scrollUp()
        }
        error("Could not read the portal session id.\n${pageText(device)}")
    }

    private fun pageText(device: UiDevice): String =
        device.findObjects(By.pkg(CHROME_PACKAGE))
            .mapNotNull { runCatching { it.text }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .ifEmpty { "(no visible Chrome text)" }

    private fun foregroundText(device: UiDevice): String =
        device.findObjects(By.depth(0))
            .flatMap { root -> runCatching { root.flatten() }.getOrDefault(emptyList()) }
            .mapNotNull { runCatching { it.text }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_DIAGNOSTIC_TEXTS)
            .joinToString("\n")
            .ifEmpty { "(no visible foreground text; package=${device.currentPackageName})" }

    private fun UiObject2.controlAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable || node.className == "android.widget.Button") return node
            node = node.parent
        }
        return null
    }

    private fun UiObject2.interactiveAncestorOrSelf(): UiObject2 {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return this
    }

    private fun UiObject2.flatten(): List<UiObject2> =
        listOf(this) + children.orEmpty().flatMap { it.flatten() }

    private fun packageVersion(context: Context, packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName ?: "<unknown>"} ($versionCode)"
    }.getOrNull()

    private fun UiDevice.waitForNewCredentialCard(known: Set<String>): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + UI_ELEMENT_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            newCredentialCard(known)?.let { return it }
            repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) {
                scrollDown()
                newCredentialCard(known)?.let { return it }
            }
            repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) { scrollUp() }
            Thread.sleep(POLL_INTERVAL)
        }
        return null
    }

    private fun UiDevice.newCredentialCard(known: Set<String>): UiObject2? =
        findObjects(By.res(CREDENTIAL_CARD_TAG))
            .firstOrNull { runCatching { it.resourceName !in known }.getOrDefault(false) }

    private fun UiDevice.credentialCardTags(): Set<String> {
        val tags = mutableSetOf<String>()
        fun collect() {
            findObjects(By.res(CREDENTIAL_CARD_TAG))
                .mapNotNullTo(tags) { runCatching { it.resourceName }.getOrNull() }
        }
        collect()
        repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) {
            scrollDown()
            collect()
        }
        repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) { scrollUp() }
        return tags
    }

    const val CHROME_PACKAGE = "com.android.chrome"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PORTAL_URL = "https://portal2.demo.walt.id/"
    const val PORTAL_VERIFIER_BASE = "https://verifier2.demo.walt.id"
    const val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"

    private const val MAX_HYDRATION_ATTEMPTS = 4
    private const val MAX_PAGE_SCROLLS = 12
    private const val MAX_DIAGNOSTIC_TEXTS = 80
    private const val CREDENTIAL_LIST_SCROLL_ATTEMPTS = 6
    private const val PAGE_SETTLE_TIMEOUT = 2_000L
    private const val HYDRATION_CONFIRM_TIMEOUT = 5_000L
    private const val OPTIONAL_CONFIRM_TIMEOUT = 3_000L
    private const val TRUST_OR_PICKER_TIMEOUT = 15_000L
    private const val POLL_INTERVAL = 500L
    private val CREDENTIAL_CARD_TAG: Pattern = Pattern.compile("wallet\\.credentialCard\\..*")
    private val SESSION_ID_PATTERN = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
    )
    private val json = Json { ignoreUnknownKeys = true }
}
