package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoSharingReviewControllerTest {
    private val option = WalletDemoPresentationCredentialOption("pid", "one", label = "Identity", issuer = "Issuer",
        format = "dc+sd-jwt", credentialDataJson = "{}", disclosures = listOf(
            WalletDemoPresentationDisclosure("Name", "name", "\"Ada\"", "Ada", selectivelyDisclosable = true, required = false),
            WalletDemoPresentationDisclosure("Age", "age", "42", "42", selectivelyDisclosable = true, required = true),
        ))
    private val review = WalletDemoSharingReview(WalletDemoSharingRequest(WalletDemoSharingRequester(fallbackName = "Verifier")), listOf(option))
    private val name = WalletDemoPresentationDisclosureSelection("pid", "one", "name")
    private fun consent(revision: String) = WalletDemoPaymentConsent(revision, "en", null, null, "Pay", null, false, emptyList())

    @Test fun aStaleRenderedActionCannotAuthorizeANewerResolvedSelection() = runTest {
        var revision = 0
        val owner = WalletDemoSharingReviewController(review, backgroundScope) { consent("revision-${++revision}") }
        runCurrent()
        val rendered = owner.state.value
        owner.toggleDisclosure(name)
        runCurrent()
        assertNull(owner.selectionForSubmission(rendered))
        assertEquals("revision-2", owner.selectionForSubmission(owner.state.value)?.paymentConsentRevision)
        owner.close()
    }

    @Test fun currentSelectionInvalidatesConsentAndLatePreparationCannotReplaceTheNewRevision() = runTest {
        val responses = mutableListOf<CompletableDeferred<WalletDemoPaymentConsent?>>()
        val owner = WalletDemoSharingReviewController(review, backgroundScope) {
            val response = CompletableDeferred<WalletDemoPaymentConsent?>().also(responses::add)
            withContext(NonCancellable) { response.await() }
        }
        runCurrent()
        assertNull(owner.selectionForSubmission())
        owner.toggleDisclosure(name)
        runCurrent()
        assertEquals(2, responses.size)
        responses[1].complete(consent("current")); runCurrent()
        assertEquals(setOf(name), owner.selectionForSubmission()?.disclosures)
        assertEquals("current", owner.selectionForSubmission()?.paymentConsentRevision)
        responses[0].complete(consent("stale")); runCurrent()
        assertEquals("current", owner.selectionForSubmission()?.paymentConsentRevision)
        owner.close()
        assertNull(owner.selectionForSubmission())
    }

    @Test fun closingDuringPreparationCannotReviveConsentAndRequiredOrUnknownDisclosuresAreIgnored() = runTest {
        val response = CompletableDeferred<WalletDemoPaymentConsent?>()
        val owner = WalletDemoSharingReviewController(review, backgroundScope) { withContext(NonCancellable) { response.await() } }
        runCurrent()
        owner.toggleDisclosure(name.copy(path = "age"))
        owner.toggleDisclosure(name.copy(credentialId = "unknown"))
        assertTrue(owner.state.value.selection.disclosures.isEmpty())
        owner.close()
        response.complete(consent("late")); runCurrent()
        assertNull(owner.selectionForSubmission())
    }

    @Test fun resolverFailureBlocksConfirmationWithoutLosingChoices() = runTest {
        val owner = WalletDemoSharingReviewController(review, backgroundScope) { error("Metadata integrity failed") }
        runCurrent()
        assertIs<WalletDemoPaymentReview.Blocked>(owner.state.value.payment)
        assertNull(owner.selectionForSubmission())
        owner.toggleDisclosure(name); runCurrent()
        assertEquals(setOf(name), owner.state.value.selection.disclosures)
        assertNull(owner.selectionForSubmission())
        owner.close()
    }
}
