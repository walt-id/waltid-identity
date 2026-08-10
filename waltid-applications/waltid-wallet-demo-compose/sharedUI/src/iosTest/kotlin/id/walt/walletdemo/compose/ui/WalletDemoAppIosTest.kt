package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoAppIosTest {
    private val scenarios = WalletDemoAppTestScenarios()

    @Test
    fun pinStorageFailureStaysLockedUntilRetrySucceeds() =
        scenarios.pinStorageFailureStaysLockedUntilRetrySucceeds()

    @Test
    fun credentialsTabShowsCompactCardsAndNavigatesToDetails() =
        scenarios.credentialsTabShowsCompactCardsAndNavigatesToDetails()

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
    fun receiveAndPresentTabsExposeQrScanActions() =
        scenarios.receiveAndPresentTabsExposeQrScanActions()

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
    fun deepLinksResetReceiveAndPresentDetailStacksEvenWhenUrlIsUnchanged() =
        scenarios.deepLinksResetReceiveAndPresentDetailStacksEvenWhenUrlIsUnchanged()

    @Test
    fun credentialsPersistAcrossControllerRecreation() =
        scenarios.credentialsPersistAcrossControllerRecreation()
}
