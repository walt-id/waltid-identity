package id.walt.walletdemo.compose.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import android.graphics.Canvas
import androidx.compose.material3.Surface
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.wallet2.mobile.*
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class PreparedProximitySharingAndroidTest {
    @Test
    fun preparationShowsExplicitActionAndReadyStateKeepsScopeAndCancellationVisible() = runComposeUiTest {
        val field = ProximityElementReference("org.iso.18013.5.1", "given_name")
        val review = ProximityReview(ProximityReviewId(kotlin.uuid.Uuid.random().toString()), 1, listOf(
            ProximityDocumentReview(0, "org.iso.18013.5.1.mDL", listOf(ProximityCredentialOption(
                "credential", "Mobile Driving Licence", "Example issuer", Instant.parse("2030-01-01T00:00:00Z"),
                ProximityDeviceAuthenticationMethod.Signature,
                listOf(ProximityRequestedElement(field.namespace, field.elementIdentifier, false)),
            ))),
        ), listOf(ProximityReaderAuthentication(ProximityReaderAuthenticationScope.WholeRequest,
            outcome = ProximityReaderAuthenticationOutcome.Valid(ProximityReaderTrustDecision(
                ProximityReaderTrustState.Trusted, ProximityReaderCertificatePathState.Valid,
                displayName = "City service desk",
            )))), emptyList(), emptyList())
        val plan = fixturePlan(review)
        val submission = ProximitySubmission(listOf(ProximityDocumentSubmission(0, "credential", setOf(field))))
        val state = mutableStateOf(WalletDemoProximityUiState(active = true,
            sessionState = ProximityState.PreparationRequired(plan), recentPlan = plan,
            selections = listOf(WalletDemoProximityDocumentSelection(0, "credential", setOf(field)))))
        var approved = false
        var cancelled = false
        setContent {
            WalletDemoTheme {
                Surface {
                WalletDemoProximityScreen(state = state.value, credentialDetailsById = emptyMap(),
                    hostActions = WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                    onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> },
                    onContinueAfterResponseChange = {}, onApprove = { approved = true }, onDecline = {},
                    onRetry = {}, onRemediate = { _, _ -> }, onCancel = { cancelled = true },
                    onDismiss = {}, onRestart = {})
                }
            }
        }
        onNodeWithText("Review before reconnecting").assertIsDisplayed()
        onNodeWithText("City service desk").assertIsDisplayed()
        onNodeWithText("Approve and get ready").assertIsDisplayed().assertIsEnabled()
        onAllNodesWithText("Stay connected for another request").assertCountEquals(0)
        assertFalse(approved)
        capture("compose-preparation")
        onNodeWithText("Approve and get ready").performClick()
        assertTrue(approved)
        runOnIdle { state.value = state.value.copy(sessionState = ProximityState.PreparationRequired(
            plan, ProximityReviewReason.PreparedSharingChanged)) }
        onNodeWithText("The reader or request has changed. Nothing was shared using your earlier approval. Review these new details.")
            .assertIsDisplayed()
        capture("compose-changed-request")
        val sharing = assertIs<ProximityPreparationResult.Prepared>(plan.approve(submission)).sharing
        runOnIdle { state.value = state.value.copy(sessionState = ProximityState.EngagementReady(listOf(ProximityEngagement.Nfc)),
            preparedSharing = sharing, preferredEngagement = ProximityEngagementMethod.Nfc) }
        onNodeWithText("Ready for one share").assertIsDisplayed()
        onNodeWithTag("proximity-prepared-countdown").assertIsDisplayed()
        onNodeWithText("City service desk").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        capture("compose-prepared-ready")
        onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
        val receipt = ProximitySharingReceipt::class.java.declaredConstructors.single()
            .apply { isAccessible = true }.newInstance(review, submission, ProximityApprovalTiming.BeforeConnection) as ProximitySharingReceipt
        runOnIdle { state.value = state.value.copy(sessionState = ProximityState.Completed(1, false, receipt)) }
        onNodeWithText("Presentation complete").assertIsDisplayed()
        onNodeWithText("What was shared").assertIsDisplayed()
        onNodeWithText("Done").assertIsDisplayed()
        onAllNodesWithTag("proximity-prepared-countdown").assertCountEquals(0)
        capture("compose-sharing-receipt")
    }

    @Test
    fun qrFitsTheFullWalletShell() = WalletDemoAppTestScenarios().proximityQrFitsWalletChromeWithoutScrolling {
        capture("compose-$it")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun qrFitsASmallPhoneWithoutScrollingToTheCode() = WalletDemoAppTestScenarios().proximityQrFitsWalletChromeWithoutScrolling {
        capture("compose-small-$it")
    }

    @Test
    @Config(qualifiers = "w720dp-h400dp")
    fun qrUsesAvailableLandscapeSpace() = WalletDemoAppTestScenarios().proximityQrFitsWalletChromeWithoutScrolling {
        capture("compose-landscape-$it")
    }

    /** Only creates display fixtures. Signed-request SDK tests prove authorization and matching. */
    private fun fixturePlan(review: ProximityReview): ProximitySharingPlan {
        val digest = ImmutableBytes.of(ByteArray(32))
        val scope = Class.forName("id.walt.wallet2.mobile.ProximityApprovalScope")
            .declaredConstructors.single().apply { isAccessible = true }.newInstance(
                ProximityProfile.Iso180135Edition2Dis2026, "fixture-certificate", digest,
                mapOf("credential" to digest), emptyList<Any>())
        return ProximitySharingPlan::class.java.declaredConstructors.single { it.parameterCount == 8 }
            .apply { isAccessible = true }.newInstance(Any(), review, scope, null, null, null, 56, null) as ProximitySharingPlan
    }

    private fun ComposeUiTest.capture(name: String) {
        val directory = System.getenv("WALLET_UI_CAPTURE_DIR")?.let(::File) ?: return
        directory.mkdirs()
        runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = activity.findViewById<android.view.View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
