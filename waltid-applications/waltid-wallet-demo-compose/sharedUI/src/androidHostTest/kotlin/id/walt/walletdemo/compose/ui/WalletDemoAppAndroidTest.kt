package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoAppAndroidTest {
    @Test fun externalOfferFailureRemainsVisibleAndCanBeCorrected() = scenarios.externalOfferFailureRemainsVisibleAndCanBeCorrected()

    @Test fun keySetupDefaultNeedsOneConfirmation() = scenarios.keySetupDefaultNeedsOneConfirmation()

    @Test fun pendingIssuanceIsReachableAndSavedDetailsDoNotResumeIt() = scenarios.pendingIssuanceIsReachableAndSavedDetailsDoNotResumeIt()

    private val scenarios = WalletDemoAppTestScenarios()

    @Test fun biometricUnlockTakesPrecedenceThenFocusesPinAfterDecline() = scenarios.biometricUnlockTakesPrecedenceThenFocusesPinAfterDecline()

    @Test fun pinSetupRequiresFourDigitsAndMatchingConfirmation() = scenarios.pinSetupRequiresFourDigitsAndMatchingConfirmation()
    @Test fun pinConfirmationPromptsBiometricsAndDeclineCompletesSetup() = scenarios.pinConfirmationPromptsBiometricsAndDeclineCompletesSetup()

    @Test fun scannerPastePreservesEditsAndNeverStartsAFlow() = scenarios.scannerPastePreservesEditsAndNeverStartsAFlow()

    @Test fun scannerResolvesWebLinksAndKeepsFailureRecoverable() = scenarios.scannerResolvesWebLinksAndKeepsFailureRecoverable()

    @Test fun scannerBackCancelsLinkResolution() = scenarios.scannerBackCancelsLinkResolution()

    @Test
    fun batchCopyControlsRequireSelectionAndRespectTheAdvertisedLimit() =
        scenarios.batchCopyControlsRequireSelectionAndRespectTheAdvertisedLimit()

    @Test
    fun keySetupGroupsChoicesAndConfirmsSelectedConfiguration() = scenarios.keySetupGroupsChoicesAndConfirmsSelectedConfiguration()

    @Test
    fun pinStorageFailureStaysLockedUntilRetrySucceeds() =
        scenarios.pinStorageFailureStaysLockedUntilRetrySucceeds()

    @Test
    fun pinSetupOffersPINOnlyWhenBiometricsAreUnavailable() =
        scenarios.pinSetupOffersPINOnlyWhenBiometricsAreUnavailable()

    @Test
    fun pinScreenRefreshesBiometricAvailabilityWhenItBecomesAvailable() =
        scenarios.pinScreenRefreshesBiometricAvailabilityWhenItBecomesAvailable()

    @Test
    fun pinSetupKeepsSubmitReachableWhenScrolled() =
        scenarios.pinSetupKeepsSubmitReachableWhenScrolled()

    @Test
    fun pinSetupDoesNotAskForSigningApproval() =
        scenarios.pinSetupDoesNotAskForSigningApproval()

    @Test
    fun credentialsTabShowsCompactCardsAndNavigatesToDetails() =
        scenarios.credentialsTabShowsCompactCardsAndNavigatesToDetails()

    @Test
    fun credentialsTabWaitsForCredentialRead() = scenarios.credentialsTabWaitsForCredentialRead()

    @Test
    fun credentialsTabDoesNotShowEmptyOnLoadFailure() = scenarios.credentialsTabDoesNotShowEmptyOnLoadFailure()

    @Test
    fun credentialsTabShowsEmptyStateAndUpdatesAfterReceive() =
        scenarios.credentialsTabShowsEmptyStateAndUpdatesAfterReceive()

    @Test
    fun receiveTabCanStartNewFlowAfterSuccess() =
        scenarios.receiveTabCanStartNewFlowAfterSuccess()

    @Test
    fun receiveDetailsStayScopedToReceiveTabNavigationStack() =
        scenarios.receiveDetailsStayScopedToReceiveTabNavigationStack()

    @Test
    fun receiveTabDisablesUrlControlsWhileReceiving() =
        scenarios.receiveTabDisablesUrlControlsWhileReceiving()

    @Test
    fun transactionCodeOfferCanBeDeclinedWithoutCode() =
        scenarios.transactionCodeOfferCanBeDeclinedWithoutCode()

    @Test
    fun authorizationCodeOfferExplainsIssuerSignIn() =
        scenarios.authorizationCodeOfferExplainsIssuerSignIn()

    @Test
    fun offerClaimsUseSemanticGroupsAndInclusionLabels() =
        scenarios.offerClaimsUseSemanticGroupsAndInclusionLabels()

    @Test
    fun scannerRoutesOfferWithoutAcceptingAndBackDiscardsReview() = scenarios.scannerRoutesOfferWithoutAcceptingAndBackDiscardsReview()

    @Test
    fun scannerBlocksUnsupportedCodesAndRecognizesInlineWebRequests() = scenarios.scannerBlocksUnsupportedCodesAndRecognizesInlineWebRequests()

    @Test
    fun walletHomeExposesUnifiedScanAndNearby() =
        scenarios.walletHomeExposesUnifiedScanAndNearby()

    @Test
    fun embeddedPresentationJourneyKeepsWalletChrome() =
        scenarios.embeddedPresentationJourneyKeepsWalletChrome()

    @Test
    fun presentTabAllowsPreviewAndDeclineWithoutCredentials() =
        scenarios.presentTabAllowsPreviewAndDeclineWithoutCredentials()

    @Test
    fun invalidPresentationCanBeDismissedLocallyOrReportedToVerifier() =
        scenarios.invalidPresentationCanBeDismissedLocallyOrReportedToVerifier()

    @Test
    fun presentTabPreviewsCredentialsAndCanStartNewFlowAfterSuccess() =
        scenarios.presentTabPreviewsCredentialsAndCanStartNewFlowAfterSuccess()

    @Test
    fun presentTabDeclineSendsProtocolRejection() =
        scenarios.presentTabDeclineSendsProtocolRejection()

    @Test
    fun presentTabShowsUnencryptedResponseState() =
        scenarios.presentTabShowsUnencryptedResponseState()

    @Test
    fun presentationDisclosureImagesRenderAsImages() =
        scenarios.presentationDisclosureImagesRenderAsImages()

    @Test
    fun presentationWithoutVerifierDisplayKeepsClientIdInTechnicalDetails() =
        scenarios.presentationWithoutVerifierDisplayKeepsClientIdInTechnicalDetails()

    @Test
    fun presentationDetailsResolveDuplicateCredentialOptionsIndependently() =
        scenarios.presentationDetailsResolveDuplicateCredentialOptionsIndependently()

    @Test
    fun presentDetailsStayScopedToPresentTabNavigationStack() =
        scenarios.presentDetailsStayScopedToPresentTabNavigationStack()

    @Test
    fun presentTabDisablesUrlControlsWhilePreviewing() =
        scenarios.presentTabDisablesUrlControlsWhilePreviewing()

    @Test
    fun deepLinksRouteToReceiveAndPresentTabs() =
        scenarios.deepLinksRouteToReceiveAndPresentTabs()

    @Test
    fun duplicateExternalLinksPreserveReviewUntilExplicitlyClosed() =
        scenarios.duplicateExternalLinksPreserveReviewUntilExplicitlyClosed()

    @Test
    fun credentialsPersistAcrossControllerRecreation() =
        scenarios.credentialsPersistAcrossControllerRecreation()

    @Test
    fun customBrandingTitleAppearsInTheHeader() =
        scenarios.customBrandingTitleAppearsInTheHeader()

    @Test
    fun sharingApprovalPreferenceIsConsistentAndPersistsAcrossJourneys() =
        scenarios.sharingApprovalPreferenceIsConsistentAndPersistsAcrossJourneys()

    @Test
    fun settingsReplacesHeaderLockAndShowsDidAndKey() =
        scenarios.settingsReplacesHeaderLockAndShowsDidAndKey()

    @Test
    fun technicalCopyPreservesFullValueWithoutChangingExpansion() =
        scenarios.technicalCopyPreservesFullValueWithoutChangingExpansion()

    @Test
    fun copyIsCancelledWhenRowLeavesComposition() = scenarios.copyIsCancelledWhenRowLeavesComposition()

    @Test
    fun readerTrustSettingsReviewAndPersistPublicCa() =
        scenarios.readerTrustSettingsReviewAndPersistPublicCa()

    @Test
    fun newUnlockAttemptPromptsBiometricsOnceAfterLock() =
        scenarios.newUnlockAttemptPromptsBiometricsOnceAfterLock()

    @Test
    fun settingsConfirmsAndAppliesSigningProtectionChange() =
        scenarios.settingsConfirmsAndAppliesSigningProtectionChange()

    @Test
    fun credentialDetailsCanCopyAndDelete() =
        scenarios.credentialDetailsCanCopyAndDelete()

    @Test
    fun deleteFromCredentialsWhileAReviewIsActive() =
        scenarios.deleteFromCredentialsWhileAReviewIsActive()

    @Test
    fun successStatusCanBeDismissedFromTheFooter() =
        scenarios.successStatusCanBeDismissedFromTheFooter()
}
