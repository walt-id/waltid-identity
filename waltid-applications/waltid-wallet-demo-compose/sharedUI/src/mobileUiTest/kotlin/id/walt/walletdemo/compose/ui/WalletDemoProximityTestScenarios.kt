package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.wallet2.mobile.MobileWalletProximityCredentialOption
import id.walt.wallet2.mobile.MobileWalletProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.MobileWalletProximityDocumentReview
import id.walt.wallet2.mobile.MobileWalletProximityElementReference
import id.walt.wallet2.mobile.MobileWalletProximityEngagement
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthentication
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthenticationScope
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthenticationValidity
import id.walt.wallet2.mobile.MobileWalletProximityReaderCertificatePathState
import id.walt.wallet2.mobile.MobileWalletProximityReaderRevocationState
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustState
import id.walt.wallet2.mobile.MobileWalletProximityRequestedElement
import id.walt.wallet2.mobile.MobileWalletProximityReview
import id.walt.wallet2.mobile.MobileWalletProximityRicalState
import id.walt.wallet2.mobile.MobileWalletProximityState
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class WalletDemoProximityTestScenarios {
    fun engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting() = runComposeUiTest {
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = MobileWalletProximityState.Connecting(
                        listOf(MobileWalletProximityEngagement.Qr("mdoc:" + "A7v9kQ2_x-".repeat(120)))
                    ),
                ),
                hostActions = hostActions,
                onSelectCredential = { _, _ -> },
                onToggleElement = { _, _ -> },
                onContinueAfterResponseChange = {},
                onApprove = {},
                onDecline = {},
                onRetry = {},
                onRemediate = { _, _ -> },
                onCancel = {},
                onDismiss = {},
                onRestart = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.ProximityScreen).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityQr).assertIsDisplayed()
        onNodeWithContentDescription("Device engagement QR code").assertIsDisplayed()
        onNodeWithText("Reader detected").assertIsDisplayed()
    }

    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() = runComposeUiTest {
        var toggled: MobileWalletProximityElementReference? = null
        var approved = false
        var declined = false
        var continued = false
        val review = proximityReview()
        val element = MobileWalletProximityElementReference(namespace, "portrait")
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = MobileWalletProximityState.ReviewRequired(review),
                    selections = listOf(
                        WalletDemoProximityDocumentSelection(
                            requestIndex = 0,
                            credentialId = "credential-1",
                            disclosedElements = setOf(element),
                        ),
                        WalletDemoProximityDocumentSelection(
                            requestIndex = 1,
                            credentialId = "proof-credential",
                            disclosedElements = setOf(
                                MobileWalletProximityElementReference(proofNamespace, "eligible")
                            ),
                        ),
                    ),
                ),
                hostActions = hostActions,
                onSelectCredential = { _, _ -> },
                onToggleElement = { _, selected -> toggled = selected },
                onContinueAfterResponseChange = { continued = it },
                onApprove = { approved = true },
                onDecline = { declined = true },
                onRetry = {},
                onRemediate = { _, _ -> },
                onCancel = {},
                onDismiss = {},
                onRestart = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.ProximityReview).assertIsDisplayed()
        onAllNodesWithText("Valid")[0].performScrollTo().assertIsDisplayed()
        onNodeWithText("Valid but untrusted").performScrollTo().assertIsDisplayed()
        onNodeWithText("Whole request").performScrollTo().assertIsDisplayed()
        onNodeWithText("Document: org.iso.18013.5.1.mDL").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Matched authority")[0].performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Good")[0].performScrollTo().assertIsDisplayed()
        onNodeWithText("A valid signature does not by itself make this reader trusted.")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Reader intends to retain this data").performScrollTo().assertIsDisplayed()
        onNodeWithText("Proof of eligibility").performScrollTo().assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.proximityElement(0, namespace, "portrait"))
            .performScrollTo()
            .performClick()
        assertEquals(element, toggled)

        onNodeWithTag(WalletUiTestTags.ProximityContinueAfterResponse)
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.ProximityApprove)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        onNodeWithTag(WalletUiTestTags.ProximityDecline).performScrollTo().performClick()
        assertTrue(approved)
        assertTrue(declined)
        assertTrue(continued)
    }
}

private val hostActions = WalletDemoProximityHostActionExecutor {
    MobileWalletProximityHostActionResult.Completed
}

private const val namespace = "org.iso.18013.5.1"
private const val proofNamespace = "org.waltid.example.proof"

private fun proximityReview(): MobileWalletProximityReview = MobileWalletProximityReview(
    exchange = 1,
    documents = listOf(
        MobileWalletProximityDocumentReview(
            requestIndex = 0,
            docType = "org.iso.18013.5.1.mDL",
            credentialOptions = listOf(
                MobileWalletProximityCredentialOption(
                    credentialId = "credential-1",
                    label = "Driving licence",
                    issuer = "Example issuer",
                    validUntil = Instant.DISTANT_FUTURE,
                    deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
                    requestedElements = listOf(
                        MobileWalletProximityRequestedElement(
                            namespace = namespace,
                            elementIdentifier = "portrait",
                            intentToRetain = true,
                        )
                    ),
                )
            ),
        ),
        MobileWalletProximityDocumentReview(
            requestIndex = 1,
            docType = proofNamespace,
            credentialOptions = listOf(
                MobileWalletProximityCredentialOption(
                    credentialId = "proof-credential",
                    label = "Proof of eligibility",
                    issuer = "Example issuer",
                    validUntil = Instant.DISTANT_FUTURE,
                    deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
                    requestedElements = listOf(
                        MobileWalletProximityRequestedElement(
                            namespace = proofNamespace,
                            elementIdentifier = "eligible",
                            intentToRetain = false,
                        )
                    ),
                )
            ),
        ),
    ),
    readerAuthentication = listOf(
        MobileWalletProximityReaderAuthentication(
            scope = MobileWalletProximityReaderAuthenticationScope.WholeRequest,
            documentRequestIndex = null,
            validity = MobileWalletProximityReaderAuthenticationValidity.Valid,
            trust = MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Good,
            rical = MobileWalletProximityRicalState.Matched,
            displayName = "Example reader",
            reason = "No reader trust policy is configured",
        ),
        MobileWalletProximityReaderAuthentication(
            scope = MobileWalletProximityReaderAuthenticationScope.Document,
            documentRequestIndex = 0,
            validity = MobileWalletProximityReaderAuthenticationValidity.Valid,
            trust = MobileWalletProximityReaderTrustState.Trusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Good,
            rical = MobileWalletProximityRicalState.Matched,
            displayName = "Document reader",
        ),
    ),
    useCases = emptyList(),
    applicationAuthorizations = emptyList(),
)
