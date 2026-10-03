package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoSharingReviewIosTest {
    private val scenarios = WalletDemoSharingReviewTestScenarios()

    @Test
    fun changingHostPreservesDisclosureChoicesAndConsentRevision() =
        scenarios.changingHostPreservesDisclosureChoicesAndConsentRevision()

    @Test
    fun inspectingAllCredentialInformationDoesNotChangeDisclosureConsent() =
        scenarios.inspectingAllCredentialInformationDoesNotChangeDisclosureConsent()

    @Test
    fun unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState() =
        scenarios.unsignedConfirmationIsInvalidatedByNewConsentAndDisabledState()

    @Test
    fun paymentReviewUsesResolvedLabelsActionsAndAllFourPlacements() =
        scenarios.paymentReviewUsesResolvedLabelsActionsAndAllFourPlacements()

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
