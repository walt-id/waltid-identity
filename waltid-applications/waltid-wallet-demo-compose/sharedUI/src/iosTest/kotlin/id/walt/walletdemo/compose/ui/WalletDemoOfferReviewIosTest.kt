package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoOfferReviewIosTest {
    @Test fun missingSavedDetailsKeepTheProviderCountAndRefreshUntilComplete() =
        WalletDemoOfferReviewTestScenarios().missingSavedDetailsKeepTheProviderCountAndRefreshUntilComplete()

    @Test fun receivedCredentialDetailsStayInTheProviderHost() =
        WalletDemoOfferReviewTestScenarios().receivedCredentialDetailsStayInTheProviderHost()

    @Test fun selectedCopiesAndTransactionCodeSurviveSheetRecreationAndSubmitOnce() =
        WalletDemoOfferReviewTestScenarios().selectedCopiesAndTransactionCodeSurviveSheetRecreationAndSubmitOnce()
}
