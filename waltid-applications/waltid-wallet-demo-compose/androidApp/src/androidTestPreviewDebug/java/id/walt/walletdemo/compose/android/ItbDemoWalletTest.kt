package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.itb.ItbDeviceWire
import id.walt.itb.ItbStatusObservation
import id.walt.itb.ItbWalletInteraction
import id.walt.itb.ItbWalletRejection
import id.walt.itb.ItbWalletResult
import id.walt.itb.checkStatusListReferences
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.identity.SigningIdentityState
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceAfterScrolling
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.regex.Pattern

/** Opt-in instrumentation around the installed Preview app's real entry points, UI and store. */
@RunWith(AndroidJUnit4::class)
class ItbDemoWalletTest {
    @Test
    fun blockedPaymentWithoutSelectableCredentialsReportsTheWalletReason() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(TextView(activity).apply {
                    text = "Payment instructions could not be retrieved."
                    accessibilityDelegate = object : View.AccessibilityDelegate() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.viewIdResourceName = "payment-consent-blocked"
                        }
                    }
                })
            }
            val wallet = runBlocking { createAndroidDemoMobileWallet(context, demoWalletConfig()).wallet }
            try {
                val driver = DemoInteractions(context, UiDevice.getInstance(instrumentation), wallet, true, "itb.example")
                val failure = assertThrows(ItbWalletRejection::class.java) {
                    runBlocking { driver.present("fixture", ItbWalletInteraction.PaymentReview("Coffee", "EUR", "12.34"), false) }
                }
                assertEquals("demo_payment_review_metadata_unavailable", failure.code)
            } finally {
                runBlocking { wallet.close() }
            }
        }
    }

    @Test
    fun runOwnedItbSessions() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val token = args.getString("wallet.itb.token")
        assumeTrue("Requires the explicitly selected live ITB runner", token != null)
        requireNotNull(token)
        val port = requireNotNull(args.getString("wallet.itb.port")).toInt()
        require(port in 1024..65535 && token.matches(Regex("[0-9a-f]{64}")))
        val attended = when (args.getString("wallet.itb.mode")) {
            "attended" -> true
            "unattended" -> false
            else -> error("Select attended or unattended explicitly")
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = demoWalletConfig()
        check(context.packageName == "id.walt.wallet.compose.test" && BuildConfig.DEBUG)
        check(config.signingProtectionMode == if (attended) WalletDemoSigningProtectionMode.Required
            else WalletDemoSigningProtectionMode.Disabled)
        check(BuildConfig.ITB_PAYMENT_TRUST == attended) { "Use the appropriate Preview build for the ITB mode" }
        if (!attended) {
            val biometrics = context.getSystemService(BiometricManager::class.java)
            check(biometrics.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
                "Use a disposable emulator without enrolled biometrics"
            }
        }
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.OFF
        KotlinLoggingConfiguration.logStartupMessage = false
        val device = UiDevice.getInstance(instrumentation)
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            // The launcher owns this listener and stops instrumentation if host setup fails.
            // A cold Gradle build or portal login can finish much later than an APK install.
            instrumentation.sendStatus(2, Bundle().apply { putString("itb_state", "listening") })
            withContext(Dispatchers.IO) { server.accept() }.use { socket ->
                val init = withTimeout(10_000) { ItbDeviceWire.read(socket) }
                ItbDeviceWire.authenticate(init, token)
                instrumentation.sendStatus(2, Bundle().apply { putString("itb_state", "wallet_setup") })
                val origin = ItbDeviceWire.approvedOrigin(init.getValue("origin").jsonPrimitive.content)
                val wallet = createAndroidDemoMobileWallet(context, config).wallet
                try {
                    check(wallet.credentials().isEmpty()) { "Use a fresh Preview installation for live ITB" }
                    try {
                        if (attended) {
                            WalletComposeE2EHelper.launchAndCreateScaIdentity(context, device, timedApproval = true)
                            val identity = (wallet.signingIdentity.state() as? SigningIdentityState.Active)?.identity
                            check(identity?.authorization == KeyUseAuthorizationPolicy.BiometricTimedReuse(10)) {
                                "The attended wallet did not select the 10-second biometric signing policy"
                            }
                        } else WalletComposeE2EHelper.launchAndUnlock(context, device)
                    } catch (_: AssertionError) {
                        error("Demo wallet setup failed")
                    }
                    ItbDeviceWire.write(socket, buildJsonObject {
                        put("ready", true)
                        put("provider", if (attended) "android-demo-attended" else "android-demo-unattended")
                    })
                    instrumentation.sendStatus(2, Bundle().apply { putString("itb_state", "ready") })
                    val driver = DemoInteractions(context, device, wallet, attended, origin.host)
                    try {
                        ItbDeviceWire.serve(socket, driver::execute)
                    } catch (ended: CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                } finally {
                    withContext(NonCancellable) { wallet.close() }
                }
            }
        }
    }
}

private class DemoInteractions(
    private val context: Context,
    private val device: UiDevice,
    private val wallet: MobileWallet,
    private val attended: Boolean,
    private val itbHost: String,
) {
    private val previewWalletLabel = context.applicationInfo.loadLabel(context.packageManager).toString()

    suspend fun execute(interaction: ItbWalletInteraction): ItbWalletResult = when (interaction) {
        is ItbWalletInteraction.Offer -> ui("offer") { receive(interaction) }
        is ItbWalletInteraction.CheckStatus -> ui("status") { status(interaction) }
        is ItbWalletInteraction.Presentation -> ui("presentation") {
            val payment = if (interaction.attended) {
                interaction.paymentReview ?: throw ItbWalletRejection("demo_payment_review_expectation_missing")
            } else null
            open(interaction.url.toString())
            present(interaction.credentialId, payment, provider = false)
        }
        is ItbWalletInteraction.DigitalCredentials -> ui("dc_presentation") {
            val payment = interaction.payment?.review
            enterProviderReview(WalletDemoSharingReviewTestTags.ShareButton, payment = payment != null)
            present(interaction.credentialId, payment, provider = true)
        }
        is ItbWalletInteraction.DigitalIssuance -> ui("dc_issuance") {
            val before = storedIds()
            enterProviderReview("wallet.offerAcceptButton")
            clickByTag(device, "wallet.offerAcceptButton")
            waitUntil(WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT, "demo_dc_issuance_receipt_missing") {
                requireProviderScreen()
                device.hasObject(By.res("wallet.provider.done"))
            }
            clickByTag(device, "wallet.provider.done")
            check(device.wait(Until.gone(By.res("wallet.provider.done")), 15_000))
            val credential = newCredential(before)
            val type = Json.parseToJsonElement(credential.credentialDataJson).jsonObject["vct"]?.jsonPrimitive?.content
            if (type != interaction.credentialType) throw ItbWalletRejection("demo_dc_issuance_missing_pid")
            ItbWalletResult(issuedCredentialIds = listOf(credential.id))
        }
    }

    private suspend fun receive(offer: ItbWalletInteraction.Offer): ItbWalletResult {
        val before = storedIds()
        open(offer.url.toString())
        waitFor("wallet.offerAcceptButton")
        offer.transactionCode?.let {
            WalletComposeE2EHelper.setTextByTag(device, "wallet.txCodeInput", it)
            WalletComposeE2EHelper.dismissKeyboard(device)
        }
        clickByTag(device, "wallet.offerAcceptButton")
        completeReceipt("issuance-done", "Receive failed", WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT)
        val credential = newCredential(before)
        return ItbWalletResult(issuedCredentialIds = listOf(credential.id))
    }

    suspend fun present(
        credentialId: String?,
        payment: ItbWalletInteraction.PaymentReview?,
        provider: Boolean,
    ): ItbWalletResult {
        val submit = WalletDemoSharingReviewTestTags.ShareButton
        if (payment == null) {
            waitFor(submit, code = "demo_presentation_review_missing")
        } else {
            waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_presentation_review_missing") {
                checkPaymentReview()
                device.hasObject(By.pkg(context.packageName).res(submit))
            }
        }
        if (credentialId != null) selectCredential(credentialId) { if (payment != null) checkPaymentReview() }
        if (payment != null) verifyPaymentReview(payment)
        waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_presentation_submit_disabled") {
            if (payment != null) checkPaymentReview()
            device.hasObject(By.pkg(context.packageName).res(submit).enabled(true))
        }
        if (payment != null) println("ITB_OPERATOR: wallet review verified. Approve the native signing prompt.")
        click(submit, "demo_presentation_submit_not_clickable")
        val timeout = if (payment != null) 165_000L else WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
        if (provider) {
            waitUntil(timeout, "demo_provider_presentation_not_completed") {
                requireProviderScreen()
                device.currentPackageName != context.packageName
            }
        } else completeReceipt("wallet.presentationDone", "Present failed", timeout)
        return ItbWalletResult()
    }

    private fun verifyPaymentReview(payment: ItbWalletInteraction.PaymentReview) {
        check(attended) { "Payment requires attended biometric signing" }
        if (waitForResourceAfterScrolling(device, "payment-consent", onPoll = ::checkPaymentReview) == null) {
            checkPaymentReview()
            val code = if (device.hasObject(By.res("payment-consent-loading"))) "demo_payment_review_loading"
                else "demo_payment_review_missing"
            throw ItbWalletRejection(code)
        }
        val payee = paymentFieldTexts("payee.name")
        if (payment.merchant !in payee) throw ItbWalletRejection("demo_payment_review_unexpected_payee")
        val total = paymentFieldTexts("amount")
        val amount = payment.amount.toBigDecimalOrNull()
            ?: throw ItbWalletRejection("demo_payment_review_expectation_invalid")
        if (payment.currency !in total || total.none { it.toBigDecimalOrNull()?.compareTo(amount) == 0 }) {
            throw ItbWalletRejection("demo_payment_review_unexpected_total")
        }
    }

    private fun paymentFieldTexts(field: String): List<String> = WalletComposeE2EHelper.readClaimTextsAfterScrolling(
        device, "transactionData[0].details.payload.$field", ::checkPaymentReview,
    )

    private fun checkPaymentReview() {
        device.findObject(blockedPaymentReview)?.let {
            throw ItbWalletRejection.blockedPaymentReview(it.text?.trim())
        }
        if (device.hasObject(By.pkg(context.packageName).res("payment-unsigned-warning"))) {
            throw ItbWalletRejection("demo_payment_review_unsigned_request")
        }
        if (device.hasObject(By.pkg(context.packageName).text("Couldn't update the information to share."))) {
            throw ItbWalletRejection("demo_payment_claim_review_failed")
        }
    }

    private val blockedPaymentReview = By.pkg(context.packageName).res("payment-consent-blocked")

    private suspend fun selectCredential(credentialId: String, checkReview: () -> Unit) {
        val suffix = "${credentialId.length}:$credentialId"
        val options = By.res(Pattern.compile("wallet\\.presentationCredentialToggle\\..*" + Pattern.quote(suffix)))
        var option: UiObject2? = null
        var scrolls = 0
        waitUntil(10_000, "demo_prerequisite_credential_not_available") {
            checkReview()
            option = device.findObject(options)
            if (option == null && scrolls < 5) {
                with(WalletComposeE2EHelper) { device.scrollDown() }
                scrolls++
            }
            option != null
        }
        val row = checkNotNull(option)
        // The app renders a single required match as a read-only row without a selector.
        if (!row.isClickable && !row.isCheckable) return
        if (!row.isSelected && !row.isChecked) {
            val tag = row.resourceName
            checkReview()
            click(tag, "demo_prerequisite_credential_not_clickable")
            waitUntil(10_000, "demo_credential_not_selected") {
                checkReview()
                device.findObject(By.res(tag))?.let { it.isSelected || it.isChecked } == true
            }
        }
    }

    private suspend fun status(input: ItbWalletInteraction.CheckStatus): ItbWalletResult {
        val credentials = wallet.credentials().associateBy { it.id }
        checkStatusListReferences(input.credentialIds.map { id ->
            val credential = credentials[id] ?: throw ItbWalletRejection("demo_status_credential_missing")
            Json.parseToJsonElement(credential.credentialDataJson).jsonObject["status"]
        })
        val observed = input.credentialIds.map { id ->
            openStatusDetails(id)
            val headline = By.pkg(context.packageName).res("credential-status-headline-$id")
            val disclosure = By.pkg(context.packageName).res("credential-status-details-$id")
            // When status facts can expand, the accessible disclosure row can own the heading text.
            fun displayedLabel(): String? = device.findObject(headline)?.text?.takeIf(String::isNotBlank)
                ?: device.findObject(disclosure)?.text?.takeIf(String::isNotBlank)

            val checkTag = "credential-status-check-$id"
            val enabledCheck = By.pkg(context.packageName).res(checkTag).enabled(true)
            val latest = By.pkg(context.packageName).res("credential-status-last-checked-$id")
            waitFor(checkTag, code = "demo_status_check_control_missing")
            var initial = "Other"
            var previousCheck: String? = null
            waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_status_check_not_ready") {
                val label = displayedLabel() ?: return@waitUntil false
                if (!device.hasObject(enabledCheck)) return@waitUntil false
                initial = label.takeIf { it in ItbStatusObservation.labels } ?: "Other"
                previousCheck = device.findObject(latest)?.text
                true
            }
            click(checkTag, "demo_status_check_not_clickable")
            waitUntil(35_000, "demo_status_label_or_refresh_mismatch") {
                val checked = device.findObject(latest)?.text
                displayedLabel() == input.expected.label && checked != null && checked != previousCheck
            }
            when {
                device.hasObject(By.res("wallet-detail-close")) -> clickByTag(device, "wallet-detail-close")
                device.hasObject(By.res("wallet.detailsBack")) -> clickByTag(device, "wallet.detailsBack")
                else -> device.pressBack()
            }
            waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_status_home_not_restored") {
                device.hasObject(collection) && !device.hasObject(statusDetails)
            }
            ItbStatusObservation(initial, input.expected.label)
        }
        return ItbWalletResult(statusObservations = observed)
    }

    private val collection = By.pkg(context.packageName).res("wallet.scanButton")
    private val statusDetails = By.pkg(context.packageName).res("wallet.credentialDetailsScreen")

    private suspend fun openStatusDetails(id: String) {
        val card = "wallet.credentialCard.$id"
        val cardSelector = By.pkg(context.packageName).res(card)
        fun activateCard(): Boolean {
            if (device.hasObject(statusDetails) || !device.hasObject(collection)) return false
            val node = device.findObject(cardSelector)?.accessibilityNodeInfo
            // The exact card's accessibility action works even when the cards overlap.
            return node?.isVisibleToUser == true && node.isEnabled && node.isClickable &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        waitFor(card, code = "demo_status_card_missing")
        waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_status_card_not_clickable") { activateCard() }
        // An accepted action can race with a collection redraw. Only retry from the collection.
        if (!device.wait(Until.hasObject(statusDetails), 5_000)) {
            var retryAccepted = false
            waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_status_detail_not_open") {
                val detailsOpen = device.hasObject(statusDetails)
                if (!detailsOpen && !retryAccepted) retryAccepted = activateCard()
                detailsOpen
            }
        }
        // The caller scrolls to the Check status control keyed to this exact credential.
    }

    private suspend fun enterProviderReview(reviewTag: String, payment: Boolean = false) {
        val picker = By.pkg("com.google.android.gms")
        val shareWithHost = Pattern.compile("Share\\s+info\\s+with\\s+${Pattern.quote(itbHost)}\\?")
        var selected = false
        var browserConfirmed = false
        waitUntil(if (attended) 90_000 else 40_000, "demo_dc_picker_or_review_missing") {
            requireProviderScreen()
            if (device.currentPackageName == CHROME_PACKAGE) {
                if (!attended && dismissChromeNotificationPrompt()) return@waitUntil false
                if (!browserConfirmed && confirmBrowserOrigin()) {
                    browserConfirmed = true
                    return@waitUntil false
                }
            }
            if (payment) checkPaymentReview()
            if (device.hasObject(By.pkg(context.packageName).res(reviewTag))) true else {
                val combinedHeading = device.hasObject(By.copy(picker).textStartsWith("Share info with"))
                val shareSheet = combinedHeading || device.hasObject(By.copy(picker).text("Share info"))
                val transactionSheet = device.hasObject(By.copy(picker).text("Confirm transaction"))
                val originConfirmation = shareSheet || transactionSheet
                val candidate = device.findObject(By.copy(picker).textContains(previewWalletLabel))
                val confirm = if (originConfirmation) {
                    val approvedOrigin = when {
                        transactionSheet -> attended && payment && device.hasObject(By.copy(picker).text(itbHost))
                        combinedHeading -> device.hasObject(By.copy(picker).text(shareWithHost))
                        else -> device.hasObject(By.copy(picker).text(itbHost))
                    }
                    if (approvedOrigin) {
                        device.findObject(By.copy(picker).text("Agree and continue"))
                    } else null
                } else {
                    device.findObject(By.copy(picker).text("Confirm"))
                        ?: device.findObject(By.copy(picker).text("Continue"))
                }
                when {
                    confirm?.isEnabled == true && (originConfirmation || candidate != null || selected) ->
                        (confirm.clickableAncestor() ?: confirm).click()
                    !originConfirmation && !selected && candidate != null ->
                        (candidate.clickableAncestor() ?: candidate).click().also { selected = true }
                }
                false
            }
        }
    }

    private fun dismissChromeNotificationPrompt(): Boolean =
        device.hasObject(By.pkg(CHROME_PACKAGE).text("Chrome notifications make things easier")) &&
            clickChromeButton("No thanks")

    private fun confirmBrowserOrigin(): Boolean {
        val chrome = By.pkg(CHROME_PACKAGE)
        if (!device.hasObject(By.copy(chrome).text("Do you trust this site with your data?")) ||
            !device.hasObject(By.copy(chrome).textStartsWith("$itbHost wants to use personal info"))
        ) return false
        return clickChromeButton("Continue")
    }

    private fun clickChromeButton(text: String): Boolean {
        val button = device.findObject(By.pkg(CHROME_PACKAGE).text(text))?.takeIf { it.isEnabled } ?: return false
        (button.clickableAncestor() ?: button).click()
        return true
    }

    private fun requireProviderScreen() {
        val failure = when {
            device.hasObject(By.pkg(context.packageName).text("Missing ProviderCreateCredentialRequest")) ->
                "demo_dc_create_provider_request_missing"
            device.hasObject(By.pkg(context.packageName).text("Missing ProviderGetCredentialRequest")) ->
                "demo_dc_get_provider_request_missing"
            device.hasObject(By.pkg(context.packageName).text("Unable to receive credentials")) ->
                "demo_dc_issuance_rejected"
            device.hasObject(By.pkg(context.packageName).text("Unable to share")) ->
                "demo_dc_presentation_rejected"
            else -> return
        }
        throw ItbWalletRejection(failure)
    }

    private suspend fun completeReceipt(tag: String, failure: String, timeout: Long) {
        val presentation = tag == "wallet.presentationDone"
        val done = By.pkg(context.packageName).res(tag)
        val confirmation = By.pkg(context.packageName).text("Presentation sent")
        var returnedFromBrowser = false
        waitUntil(timeout, if (presentation) "demo_presentation_receipt_missing" else "demo_issuance_receipt_missing") {
            if (presentation && !returnedFromBrowser && resumeWalletFromChrome()) {
                // Some verifier responses switch to Chrome. Resume the existing wallet task
                // to observe its receipt before acknowledging ITB.
                returnedFromBrowser = true
                return@waitUntil false
            }
            if (WalletComposeE2EHelper.latestStatus(device).startsWith(failure)) {
                throw ItbWalletRejection(if (presentation) "demo_presentation_rejected" else "demo_issuance_rejected")
            }
            device.hasObject(done) && (!presentation || device.hasObject(confirmation))
        }
        var dismissed = false
        var resumed = false
        var screen = "unknown"
        val operation = if (presentation) "presentation" else "issuance"
        waitUntil(WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT, "demo_${operation}_home_not_restored", { "_$screen" }) {
            when (device.currentPackageName) {
                CHROME_PACKAGE -> {
                    screen = "chrome"
                    if (presentation && !resumed) resumed = resumeWalletFromChrome()
                }
                context.packageName -> {
                    if (device.hasObject(done)) {
                        screen = "receipt"
                        if (!dismissed) dismissed = WalletComposeE2EHelper.clickVisibleResource(device, tag)
                    } else if (device.hasObject(By.pkg(context.packageName).res("wallet.pinInput"))) {
                        screen = "locked"
                    } else {
                        screen = "wallet"
                        return@waitUntil WalletComposeE2EHelper.findVisibleResource(device, "wallet.scanButton") != null
                    }
                }
                else -> screen = "other"
            }
            false
        }
    }

    private fun resumeWalletFromChrome(): Boolean {
        if (device.currentPackageName != CHROME_PACKAGE) return false
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launch)
        return true
    }

    private fun open(url: String) {
        ensureHome()
        // Package-scoped implicit resolution still requires the real exported intent filter.
        val uri = Uri.parse(url)
        require(uri.scheme in setOf("openid-credential-offer", "openid4vp", "eu-eaap", "haip-vp"))
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun ensureHome() {
        if (device.hasObject(By.res("wallet.scanButton"))) return
        WalletComposeE2EHelper.launch(context, if (attended) "required" else "disabled")
        WalletComposeE2EHelper.unlock(device)
    }

    private suspend fun storedIds(): Set<String> = wallet.credentials().mapTo(mutableSetOf()) { it.id }

    private suspend fun newCredential(before: Set<String>): MobileWalletCredential = withTimeout(15_000) {
        var added = wallet.credentials().filter { it.id !in before }
        while (added.isEmpty()) {
            delay(250)
            added = wallet.credentials().filter { it.id !in before }
        }
        added.singleOrNull() ?: throw ItbWalletRejection("demo_expected_one_new_credential")
    }

    private fun waitFor(tag: String, code: String = "demo_expected_screen_missing"): UiObject2 =
        waitForResourceAfterScrolling(device, tag, WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT)
            ?: throw ItbWalletRejection(code)

    private fun click(tag: String, code: String) {
        try {
            clickByTag(device, tag)
        } catch (_: AssertionError) {
            throw ItbWalletRejection(code)
        }
    }

    private suspend fun waitUntil(
        timeout: Long, code: String, detail: () -> String = { "" }, condition: suspend () -> Boolean,
    ) {
        val ready = withTimeoutOrNull(timeout) {
            while (true) {
                try {
                    if (condition()) return@withTimeoutOrNull true
                } catch (_: StaleObjectException) {
                    // Compose changed the accessibility tree. Read the current nodes.
                }
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (ready != true) throw ItbWalletRejection(code + detail())
    }

    private fun UiObject2.clickableAncestor(): UiObject2? =
        generateSequence(this) { it.parent }.firstOrNull { it.isClickable }

    private suspend fun ui(operation: String, action: suspend () -> ItbWalletResult): ItbWalletResult = try {
        action()
    } catch (_: AssertionError) {
        // Existing UI helpers include screen dumps in their diagnostics. Keep these on-device.
        throw ItbWalletRejection("demo_${operation}_ui_assertion_failed")
    }
}

private const val CHROME_PACKAGE = "com.android.chrome"
