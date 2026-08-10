package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewFixtures.credentialOption
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
 * Android-only because only a platform with a back gesture can dispatch one: the gesture is delivered
 * through the host Activity's own dispatcher, so the review is hosted in a real [ComponentActivity]
 * rather than in the platform-neutral test host. What the gesture must *mean* is not Android-specific,
 * and the review under test is the shared one - these assertions are about the split the screen
 * implements, not about a second Android review.
 *
 * The outcome each case resolves to on the Credential Manager side belongs to
 * `DigitalCredentialProviderActivity`, which is what turns `onBackAtRoot` into `RESULT_CANCELED`.
 * Here the concern is only which of the three the review chooses.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoSharingReviewBackHandlingAndroidTest {

    /**
     * With credential details open the gesture is the screen's own navigation, so it closes them and
     * the host is not told anything. Reporting a host-level back here would end an OS-invoked surface
     * because the user looked at what they were about to share.
     */
    @Test
    fun backClosesCredentialDetailsWithoutLeavingTheReview() =
        runAndroidComposeUiTest<ComponentActivity> {
            var backAtRoot = 0
            var cancelled = 0
            val option = credentialOption()
            setContent {
                WalletDemoSharingReviewScreen(
                    review = digitalCredentialReview(credentialOptions = listOf(option)),
                    title = "Share digital credential?",
                    onSubmit = {},
                    onCancel = { cancelled++ },
                    onBackAtRoot = { backAtRoot++ },
                )
            }

            onNodeWithTag(WalletUiTestTags.credentialCard(option.selection.id))
                .performScrollTo()
                .performClick()
            onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()

            pressBack()

            onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
            assertEquals(0, backAtRoot)
            assertEquals(0, cancelled)
        }

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
     * A submission already in flight consumes the gesture and does nothing. The response is on its way,
     * so neither leaving the surface nor cancelling the request is an outcome the user can still be
     * given - and a host that received either would report a second, contradictory result for one
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
     * Dispatches a back gesture the way the operating system does, through the host Activity's
     * dispatcher. Compose's own test host has no back input, so the gesture has to be injected where
     * the platform injects it - which is also what proves the review registered against that
     * dispatcher rather than against a handler nothing dispatches to.
     */
    private fun AndroidComposeUiTest<ComponentActivity>.pressBack() {
        runOnUiThread { requireNotNull(activity) { "No host activity" }.onBackPressedDispatcher.onBackPressed() }
        waitForIdle()
    }
}
