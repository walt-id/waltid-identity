package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf

import id.walt.wallet2.mobile.ProximityReaderTrustDecision
import id.walt.wallet2.mobile.ProximityReaderAuthenticationOutcome
import id.walt.wallet2.mobile.ProximityReviewId
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityRuntimeObservation
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
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityErrorCategory
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityReaderAuthentication
import id.walt.wallet2.mobile.ProximityReaderAuthenticationScope
import id.walt.wallet2.mobile.ProximityReaderCertificatePathState
import id.walt.wallet2.mobile.ProximityReaderRevocationState
import id.walt.wallet2.mobile.ProximityReaderTrustState
import id.walt.wallet2.mobile.ProximityRequestedElement
import id.walt.wallet2.mobile.ProximityProfile
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximityRicalState
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.ProximityTransportCapability
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
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class WalletDemoProximityTestScenarios {
    fun userFixedPermissionShowsSettingsWithoutChangingTheSdkAction() = runComposeUiTest {
        var remediated: ProximityRemediationAction? = null
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = ProximityState.CheckingPrerequisites(permissionBlockedCapabilities),
                ),
                credentialDetailsById = emptyMap(),
                hostActions = hostActions,
                hostActionForDisplay = { action ->
                    if (action == ProximityRemediationAction.RequestBluetoothPermission) {
                        ProximityRemediationAction.OpenApplicationSettings
                    } else {
                        action
                    }
                },
                onSelectCredential = { _, _ -> },
                onToggleElement = { _, _ -> },
                onContinueAfterResponseChange = {},
                onApprove = {},
                onDecline = {},
                onRetry = {},
                onRemediate = { action, _ -> remediated = action },
                onCancel = {},
                onDismiss = {},
                onRestart = {},
            )
        }

        onNodeWithText("Open app settings").assertIsDisplayed().performClick()
        assertEquals(ProximityRemediationAction.RequestBluetoothPermission, remediated)
    }

    fun engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting() = runComposeUiTest {
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = ProximityState.Connecting(
                        listOf(ProximityEngagement.Qr("mdoc:" + "A7v9kQ2_x-".repeat(120)))
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
    }

    fun completedPresentationShowsDoneAndNoConnectionControls() = runComposeUiTest {
        val sessionState = mutableStateOf<ProximityState>(ProximityState.Completed(1, false))
        setContent {
            MaterialTheme {
                WalletDemoProximityScreen(WalletDemoProximityUiState(active = true,
                    sessionState = sessionState.value), emptyMap(), hostActions,
                    onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> },
                    onContinueAfterResponseChange = {}, onApprove = {}, onDecline = {}, onRetry = {},
                    onRemediate = { _, _ -> }, onCancel = {}, onDismiss = {}, onRestart = {})
            }
        }
        onNodeWithText("Presentation complete").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed()
        onAllNodesWithText("Connection settings").assertCountEquals(0)
        onAllNodesWithTag("proximity-show-Qr").assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.ProximityQr).assertCountEquals(0)
        runOnIdle { sessionState.value = ProximityState.NoData(2) }
        onNodeWithText("No data shared").assertIsDisplayed()
        onNodeWithText("No credential data was shared for this request.").assertIsDisplayed()
        onAllNodesWithText("Presentation complete").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed()
    }

    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() = runComposeUiTest {
        var toggled: ProximityElementReference? = null
        var approved = false
        var declined = false
        var cancelled = false
        var continued = false
        val review = proximityReview()
        val element = ProximityElementReference(namespace, "portrait")
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = ProximityState.ReviewRequired(review),
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
                                ProximityElementReference(proofNamespace, "eligible")
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
        onAllNodesWithText("Authentication missing for part of the request").assertCountEquals(0)
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
                ProximityReaderAuthentication(
            scope = ProximityReaderAuthenticationScope.Document(0),
            outcome = ProximityReaderAuthenticationOutcome.Absent,
        )
            )
        )
        setContent {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(
                    active = true,
                    sessionState = ProximityState.ReviewRequired(review),
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
    ProximityHostActionResult.Completed
}

private val permissionBlockedCapabilities = ProximityCapabilities(
    session = id.walt.wallet2.mobile.ProximitySessionConfiguration.Qr(),
    profile = ProximityProfile.Iso180135Edition2Dis2026,
    qrEngagement = ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        selected = true,
        runtime = ProximityRuntimeObservation.Available,
    ),
    nfcEngagement = availableUnselectedCapability(),
    bluetoothLowEnergy = ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        selected = true,
        runtime = ProximityRuntimeObservation.Unavailable(ProximityError(
            category = ProximityErrorCategory.Capability,
            code = "bluetooth_permission_required",
            message = "Bluetooth permission is required",
            recovery = ProximityRecovery.RetryPrerequisites,
        ), listOf(ProximityRemediationAction.RequestBluetoothPermission)),
    ),
    nfcRetrieval = availableUnselectedCapability(),
    nfcV2Retrieval = availableUnselectedCapability(),
    wifiAwareRetrieval = availableUnselectedCapability(),
)

private fun availableUnselectedCapability() = ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        selected = false,
        runtime = ProximityRuntimeObservation.Available,
    )

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

private fun proximityReview(): ProximityReview = ProximityReview(
    reviewId = ProximityReviewId(Uuid.random().toString()),
    exchange = 1,
    documents = listOf(
        ProximityDocumentReview(
            requestIndex = 0,
            docType = "org.iso.18013.5.1.mDL",
            credentialOptions = listOf(
                ProximityCredentialOption(
                    credentialId = "credential-1",
                    label = "Driving licence",
                    issuer = "Example issuer",
                    validUntil = Instant.DISTANT_FUTURE,
                    deviceAuthentication = ProximityDeviceAuthenticationMethod.Signature,
                    requestedElements = listOf(
                        ProximityRequestedElement(
                            namespace = namespace,
                            elementIdentifier = "portrait",
                            intentToRetain = true,
                        )
                    ),
                )
            ),
        ),
        ProximityDocumentReview(
            requestIndex = 1,
            docType = proofNamespace,
            credentialOptions = listOf(
                ProximityCredentialOption(
                    credentialId = "proof-credential",
                    label = "Proof of eligibility",
                    issuer = "Example issuer",
                    validUntil = Instant.DISTANT_FUTURE,
                    deviceAuthentication = ProximityDeviceAuthenticationMethod.Signature,
                    requestedElements = listOf(
                        ProximityRequestedElement(
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
        ProximityReaderAuthentication(
            scope = ProximityReaderAuthenticationScope.Document(1),
            outcome = ProximityReaderAuthenticationOutcome.Absent,
        ),
        ProximityReaderAuthentication(
            scope = ProximityReaderAuthenticationScope.WholeRequest,
            outcome = ProximityReaderAuthenticationOutcome.Valid(ProximityReaderTrustDecision(
                state = ProximityReaderTrustState.ValidButUntrusted,
                certificatePath = ProximityReaderCertificatePathState.Valid,
                revocation = ProximityReaderRevocationState.Good,
                rical = ProximityRicalState.Matched,
                displayName = "Example reader",
                reason = "No reader trust policy is configured",
            )),
        ),
        ProximityReaderAuthentication(
            scope = ProximityReaderAuthenticationScope.Document(0),
            outcome = ProximityReaderAuthenticationOutcome.Valid(ProximityReaderTrustDecision(
                state = ProximityReaderTrustState.Trusted,
                certificatePath = ProximityReaderCertificatePathState.Valid,
                revocation = ProximityReaderRevocationState.Good,
                rical = ProximityRicalState.Matched,
                displayName = "Document reader",
            )),
        ),
    ),
    useCases = emptyList(),
    applicationAuthorizations = emptyList(),
)
