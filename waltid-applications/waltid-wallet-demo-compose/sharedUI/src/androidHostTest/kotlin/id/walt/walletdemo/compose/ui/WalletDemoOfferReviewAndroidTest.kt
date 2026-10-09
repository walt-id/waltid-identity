package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoOfferReviewAndroidTest {
    @Test fun receivedCredentialDetailsStayInTheProviderHost() =
        WalletDemoOfferReviewTestScenarios().receivedCredentialDetailsStayInTheProviderHost()

    @Test fun selectedCopiesAndTransactionCodeSurviveSheetRecreationAndSubmitOnce() =
        WalletDemoOfferReviewTestScenarios().selectedCopiesAndTransactionCodeSurviveSheetRecreationAndSubmitOnce()
}
