package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.activeWindow
import androidx.test.uiautomator.waitForStable
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class OperationHistoryE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val credentialCards = By.res(Pattern.compile("wallet\\.credentialCard\\..+"))
    private val historyEntries = By.res(Pattern.compile("transactionLog\\.entry\\.[0-9a-f-]{36}"))

    @Test
    fun recordsIssuancePresentationErrorPresentationSuccessAndDeletion() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        device.pressHome()
        launchAndUnlock(context, device)
        val existingCards = device.findObjects(credentialCards).map { it.resourceName }.toSet()

        val offer = DemoTestBackend.createOffer(scenario)
        sendDeepLink(context, offer.offerUrl)
        assertSurface("wallet.offerAcceptButton")
        clickByTag(device, "wallet.offerAcceptButton")
        assertSurface("issuance-done")
        clickByTag(device, "issuance-done")
        assertSurface("wallet.scanButton")
        val issuedCard = device.wait(Condition<UiDevice, String?> { current ->
            current.findObjects(credentialCards).map { it.resourceName }
                .singleOrNull { it !in existingCards }
        }, UI_ELEMENT_TIMEOUT)
        assertNotNull("The newly issued credential must appear in the wallet", issuedCard)
        val credentialTag = requireNotNull(issuedCard)
        val issuance = assertLatestHistory("Issuance", "Completed", "01-issuance", "Credentials issued")
        closeHistory()

        // A unique, unavailable document type makes the error independent of existing wallet cards.
        val unavailableQuery = buildJsonObject {
            scenario.verifierCredentialQuery.forEach { (key, value) ->
                if (key != "meta") put(key, value)
            }
            putJsonObject("meta") {
                put("doctype_value", "org.waltid.history.missing.${UUID.randomUUID()}")
            }
        }
        val failingSession = DemoTestBackend.createVerifierSession(unavailableQuery)
        sendDeepLink(context, failingSession.authorizationRequestUri)
        assertSurface("wallet.presentationError")
        assertTextVisibleAfterScrolling(
            device, listOf("OpenID4VP error: access_denied"), "Expected a missing-credential error",
        )
        clickByTag(device, "wallet.presentationErrorNotifyButton")
        assertSurface("wallet.presentationDone")
        assertTrue("The verifier must be notified of the presentation error",
            device.hasObject(By.text("Verifier notified")))
        clickByTag(device, "wallet.presentationDone")
        val presentationError = assertLatestHistory(
            "Presentation", "NotCompleted", "02-presentation-error", "Reason not completed",
        )
        assertFalse("The error must create a new history entry", presentationError == issuance)
        closeHistory()

        val session = DemoTestBackend.createVerifierSession(scenario)
        sendDeepLink(context, session.authorizationRequestUri)
        assertSurface("wallet.presentationSubmitButton")
        clickByTag(device, "wallet.presentationSubmitButton")
        assertSurface("wallet.presentationDone")
        DemoTestBackend.waitForVerifierSuccess(session.sessionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
        clickByTag(device, "wallet.presentationDone")
        val presentationSuccess = assertLatestHistory(
            "Presentation", "Completed", "03-presentation-success", "Claims shared",
        )
        assertFalse("The successful presentation must create a new entry",
            presentationSuccess in setOf(issuance, presentationError))
        closeHistory()

        clickByTag(device, credentialTag)
        assertSurface("wallet.credentialDetailsScreen")
        clickByTag(device, "wallet.detailsMenu")
        clickByTag(device, "wallet.deleteCredential")
        clickByTag(device, "wallet.deleteCredentialConfirm")
        assertSurface("wallet.scanButton")
        assertTrue("The issued credential must be removed",
            device.wait(Until.gone(By.res(credentialTag)), UI_ELEMENT_TIMEOUT))
        val deletion = assertLatestHistory("Deletion", "Completed", "04-deletion", "Credential type")
        assertFalse("Deletion must create a new history entry",
            deletion in setOf(issuance, presentationError, presentationSuccess))
        clickByTag(device, deletion)
        clickByTag(device, "transactionLog.filter.All")
        assertEquals("History must show the four new events newest first",
            listOf(deletion, presentationSuccess, presentationError, issuance),
            device.findObjects(historyEntries).take(4).map { it.resourceName })
        captureHistory("05-all-events")

        assertFilter("Issuance", listOf(issuance))
        assertFilter("Presentation", listOf(presentationSuccess, presentationError))
        assertFilter("Deletion", listOf(deletion))
        closeHistory()
    }

    private fun assertLatestHistory(type: String, result: String, screenshot: String, detail: String): String {
        clickByTag(device, "wallet.settingsButton")
        clickByTag(device, "settings.transactionLog")
        assertSurface("transactionLog.screen")
        clickByTag(device, "transactionLog.filter.All")
        assertTrue("History must contain the new $type event",
            device.wait(Until.hasObject(historyEntries), UI_ELEMENT_TIMEOUT))
        val entry = device.findObjects(historyEntries).first()
        val tag = entry.resourceName
        assertNotNull("Newest entry must be $type", entry.findObject(By.res("transactionLog.type.$type")))
        assertNotNull("Newest $type must be $result", entry.findObject(By.res("transactionLog.result.$result")))
        clickByTag(device, tag)
        assertTextVisibleAfterScrolling(device, listOf(detail), "$type details must expand")
        captureHistory(screenshot)
        return tag
    }

    private fun assertFilter(type: String, expectedEntries: List<String>) {
        clickByTag(device, "transactionLog.filter.$type")
        assertTrue("The $type filter must show the new entries in order and exclude other operation types",
            device.wait(Condition<UiDevice, Boolean> { current ->
                try {
                    val entries = current.findObjects(historyEntries)
                    entries.take(expectedEntries.size).map { it.resourceName } == expectedEntries &&
                        entries.all { it.findObject(By.res("transactionLog.type.$type")) != null }
                } catch (_: StaleObjectException) {
                    // Changing the filter replaces the lazy list's accessibility nodes.
                    false
                }
            }, UI_ELEMENT_TIMEOUT))
    }

    private fun closeHistory() {
        clickByTag(device, "wallet.settingsBack")
        clickByTag(device, "wallet.settingsBack")
        assertSurface("wallet.scanButton")
    }

    private fun assertSurface(tag: String) {
        assertNotNull("Expected $tag. Latest status: ${latestStatus(device)}",
            waitForResource(device, tag, CREDENTIAL_OPERATION_TIMEOUT))
    }

    private fun captureHistory(name: String) {
        assertSurface("transactionLog.screen")
        assertEquals("History must be captured from the wallet", context.packageName, device.currentPackageName)
        val stable = device.activeWindow().waitForStable(stableTimeoutMs = UI_ELEMENT_TIMEOUT)
        stable.screenshot?.recycle()
        assertFalse("History did not stabilize before capture", stable.isTimeout)
        val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: requireNotNull(context.getExternalFilesDir(null)) { "No screenshot output directory available" }
        val directory = File(root, "operation-history")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create $directory" }
        val target = File(directory, "$name.png")
        assertTrue("Could not capture history screenshot $target", device.takeScreenshot(target))
        println("OPERATION_HISTORY_SCREENSHOT=${target.absolutePath}")
    }
}
