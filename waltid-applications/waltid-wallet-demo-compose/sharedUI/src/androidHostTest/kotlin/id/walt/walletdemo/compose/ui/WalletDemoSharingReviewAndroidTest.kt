package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoSharingReviewAndroidTest {
    private val scenarios = WalletDemoSharingReviewTestScenarios()

    @Test
    fun statusConfirmationUsesOnlyDistinctSelectedCredentialsAndCapturedRevisions() = scenarios.statusConfirmationUsesOnlyDistinctSelectedCredentialsAndCapturedRevisions()

    @Test
    fun statusConfirmationInvalidatesReviewedChangesButNotUnselectedOrTimestampChanges() = scenarios.statusConfirmationInvalidatesReviewedChangesButNotUnselectedOrTimestampChanges()

    @Test
    fun unavailableStatusCannotInventAcknowledgementAndLegacyFallbackIsExplicit() = scenarios.unavailableStatusCannotInventAcknowledgementAndLegacyFallbackIsExplicit()

    @Test
    fun unsignedPaymentAndAdverseStatusUseOneDialogAndPreservePaymentActions() = scenarios.unsignedPaymentAndAdverseStatusUseOneDialogAndPreservePaymentActions()

    @Test
    fun choosingFromTheWholeRowUpdatesInformationAndSubmission() =
        scenarios.choosingFromTheWholeRowUpdatesInformationAndSubmission()

    @Test
    fun resizingAnOfferPreservesSelectionAndKeepsItsTitleReadable() =
        scenarios.resizingAnOfferPreservesSelectionAndKeepsItsTitleReadable()

    @Test
    fun recreatingSheetPreservesDisclosureChoicesAndConsentRevision() =
        scenarios.recreatingSheetPreservesDisclosureChoicesAndConsentRevision()

    @Test
    fun reviewShowsRequestedValuesWithoutExposingUnsharedStoredInformation() =
        scenarios.reviewShowsRequestedValuesWithoutExposingUnsharedStoredInformation()

    @Test
    fun unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState() =
        scenarios.unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState()

    @Test
    fun compactStatusRefreshRequiresAnExplicitActionAndRespectsHostCapability() =
        scenarios.compactStatusRefreshRequiresAnExplicitActionAndRespectsHostCapability()

    @Test
    fun paymentReviewUsesResolvedLabelsActionsAndAllFourPlacements() =
        scenarios.paymentReviewUsesResolvedLabelsActionsAndAllFourPlacements()

    @Test
    fun payeeWebsiteOpensOnlyOnExplicitTap() = scenarios.payeeWebsiteOpensOnlyOnExplicitTap()

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun paymentMoneyUsesAvailableWidthAtLargeText() = scenarios.paymentMoneyUsesAvailableWidthAtLargeText()

    @Test
    fun unavailablePaymentInstructionsBlockSubmissionWithoutGenericFallback() =
        scenarios.unavailablePaymentInstructionsBlockSubmissionWithoutGenericFallback()

    @Test
    fun digitalCredentialReviewShowsOriginTransactionDataAndEncryption() =
        scenarios.digitalCredentialReviewShowsOriginTransactionDataAndEncryption()

    @Test
    fun verifiedOriginStaysVisibleBesideSelfAssertedVerifierMetadata() =
        scenarios.verifiedOriginStaysVisibleBesideSelfAssertedVerifierMetadata()

    @Test
    fun credentialManagerReviewOffersShareAndCancelWithoutReject() =
        scenarios.credentialManagerReviewOffersShareAndCancelWithoutReject()

    @Test
    fun disabledReviewCannotBeSubmittedTwice() =
        scenarios.disabledReviewCannotBeSubmittedTwice()

    @Test
    fun reviewWithoutReaderAuthenticationShowsNoReaderSection() =
        scenarios.reviewWithoutReaderAuthenticationShowsNoReaderSection()

    @Test
    fun untrustedReaderIsDescribedAsATrustDecisionNotASignatureFailure() =
        scenarios.untrustedReaderIsDescribedAsATrustDecisionNotASignatureFailure()

    @Test
    fun trustedReaderIsNamedOnTheReview() =
        scenarios.trustedReaderIsNamedOnTheReview()

    @Test
    fun shareStaysDisabledUntilEveryRequestedDocumentHasACredential() =
        scenarios.shareStaysDisabledUntilEveryRequestedDocumentHasACredential()

    @Test
    fun choosingAnotherCredentialForOneQueryReplacesItAndItsDisclosures() =
        scenarios.choosingAnotherCredentialForOneQueryReplacesItAndItsDisclosures()

    @Test
    fun optionalDisclosuresStartOffAndTravelOnlyWhenTurnedOn() =
        scenarios.optionalDisclosuresStartOffAndTravelOnlyWhenTurnedOn()
}
