package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.itb.ItbDeviceWire
import id.walt.itb.ItbStatusObservation
import id.walt.itb.ItbWalletInteraction
import id.walt.itb.ItbWalletRejection
import id.walt.itb.ItbWalletResult
import id.walt.itb.checkStatusListReferences
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceAfterScrolling
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket

/** Opt-in instrumentation around the installed Preview app's real entry points, UI and store. */
@RunWith(AndroidJUnit4::class)
class ItbDemoWalletTest {
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
            server.soTimeout = 120_000
            instrumentation.sendStatus(2, Bundle().apply { putString("itb_state", "listening") })
            withContext(Dispatchers.IO) { server.accept() }.use { socket ->
                val init = withTimeout(10_000) { ItbDeviceWire.read(socket) }
                ItbDeviceWire.authenticate(init, token)
                val origin = ItbDeviceWire.approvedOrigin(init.getValue("origin").jsonPrimitive.content)
                val wallet = createAndroidDemoMobileWallet(context, config).wallet
                try {
                    check(wallet.credentials().isEmpty()) { "Use a fresh Preview installation for live ITB" }
                    try {
                        if (attended) WalletComposeE2EHelper.launchAndCreateScaIdentity(context, device)
                        else WalletComposeE2EHelper.launchAndUnlock(context, device)
                    } catch (_: AssertionError) {
                        error("Demo wallet setup failed")
                    }
                    ItbDeviceWire.write(socket, buildJsonObject {
                        put("ready", true)
                        put("provider", if (attended) "android-demo-attended" else "android-demo-unattended")
                    })
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
    suspend fun execute(interaction: ItbWalletInteraction): ItbWalletResult = when (interaction) {
        is ItbWalletInteraction.Offer -> ui("offer") { receive(interaction) }
        is ItbWalletInteraction.CheckStatus -> ui("status") { status(interaction) }
        is ItbWalletInteraction.Presentation -> ui("presentation") {
            open(interaction.url.toString())
            present(interaction.credentialId, interaction.attended, provider = false)
        }
        is ItbWalletInteraction.DigitalCredentials -> ui("dc_presentation") {
            enterProviderReview("wallet.presentationSubmitButton")
            present(interaction.credentialId, interaction.payment != null, provider = true)
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

    private suspend fun present(credentialId: String?, payment: Boolean, provider: Boolean): ItbWalletResult {
        waitFor("wallet.presentationSubmitButton")
        if (credentialId != null) selectCredential(credentialId)
        if (payment) {
            check(attended) { "Payment requires operator consent" }
            waitFor("payment-consent")
            println("ITB_OPERATOR: review the payee and amount in the wallet, then confirm and approve the native signing prompt.")
        } else clickByTag(device, "wallet.presentationSubmitButton")
        val timeout = if (payment) 165_000L else WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
        if (provider) {
            waitUntil(timeout, "demo_provider_presentation_not_completed") {
                requireProviderScreen()
                device.currentPackageName != context.packageName
            }
        } else completeReceipt("wallet.presentationDone", "Present failed", timeout)
        return ItbWalletResult()
    }

    private suspend fun selectCredential(credentialId: String) {
        val suffix = "${credentialId.length}:$credentialId"
        val options = By.res(java.util.regex.Pattern.compile("wallet\\.presentationCredentialToggle\\..*" +
            java.util.regex.Pattern.quote(suffix)))
        var node = device.wait(Until.findObject(options), 5_000)
        if (node == null) {
            repeat(5) {
                if (node == null) {
                    with(WalletComposeE2EHelper) { device.scrollDown() }
                    node = device.findObject(options)
                }
            }
        }
        val option = node ?: throw ItbWalletRejection("demo_prerequisite_credential_not_available")
        // The app renders a single required match as a read-only row without a selector.
        if (!option.isClickable && !option.isCheckable) return
        if (!option.isSelected && !option.isChecked) {
            val tag = option.resourceName
            clickByTag(device, tag)
            waitUntil(10_000, "demo_credential_not_selected") {
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
            waitFor("wallet.credentialCard.$id")
            WalletComposeE2EHelper.tapStoredCard(device, "wallet.credentialCard.$id")
            val headline = "credential-status-headline-$id"
            val initial = waitFor(headline).text.takeIf { it in ItbStatusObservation.labels } ?: "Other"
            val latest = "credential-status-last-checked-$id"
            val previousCheck = device.findObject(By.res(latest))?.text
            clickByTag(device, "credential-status-check-$id")
            waitUntil(35_000, "demo_status_label_or_refresh_mismatch") {
                val label = device.findObject(By.res(headline))?.text
                val checked = device.findObject(By.res(latest))?.text
                label == input.expected.label && checked != null && checked != previousCheck
            }
            when {
                device.hasObject(By.res("wallet-detail-close")) -> clickByTag(device, "wallet-detail-close")
                device.hasObject(By.res("wallet.detailsBack")) -> clickByTag(device, "wallet.detailsBack")
                else -> device.pressBack()
            }
            waitFor("wallet.scanButton")
            ItbStatusObservation(initial, input.expected.label)
        }
        return ItbWalletResult(statusObservations = observed)
    }

    private suspend fun enterProviderReview(reviewTag: String) {
        val picker = By.pkg("com.google.android.gms")
        var selected = false
        var browserConfirmed = false
        waitUntil(if (attended) 90_000 else 40_000, "demo_dc_picker_or_review_missing") {
            requireProviderScreen()
            if (!attended && !browserConfirmed && device.currentPackageName == CHROME_PACKAGE && confirmBrowserOrigin()) {
                browserConfirmed = true
                return@waitUntil false
            }
            if (device.hasObject(By.res(reviewTag))) true else {
                val candidate = device.findObject(By.copy(picker).textContains("walt.id Wallet"))
                val confirm = device.findObject(By.copy(picker).text("Confirm"))
                    ?: device.findObject(By.copy(picker).text("Continue"))
                when {
                    confirm?.isEnabled == true && (candidate != null || selected) ->
                        (confirm.clickableAncestor() ?: confirm).click()
                    !selected && candidate != null -> (candidate.clickableAncestor() ?: candidate).click().also { selected = true }
                }
                false
            }
        }
    }

    private fun confirmBrowserOrigin(): Boolean {
        val chrome = By.pkg(CHROME_PACKAGE)
        if (!device.hasObject(By.copy(chrome).text("Do you trust this site with your data?")) ||
            !device.hasObject(By.copy(chrome).textStartsWith("$itbHost wants to use personal info"))
        ) return false
        val proceed = device.findObject(By.copy(chrome).text("Continue"))?.takeIf { it.isEnabled } ?: return false
        (proceed.clickableAncestor() ?: proceed).click()
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
        var returnedFromBrowser = false
        waitUntil(timeout, "demo_receipt_missing") {
            if (presentation && !returnedFromBrowser && device.currentPackageName == CHROME_PACKAGE) {
                // Some verifier responses switch to Chrome. Resume the existing wallet task
                // to observe its receipt before acknowledging ITB.
                val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                context.startActivity(launch)
                returnedFromBrowser = true
                return@waitUntil false
            }
            if (WalletComposeE2EHelper.latestStatus(device).startsWith(failure)) {
                throw ItbWalletRejection(if (presentation) "demo_presentation_rejected" else "demo_issuance_rejected")
            }
            val receiptVisible = device.hasObject(By.res(tag))
            if (presentation && receiptVisible && !device.hasObject(By.text("Presentation sent"))) {
                throw ItbWalletRejection("demo_presentation_rejected")
            }
            receiptVisible
        }
        clickByTag(device, tag)
        waitFor("wallet.scanButton")
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

    private fun waitFor(tag: String, timeout: Long = WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT): UiObject2 =
        waitForResourceAfterScrolling(device, tag, timeout) ?: throw ItbWalletRejection("demo_expected_screen_missing")

    private suspend fun waitUntil(timeout: Long, code: String, condition: suspend () -> Boolean) {
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
        if (ready != true) throw ItbWalletRejection(code)
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
