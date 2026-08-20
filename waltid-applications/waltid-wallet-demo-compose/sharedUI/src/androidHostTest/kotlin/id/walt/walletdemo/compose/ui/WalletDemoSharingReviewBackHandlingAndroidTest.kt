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
 * Android-only because the gesture is delivered through the host Activity's own dispatcher, so the
 * shared review has to be hosted in a real [ComponentActivity]. Which Credential Manager outcome each
 * case resolves to belongs to `DigitalCredentialProviderActivity`; here the concern is only which of the
 * three the review chooses.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoSharingReviewBackHandlingAndroidTest {

    /**
     * The operating-system container is dismissible without turning that dismissal into a rejected
     * credential request. This preserves the provider contract: dismissal lets the platform continue,
     * while the explicit Cancel action answers the request.
     */
    @Test
    fun backAtTheSheetRootDismissesWithoutCancelling() =
        runAndroidComposeUiTest<ComponentActivity> {
            var dismissed = 0
            var cancelled = 0
            setContent {
                WalletDemoSharingReviewSheet(
                    review = digitalCredentialReview().withoutTransactionData(),
                    title = "Share digital credential?",
                    onSubmit = {},
                    onCancel = { cancelled++ },
                    onDismiss = { dismissed++ },
                )
            }

            onNodeWithTag(WalletDemoSharingReviewTestTags.Sheet).assertIsDisplayed()

            pressBack()

            waitUntil { dismissed == 1 }
            assertEquals(0, cancelled)
        }

    /** Technical details remain an internal horizontal destination even inside the platform tray. */
    @Test
    fun backFromCredentialDetailsStaysInsideTheSheet() =
        runAndroidComposeUiTest<ComponentActivity> {
            var dismissed = 0
            var cancelled = 0
            val option = credentialOption()
            setContent {
                WalletDemoSharingReviewSheet(
                    review = digitalCredentialReview(credentialOptions = listOf(option)),
                    title = "Share digital credential?",
                    onSubmit = {},
                    onCancel = { cancelled++ },
                    onDismiss = { dismissed++ },
                )
            }

            onNodeWithTag(WalletUiTestTags.credentialCard(option.selection.id))
                .performScrollTo()
                .performClick()
            onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()

            pressBack()

            onNodeWithTag(WalletDemoSharingReviewTestTags.Sheet).assertIsDisplayed()
            onNodeWithTag(WalletDemoSharingReviewTestTags.Review).assertIsDisplayed()
            assertEquals(0, dismissed)
            assertEquals(0, cancelled)
        }

    /**
     * With credential details open the gesture is the screen's own navigation: it closes them and the host
     * is told nothing, rather than ending an OS-invoked surface because the user looked at what they were
     * about to share.
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

    private fun id.walt.walletdemo.compose.logic.WalletDemoSharingReview.withoutTransactionData() = copy(
        request = request.copy(transactionData = emptyList()),
    )
}
