package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoSharingReviewAndroidTest {
    private val scenarios = WalletDemoSharingReviewTestScenarios()

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
}
