package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoOfferReviewIosTest {
    @Test fun receivedCredentialDetailsStayInTheProviderHost() =
        WalletDemoOfferReviewTestScenarios().receivedCredentialDetailsStayInTheProviderHost()

    @Test fun selectedCopiesAndTransactionCodeSurviveHostChangesAndSubmitOnce() =
        WalletDemoOfferReviewTestScenarios().selectedCopiesAndTransactionCodeSurviveHostChangesAndSubmitOnce()
}
