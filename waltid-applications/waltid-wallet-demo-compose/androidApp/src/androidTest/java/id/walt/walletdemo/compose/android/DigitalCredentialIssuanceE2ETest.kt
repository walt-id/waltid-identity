package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.mobile.test.backend.EnterpriseMobileFixtureClient
import id.walt.mobile.test.backend.EnterpriseMobilePlatform
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceTextEquals
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.credentialCardTags
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.recreateActivity
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollDown
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollUp
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OS-mediated Digital Credentials create E2E for OpenID4VCI offers.
 *
 * Requires Google Play services, so only the dedicated Google APIs lane should run it.
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

    /** Requires the coordinated fixture's unattended test IdP; public-demo runs skip this case. */
    @Test
    fun authorizationCodeRetainsCopiesThroughBrowserReturn() = runBlocking {
        val fixtureUrl = InstrumentationRegistry.getArguments()
            .getString("enterprise_fixture_base_url")?.takeIf { it.isNotBlank() }
        assumeTrue("Requires enterprise_fixture_base_url", fixtureUrl != null)
        val backend = EnterpriseMobileFixtureClient(requireNotNull(fixtureUrl))
        val scenario = backend.scenarios().first { it.id == "enterprise-mdl-authorized" }
        val offer = backend.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val uri = Uri.parse(offer.offerUrl)
        val offerJson = uri.getQueryParameter("credential_offer") ?: withTimeout(30_000) {
            HttpClient { expectSuccess = true }.use { client ->
                client.get(requireNotNull(uri.getQueryParameter("credential_offer_uri"))).bodyAsText()
            }
        }
        assertTrue(
            "Fixture must exercise the authorization-code grant",
            Json.parseToJsonElement(offerJson).jsonObject.getValue("grants").jsonObject.containsKey("authorization_code"),
        )
        val fixture = start()
        launchOffer(fixture, offerJson)
        fixture.device.selectWalletCreateCandidate()
        fixture.device.confirmSelectorIfAsked()
        assertNotNull(
            "Authorization offer review missing",
            waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        val configurationId = "org.iso.18013.5.1.mDL"
        assertResourceVisibleAfterScrolling(fixture.device, "issuance-copies-$configurationId", "Copies missing", UI_ELEMENT_TIMEOUT)
        clickByTag(fixture.device, "issuance-more-$configurationId")
        assertResourceTextEquals(
            fixture.device, "issuance-copies-$configurationId", "Copies: 2",
            UI_ELEMENT_TIMEOUT, "Explicit batch choice was not applied",
        )
        val provider = resumedCreateProvider()
        clickByTag(fixture.device, "wallet.offerAcceptButton")
        assertNotNull(
            "Browser authorization did not return to the provider receipt",
            waitForResource(fixture.device, "wallet.provider.done", 90_000),
        )
        assertSame("Browser return must resume the original request host", provider, resumedCreateProvider())
        clickByTag(fixture.device, "wallet.provider.done")
        fixture.awaitCreateProviderCompletion()
        relaunchAndUnlock(fixture.context, fixture.device)
        assertNotNull("No credential stored after browser return",
            fixture.device.waitForNewCredentialCard(fixture.preexistingCardTags))
        val received = fixture.device.credentialCardTags() - fixture.preexistingCardTags
        assertEquals("Browser return must preserve the explicit two-copy choice", 2, received.size)
        fixture.assertStoredCredentialIs(DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" })
    }

    private fun resumedCreateProvider(): DigitalCredentialCreateActivity {
        var provider: DigitalCredentialCreateActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            provider = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<DigitalCredentialCreateActivity>().singleOrNull()
        }
        return requireNotNull(provider) { "Create provider is not resumed" }
    }

    /**
     * Mirrors the Portal2 DC API request shape, including standards-valid numeric COSE algorithm IDs
     * in the issuer's mdoc metadata.
     *
     * This protects the matcher/parser boundary: the wallet must surface, complete issuance, and
     * store the resulting mdoc when the unused numeric metadata field is present.
     */
    @Test
    fun acceptsPortalShapedOfferThroughCreateCredential() = runBlocking {
        val fixture = start()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val portalOffer = createPortalShapedOffer(scenario)

        launchOffer(fixture, portalOffer.enrichedOfferJson)

        fixture.device.selectWalletCreateCandidate()
        fixture.device.confirmSelectorIfAsked()

        assertNotNull(
            "Wallet create offer review did not open for the portal-shaped offer",
            waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(fixture.device, "wallet.offerAcceptButton")

        DemoTestBackend.waitForIssuerIssuanceSuccess(portalOffer.offerId)
        assertNotNull("Provider receipt did not open", waitForResource(fixture.device, "wallet.provider.done", UI_ELEMENT_TIMEOUT))
        clickByTag(fixture.device, "wallet.provider.done")
        fixture.awaitCreateProviderCompletion()
        fixture.assertStoredCredentialIs(scenario)
    }

    @Test
    fun acceptsPreAuthorizedOfferThroughCreateCredential() = runBlocking {
        val fixture = start()
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
        assertNotNull("Provider receipt did not open", waitForResource(fixture.device, "wallet.provider.done", UI_ELEMENT_TIMEOUT))
        recreateActivity(DigitalCredentialCreateActivity::class.java, fixture.device)
        assertNotNull("Provider receipt was lost during recreation", waitForResource(fixture.device, "wallet.provider.done", UI_ELEMENT_TIMEOUT))
        clickByTag(fixture.device, "wallet.provider.done")
        fixture.awaitCreateProviderCompletion()
        fixture.assertStoredCredentialIs(scenario)
    }

    @Test
    fun acceptsPreAuthorizedOfferWithTransactionCodeThroughCreateCredential() = runBlocking {
        val fixture = start()
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
        recreateActivity(DigitalCredentialCreateActivity::class.java, fixture.device)
        assertNotNull("Offer review was lost during recreation", waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT))
        clickByTag(fixture.device, "wallet.offerAcceptButton")

        DemoTestBackend.waitForIssuerIssuanceSuccess(offer.offerId)
        assertNotNull("Provider receipt did not open", waitForResource(fixture.device, "wallet.provider.done", UI_ELEMENT_TIMEOUT))
        clickByTag(fixture.device, "wallet.provider.done")
        fixture.awaitCreateProviderCompletion()
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

    private fun launchOffer(fixture: Fixture, offerJson: String) {
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

        // Multiple tests in this class issue the same doctype, so the card must be new to
        // distinguish this run from credentials already stored in the wallet.
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

    private class Fixture(
        val context: Context,
        val device: UiDevice,
        val preexistingCardTags: Set<String>,
    )

    /**
     * Issuer success can arrive before the provider finishes parsing and storing the credential.
     * Relaunching with FLAG_ACTIVITY_CLEAR_TASK before this sheet closes destroys the provider
     * Activity and cancels its issuance coroutine, turning a successful issuer response into an
     * empty local store. Wait for the provider-owned review to disappear before relaunching.
     */
    private fun Fixture.awaitCreateProviderCompletion() {
        val completed = device.wait(
            Until.gone(By.res("wallet.provider.done")),
            UI_ELEMENT_TIMEOUT,
        )
        if (!completed) {
            fail("Wallet create provider did not finish after issuer success")
        }
    }

    private fun start(): Fixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )
        val device = UiDevice.getInstance(instrumentation)
        launchAndUnlock(context, device)
        // Recorded before issuance so the post-flow assertion can tell this run's credential apart
        // from one an earlier test method in the same class already stored.
        return Fixture(context, device, device.credentialCardTags())
    }

    private fun hasGooglePlayServices(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        }.getOrDefault(false)

    private suspend fun createPortalShapedOffer(
        scenario: DemoTestBackend.CredentialScenario,
    ): PortalOffer {
        val generatedOffer = DemoTestBackend.createOffer(scenario, inlineOffer = true)
        val offerId = generatedOffer.offerId
        val offerUrl = generatedOffer.offerUrl
        val offerJson = requireNotNull(Uri.parse(offerUrl).getQueryParameter("credential_offer")) {
            "Demo offer was not inline (portal DC API requires BY_VALUE): $offerUrl"
        }
        val offerObject = Json.parseToJsonElement(offerJson).jsonObject
        val credentialIssuer = requireNotNull(
            offerObject["credential_issuer"]?.jsonPrimitive?.content
        ) { "Generated offer did not contain credential_issuer" }.trimEnd('/')
        val enrichedOffer = buildJsonObject {
            offerObject.forEach { (key, value) -> put(key, value) }
            // These are the two additional top-level objects Portal2 passes to Chrome.
            // Their issuer identities remain consistent with the generated inline offer.
            // They reproduce the matcher-facing request shape; the wallet engine still
            // performs its normal issuer and authorization-server metadata discovery.
            putJsonObject("credential_issuer_metadata") {
                put("credential_issuer", credentialIssuer)
                put("credential_endpoint", "$credentialIssuer/credential")
                putJsonObject("credential_configurations_supported") {
                    putJsonObject("org.iso.18013.5.1.mDL") {
                        put("format", "mso_mdoc")
                        put("doctype", "org.iso.18013.5.1.mDL")
                        putJsonArray("credential_signing_alg_values_supported") {
                            add(JsonPrimitive(-7))
                            add(JsonPrimitive(-9))
                        }
                        putJsonArray("cryptographic_binding_methods_supported") {
                            add(JsonPrimitive("cose_key"))
                        }
                        putJsonObject("proof_types_supported") {
                            putJsonObject("jwt") {
                                putJsonArray("proof_signing_alg_values_supported") {
                                    add(JsonPrimitive("ES256"))
                                    add(JsonPrimitive("EdDSA"))
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("authorization_server_metadata") {
                put("issuer", credentialIssuer)
                put("token_endpoint", "$credentialIssuer/token")
            }
        }
        return PortalOffer(offerId, enrichedOffer.toString())
    }

    private data class PortalOffer(
        val offerId: String,
        val enrichedOfferJson: String,
    )

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
