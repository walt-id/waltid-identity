package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoPlatformPresentationLayoutTest {
    @Test
    fun simpleSingleCredentialReviewUsesCompactLayout() {
        assertEquals(WalletDemoPlatformPresentationLayout.Compact, simpleReview().platformPresentationLayout())
    }

    @Test
    fun credentialChoiceUsesExpandedLayout() {
        val first = option(credentialId = "credential-1")
        val second = option(credentialId = "credential-2")
        val review = simpleReview().copy(
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(
                    options = listOf(listOf(first.credentialId), listOf(second.credentialId)),
                )
            ),
        )

        assertEquals(WalletDemoPlatformPresentationLayout.Expanded, review.platformPresentationLayout())
    }

    @Test
    fun optionalDisclosureUsesExpandedLayout() {
        val disclosure = WalletDemoPresentationDisclosure(
            label = "Age over 18",
            path = "age_over_18",
            valueJson = "true",
            selectivelyDisclosable = true,
            required = false,
            selectable = true,
        )
        val review = simpleReview().copy(
            credentialOptions = listOf(option(disclosures = listOf(disclosure))),
        )

        assertEquals(WalletDemoPlatformPresentationLayout.Expanded, review.platformPresentationLayout())
    }

    @Test
    fun transactionDataUsesExpandedLayout() {
        val review = simpleReview().copy(
            request = simpleReview().request.copy(
                transactionData = listOf(ClaimGroup(title = "Payment authorization", items = emptyList())),
            ),
        )

        assertEquals(WalletDemoPlatformPresentationLayout.Expanded, review.platformPresentationLayout())
    }

    @Test
    fun readerAuthenticationStateUsesExpandedLayout() {
        val review = simpleReview().copy(
            request = simpleReview().request.copy(readerTrust = WalletDemoReaderTrust.PendingVerification),
        )

        assertEquals(WalletDemoPlatformPresentationLayout.Expanded, review.platformPresentationLayout())
    }

    private fun simpleReview(): WalletDemoSharingReview {
        val credential = option()
        return WalletDemoSharingReview(
            request = WalletDemoSharingRequest(
                verifier = WalletDemoSharingVerifier(fallbackName = "Example Verifier"),
                responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                    mechanism = WalletDemoSharingEncryptionMechanism.DcApiJwt,
                ),
            ),
            credentialOptions = listOf(credential),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf(credential.credentialId)))
            ),
        )
    }

    private fun option(
        credentialId: String = "credential-1",
        disclosures: List<WalletDemoPresentationDisclosure> = listOf(
            WalletDemoPresentationDisclosure(
                label = "Given name",
                path = "given_name",
                valueJson = "\"Ada\"",
                selectivelyDisclosable = false,
            )
        ),
    ): WalletDemoPresentationCredentialOption = WalletDemoPresentationCredentialOption(
        queryId = "identity",
        credentialId = credentialId,
        label = "Personal ID",
        issuer = "Example Issuer",
        format = "dc+sd-jwt",
        credentialDataJson = "{}",
        disclosures = disclosures,
    )
}
