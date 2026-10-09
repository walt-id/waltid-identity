package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.digitalCredentialReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the platform back gesture does to the sharing review.
 *
 * Android-only because the gesture is delivered through the host Activity's own dispatcher, so the
 * shared review has to be hosted in a real [ComponentActivity]. Which Credential Manager outcome each
 * case resolves to belongs to `DigitalCredentialProviderActivity`; here the concern is only which of the
 * two the review chooses.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoSharingReviewBackHandlingAndroidTest {

    /**
     * At the review root the screen has nothing left to undo, so the gesture goes to the host - and to
     * [WalletDemoSharingReviewScreen]'s `onBackAtRoot`, not to `onCancel`. The two are different
     * decisions: cancelling answers the request, while backing out of this wallet's review leaves the
     * request unanswered so the platform can offer it elsewhere.
     */
    @Test
    fun backAtTheReviewRootReachesTheHostAndIsNotACancellation() =
        runAndroidComposeUiTest<ComponentActivity> {
            var backAtRoot = 0
            var cancelled = 0
            setContent {
                WalletDemoSharingReviewScreen(
                    review = digitalCredentialReview(),
                    title = "Share digital credential?",
                    onSubmit = {},
                    onCancel = { cancelled++ },
                    onBackAtRoot = { backAtRoot++ },
                )
            }

            onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()

            pressBack()

            assertEquals(1, backAtRoot)
            assertEquals(0, cancelled)
        }

    /**
     * A submission already in flight consumes the gesture and does nothing: the response is on its way,
     * and a host that received a back or a cancel now would report a second, contradictory result for one
     * request.
     */
    @Test
    fun backDuringSubmissionIsConsumedAndChangesNothing() =
        runAndroidComposeUiTest<ComponentActivity> {
            var backAtRoot = 0
            var cancelled = 0
            var submitted: WalletDemoSharingSelection? = null
            setContent {
                WalletDemoSharingReviewScreen(
                    review = digitalCredentialReview(),
                    title = "Share digital credential?",
                    enabled = false,
                    onSubmit = { submitted = it },
                    onCancel = { cancelled++ },
                    onBackAtRoot = { backAtRoot++ },
                )
            }

            onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()

            pressBack()

            assertEquals(0, backAtRoot)
            assertEquals(0, cancelled)
            assertNull(submitted)
            // Still the review: the gesture was consumed rather than passed to the host, which for a
            // provider Activity would have finished it.
            onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
        }

    /**
     * Dispatches a back gesture the way the operating system does, through the host Activity's dispatcher,
     * which also proves the review registered against that dispatcher and not against a handler nothing
     * dispatches to.
     */
    private fun AndroidComposeUiTest<ComponentActivity>.pressBack() {
        runOnUiThread { requireNotNull(activity) { "No host activity" }.onBackPressedDispatcher.onBackPressed() }
        waitForIdle()
    }
}
