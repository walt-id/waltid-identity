package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoBootstrapResult
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosure
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.statusText
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WalletDemoAppTestScenarios {

    fun credentialsTabShowsCompactCardsAndNavigatesToDetails() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }

        unlockWithPin()

        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithTag("wallet.tab.credentials").assertIsDisplayed()
        onNodeWithTag("wallet.tab.receive").assertIsDisplayed()
        onNodeWithTag("wallet.tab.present").assertIsDisplayed()
        onNodeWithContentDescription("Credentials tab").assertIsDisplayed()
        onNodeWithContentDescription("Receive tab").assertIsDisplayed()
        onNodeWithContentDescription("Present tab").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithContentDescription("Credential portrait").assertIsDisplayed()
        onNodeWithText("Example Credential").assertIsDisplayed()
        onNodeWithText("Mobile driving licence").assertIsDisplayed()
        onNodeWithText("Ada Lovelace").assertIsDisplayed()
        onNodeWithText("Expires 2026-06-17").assertIsDisplayed()

        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithContentDescription("Back").assertIsDisplayed()
        onNodeWithText("Credential details").assertIsDisplayed()
        onAllNodesWithText("Example Credential").assertCountEquals(1)
        onNodeWithText("Example Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("system.format")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithText("Street address").performScrollTo().assertIsDisplayed()
        onNodeWithText("Main Street 1").performScrollTo().assertIsDisplayed()
        onNodeWithText("Portrait").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Credential image").performScrollTo().assertIsDisplayed()
        onNodeWithText("image/png").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Raw credential data").assertCountEquals(0)
        onNodeWithTag("wallet.detailsBack").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        assertEquals(1, wallet.bootstrapCalls)
    }

    fun credentialsTabShowsEmptyStateAndUpdatesAfterReceive() = runComposeUiTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentials.empty").assertIsDisplayed()
        onNodeWithTag("wallet.tab.receive").performClick()
        wallet.credentials = listOf(sampleCredential)
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithTag("wallet.offerInput").assertIsNotEnabled()
        onNodeWithTag("wallet.receiveNewButton").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").performClick()
        onNodeWithTag("wallet.receiveTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
    }

    fun receiveTabCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }

        onNodeWithTag("wallet.offerInput").assertIsNotEnabled()
        onNodeWithTag("wallet.receiveNewButton").performScrollTo().performClick()
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onNodeWithTag("wallet.offerInput").assertTextContains("")
        onNodeWithTag("wallet.receiveButton").assertIsNotEnabled()
    }

    fun receiveDetailsStayScopedToReceiveTabNavigationStack() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }

        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()

        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
    }

    fun receiveTabDisablesUrlControlsWhileReceiving() = runComposeUiTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            receiveGate = receiveGate,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.operation is WalletOperationState.Receiving }
        onNodeWithTag("wallet.offerInput").assertIsNotEnabled()
        onNodeWithTag("wallet.receiveButton").assertIsNotEnabled()

        receiveGate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()
    }

    fun presentTabExplainsWhyPreviewIsUnavailableWithoutCredentials() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = emptyList())
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")

        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsNotEnabled()
        onNodeWithText("No credentials available").performScrollTo().assertIsDisplayed()
    }

    fun presentTabPreviewsCredentialsAndCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        assertPresentationActionsFollowReviewContent()
        onNodeWithTag("wallet.presentationInput").assertIsDisplayed()
        onNodeWithTag("wallet.presentationInput").assertIsNotEnabled()
        onNodeWithTag("wallet.presentationSubmitButton").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.presentationVerifier").performScrollTo().assertIsDisplayed()
        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        assertVerifierTechnicalDetailsCollapsedUntilRequested()
        onNodeWithTag(WalletUiTestTags.credentialCard(samplePresentationCredentialOption.selection.id)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.credentialCard(samplePresentationCredentialOption.selection.id)).performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithText("Disclosure 7").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").performClick()

        onNodeWithTag("wallet.presentationSubmitButton").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        onNodeWithTag("wallet.presentationInput").assertIsNotEnabled()
        assertPresentationNewActionPrecedesReadOnlyReview()
        onNodeWithTag(WalletUiTestTags.credentialCard(samplePresentationCredentialOption.selection.id)).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag("wallet.presentationSubmitButton").assertCountEquals(0)
        onAllNodesWithTag("wallet.presentationCancelButton").assertCountEquals(0)
        onNodeWithTag("wallet.presentationNewButton").performScrollTo().performClick()
        onNodeWithTag("wallet.presentationInput").assertIsEnabled()
        onNodeWithTag("wallet.presentButton").assertIsNotEnabled()
        assertEquals("openid4vp://example", wallet.previewedRequestUrl)
        assertEquals("openid4vp://example", wallet.submittedRequestUrl)
    }

    fun presentationDisclosureImagesRenderAsImages() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(pathOnlyPortraitDisclosureCredentialOption),
            ),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        val portraitDisclosurePath = "disclosures[0].portrait"
        onNodeWithTag(WalletUiTestTags.claim(portraitDisclosurePath)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage(portraitDisclosurePath)).assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.credentialCard(pathOnlyPortraitDisclosureCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onAllNodesWithText("$.portrait").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.claim(portraitDisclosurePath)).performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Portrait").assertCountEquals(2)
        onNodeWithTag(WalletUiTestTags.claimImage(portraitDisclosurePath)).assertIsDisplayed()
    }

    fun presentTabShowsReadableVerifierFallbackForDidClientIds() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                verifierName = null,
                clientId = sampleDidClientId,
            ),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithText("DID verifier").performScrollTo().assertIsDisplayed()
        onAllNodesWithText(sampleDidClientId).assertCountEquals(0)
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performScrollTo().performClick()
        onNodeWithText(sampleDidClientId).performScrollTo().assertIsDisplayed()
    }

    fun presentTabShowsReadableVerifierFallbackForX509SanDnsClientIds() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                verifierName = null,
                clientId = sampleX509SanDnsClientId,
            ),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithText("verifier.example").performScrollTo().assertIsDisplayed()
        onAllNodesWithText(sampleX509SanDnsClientId).assertCountEquals(0)
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performScrollTo().performClick()
        onNodeWithText(sampleX509SanDnsClientId).performScrollTo().assertIsDisplayed()
    }

    fun presentationDetailsResolveDuplicateCredentialOptionsIndependently() = runComposeUiTest {
        val identityDisclosure = WalletDemoPresentationDisclosureSelection("identity", "cred-1", "$.given_name")
        val ageDisclosure = WalletDemoPresentationDisclosureSelection("age", "cred-1", "$.age_over_18")
        val identityOption = samplePresentationCredentialOption.copy(
            queryId = "identity",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Identity disclosure",
                    path = identityDisclosure.path,
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val ageOption = samplePresentationCredentialOption.copy(
            queryId = "age",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Age disclosure",
                    path = ageDisclosure.path,
                    valueJson = "\"Over 18\"",
                    displayValue = "Over 18",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(identityOption, ageOption),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("identity", "age")))
                ),
            ),
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(identityDisclosure.id)).performScrollTo().assertIsOff()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(ageDisclosure.id)).performScrollTo().assertIsOff()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(identityDisclosure.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(identityDisclosure.id)).performScrollTo().assertIsOn()
        onNodeWithTag(WalletUiTestTags.presentationDisclosureToggle(ageDisclosure.id)).performScrollTo().assertIsOff()

        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performScrollTo().assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(identityOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(identityOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).performScrollTo().assertIsEnabled()

        onNodeWithTag(WalletUiTestTags.credentialCard(ageOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onNodeWithText("Age disclosure").performScrollTo().assertIsDisplayed()
        onNodeWithText("Over 18").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Identity disclosure").assertCountEquals(0)
    }

    fun presentDetailsStayScopedToPresentTabNavigationStack() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.credentialCard(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()

        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimGroup("Requested disclosures")).performScrollTo().assertIsDisplayed()
    }

    fun presentTabDisablesUrlControlsWhilePreviewing() = runComposeUiTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
            previewGate = previewGate,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Resolving presentation..." }
        onNodeWithTag("wallet.status").assertTextContains("Resolving presentation...")
        onNodeWithTag("wallet.presentationInput").assertIsNotEnabled()
        onNodeWithTag("wallet.presentButton").assertIsNotEnabled()

        previewGate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithTag("wallet.presentationSubmitButton").performScrollTo().assertIsDisplayed()
    }

    fun deepLinksRouteToReceiveAndPresentTabs() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.receiveTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.offerInput").assertTextContains(offerUrl)

        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.presentationInput").assertTextContains(requestUrl)

        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithTag("wallet.presentationSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals(offerUrl, wallet.receivedOfferUrl)
        assertEquals(requestUrl, wallet.previewedRequestUrl)
        assertEquals(requestUrl, wallet.submittedRequestUrl)
    }

    fun deepLinksResetReceiveAndPresentDetailStacksEvenWhenUrlIsUnchanged() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        controller.handleDeepLink(offerUrl)
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.receiveTabContent").assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag("wallet.offerInput").assertTextContains(offerUrl)
        onNodeWithTag("wallet.receiveButton").assertIsEnabled()

        controller.handleDeepLink(requestUrl)
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithTag(WalletUiTestTags.credentialCard(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentTabContent").assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag("wallet.presentationInput").assertTextContains(requestUrl)
        onNodeWithTag("wallet.presentButton").assertIsEnabled()
    }

    fun credentialsPersistAcrossControllerRecreation() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val firstController = WalletDemoController(wallet)
        var activeController by mutableStateOf(firstController)

        setContent { WalletDemoApp(activeController) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.session is WalletSessionState.Ready }

        firstController.handleDeepLink("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        val recreatedController = WalletDemoController(wallet)
        activeController = recreatedController
        waitForIdle()
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { recreatedController.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        assertEquals(2, wallet.bootstrapCalls)
    }

    private fun ComposeUiTest.unlockWithPin() {
        onNodeWithTag("wallet.pinInput").performClick().performTextInput("1234")
        onNodeWithTag("wallet.pinConfirmationInput").performClick().performTextInput("1234")
        waitForIdle()
        onNodeWithTag("wallet.pinSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeUiTest.assertPresentationActionsFollowReviewContent() {
        val expectedCredentialTag = WalletUiTestTags.presentationCredential(
            WalletDemoPresentationCredentialSelection(queryId = "pid", credentialId = "cred-1").id
        )
        val reviewLandmarkTags = onAllNodes(
            matcher = hasAnyAncestor(hasTestTag("wallet.presentationReview")) and (
                hasTestTag("wallet.presentationVerifier") or
                    hasTestTag(expectedCredentialTag) or
                    hasTestTag("wallet.presentationActions")
                ),
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }

        val verifierIndex = reviewLandmarkTags.indexOf("wallet.presentationVerifier")
        val credentialIndex = reviewLandmarkTags.indexOf(expectedCredentialTag)
        val actionsIndex = reviewLandmarkTags.indexOf("wallet.presentationActions")

        assertTrue(verifierIndex >= 0, "Verifier details are missing from presentation review: $reviewLandmarkTags")
        assertTrue(credentialIndex >= 0, "Shared credential is missing from presentation review: $reviewLandmarkTags")
        assertTrue(actionsIndex >= 0, "Share actions are missing from presentation review: $reviewLandmarkTags")
        assertTrue(
            verifierIndex < actionsIndex,
            "Share action should follow verifier details so the verifier is reviewed before consent: $reviewLandmarkTags",
        )
        assertTrue(
            credentialIndex < actionsIndex,
            "Share action should follow shared credential details so the credential is reviewed before consent: $reviewLandmarkTags",
        )
    }

    private fun ComposeUiTest.assertVerifierTechnicalDetailsCollapsedUntilRequested() {
        onAllNodesWithText("Client ID").assertCountEquals(0)
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performClick()
        onNodeWithText("Client ID").performScrollTo().assertIsDisplayed()
        onNodeWithText("https://verifier.example/response").performScrollTo().assertIsDisplayed()
        onNodeWithText("state-123").performScrollTo().assertIsDisplayed()
        onNodeWithText("nonce-456").performScrollTo().assertIsDisplayed()
    }

    private fun ComposeUiTest.assertPresentationNewActionPrecedesReadOnlyReview() {
        val presentTabLandmarkTags = onAllNodes(
            matcher = hasAnyAncestor(hasTestTag("wallet.presentTabContent")) and (
                hasTestTag("wallet.presentationNewButton") or
                    hasTestTag("wallet.presentationReview")
                ),
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }

        val newActionIndex = presentTabLandmarkTags.indexOf("wallet.presentationNewButton")
        val reviewIndex = presentTabLandmarkTags.indexOf("wallet.presentationReview")

        assertTrue(newActionIndex >= 0, "New presentation action is missing: $presentTabLandmarkTags")
        assertTrue(reviewIndex >= 0, "Read-only presentation review is missing: $presentTabLandmarkTags")
        assertTrue(
            newActionIndex < reviewIndex,
            "New presentation action should precede the read-only review so starting over stays easy: $presentTabLandmarkTags",
        )
    }

    companion object {
        val sampleCredential = WalletDemoCredential(
            id = "cred-1",
            format = "jwt_vc_json",
            issuer = "Example Issuer",
            label = "Example Credential",
            addedAt = "2026-07-09",
            credentialDataJson = """
                {
                  "vct": "https://issuer.example/credential-types/mobile-driving-licence",
                  "given_name": "Ada",
                  "family_name": "Lovelace",
                  "valid_to": 1781654400,
                  "resident_address": {
                    "street_address": "Main Street 1",
                    "locality": "Vienna"
                  },
                  "portrait": {
                    "elementValue": [-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]
                  }
                }
            """.trimIndent(),
        )

        val samplePresentationPreview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example/client",
            responseUri = "https://verifier.example/response",
            state = "state-123",
            nonce = "nonce-456",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    credentialDataJson = checkNotNull(sampleCredential.credentialDataJson),
                    disclosures = (1..7).map { index ->
                        WalletDemoPresentationDisclosure(
                            label = "Disclosure $index",
                            valueJson = "\"Value $index\"",
                            displayValue = "Value $index",
                            selectivelyDisclosable = true,
                        )
                    } + WalletDemoPresentationDisclosure(
                        label = "Portrait",
                        path = "$.portrait",
                        valueJson = samplePortraitDisclosureValueJson,
                        displayValue = null,
                        selectivelyDisclosable = true,
                    ),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )

        val pathOnlyPortraitDisclosureCredentialOption = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "Example Credential",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = checkNotNull(sampleCredential.credentialDataJson),
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Portrait",
                    path = "$.portrait",
                    valueJson = samplePortraitDisclosureValueJson,
                    displayValue = null,
                    selectivelyDisclosable = true,
                )
            ),
        )

        const val sampleDidClientId = "decentralized_identifier:did:jwk:abc"
        const val sampleX509SanDnsClientId = "x509_san_dns:verifier.example"
        private const val samplePortraitDisclosureValueJson =
            "[-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]"

        private val samplePresentationCredentialOption: WalletDemoPresentationCredentialOption
            get() = samplePresentationPreview.credentialOptions.single()
    }
}

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val credentialsAfterReceive: List<WalletDemoCredential>? = null,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success("Presentation sent"),
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoAppTestScenarios.samplePresentationPreview,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val previewGate: CompletableDeferred<Unit>? = null,
) : DemoWallet {
    var bootstrapCalls = 0
    var receivedOfferUrl: String? = null
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var submittedRequestUrl: String? = null

    override suspend fun bootstrap(): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(keyId = "key-1", did = "did:key:test")
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

    override suspend fun receive(offerUrl: String): List<String> {
        receivedOfferUrl = offerUrl
        receiveGate?.await()
        credentialsAfterReceive?.let { credentials = it }
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview {
        previewedRequestUrl = requestUrl
        previewGate?.await()
        return presentationPreview
    }

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult {
        submittedRequestUrl = requestUrl
        return presentationResult
    }
}
