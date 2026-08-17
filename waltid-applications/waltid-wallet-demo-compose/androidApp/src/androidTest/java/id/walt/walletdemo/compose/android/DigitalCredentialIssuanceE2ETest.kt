package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollDown
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollUp
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OS-mediated Digital Credentials create E2E for OpenID4VCI pre-authorized offers.
 *
 * Requires Google Play services, so only the dedicated Play Store lane should run it.
 *
 * Success is asserted from the issuer session and the wallet's own storage, not from the
 * `CreateDigitalCredentialResponse`. The provider acknowledgment is a fixed `{"data":{}}` payload
 * built from constants in `AndroidDigitalCredentialCreateProvider`, so it is byte-identical whether
 * or not a credential was issued. It is also not reliably delivered: GMS reports the create result
 * through `reportDummyResult()`, which omits `ACTIVITY_REQUEST_CODE_TAG`, and
 * `CreateDigitalCredentialController` drops any result whose request code does not match, so the
 * `CredentialManager.createCredential` continuation is never resumed. The Get controller has a
 * branch for that case; the Create controller does not, up to and including 1.7.0-alpha03.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialIssuanceE2ETest {

    @Test
    fun acceptsPreAuthorizedOfferThroughCreateCredential() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario, inlineOffer = true)
        val offerJson = requireNotNull(Uri.parse(offer.offerUrl).getQueryParameter("credential_offer")) {
            "Demo offer URL did not carry an inline credential_offer"
        }

        DigitalCredentialTestIssuer.reset(
            requestJson = """
                {"requests":[{"protocol":"openid4vci-v1","data":$offerJson}]}
            """.trimIndent(),
        )
        fixture.device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        fixture.context.startActivity(
            Intent(fixture.context, DigitalCredentialTestIssuerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        fixture.device.selectWalletCreateCandidate()
        fixture.device.confirmSelectorIfAsked()

        assertNotNull(
            "Wallet create offer review did not open",
            waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(fixture.device, "wallet.offerAcceptButton")

        DemoTestBackend.waitForIssuerIssuanceSuccess(offer.offerId)
        fixture.assertStoredCredentialIs(scenario)
    }

    @Test
    fun acceptsPreAuthorizedOfferWithTransactionCodeThroughCreateCredential() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(
            scenario,
            withGeneratedTransactionCode = true,
            inlineOffer = true,
        )
        val txCode = requireNotNull(offer.txCode) { "Issuer did not return a transaction code" }
        val offerJson = requireNotNull(Uri.parse(offer.offerUrl).getQueryParameter("credential_offer")) {
            "Demo offer URL did not carry an inline credential_offer"
        }

        DigitalCredentialTestIssuer.reset(
            requestJson = """
                {"requests":[{"protocol":"openid4vci-v1","data":$offerJson}]}
            """.trimIndent(),
        )
        fixture.device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        fixture.context.startActivity(
            Intent(fixture.context, DigitalCredentialTestIssuerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        fixture.device.selectWalletCreateCandidate()
        fixture.device.confirmSelectorIfAsked()

        assertNotNull(
            "Wallet create offer review did not open",
            waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        assertNotNull(
            "Transaction code field was not shown",
            waitForResource(fixture.device, "wallet.txCodeInput", UI_ELEMENT_TIMEOUT),
        )
        setTextByTag(fixture.device, "wallet.txCodeInput", txCode)
        clickByTag(fixture.device, "wallet.offerAcceptButton")

        DemoTestBackend.waitForIssuerIssuanceSuccess(offer.offerId)
        fixture.assertStoredCredentialIs(scenario)
    }

    /**
     * Picks the wallet entry in the Credential Manager create picker.
     *
     * Scoped to [CREDENTIAL_SELECTOR_PACKAGE] because the wallet Activity is itself in the
     * foreground when the picker is requested, and its own header reads "walt.id Wallet": an
     * unscoped text match resolves that non-clickable TextView before the picker window is even
     * added, so the click lands nowhere and the flow never starts.
     */
    private fun UiDevice.selectWalletCreateCandidate() {
        val picker = By.pkg(CREDENTIAL_SELECTOR_PACKAGE)
        val candidate = wait(Until.findObject(By.copy(picker).textContains("walt.id")), UI_ELEMENT_TIMEOUT)
            ?: wait(Until.findObject(By.copy(picker).textContains("Wallet")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not surface the wallet create option", candidate)
        // The row's clickable node is an ancestor of the label when the picker lists candidates. On
        // builds that pre-select the only candidate the label has no clickable ancestor at all, and
        // the flow advances through the confirmation step instead, so this must not be fatal.
        val target = requireNotNull(candidate)
        (target.clickableAncestorOrSelf() ?: target).click()
        waitForIdle()
    }

    /** Some builds add a confirmation step between candidate selection and the provider. */
    private fun UiDevice.confirmSelectorIfAsked() {
        val picker = By.pkg(CREDENTIAL_SELECTOR_PACKAGE)
        val confirm = wait(Until.findObject(By.copy(picker).res("continue_button")), CONFIRM_STEP_TIMEOUT)
            ?: wait(Until.findObject(By.copy(picker).text("Continue")), CONFIRM_STEP_TIMEOUT)
        confirm?.clickableAncestorOrSelf()?.click()
        waitForIdle()
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
     * Asserts the wallet actually stored the credential [scenario] describes.
     *
     * The create flow runs in `DigitalCredentialCreateActivity`, which builds its own wallet instance
     * and never reports into the main UI's status line, so the only wallet-side proof is the stored
     * credential itself. Its card test tag is keyed by a wallet-local id, hence the tag pattern plus
     * an assertion on the rendered doctype, which is the scenario's credential configuration id.
     */
    private fun Fixture.assertStoredCredentialIs(scenario: DemoTestBackend.CredentialScenario) {
        relaunchAndUnlock(context, device)
        clickByTag(device, "wallet.tab.credentials")

        // Both methods issue the same doctype, and another test may already have stored one, so
        // existence of that doctype alone cannot prove this issuance succeeded.
        val card = device.waitForNewCredentialCard(preexistingCardTags)
        if (card == null) {
            fail(
                "Wallet stored no new credential after the create flow " +
                    "(cards before: $preexistingCardTags, after: ${device.credentialCardTags()})"
            )
            return
        }
        val cardTag = requireNotNull(card.resourceName) { "Credential card is missing its test tag" }
        // Via clickByTag rather than card.click(): the scroll sweep above may have left the node
        // stale or off screen, and clickByTag re-resolves and scrolls the tag into view.
        clickByTag(device, cardTag)

        val detailsTag = cardTag.replace("wallet.credentialCard.", "wallet.credentialDetails.")
        assertNotNull(
            "Credential details did not open for $cardTag",
            waitForResource(device, detailsTag, UI_ELEMENT_TIMEOUT),
        )
        assertClaimValueVisibleAfterScrolling(
            device = device,
            path = "docType",
            label = "Doc type",
            expectedValues = listOf(scenario.credentialConfigurationId),
            message = "Stored credential is not the ${scenario.displayName} this test issued",
        )
    }

    /**
     * The card only composes once the store reload lands, which is asynchronous after relaunch.
     *
     * The Credentials tab is a plain scrolling Column, so cards past the fold are not in the
     * accessibility tree at all. When the sharing tests have already run on the same device the
     * wallet holds several credentials and the new one is off screen, hence the scroll sweep.
     */
    private fun UiDevice.waitForNewCredentialCard(known: Set<String>): UiObject2? {
        val deadline = System.currentTimeMillis() + UI_ELEMENT_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            newCredentialCard(known)?.let { return it }
            repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) {
                scrollDown()
                newCredentialCard(known)?.let { return it }
            }
            repeat(CREDENTIAL_LIST_SCROLL_ATTEMPTS) { scrollUp() }
            Thread.sleep(500)
        }
        return null
    }

    private fun UiDevice.newCredentialCard(known: Set<String>): UiObject2? =
        findObjects(By.res(CREDENTIAL_CARD_TAG))
            .firstOrNull { runCatching { it.resourceName !in known }.getOrDefault(false) }

    /** Every card tag currently in the tree, sweeping the list so off-screen cards are included. */
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

    private class Fixture(
        val context: Context,
        val device: UiDevice,
        val preexistingCardTags: Set<String>,
    )

    private fun start(): Fixture? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )
        val device = UiDevice.getInstance(instrumentation)
        launchAndUnlock(context, device)
        // Recorded before issuance so the post-flow assertion can tell this run's credential apart
        // from one an earlier test method in the same class already stored.
        clickByTag(device, "wallet.tab.credentials")
        return Fixture(context, device, device.credentialCardTags())
    }

    private fun hasGooglePlayServices(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        }.getOrDefault(false)

    private companion object {
        /** Owns `CredentialSelectorActivity`, i.e. the picker window these tests drive. */
        private const val CREDENTIAL_SELECTOR_PACKAGE = "com.google.android.gms"

        /** Short: the confirmation step is optional, so its absence must not cost a full timeout. */
        private const val CONFIRM_STEP_TIMEOUT = 5_000L

        /** Card tags carry a wallet-local credential id, so they can only be matched by pattern. */
        private val CREDENTIAL_CARD_TAG: Pattern = Pattern.compile("wallet\\.credentialCard\\..*")

        /** Enough to walk a Credentials list holding every credential the DC API lane issues. */
        private const val CREDENTIAL_LIST_SCROLL_ATTEMPTS = 6
    }
}
