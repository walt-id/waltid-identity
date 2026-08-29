package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
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
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.CredentialSummary
import id.walt.walletdemo.compose.logic.DisplayValue
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
                        listOf(
                            MobileWalletProximityEngagement.Qr("mdoc:" + "A7v9kQ2_x-".repeat(120)),
                            MobileWalletProximityEngagement.Nfc,
                        )
                    ),
                ),
                credentialDetailsById = emptyMap(),
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
        onNodeWithText("Keep this screen open while the secure connection is established.").assertIsDisplayed()
    }

    fun nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode() = runComposeUiTest {
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = MobileWalletProximityState.EngagementReady(
                        listOf(MobileWalletProximityEngagement.Nfc)
                    ),
                ),
                credentialDetailsById = emptyMap(),
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

        onNodeWithText("Hold near the reader").assertIsDisplayed()
        onNodeWithText("Hold this phone near a compatible reader and keep this screen open.").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.ProximityQr).assertCountEquals(0)
    }

    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() = runComposeUiTest {
        var toggled: MobileWalletProximityElementReference? = null
        var approved = false
        var declined = false
        var cancelled = false
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
                credentialDetailsById = proximityCredentialDetails(),
                hostActions = hostActions,
                onSelectCredential = { _, _ -> },
                onToggleElement = { _, selected -> toggled = selected },
                onContinueAfterResponseChange = { continued = it },
                onApprove = { approved = true },
                onDecline = { declined = true },
                onRetry = {},
                onRemediate = { _, _ -> },
                onCancel = { cancelled = true },
                onDismiss = {},
                onRestart = {},
            )
        }

        onNodeWithTag(WalletUiTestTags.ProximityReview).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.ProximityReaderDetails).assertCountEquals(0)
        onNodeWithText("Multiple reader identities").assertIsDisplayed()
        onNodeWithText("Valid but untrusted").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityReaderDetailsToggle).performClick()
        onNodeWithTag(WalletUiTestTags.ProximityReaderDetails).assertIsDisplayed()
        onAllNodesWithText("Valid")[0].performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Valid but untrusted").assertCountEquals(2)
        onNodeWithText("Whole request").performScrollTo().assertIsDisplayed()
        onNodeWithText("Document: Mobile Driving Licence").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Matched authority")[0].performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Good")[0].performScrollTo().assertIsDisplayed()
        onNodeWithText("A valid signature does not by itself make this reader trusted.")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Reader intends to retain this data").performScrollTo().assertIsDisplayed()
        onNodeWithText("Mobile Driving Licence").performScrollTo().assertIsDisplayed()
        onNodeWithText("Portrait").performScrollTo().assertIsDisplayed()
        onNodeWithText("Portrait available").performScrollTo().assertIsDisplayed()
        onNodeWithText("Proof of eligibility").performScrollTo().assertIsDisplayed()
        onNodeWithText("Share").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Decline").assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.proximityElement(0, namespace, "portrait"))
            .performScrollTo()
            .performClick()
        assertEquals(element, toggled)

        onNodeWithTag(WalletUiTestTags.ProximityContinueAfterResponse)
            .performScrollTo()
            .performClick()
        onNodeWithTag(WalletUiTestTags.ProximityApprove)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        onNodeWithTag(WalletUiTestTags.ProximityDecline).assertIsDisplayed().performClick()
        onNodeWithTag(WalletUiTestTags.ProximityCancel).assertIsDisplayed().performClick()
        assertTrue(approved)
        assertTrue(declined)
        assertTrue(cancelled)
        assertTrue(continued)
    }

    fun reviewDoesNotInventAnIdentityForAnUnsignedReader() = runComposeUiTest {
        val review = proximityReview().copy(
            readerAuthentication = listOf(
                MobileWalletProximityReaderAuthentication(
                    scope = MobileWalletProximityReaderAuthenticationScope.Document,
                    documentRequestIndex = 0,
                    validity = MobileWalletProximityReaderAuthenticationValidity.Absent,
                    trust = MobileWalletProximityReaderTrustState.NotEvaluated,
                    certificatePath = MobileWalletProximityReaderCertificatePathState.NotEvaluated,
                    revocation = MobileWalletProximityReaderRevocationState.NotChecked,
                    rical = MobileWalletProximityRicalState.NotEvaluated,
                )
            )
        )
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = MobileWalletProximityState.ReviewRequired(review),
                    selections = emptyList(),
                ),
                credentialDetailsById = proximityCredentialDetails(),
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

        onNodeWithTag(WalletUiTestTags.ProximityReaderSection).assertIsDisplayed()
        onNodeWithText("Reader identity not provided").assertIsDisplayed()
        onNodeWithText("This request was not signed by the reader.").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.ProximityReaderDetailsToggle).assertCountEquals(0)
        onAllNodesWithText("Unnamed reader").assertCountEquals(0)
        onAllNodesWithText("Absent").assertCountEquals(0)
    }
}

private val hostActions = WalletDemoProximityHostActionExecutor {
    MobileWalletProximityHostActionResult.Completed
}

private const val namespace = "org.iso.18013.5.1"
private const val proofNamespace = "org.waltid.example.proof"

private fun proximityCredentialDetails(): Map<String, CredentialDetails> = listOf(
    CredentialDetails(
        summary = CredentialSummary(
            id = "credential-1",
            format = "mso_mdoc",
            issuer = "Example issuer",
            label = "Mobile Driving Licence",
        ),
        groups = listOf(
            ClaimGroup(
                title = "Personal details",
                items = listOf(
                    ClaimItem(
                        path = ClaimItemPath.topLevel("$namespace.portrait"),
                        pathComponents = listOf(namespace, "portrait"),
                        label = "Portrait",
                        value = DisplayValue.DecodedText("Portrait available"),
                    )
                ),
            )
        ),
    ),
    CredentialDetails(
        summary = CredentialSummary(
            id = "proof-credential",
            format = "mso_mdoc",
            issuer = "Example issuer",
            label = "Proof of eligibility",
        ),
        groups = listOf(
            ClaimGroup(
                title = "Credential data",
                items = listOf(
                    ClaimItem(
                        path = ClaimItemPath.topLevel("$proofNamespace.eligible"),
                        pathComponents = listOf(proofNamespace, "eligible"),
                        label = "Eligible",
                        value = DisplayValue.BooleanValue(true),
                    )
                ),
            )
        ),
    ),
).associateBy { details -> details.summary.id }

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
