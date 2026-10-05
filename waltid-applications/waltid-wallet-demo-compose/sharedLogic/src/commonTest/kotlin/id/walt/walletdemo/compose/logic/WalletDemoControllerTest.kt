package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val presentationPreviewHandle = WalletDemoPresentationPreviewHandle("presentation-preview")

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoControllerTest {

    @Test
    fun failedWalletOpeningCanBeRetriedOnceWithoutDuplicateBootstrap() = runTest {
        var unavailable = true
        val delegate = FakeDemoWallet()
        val wallet = object : DemoWallet by delegate {
            override suspend fun listCredentials(): List<WalletDemoCredential> {
                check(!unavailable) { "Credential storage unavailable" }
                return emptyList()
            }
        }
        val controller = unlockedControllerWith(wallet, this)
        assertIs<WalletSessionState.Failed>(controller.state.value.session)
        assertEquals(1, delegate.bootstrapCalls)
        unavailable = false
        controller.retryOpeningWallet()
        controller.retryOpeningWallet()
        assertIs<WalletSessionState.Bootstrapping>(controller.state.value.session)
        runCurrent()
        assertIs<WalletSessionState.Ready>(controller.state.value.session)
        assertEquals(2, delegate.bootstrapCalls)
        controller.retryOpeningWallet()
        runCurrent()
        assertEquals(2, delegate.bootstrapCalls)
    }

    @Test
    fun cancelledKeyApprovalRetainsSetupAndReleasesBusyStateForRetry() = runTest {
        val option = WalletDemoKeySetupOption("create", WalletDemoKeyChoice("new", "No backup", ""),
            WalletDemoKeyChoice("native", "Native", ""), WalletDemoKeyChoice("approval", "Approval", ""))
        val setup = WalletDemoIdentitySetup.Choose(listOf(option))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val wallet = object : DemoWallet by FakeDemoWallet() {
            override suspend fun identitySetup() = setup
            override suspend fun chooseIdentity(choiceId: String) {
                calls++
                gate.await()
                throw WalletDemoKeyOperationException("Signing approval was not completed. Try again.")
            }
        }
        val controller = unlockedControllerWith(wallet, this)
        controller.chooseIdentity("create")
        controller.chooseIdentity("create")
        runCurrent()
        assertEquals(1, calls)
        assertEquals("Creating key…", controller.state.value.identityProgress)
        assertEquals(setup, (controller.state.value.session as WalletSessionState.IdentitySetup).setup)
        gate.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.identityBusy)
        assertEquals(setup, (controller.state.value.session as WalletSessionState.IdentitySetup).setup)
        assertEquals("Signing approval was not completed. Try again.", controller.state.value.warning)
        controller.chooseIdentity("create")
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun signingDetailsFailureStaysDistinctFromUnsupportedAndCanBeRetried() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        val details = WalletDemoIdentityDetails("Hardware", "Generated", "None", "No backup", emptyList())
        wallet.identityDetailsGate = CompletableDeferred()
        wallet.identityDetailsValue = details
        controller.refreshIdentityDetails()
        controller.refreshIdentityDetails()
        runCurrent()
        assertEquals(WalletDemoIdentityDetailsState.Loading, controller.state.value.identityDetails)
        assertTrue(controller.state.value.identityBusy)
        assertEquals(1, wallet.identityDetailsCalls)

        wallet.identityDetailsGate!!.complete(Unit)
        runCurrent()
        assertEquals(WalletDemoIdentityDetailsState.Available(details), controller.state.value.identityDetails)
        assertFalse(controller.state.value.identityBusy)

        wallet.identityDetailsError = IllegalStateException("Provider offline")
        controller.refreshIdentityDetails()
        runCurrent()
        assertTrue(controller.state.value.identityDetails is WalletDemoIdentityDetailsState.Failed)
        assertFalse(controller.state.value.identityBusy)

        wallet.identityDetailsError = null
        controller.refreshIdentityDetails()
        runCurrent()
        assertEquals(WalletDemoIdentityDetailsState.Available(details), controller.state.value.identityDetails)

        wallet.identityDetailsValue = null
        controller.refreshIdentityDetails()
        runCurrent()
        assertEquals(WalletDemoIdentityDetailsState.Unsupported, controller.state.value.identityDetails)
    }

    @Test
    fun sharingPreferenceFailureKeepsTheCommittedValueAndAllowsRetry() = runTest {
        val saved = InMemoryDemoSharingSettingsStore()
        var fail = true
        val store = object : DemoSharingSettingsStore by saved {
            override fun setProximityApprovalMode(mode: WalletDemoProximityApprovalMode) {
                check(!fail) { "Disk unavailable" }
                saved.setProximityApprovalMode(mode)
            }
        }
        val controller = WalletDemoController(FakeDemoWallet(), InMemoryDemoPinStore(), sharingSettings = store)
        controller.setProximityApprovalMode(WalletDemoProximityApprovalMode.PrepareSharing)
        assertEquals(WalletDemoProximityApprovalMode.AskEachTime, controller.state.value.proximityApprovalMode)
        assertEquals(WalletDemoProximityApprovalMode.AskEachTime, saved.proximityApprovalMode())
        assertEquals("Could not save sharing approval. Try again.", controller.state.value.sharingSettingsError)
        fail = false
        controller.setProximityApprovalMode(WalletDemoProximityApprovalMode.PrepareSharing)
        assertEquals(WalletDemoProximityApprovalMode.PrepareSharing, controller.state.value.proximityApprovalMode)
        assertEquals(WalletDemoProximityApprovalMode.PrepareSharing, saved.proximityApprovalMode())
        assertNull(controller.state.value.sharingSettingsError)
    }

    @Test
    fun backupFailureIsScopedToSigningKeyAndRetryClearsIt() = runTest {
        val delegate = FakeDemoWallet()
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val details = WalletDemoIdentityDetails("Native", "Generated", "None", "No backup",
            listOf(WalletDemoIdentityChoice("backup", "Back up signing key", "Provider", true)))
        val wallet = object : DemoWallet by delegate {
            override suspend fun identityDetails() = details
            override suspend fun chooseIdentity(choiceId: String) {
                attempts++
                if (attempts == 1) throw WalletDemoKeyOperationException("The backup provider is unavailable. Try again later.")
                gate.await()
            }
        }
        val controller = unlockedControllerWith(wallet, this)
        controller.refreshIdentityDetails()
        runCurrent()
        val warning = controller.state.value.warning
        controller.performIdentityAction("backup")
        runCurrent()
        assertEquals("The backup provider is unavailable. Try again later.", controller.state.value.identityError)
        assertEquals(warning, controller.state.value.warning)
        assertFalse(controller.state.value.identityBusy)
        controller.performIdentityAction("backup")
        runCurrent()
        assertNull(controller.state.value.identityError)
        assertTrue(controller.state.value.identityBusy)
        controller.performIdentityAction("backup")
        assertEquals(2, attempts)
        gate.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.identityBusy)
        assertEquals(WalletDemoIdentityDetailsState.Available(details), controller.state.value.identityDetails)
    }

    @Test
    fun foregroundRefreshKeepsSetupVisibleAndDoesNotBootstrapOrDuplicateRequests() = runTest {
        val original = WalletDemoIdentitySetup.Choose(emptyList())
        val wallet = FakeDemoWallet().apply { identitySetupValue = original }
        val controller = unlockedControllerWith(wallet, this)
        val calls = wallet.identitySetupCalls
        wallet.identitySetupGate = CompletableDeferred()
        val refreshed = WalletDemoIdentitySetup.Choose(emptyList(), message = "Provider now available")
        wallet.identitySetupValue = refreshed

        controller.handleApplicationForegrounded()
        controller.handleApplicationForegrounded()
        runCurrent()
        assertEquals(original, (controller.state.value.session as WalletSessionState.IdentitySetup).setup)
        assertTrue(controller.state.value.identityBusy)
        assertEquals(calls + 1, wallet.identitySetupCalls)
        assertEquals(0, wallet.bootstrapCalls)

        wallet.identitySetupGate!!.complete(Unit)
        runCurrent()
        assertEquals(refreshed, (controller.state.value.session as WalletSessionState.IdentitySetup).setup)
        assertFalse(controller.state.value.identityBusy)
    }

    @Test
    fun failedOptionsRefreshRetainsSetupAndCanBeRetried() = runTest {
        val original = WalletDemoIdentitySetup.Choose(emptyList())
        val wallet = FakeDemoWallet().apply { identitySetupValue = original }
        val controller = unlockedControllerWith(wallet, this)
        wallet.identitySetupError = IllegalStateException("Provider offline")
        controller.refreshIdentityChoices()
        runCurrent()
        assertEquals(original, (controller.state.value.session as WalletSessionState.IdentitySetup).setup)
        assertEquals("Signing key options could not be loaded. Try again.", controller.state.value.warning)
        assertFalse(controller.state.value.identityBusy)

        wallet.identitySetupError = null
        controller.refreshIdentityChoices()
        runCurrent()
        assertNull(controller.state.value.warning)
        assertEquals(0, wallet.bootstrapCalls)
    }

    @Test
    fun pinStorageReadFailureStaysLockedUntilRetrySucceeds() = runTest {
        val pinStore = RecoverableDemoPinStore()
        val wallet = FakeDemoWallet()
        val controller = controllerWith(wallet, this, pinStore)

        assertTrue(controller.state.value.auth is WalletAuthState.StorageUnavailable)

        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.StorageUnavailable)
        assertEquals(0, pinStore.setPinCalls)
        assertEquals(0, wallet.bootstrapCalls)

        pinStore.isAvailable = true
        controller.retryPinStorage()

        assertTrue(controller.state.value.auth is WalletAuthState.Login)
    }

    @Test
    fun invalidPresentationPreviewCanBeDismissedOrReturnedToVerifier() = runTest {
        val error = WalletDemoPresentationError(
            previewHandle = presentationPreviewHandle,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            errorCode = "invalid_transaction_data",
            message = "Unsupported transaction_data type",
        )
        val wallet = FakeDemoWallet(presentationError = error)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://invalid")
        controller.previewPresentation()
        runCurrent()

        assertEquals(error, controller.state.value.presentationError)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(WalletDisplayText.ReviewPresentationError, controller.state.value.statusText)
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationReviewEnabled)

        controller.rejectPresentation()
        runCurrent()

        assertEquals(listOf(presentationPreviewHandle), wallet.rejectedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.presentationError)
        assertEquals(WalletDisplayText.VerifierNotified, controller.state.value.statusText)

        controller.updatePresentationRequestUrl("openid4vp://invalid-again")
        controller.previewPresentation()
        runCurrent()
        controller.startNewPresentationFlow()
        runCurrent()

        assertEquals(null, controller.state.value.presentationError)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
    }

    @Test
    fun setupPinRejectsInvalidLengthAndNonDigits() = runTest {
        for (pin in listOf("1234", "12345", "1234567", "12a456", "１２３４５６", "1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣", "12345\n")) {
            val store = InMemoryDemoPinStore()
            val biometrics = FakeDemoBiometricAuthenticator()
            val controller = controllerWith(FakeDemoWallet(), this, store, biometrics)
            controller.updatePin(pin)
            controller.submitPin()
            val auth = controller.state.value.auth as WalletAuthState.Setup
            assertEquals(PinSetupStep.Choose, auth.step)
            assertEquals("Choose a six-digit PIN", auth.error)
            assertFalse(store.hasPin())
            assertEquals(0, biometrics.authenticateCalls)
        }
    }

    @Test
    fun chooseThenConfirmKeepsPinUncommittedUntilThePinsMatch() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val store = InMemoryDemoPinStore()
        val controller = controllerWith(wallet, this, store)
        controller.updatePin("123456")
        controller.updatePinConfirmation("123456") // Choosing cannot bypass confirmation.
        controller.submitPin()
        assertEquals(PinSetupStep.Confirm, (controller.state.value.auth as WalletAuthState.Setup).step)
        assertFalse(store.hasPin())
        assertEquals(0, wallet.bootstrapCalls)
        controller.updatePinConfirmation("654321")
        val mismatch = controller.state.value.auth as WalletAuthState.Setup
        assertEquals("PIN confirmation does not match", mismatch.error)
        assertEquals("", mismatch.confirmation)
        assertFalse(store.hasPin())
        controller.updatePinConfirmation("123456") // A complete match submits without another button.
        runCurrent()
        assertIs<WalletAuthState.Unlocked>(controller.state.value.auth)
        assertEquals(listOf(sampleCredential), (controller.state.value.session as WalletSessionState.Ready).credentials)
        assertEquals(1, wallet.bootstrapCalls)
        assertTrue(store.verifyPin("123456"))
        assertFalse(store.isBiometricUnlockEnabled())
    }

    @Test
    fun editingChosenPinInvalidatesThePreviousConfirmation() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123")
        controller.editSetupPin()
        assertEquals(PinSetupStep.Choose, (controller.state.value.auth as WalletAuthState.Setup).step)
        assertEquals("", (controller.state.value.auth as WalletAuthState.Setup).confirmation)
        controller.updatePin("654321")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        assertEquals("PIN confirmation does not match", (controller.state.value.auth as WalletAuthState.Setup).error)
        assertFalse(controller.state.value.isAuthenticating)
    }

    @Test
    fun matchingConfirmationPromptsOnceAndOnlyOsSuccessEnablesBiometrics() = runTest {
        for (result in DemoBiometricResult.entries) {
            val store = InMemoryDemoPinStore()
            val gate = CompletableDeferred<DemoBiometricResult>()
            var calls = 0
            val biometrics = object : DemoBiometricAuthenticator {
                override fun isAvailable() = true
                override suspend fun authenticate(reason: String): DemoBiometricResult {
                    assertTrue(store.hasPin())
                    assertFalse(store.isBiometricUnlockEnabled())
                    calls++
                    return gate.await()
                }
            }
            val wallet = FakeDemoWallet()
            val controller = controllerWith(wallet, this, store, biometrics)
            controller.updatePin("123456")
            controller.submitPin()
            assertEquals(0, calls)
            controller.updatePinConfirmation("123456")
            repeat(2) { controller.submitPin() }
            runCurrent()
            assertEquals(1, calls)
            assertTrue(controller.state.value.isAuthenticating)
            assertEquals(0, wallet.bootstrapCalls)
            controller.updatePin("999999")
            controller.editSetupPin()
            assertEquals("123456", (controller.state.value.auth as WalletAuthState.Setup).pin)
            gate.complete(result)
            runCurrent()
            assertIs<WalletAuthState.Unlocked>(controller.state.value.auth)
            assertFalse(controller.state.value.isAuthenticating)
            assertEquals(result == DemoBiometricResult.Succeeded, store.isBiometricUnlockEnabled())
            assertEquals(1, wallet.bootstrapCalls)
        }
    }

    @Test
    fun unavailableOrFailedBiometricPromptCompletesPinOnlySetup() = runTest {
        for (available in listOf(false, true)) {
            val store = InMemoryDemoPinStore()
            var calls = 0
            val biometrics = object : DemoBiometricAuthenticator {
                override fun isAvailable() = available
                override suspend fun authenticate(reason: String): DemoBiometricResult {
                    calls++
                    throw IllegalStateException("OS prompt unavailable")
                }
            }
            val controller = controllerWith(FakeDemoWallet(), this, store, biometrics)
            controller.updatePin("123456")
            controller.submitPin()
            controller.updatePinConfirmation("123456")
            runCurrent()
            assertIs<WalletAuthState.Unlocked>(controller.state.value.auth)
            assertFalse(store.isBiometricUnlockEnabled())
            assertEquals(if (available) 1 else 0, calls)
        }
    }

    @Test
    fun failedPinPersistenceDoesNotPromptAndCanBeRetried() = runTest {
        val stored = InMemoryDemoPinStore()
        var fail = true
        val store = object : DemoPinStore by stored {
            override suspend fun setPin(pin: String) {
                check(!fail) { "Disk unavailable" }
                stored.setPin(pin)
            }
        }
        val biometrics = FakeDemoBiometricAuthenticator()
        val controller = controllerWith(FakeDemoWallet(), this, store, biometrics)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        assertEquals("PIN could not be saved. Try again.", (controller.state.value.auth as WalletAuthState.Setup).error)
        assertFalse(controller.state.value.isAuthenticating)
        assertEquals(0, biometrics.authenticateCalls)
        fail = false
        controller.submitPin()
        runCurrent()
        assertIs<WalletAuthState.Unlocked>(controller.state.value.auth)
        assertEquals(1, biometrics.authenticateCalls)
    }

    @Test
    fun resetDuringBiometricOptInIgnoresLateOsSuccess() = runTest {
        val gate = CompletableDeferred<DemoBiometricResult>()
        val store = InMemoryDemoPinStore()
        val biometrics = object : DemoBiometricAuthenticator {
            override fun isAvailable() = true
            override suspend fun authenticate(reason: String) = withContext(NonCancellable) { gate.await() }
        }
        val wallet = FakeDemoWallet()
        val controller = controllerWith(wallet, this, store, biometrics)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        controller.resetWallet()
        runCurrent()
        gate.complete(DemoBiometricResult.Succeeded)
        runCurrent()
        assertIs<WalletAuthState.Setup>(controller.state.value.auth)
        assertFalse(store.hasPin())
        assertFalse(store.isBiometricUnlockEnabled())
        assertFalse(controller.state.value.isAuthenticating)
        assertEquals(0, wallet.bootstrapCalls)
    }

    @Test
    fun biometricUnlockOpensWalletWithoutPin() = runTest {
        val pinStore = InMemoryDemoPinStore()
        pinStore.setPin("1234")
        pinStore.setBiometricUnlockEnabled(true)
        val wallet = FakeDemoWallet()
        val biometrics = FakeDemoBiometricAuthenticator()
        val controller = controllerWith(wallet, this, pinStore, biometrics)

        assertTrue(controller.state.value.auth is WalletAuthState.Login)
        controller.unlockWithBiometrics()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertEquals(1, biometrics.authenticateCalls)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun cancelledBiometricsLeavesPinFallback() = runTest {
        val pinStore = InMemoryDemoPinStore()
        pinStore.setPin("1234")
        pinStore.setBiometricUnlockEnabled(true)
        val wallet = FakeDemoWallet()
        val biometrics = FakeDemoBiometricAuthenticator(result = DemoBiometricResult.Failed)
        val controller = controllerWith(wallet, this, pinStore, biometrics)

        controller.unlockWithBiometrics()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Login)
        assertEquals(0, wallet.bootstrapCalls)
        controller.handleApplicationForegrounded()
        controller.unlockWithBiometrics()
        runCurrent()
        assertEquals(1, biometrics.authenticateCalls)
        assertTrue((controller.state.value.auth as WalletAuthState.Login).biometricPromptConsumed)

        controller.updatePin("1234")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun newUnlockAttemptPromptsBiometricsOnceAfterLock() = runTest {
        val pinStore = InMemoryDemoPinStore()
        pinStore.setPin("1234")
        pinStore.setBiometricUnlockEnabled(true)
        val wallet = FakeDemoWallet()
        val biometrics = FakeDemoBiometricAuthenticator()
        val controller = controllerWith(wallet, this, pinStore, biometrics)

        controller.unlockWithBiometrics()
        runCurrent()
        assertEquals(1, biometrics.authenticateCalls)
        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)

        controller.lock()
        val login = controller.state.value.auth as WalletAuthState.Login
        assertFalse(login.biometricPromptConsumed)

        controller.unlockWithBiometrics()
        controller.handleApplicationForegrounded()
        runCurrent()
        assertEquals(2, biometrics.authenticateCalls)
        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun refreshBiometricUnlockAvailabilityPicksUpEnrollmentChange() = runTest {
        val biometrics = FakeDemoBiometricAuthenticator(available = false)
        val controller = controllerWith(FakeDemoWallet(), this, biometricAuthenticator = biometrics)

        assertFalse(controller.state.value.biometricUnlockAvailable)
        assertFalse(controller.isBiometricUnlockAvailable())

        biometrics.available = true
        controller.refreshBiometricUnlockAvailability()

        assertTrue(controller.state.value.biometricUnlockAvailable)
        assertTrue(controller.isBiometricUnlockAvailable())
    }

    @Test
    fun optionalSetupPersistsAndAppliesTheSelectedSigningProtection() = runTest {
        val wallet = FakeDemoWallet()
        val store = InMemoryWalletDemoSigningProtectionStore()
        val controller = controllerWith(wallet, this, signingProtectionStore = store)

        controller.selectSigningProtection(WalletDemoSigningProtection.None)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()

        assertEquals(WalletDemoSigningProtection.None, store.load())
        assertEquals(listOf(WalletDemoSigningProtection.None), wallet.bootstrappedSigningProtections)
        val ready = controller.state.value.session as WalletSessionState.Ready
        assertEquals(WalletDemoSigningProtection.None, ready.signingProtection)
    }

    @Test
    fun pinSetupDoesNotDependOnSigningBiometricEnrollment() = runTest {
        val wallet = FakeDemoWallet().apply {
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
        }
        val pinStore = InMemoryDemoPinStore()
        val controller = controllerWith(
            wallet,
            this,
            pinStore,
            signingProtectionMode = WalletDemoSigningProtectionMode.Required,
        )

        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()

        assertEquals(WalletAuthState.Unlocked, controller.state.value.auth)
        assertTrue(pinStore.hasPin())
        assertEquals(0, wallet.deleteWalletCalls)
    }

    @Test
    fun unavailableBiometricSigningCannotBeSelectedButNoneRemainsSelectable() = runTest {
        val wallet = FakeDemoWallet().apply {
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
        }
        val controller = controllerWith(wallet, this)
        controller.handleApplicationForegrounded()
        runCurrent()

        assertEquals(
            WalletDemoSigningProtectionAvailability.BiometricNotEnrolled,
            controller.state.value.biometricSigningAvailability,
        )
        controller.selectSigningProtection(WalletDemoSigningProtection.None)
        controller.selectSigningProtection(WalletDemoSigningProtection.Biometric)

        assertEquals(
            WalletDemoSigningProtection.None,
            controller.state.value.selectedSigningProtection,
        )
    }

    @Test
    fun cancelledBiometricSigningRefreshDoesNotPublishUnsupportedAvailability() = runTest {
        val wallet = FakeDemoWallet().apply {
            signingProtectionAvailabilityError = CancellationException("foreground refresh superseded")
        }
        val controller = controllerWith(wallet, this)

        controller.handleApplicationForegrounded()
        runCurrent()

        assertNull(controller.state.value.biometricSigningAvailability)
    }

    @Test
    fun cancelledBiometricSigningRefreshDoesNotPublishALateResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val wallet = object : DemoWallet by FakeDemoWallet() {
            override suspend fun signingProtectionAvailability(
                signingProtection: WalletDemoSigningProtection,
            ): WalletDemoSigningProtectionAvailability {
                calls += 1
                if (calls == 1) {
                    withContext(NonCancellable) { gate.await() }
                    return WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
                }
                return WalletDemoSigningProtectionAvailability.Available
            }
        }
        val controller = controllerWith(wallet, this)
        val seen = mutableListOf<WalletDemoSigningProtectionAvailability?>()
        val collector = launch {
            controller.state.map { it.biometricSigningAvailability }.distinctUntilChanged().collect { seen += it }
        }
        runCurrent()

        controller.handleApplicationForegrounded()
        runCurrent()
        controller.handleApplicationForegrounded()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        collector.cancel()

        assertEquals(listOf(null, WalletDemoSigningProtectionAvailability.Available), seen)
    }

    @Test
    fun biometricRefreshCancelsThePreviousCallOnItsDispatcher() {
        val dispatcher = ManualDispatcher()
        var calls = 0
        var cancelledOnDispatcher = false
        val wallet = object : DemoWallet by FakeDemoWallet() {
            override suspend fun signingProtectionAvailability(
                signingProtection: WalletDemoSigningProtection,
            ): WalletDemoSigningProtectionAvailability {
                calls += 1
                if (calls == 1) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        continuation.invokeOnCancellation { cancelledOnDispatcher = dispatcher.executing }
                    }
                }
                return WalletDemoSigningProtectionAvailability.Available
            }
        }
        runManualRefresh(wallet, dispatcher) { controller, running ->
            controller.handleApplicationForegrounded()
            running.drain()
            controller.handleApplicationForegrounded()
            running.drain()
            assertTrue(cancelledOnDispatcher)
            assertEquals(
                WalletDemoSigningProtectionAvailability.Available,
                controller.state.value.biometricSigningAvailability,
            )
        }
    }

    @Test
    fun supersededQueuedBiometricRefreshCancelsTheRunningCheck() {
        val first = CompletableDeferred<Unit>()
        var calls = 0
        val cancelled = mutableListOf<Int>()
        runManualRefresh(object : DemoWallet by FakeDemoWallet() {
            override suspend fun signingProtectionAvailability(
                signingProtection: WalletDemoSigningProtection,
            ): WalletDemoSigningProtectionAvailability {
                val call = ++calls
                if (call == 1) {
                    try {
                        first.await()
                    } catch (cancellation: CancellationException) {
                        cancelled += call
                        throw cancellation
                    }
                }
                return if (call == 1) {
                    WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
                } else {
                    WalletDemoSigningProtectionAvailability.Available
                }
            }
        }) { controller, dispatcher ->
            controller.handleApplicationForegrounded()
            dispatcher.drain()
            assertEquals(1, calls)

            controller.handleApplicationForegrounded()
            controller.handleApplicationForegrounded()
            dispatcher.runLatest()
            dispatcher.drain()

            assertEquals(listOf(1), cancelled)
            assertEquals(2, calls)
            assertEquals(
                WalletDemoSigningProtectionAvailability.Available,
                controller.state.value.biometricSigningAvailability,
            )
            first.complete(Unit)
            dispatcher.drain()
            assertEquals(
                WalletDemoSigningProtectionAvailability.Available,
                controller.state.value.biometricSigningAvailability,
            )
        }
    }

    @Test
    fun supersededQueuedRefreshCancelsObsoleteLazyWalletInitializer() {
        val inner = FakeDemoWallet().apply {
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.Available
        }
        var creates = 0
        var obsoleteInitializerCancelled = false
        val wallet = object : LazyDemoWallet<DemoWallet>({
            val attempt = ++creates
            if (attempt == 1) {
                try {
                    awaitCancellation()
                } catch (cancellation: CancellationException) {
                    obsoleteInitializerCancelled = true
                    throw cancellation
                }
            }
            inner
        }) {}
        runManualRefresh(wallet) { controller, dispatcher ->
            controller.handleApplicationForegrounded()
            dispatcher.drain()
            assertEquals(1, creates)

            controller.handleApplicationForegrounded()
            controller.handleApplicationForegrounded()
            dispatcher.runLatest()
            dispatcher.drain()

            assertTrue(obsoleteInitializerCancelled)
            assertEquals(2, creates)
            assertEquals(
                WalletDemoSigningProtectionAvailability.Available,
                controller.state.value.biometricSigningAvailability,
            )
        }
    }

    @Test
    fun foregroundWarnsWhenAppliedBiometricSigningBecomesUnavailable() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled

        controller.handleApplicationForegrounded()
        runCurrent()

        assertEquals(
            WalletDemoSigningProtectionAvailability.BiometricNotEnrolled,
            controller.state.value.biometricSigningAvailability,
        )
        assertTrue(controller.state.value.signingProtectionWarning.orEmpty().contains("cannot be used again"))

        controller.dismissSigningProtectionWarning()
        assertNull(controller.state.value.signingProtectionWarning)

        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.Available
        controller.handleApplicationForegrounded()
        runCurrent()

        assertEquals(
            WalletDemoSigningProtectionAvailability.Available,
            controller.state.value.biometricSigningAvailability,
        )
        assertNull(controller.state.value.signingProtectionWarning)
    }

    @Test
    fun disabledModeStillWarnsUntilExistingBiometricWalletIsReprovisioned() = runTest {
        val wallet = FakeDemoWallet().apply {
            reportedSigningProtection = WalletDemoSigningProtection.Biometric
        }
        val controller = controllerWith(
            wallet = wallet,
            scope = this,
            signingProtectionMode = WalletDemoSigningProtectionMode.Disabled,
        )
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled

        controller.handleApplicationForegrounded()
        runCurrent()

        assertEquals(
            WalletDemoSigningProtection.Biometric,
            (controller.state.value.session as WalletSessionState.Ready).signingProtection,
        )
        assertTrue(controller.state.value.signingProtectionWarning.orEmpty().contains("reset the wallet"))
    }

    @Test
    fun requiredModeWarningDoesNotOfferAProhibitedSigningChoice() = runTest {
        val wallet = FakeDemoWallet()
        val controller = controllerWith(
            wallet = wallet,
            scope = this,
            signingProtectionMode = WalletDemoSigningProtectionMode.Required,
        )
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled

        controller.handleApplicationForegrounded()
        runCurrent()

        assertTrue(controller.state.value.signingProtectionWarning.orEmpty().contains("required by app configuration"))
        assertFalse(controller.state.value.signingProtectionWarning.orEmpty().contains("reset the wallet"))
    }

    @Test
    fun unavailableBiometricSigningWarningWaitsForPinUnlock() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        controller.lock()
        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled

        controller.handleApplicationForegrounded()
        runCurrent()
        assertNull(controller.state.value.signingProtectionWarning)

        controller.updatePin("123456")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(controller.state.value.signingProtectionWarning.orEmpty().contains("cannot be used again"))
    }

    @Test
    fun biometricUnlockAndStrongBiometricSigningAvailabilityStayIndependent() = runTest {
        val wallet = FakeDemoWallet().apply {
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
        }
        val biometrics = FakeDemoBiometricAuthenticator(available = true)
        val controller = controllerWith(wallet, this, biometricAuthenticator = biometrics)
        controller.handleApplicationForegrounded()
        runCurrent()

        assertTrue(controller.state.value.biometricUnlockAvailable)
        assertEquals(
            WalletDemoSigningProtectionAvailability.BiometricNotEnrolled,
            controller.state.value.biometricSigningAvailability,
        )
    }

    @Test
    fun unavailableSigningProtectionDoesNotReplaceTheWallet() = runTest {
        val wallet = FakeDemoWallet()
        val controller = controllerWith(wallet, this)
        controller.selectSigningProtection(WalletDemoSigningProtection.None)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        wallet.signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled

        controller.requestSigningProtectionChange(WalletDemoSigningProtection.Biometric)
        runCurrent()

        assertEquals(0, wallet.deleteWalletCalls)
        assertEquals(null, controller.state.value.pendingSigningProtectionChange)
        assertEquals(WalletDemoSigningProtection.None, controller.state.value.selectedSigningProtection)
        assertEquals(WalletDisplayText.BiometricNotEnrolled, controller.state.value.signingProtectionError)
    }

    @Test
    fun confirmedSigningProtectionChangeReprovisionsWalletAndPreservesPin() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val store = InMemoryWalletDemoSigningProtectionStore()
        val controller = controllerWith(wallet, this, pinStore, signingProtectionStore = store)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()

        controller.requestSigningProtectionChange(WalletDemoSigningProtection.None)
        runCurrent()
        assertEquals(WalletDemoSigningProtection.None, controller.state.value.pendingSigningProtectionChange)

        controller.confirmSigningProtectionChange()
        runCurrent()

        assertEquals(1, wallet.deleteWalletCalls)
        assertEquals(
            listOf(WalletDemoSigningProtection.Biometric, WalletDemoSigningProtection.None),
            wallet.bootstrappedSigningProtections,
        )
        assertEquals(WalletDemoSigningProtection.None, store.load())
        assertTrue(pinStore.hasPin())
        val ready = controller.state.value.session as WalletSessionState.Ready
        assertEquals(WalletDemoSigningProtection.None, ready.signingProtection)
        assertEquals(emptyList(), ready.credentials)
    }

    @Test
    fun failedReprovisionKeepsTheTargetAndCanBeRetriedWithoutClearingPin() = runTest {
        val wallet = FakeDemoWallet()
        val pinStore = InMemoryDemoPinStore()
        val store = InMemoryWalletDemoSigningProtectionStore()
        val controller = controllerWith(wallet, this, pinStore, signingProtectionStore = store)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        wallet.bootstrapError = IllegalStateException("provisioning failed")

        controller.requestSigningProtectionChange(WalletDemoSigningProtection.None)
        runCurrent()
        controller.confirmSigningProtectionChange()
        runCurrent()

        assertTrue(controller.state.value.session is WalletSessionState.Failed)
        assertEquals(WalletDemoSigningProtection.None, store.load())
        assertTrue(pinStore.hasPin())

        wallet.bootstrapError = null
        controller.requestSigningProtectionChange(WalletDemoSigningProtection.None)
        runCurrent()

        val ready = controller.state.value.session as WalletSessionState.Ready
        assertEquals(WalletDemoSigningProtection.None, ready.signingProtection)
        assertEquals(2, wallet.deleteWalletCalls)
        assertNull(controller.state.value.signingProtectionError)
        assertTrue(pinStore.hasPin())
    }

    @Test
    fun reprovisionFailsClosedWhenTheWalletReportsADifferentAppliedPolicy() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        wallet.reportedSigningProtection = WalletDemoSigningProtection.Biometric

        controller.requestSigningProtectionChange(WalletDemoSigningProtection.None)
        runCurrent()
        controller.confirmSigningProtectionChange()
        runCurrent()

        assertEquals(1, wallet.deleteWalletCalls)
        assertTrue(controller.state.value.session is WalletSessionState.Failed)
        assertTrue(
            controller.state.value.signingProtectionError.orEmpty()
                .contains("did not apply the selected signing protection"),
        )
    }

    @Test
    fun successStatusCanBeDismissedAndAutoHides() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(), this)
        assertTrue(controller.state.value.isStatusVisible)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.dismissStatus()
        assertFalse(controller.state.value.isStatusVisible)

        val autoHideController = unlockedControllerWith(FakeDemoWallet(), this)
        assertTrue(autoHideController.state.value.isStatusVisible)
        advanceTimeBy(4_000)
        runCurrent()
        assertFalse(autoHideController.state.value.isStatusVisible)
        assertEquals("Wallet ready", autoHideController.state.value.statusText)
    }

    @Test
    fun dismissedSuccessReappearsOnLaterIdenticalOutcome() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = unlockedControllerWith(wallet, this)
        wallet.credentials = listOf(sampleCredential)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://first")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertTrue(controller.state.value.isStatusVisible)
        controller.dismissStatus()
        assertFalse(controller.state.value.isStatusVisible)

        controller.startNewReceiveFlow()
        controller.updateOfferUrl("openid-credential-offer://second")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertTrue(controller.state.value.isStatusVisible)
    }

    @Test
    fun dismissedErrorReappearsOnLaterIdenticalFailure() = runTest {
        val wallet = FakeDemoWallet(startIssuanceError = IllegalStateException("issuer unavailable"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://same")
        controller.previewOffer()
        runCurrent()

        val firstBanner = checkNotNull(controller.state.value.statusBanner())
        assertEquals(WalletStatusKind.Error, firstBanner.kind)
        assertTrue(firstBanner.message.contains("issuer unavailable"))
        assertTrue(controller.state.value.isStatusVisible)
        controller.dismissStatus()
        assertFalse(controller.state.value.isStatusVisible)

        controller.previewOffer()
        runCurrent()

        val secondBanner = checkNotNull(controller.state.value.statusBanner())
        assertEquals(firstBanner.message, secondBanner.message)
        assertTrue(secondBanner.occurrenceId > firstBanner.occurrenceId)
        assertTrue(controller.state.value.isStatusVisible)
    }

    @Test
    fun deleteCredentialRemovesItFromTheReadySession() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.deleteCredential("cred-1")
        runCurrent()

        assertEquals(listOf("cred-1"), wallet.deletedCredentialIds)
        val session = controller.state.value.session as WalletSessionState.Ready
        assertEquals(emptyList(), session.credentials)
    }

    @Test
    fun deleteCredentialDiscardsAnActivePresentationReview() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertTrue(controller.state.value.presentationReviewEnabled)
        assertTrue(controller.state.value.presentationReview != null)

        controller.deleteCredential("cred-1")
        runCurrent()

        assertEquals(null, controller.state.value.presentationReview)
        assertFalse(controller.state.value.presentationReviewEnabled)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
        val session = controller.state.value.session as WalletSessionState.Ready
        assertEquals(emptyList(), session.credentials)
    }

    @Test
    fun resetWalletDeletesDataClearsPinAndReturnsToSetup() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val sharingSettings = InMemoryDemoSharingSettingsStore()
        val controller = controllerWith(wallet, this, pinStore, sharingSettings = sharingSettings)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        assertTrue(pinStore.hasPin())
        controller.setShowDcApiPresentationPreview(false)
        controller.setProximityTransportProfile(WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct)

        controller.resetWallet()
        runCurrent()

        assertEquals(1, wallet.deleteWalletCalls)
        assertFalse(pinStore.hasPin())
        assertTrue(controller.state.value.auth is WalletAuthState.Setup)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
        assertFalse(controller.state.value.showDcApiPresentationPreview)
        assertFalse(sharingSettings.showDcApiPresentationPreview())
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct,
            controller.state.value.proximityTransportProfile,
        )
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct,
            sharingSettings.proximityTransportProfile(),
        )
    }

    @Test
    fun dcApiPresentationPreviewPreferencePersistsAcrossControllerRecreation() = runTest {
        val sharingSettings = InMemoryDemoSharingSettingsStore()
        val firstController = controllerWith(FakeDemoWallet(), this, sharingSettings = sharingSettings)
        assertTrue(firstController.state.value.showDcApiPresentationPreview)

        firstController.setShowDcApiPresentationPreview(false)
        assertFalse(firstController.state.value.showDcApiPresentationPreview)
        assertFalse(sharingSettings.showDcApiPresentationPreview())

        val recreatedController = controllerWith(FakeDemoWallet(), this, sharingSettings = sharingSettings)
        assertFalse(recreatedController.state.value.showDcApiPresentationPreview)
    }

    @Test
    fun proximityTransportProfilePersistsAcrossControllerRecreation() = runTest {
        val sharingSettings = InMemoryDemoSharingSettingsStore()
        val firstController = controllerWith(FakeDemoWallet(), this, sharingSettings = sharingSettings)
        assertEquals(
            WalletDemoProximityTransportProfile.Default,
            firstController.state.value.proximityTransportProfile,
        )

        firstController.setProximityTransportProfile(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid,
        )

        val recreatedController = controllerWith(FakeDemoWallet(), this, sharingSettings = sharingSettings)
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid,
            recreatedController.state.value.proximityTransportProfile,
        )
    }

    @Test
    fun resetWalletLeavesSetupWhenPinClearFailsAfterDelete() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val pinStore = FailingClearDemoPinStore()
        val controller = controllerWith(wallet, this, pinStore)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        assertTrue(pinStore.hasPin())
        assertTrue(controller.state.value.session is WalletSessionState.Ready)

        controller.resetWallet()
        runCurrent()

        assertEquals(1, wallet.deleteWalletCalls)
        assertTrue(pinStore.hasPin())
        val setup = controller.state.value.auth as WalletAuthState.Setup
        assertEquals(
            WalletDisplayText.failure(WalletDisplayText.ResetWalletFailed, "PIN verifier could not be cleared"),
            setup.error,
        )
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun resetWalletDisablesTheSessionOnDeletionFailureAndAllowsRetry() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        wallet.deleteWalletError = IllegalStateException("HTTP 500")
        val controller = unlockedControllerWith(wallet, this)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)

        controller.resetWallet()
        runCurrent()

        assertEquals(1, wallet.deleteWalletCalls)
        assertEquals(listOf(sampleCredential), wallet.credentials)
        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(controller.state.value.session is WalletSessionState.Failed)
        assertEquals(
            WalletOperationState.Failed(
                WalletDisplayText.failure(WalletDisplayText.ResetWalletFailed, "HTTP 500"),
                WalletDemoTab.Credentials,
            ),
            controller.state.value.operation,
        )
        wallet.deleteWalletError = null
        controller.resetWallet()
        runCurrent()
        assertEquals(2, wallet.deleteWalletCalls)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun configuredPinStartsRecreatedControllerInLoginAndUnlocksWithOriginalPin() = runTest {
        val pinStore = InMemoryDemoPinStore()
        val firstController = controllerWith(FakeDemoWallet(), this, pinStore)
        firstController.updatePin("123456")
        firstController.submitPin()
        firstController.updatePinConfirmation("123456")
        runCurrent()

        val recreatedWallet = FakeDemoWallet()
        val recreatedController = controllerWith(recreatedWallet, this, pinStore)

        assertTrue(recreatedController.state.value.auth is WalletAuthState.Login)
        recreatedController.updatePin("123456")
        recreatedController.submitPin()
        runCurrent()

        assertTrue(recreatedController.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(recreatedController.state.value.session is WalletSessionState.Ready)
        assertEquals(1, recreatedWallet.bootstrapCalls)
    }

    @Test
    fun recreatedControllerRejectsWrongPin() = runTest {
        val pinStore = InMemoryDemoPinStore().also { it.setPin("1234") }
        val controller = controllerWith(FakeDemoWallet(), this, pinStore)

        controller.updatePin("9999")
        controller.submitPin()
        runCurrent()

        val auth = controller.state.value.auth as WalletAuthState.Login
        assertEquals("Wrong PIN", auth.error)
        assertFalse(controller.state.value.isAuthenticating)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun wrongLoginPinKeepsWalletLocked() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(), this)

        controller.lock()
        controller.updatePin("9999")
        controller.submitPin()
        runCurrent()

        val auth = controller.state.value.auth as WalletAuthState.Login
        assertEquals("Wrong PIN", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
    }

    @Test
    fun correctLoginPinUnlocksWithoutRepeatingBootstrapWhenReady() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        assertEquals(1, wallet.bootstrapCalls)

        controller.lock()
        controller.updatePin("123456")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun receiveRefreshesCredentialsAndStatus() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(sampleCredential)
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.resolvedOfferUrl)
        assertEquals(1, wallet.receiveCalls)
        assertEquals(
            WalletOperationState.Succeeded("Received 1 credential(s)", WalletDemoTab.Credentials),
            controller.state.value.operation,
        )
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        val session = controller.state.value.session as WalletSessionState.Ready
        assertEquals(listOf(sampleCredential), session.credentials)
    }

    @Test
    fun receiveIgnoresBlankOfferUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("   ")
        controller.previewOffer()
        runCurrent()

        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveRequiresNonBlankTransactionCodeAndIssuesOnce() = runTest {
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
            receivedCredentialIds = listOf("cred-1"),
        )
        val controller = unlockedControllerWith(wallet, this)
        val offerUrl = "openid-credential-offer://example"

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl(offerUrl)
        controller.previewOffer()
        runCurrent()

        assertEquals(offerUrl, wallet.resolvedOfferUrl)
        assertEquals(0, wallet.receiveCalls)
        assertEquals(textTransactionCode(), controller.state.value.offerPreview?.transactionCode)
        assertFalse(controller.state.value.acceptOfferEnabled)
        assertEquals(WalletOperationState.OfferPreview, controller.state.value.operation)

        controller.acceptOffer()
        runCurrent()
        assertEquals(0, wallet.receiveCalls)

        controller.updateTxCode(" abc-123 ")
        assertTrue(controller.state.value.acceptOfferEnabled)
        wallet.credentials = listOf(sampleCredential)
        controller.acceptOffer()
        runCurrent()

        assertEquals(1, wallet.receiveCalls)
        assertEquals("abc-123", wallet.receivedTxCode)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun changingOfferResetsTransactionCodeState() = runTest {
        val wallet = FakeDemoWallet(offerResolution = offerPreview(transactionCode = textTransactionCode()))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://first")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("1234")

        controller.updateOfferUrl("openid-credential-offer://second")

        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun numericTransactionCodeIsFilteredCappedAndValidated() = runTest {
        val requirement = WalletDemoTransactionCodeRequirement(
            inputMode = WalletDemoTransactionCodeInputMode.Numeric,
            length = 6,
            description = null,
        )
        val controller = unlockedControllerWith(
            FakeDemoWallet(offerResolution = offerPreview(transactionCode = requirement)),
            this,
        )

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("12a34")

        assertEquals("1234", controller.state.value.requestDrafts.txCode)
        assertFalse(controller.state.value.acceptOfferEnabled)

        controller.updateTxCode("12a345678")

        assertEquals("123456", controller.state.value.requestDrafts.txCode)
        assertTrue(controller.state.value.acceptOfferEnabled)
    }

    @Test
    fun decliningOfferCancelsIssuanceSession() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.declineOffer()
        runCurrent()

        assertEquals(listOf("issuance-session"), wallet.cancelledIssuanceSessionIds)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun authorizationCodeIssuanceCompletesThroughTheCallbackSession() = runTest {
        val wallet = FakeDemoWallet(
            issuanceGrant = WalletDemoIssuanceGrant.AuthorizationCode,
            credentials = listOf(sampleCredential.copy(id = "cred-auth")),
            authorizationOutcome = WalletDemoIssuanceOutcome.Stored(listOf("cred-auth")),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://authorization-code")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals("openid://authorization", controller.state.value.authorizationRequestUrl)
        controller.authorizationRequestOpened()
        controller.handleDeepLink("openid://callback?code=code-1&state=state-1")
        runCurrent()

        assertEquals(listOf("openid://callback?code=code-1&state=state-1"), wallet.authorizationCallbackUris)
        assertEquals(listOf("cred-auth"), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun copySelectionIsBoundedAndForwardedForBothGrantsWithoutGeneratingKeysBeforeAcceptance() = runTest {
        for (grant in WalletDemoIssuanceGrant.entries) {
            val offered = offerPreview().let { it.copy(batchSize = 3, offeredCredentials = it.offeredCredentials +
                it.offeredCredentials.single().copy(configurationId = "OtherCredential")) }
            val wallet = FakeDemoWallet(offerResolution = offered, issuanceGrant = grant)
            val controller = unlockedControllerWith(wallet, this)
            controller.updateOfferUrl("openid-credential-offer://batch")
            controller.previewOffer()
            runCurrent()
            assertEquals(mapOf("ExampleCredential" to 1, "OtherCredential" to 1), controller.state.value.issuanceCopyCounts)
            controller.updateIssuanceCopies("ExampleCredential", 0)
            controller.updateIssuanceCopies("OtherCredential", 0)
            assertFalse(controller.state.value.acceptOfferEnabled)
            controller.updateIssuanceCopies("ExampleCredential", 99)
            assertEquals(3, controller.state.value.issuanceCopyCounts["ExampleCredential"])
            controller.acceptOffer()
            runCurrent()
            val selection = wallet.receivedIssuanceSelections.single().single()
            assertEquals("ExampleCredential", selection.credentialConfigurationId)
            assertEquals(WalletDemoCredentialHolders.NewKeys(3), selection.holders)
            if (grant == WalletDemoIssuanceGrant.AuthorizationCode) {
                controller.authorizationRequestOpened()
                controller.handleDeepLink("openid://callback?code=code-1&state=state-1")
                runCurrent()
            }
        }
    }

    @Test
    fun sharedHolderBudgetLimitsTheWholeOfferReview() = runTest {
        val example = offerPreview().offeredCredentials.single()
        val offered = offerPreview().copy(
            batchSize = 2,
            holderKeyBudget = 2,
            offeredCredentials = listOf(
                example,
                example.copy(configurationId = "OtherCredential"),
                example.copy(configurationId = "ThirdCredential"),
            ),
        )
        val wallet = FakeDemoWallet(offerResolution = offered)
        val controller = unlockedControllerWith(wallet, this)
        controller.updateOfferUrl("openid-credential-offer://budget")
        controller.previewOffer()
        runCurrent()
        assertEquals(
            mapOf("ExampleCredential" to 1, "OtherCredential" to 1, "ThirdCredential" to 1),
            controller.state.value.issuanceCopyCounts,
        )
        assertTrue(controller.state.value.acceptOfferEnabled)
        controller.updateIssuanceCopies("ExampleCredential", 2)
        assertEquals(
            mapOf("ExampleCredential" to 2, "OtherCredential" to 1, "ThirdCredential" to 1),
            controller.state.value.issuanceCopyCounts,
        )
        controller.updateIssuanceCopies("OtherCredential", 2)
        assertEquals(1, controller.state.value.issuanceCopyCounts["OtherCredential"])
        assertTrue(controller.state.value.acceptOfferEnabled)
        controller.updateIssuanceCopies("OtherCredential", 0)
        controller.updateIssuanceCopies("ThirdCredential", 0)
        assertEquals(mapOf("ExampleCredential" to 2, "OtherCredential" to 0, "ThirdCredential" to 0), controller.state.value.issuanceCopyCounts)
        controller.acceptOffer()
        runCurrent()
        val selection = wallet.receivedIssuanceSelections.single().single()
        assertEquals("ExampleCredential", selection.credentialConfigurationId)
        assertEquals(WalletDemoCredentialHolders.NewKeys(2), selection.holders)
    }

    @Test
    fun singleCopiesOfSeveralTypesShareTheCurrentHolder() = runTest {
        val example = offerPreview().offeredCredentials.single()
        val offered = offerPreview().copy(
            batchSize = 2,
            holderKeyBudget = 1,
            offeredCredentials = listOf(example, example.copy(configurationId = "OtherCredential")),
        )
        val wallet = FakeDemoWallet(offerResolution = offered)
        val controller = unlockedControllerWith(wallet, this)
        controller.updateOfferUrl("openid-credential-offer://shared-holder")
        controller.previewOffer()
        runCurrent()
        assertEquals(mapOf("ExampleCredential" to 1, "OtherCredential" to 1), controller.state.value.issuanceCopyCounts)
        assertTrue(controller.state.value.acceptOfferEnabled)
        controller.updateIssuanceCopies("ExampleCredential", 2)
        assertEquals(1, controller.state.value.issuanceCopyCounts["ExampleCredential"])
        val currentKey = (controller.state.value.session as WalletSessionState.Ready).keyId
        controller.acceptOffer()
        runCurrent()
        val selections = wallet.receivedIssuanceSelections.single()
        assertEquals(listOf("ExampleCredential", "OtherCredential"), selections.map { it.credentialConfigurationId })
        selections.forEach { selection ->
            val holders = selection.holders as WalletDemoCredentialHolders.Existing
            assertEquals(listOf(currentKey), holders.bindings.map { it.keyId })
        }
    }

    @Test
    fun singleCopyUsesCurrentKeyAndRetriesForwardTheSameBatchPlan() = runTest {
        for (limit in listOf(null, 3)) {
            val wallet = FakeDemoWallet(offerResolution = offerPreview().copy(batchSize = limit),
                preAuthorizedOutcome = WalletDemoIssuanceOutcome.Failed("Try the transaction code again"))
            val controller = unlockedControllerWith(wallet, this)
            controller.updateOfferUrl("openid-credential-offer://retry")
            controller.previewOffer()
            runCurrent()
            val currentKey = (controller.state.value.session as WalletSessionState.Ready).keyId
            controller.acceptOffer()
            runCurrent()
            assertEquals(listOf(currentKey), (wallet.receivedIssuanceSelections.single().single().holders as WalletDemoCredentialHolders.Existing).bindings.map { it.keyId })
            controller.updateIssuanceCopies("ExampleCredential", 99)
            assertEquals(limit ?: 1, controller.state.value.issuanceCopyCounts["ExampleCredential"])
            controller.acceptOffer()
            runCurrent()
            controller.acceptOffer()
            runCurrent()
            assertEquals(wallet.receivedIssuanceSelections[1], wallet.receivedIssuanceSelections[2])
        }
    }

    @Test
    fun httpErrorAuthorizationCallbackIsDispatched() = runTest {
        val wallet = FakeDemoWallet(
            issuanceGrant = WalletDemoIssuanceGrant.AuthorizationCode,
            authorizationOutcome = WalletDemoIssuanceOutcome.Cancelled,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://authorization-code")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()
        controller.authorizationRequestOpened()
        controller.handleDeepLink("http://localhost:7106/?error=access_denied&state=state-1")
        runCurrent()

        assertEquals(
            listOf("http://localhost:7106/?error=access_denied&state=state-1"),
            wallet.authorizationCallbackUris,
        )
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals("", controller.state.value.requestDrafts.offerUrl)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertEquals(
            WalletOperationState.Succeeded(
                WalletDisplayText.CredentialOfferDeclined,
                WalletDemoTab.Receive,
            ),
            controller.state.value.operation,
        )
        assertEquals(WalletDisplayText.CredentialOfferDeclined, controller.state.value.statusText)
    }

    @Test
    fun preAuthorizedCancellationDeclinesOfferInsteadOfReceiveFailed() = runTest {
        val wallet = FakeDemoWallet(preAuthorizedOutcome = WalletDemoIssuanceOutcome.Cancelled)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(
            WalletOperationState.Succeeded(
                WalletDisplayText.CredentialOfferDeclined,
                WalletDemoTab.Receive,
            ),
            controller.state.value.operation,
        )
        assertEquals(WalletDisplayText.CredentialOfferDeclined, controller.state.value.statusText)
    }

    @Test
    fun deferredIssuanceCanBeResumedFromTheReceiveState() = runTest {
        val deferredCredential = WalletDemoDeferredCredential(
            id = "deferred-1",
            credentialConfigurationId = "ExampleCredential",
            intervalSeconds = 5,
        )
        val wallet = FakeDemoWallet(
            preAuthorizedOutcome = WalletDemoIssuanceOutcome.Deferred(
                storedCredentialIds = emptyList(),
                credentials = listOf(deferredCredential),
            ),
            deferredOutcome = WalletDemoIssuanceOutcome.Stored(listOf("cred-deferred")),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://deferred")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals(listOf(deferredCredential), controller.state.value.deferredCredentials)
        assertFalse(controller.state.value.receiveCompleted)

        controller.resumeDeferredCredential(deferredCredential.id)
        runCurrent()

        assertEquals(listOf(deferredCredential.id), wallet.resumedDeferredCredentialIds)
        assertEquals(emptyList(), controller.state.value.deferredCredentials)
        assertEquals(listOf("cred-deferred"), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
    }

    @Test
    fun partialIssuanceRefreshesSavedCredentialsAndKeepsPendingHandlesForBothOutcomes() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", 5, "dataset-2")
        for (outcome in listOf(
            WalletDemoIssuanceOutcome.Deferred(listOf("cred-1"), listOf(pending)),
            WalletDemoIssuanceOutcome.Failed("Later target failed", listOf("cred-1"), listOf(pending), offerConsumed = true, failedTargetCount = 1, notAttemptedTargetCount = 2),
        )) {
            val wallet = FakeDemoWallet(preAuthorizedOutcome = outcome)
            val controller = unlockedControllerWith(wallet, this)
            controller.updateOfferUrl("openid-credential-offer://partial")
            controller.previewOffer()
            runCurrent()
            wallet.credentials = listOf(sampleCredential)
            wallet.pendingCredentials = listOf(pending)
            controller.acceptOffer()
            runCurrent()
            assertEquals(listOf(sampleCredential), (controller.state.value.session as WalletSessionState.Ready).credentials)
            assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
            assertEquals(listOf(pending), controller.state.value.deferredCredentials)
            val operation = controller.state.value.operation
            val message = when (operation) {
                is WalletOperationState.Failed -> operation.message
                is WalletOperationState.Succeeded -> operation.message
                else -> error("Expected a completed receive operation")
            }
            assertTrue(message.contains("Saved credentials: 1. Pending targets: 1."))
            if (outcome is WalletDemoIssuanceOutcome.Failed) {
                assertTrue(message.contains("Failed targets: 1. Not attempted: 2."))
            }
            assertNull(controller.state.value.offerPreview)
            assertFalse(controller.state.value.acceptOfferEnabled)
            val reopened = unlockedControllerWith(wallet, this)
            assertEquals(listOf(pending), reopened.state.value.deferredCredentials)
        }
    }

    @Test
    fun continuationRefreshUsesRetainedStateAndResumeKeepsEarlierSavedIds() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", 5)
        val metadata = """{"credentialDisplay":[{"name":"Resident card"}]}"""
        val retained = pending.copy(status = WalletDemoContinuationStatus.AwaitingLocalSave, displayMetadataJson = metadata)
        val wallet = FakeDemoWallet(preAuthorizedOutcome = WalletDemoIssuanceOutcome.Deferred(listOf("cred-1"), listOf(pending)),
            deferredOutcome = WalletDemoIssuanceOutcome.Stored(listOf("cred-1", "cred-2")))
        val controller = unlockedControllerWith(wallet, this)
        controller.updateOfferUrl("openid-credential-offer://partial")
        controller.previewOffer()
        runCurrent()
        wallet.pendingCredentials = listOf(retained)
        wallet.credentials = listOf(sampleCredential)
        controller.acceptOffer()
        runCurrent()
        assertEquals(listOf(retained), controller.state.value.deferredCredentials)
        assertEquals(setOf("pending"), controller.state.value.issuanceReceipt?.pendingIds)
        wallet.credentials += sampleCredential.copy(id = "cred-2")
        controller.resumeDeferredCredential("pending")
        runCurrent()
        assertEquals(listOf("cred-1", "cred-2"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(emptySet(), controller.state.value.issuanceReceipt?.pendingIds)
    }

    @Test
    fun uncertainContinuationCannotResumeAndRefreshOnlyReadsLocalState() = runTest {
        for (status in listOf(WalletDemoContinuationStatus.RemoteOutcomeUncertain, WalletDemoContinuationStatus.StorageOutcomeUncertain)) {
            val pending = WalletDemoDeferredCredential("pending", "pid", null, status = status)
            val wallet = FakeDemoWallet(pendingCredentials = listOf(pending))
            val controller = unlockedControllerWith(wallet, this)
            controller.resumeDeferredCredential(pending.id)
            runCurrent()
            assertTrue(wallet.resumedDeferredCredentialIds.isEmpty())
            wallet.pendingCredentials = listOf(pending.copy(status = WalletDemoContinuationStatus.AwaitingLocalSave))
            controller.refreshIssuanceStatus()
            runCurrent()
            assertEquals(WalletDemoContinuationStatus.AwaitingLocalSave, controller.state.value.deferredCredentials.single().status)
            assertTrue(wallet.resumedDeferredCredentialIds.isEmpty())
        }
    }

    @Test
    fun failedStatusReadPreservesUncertainOutcomeAndDoesNotEnableASecondResume() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", null)
        val wallet = FakeDemoWallet(pendingCredentials = listOf(pending),
            deferredOutcome = WalletDemoIssuanceOutcome.Failed("Response lost", deferredCredentials = listOf(pending),
                kind = WalletDemoIssuanceFailureKind.RemoteOutcomeUncertain))
        val controller = unlockedControllerWith(wallet, this)
        wallet.listDeferredError = IllegalStateException("Status unavailable")
        controller.resumeDeferredCredential(pending.id)
        runCurrent()
        assertEquals(WalletDemoContinuationStatus.RemoteOutcomeUncertain, controller.state.value.deferredCredentials.single().status)
        controller.resumeDeferredCredential(pending.id)
        runCurrent()
        assertEquals(listOf(pending.id), wallet.resumedDeferredCredentialIds)
        assertTrue(controller.state.value.issuanceReceipt?.problem?.message == "Response lost")
    }

    @Test
    fun deferredFailureReplacesHandleWithLatestIntervalAndPreservesStoredProgress() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", 5)
        val updated = pending.copy(intervalSeconds = 15)
        val wallet = FakeDemoWallet(
            pendingCredentials = listOf(pending),
            deferredOutcome = WalletDemoIssuanceOutcome.Failed("Storage unavailable", listOf("cred-1"), listOf(updated)),
        )
        val controller = unlockedControllerWith(wallet, this)
        wallet.credentials = listOf(sampleCredential)
        controller.resumeDeferredCredential(pending.id)
        runCurrent()
        assertEquals(listOf(updated), controller.state.value.deferredCredentials)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(listOf(sampleCredential), (controller.state.value.session as WalletSessionState.Ready).credentials)
        assertIs<WalletOperationState.Failed>(controller.state.value.operation)
    }

    @Test
    fun mixedDeferredResumeRefreshesSavedCredentialsAndReportsPendingTargets() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", 5)
        val updated = pending.copy(intervalSeconds = 15)
        val wallet = FakeDemoWallet(pendingCredentials = listOf(pending),
            deferredOutcome = WalletDemoIssuanceOutcome.Deferred(listOf("cred-1"), listOf(updated)))
        val controller = unlockedControllerWith(wallet, this)
        wallet.credentials = listOf(sampleCredential)
        controller.resumeDeferredCredential(pending.id)
        runCurrent()
        assertEquals(listOf(updated), controller.state.value.deferredCredentials)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(listOf(sampleCredential), (controller.state.value.session as WalletSessionState.Ready).credentials)
        assertEquals("Saved credentials: 1. Pending targets: 1.",
            assertIs<WalletOperationState.Succeeded>(controller.state.value.operation).message)
    }

    @Test
    fun deferredPollingIsSingleFlightAndItsLateResultCannotReplaceANewReceiveFlow() = runTest {
        val pending = WalletDemoDeferredCredential("pending", "pid", 5)
        val gate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            pendingCredentials = listOf(pending),
            deferredOutcome = WalletDemoIssuanceOutcome.Failed("Old polling failed"),
            deferredGate = gate,
        )
        val controller = unlockedControllerWith(wallet, this)
        controller.resumeDeferredCredential(pending.id)
        controller.resumeDeferredCredential(pending.id)
        runCurrent()
        assertEquals(listOf(pending.id), wallet.resumedDeferredCredentialIds)
        controller.startNewReceiveFlow()
        controller.updateOfferUrl("openid-credential-offer://new")
        controller.previewOffer()
        runCurrent()
        val current = controller.state.value
        gate.complete(Unit)
        runCurrent()
        assertEquals(current, controller.state.value)
    }

    @Test
    fun receiveIsSingleFlight() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(startIssuanceGate = resolutionGate)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        controller.previewOffer()
        runCurrent()

        assertEquals(1, wallet.startIssuanceCalls)
        assertEquals(WalletOperationState.ResolvingOffer, controller.state.value.operation)

        wallet.credentials = listOf(sampleCredential)
        resolutionGate.complete(Unit)
        runCurrent()
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals(1, wallet.startIssuanceCalls)
        assertEquals(1, wallet.receiveCalls)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
    }

    @Test
    fun staleOfferResolutionCannotOverwriteIncomingDeepLink() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
            startIssuanceGate = resolutionGate,
            ignoreStartIssuanceCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)
        val replacementOffer = "openid-credential-offer://replacement"

        controller.updateOfferUrl("openid-credential-offer://original")
        controller.previewOffer()
        runCurrent()
        controller.handleDeepLink(replacementOffer)
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertEquals(null, state.offerPreview)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(0, wallet.receiveCalls)
    }

    @Test
    fun staleOfferResolutionFailureCannotOverwriteIncomingDeepLink() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            startIssuanceGate = resolutionGate,
            ignoreStartIssuanceCancellation = true,
            startIssuanceError = IllegalStateException("stale failure"),
        )
        val controller = unlockedControllerWith(wallet, this)
        val replacementOffer = "openid-credential-offer://replacement"

        controller.updateOfferUrl("openid-credential-offer://original")
        controller.previewOffer()
        runCurrent()
        controller.handleDeepLink(replacementOffer)
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertFalse(state.isError)
    }

    @Test
    fun lockCancelsOfferResolutionAndInvalidatesReceiveFlow() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            startIssuanceGate = resolutionGate,
            ignoreStartIssuanceCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        val resetKeyBeforeLock = controller.state.value.receiveNavigationResetKey

        controller.lock()
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertTrue(state.auth is WalletAuthState.Login)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(resetKeyBeforeLock + 1, state.receiveNavigationResetKey)
        assertEquals(0, wallet.receiveCalls)
        assertFalse(state.receiveCompleted)
        assertEquals(listOf("issuance-session"), wallet.cancelledIssuanceSessionIds)
    }

    @Test
    fun lockCancelsIssuanceAndClearsTransactionCode() = runTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
            receiveGate = receiveGate,
            ignoreReceiveCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("123456")
        controller.acceptOffer()
        runCurrent()
        assertEquals(1, wallet.receiveCalls)

        controller.lock()
        wallet.credentials = listOf(sampleCredential)
        receiveGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertTrue(state.auth is WalletAuthState.Login)
        assertEquals("", state.requestDrafts.txCode)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(emptyList(), state.lastReceivedCredentialIds)
        assertFalse(state.receiveCompleted)
    }

    @Test
    fun lockCancelsIssuanceAndDiscardsPresentationPreview() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        controller.lock()
        runCurrent()

        assertEquals(listOf("issuance-session"), wallet.cancelledIssuanceSessionIds)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals(null, controller.state.value.presentationPreview)
    }

    @Test
    fun presentationDeepLinkCancelsActiveIssuance() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://issuer.example")
        controller.previewOffer()
        runCurrent()
        val receiveResetKey = controller.state.value.receiveNavigationResetKey

        controller.handleDeepLink("openid4vp://verifier.example")
        runCurrent()

        assertEquals(listOf("issuance-session"), wallet.cancelledIssuanceSessionIds)
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals(receiveResetKey + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
    }

    @Test
    fun presentUpdatesStatusOnSuccess() = runTest {
        val wallet = FakeDemoWallet(presentationResult = WalletDemoOperationResult.Success("Presentation sent"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals("Presentation sent", controller.state.value.statusText)
    }

    @Test
    fun presentationPreviewSubmitRejectAndDismissUseSelectedHandle() = runTest {
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    credentialDataJson = "{}",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(preview, controller.state.value.presentationPreview)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Review presentation request", controller.state.value.statusText)
        assertEquals(setOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), controller.state.value.selectedPresentationCredentialOptions)

        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )

        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.cancelPresentationReview()
        runCurrent()

        assertEquals(
            WalletOperationState.Succeeded("Presentation review cancelled", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(listOf(presentationPreviewHandle), wallet.rejectedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(
            WalletOperationState.Succeeded("Presentation rejected", WalletDemoTab.Present),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentationPreviewIsSingleFlight() = runTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewGate = previewGate,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        controller.previewPresentation()
        runCurrent()

        assertEquals(1, wallet.previewPresentationCalls)
        assertEquals(WalletOperationState.ResolvingPresentation, controller.state.value.operation)

        previewGate.complete(Unit)
        runCurrent()
        controller.previewPresentation()
        runCurrent()

        assertEquals(1, wallet.previewPresentationCalls)
        assertEquals(presentationPreviewHandle, controller.state.value.presentationPreview?.previewHandle)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
    }

    @Test
    fun presentationActionsAreSingleFlightAndCannotOverwriteLock() = runTest {
        val submitGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationSubmitGate = submitGate,
            ignorePresentationSubmitCancellation = true,
            presentationPreview = WalletDemoPresentationPreview(
                previewHandle = presentationPreviewHandle,
                verifierMetadata = null,
                clientId = null,
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = sampleCredential.id,
                        label = sampleCredential.label,
                        issuer = sampleCredential.issuer.orEmpty(),
                        format = sampleCredential.format,
                        credentialDataJson = sampleCredential.credentialDataJson.orEmpty(),
                        disclosures = emptyList(),
                    )
                ),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.submitPresentation()
        controller.submitPresentation()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(1, wallet.submitPresentationCalls)
        assertEquals(emptyList(), wallet.rejectedPresentationPreviewHandles)
        assertEquals(WalletOperationState.Presenting, controller.state.value.operation)

        controller.lock()
        submitGate.complete(Unit)
        runCurrent()

        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(1, wallet.submitPresentationCalls)
    }

    @Test
    fun lockSuppressesLatePaymentInstructionsAndLoadingCannotSubmit() = runTest {
        val gate = CompletableDeferred<Unit>()
        var preparations = 0
        val base = FakeDemoWallet(presentationPreview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle, responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = null, clientId = null, requiresPaymentConsent = true,
            credentialOptions = listOf(WalletDemoPresentationCredentialOption(
                queryId = "payment", credentialId = "card", label = "Card", issuer = "Issuer",
                format = "dc+sd-jwt", credentialDataJson = "{}", disclosures = emptyList()))))
        val wallet = object : DemoWallet by base {
            override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
                val result = base.previewPresentation(requestUrl) as WalletDemoPresentationPreviewResult.Ready
                return WalletDemoPresentationPreviewResult.Ready(result.preview.copy(requiresPaymentConsent = true))
            }
            override suspend fun preparePaymentConsent(
                previewHandle: WalletDemoPresentationPreviewHandle,
                selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
                selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
                did: String?,
            ): WalletDemoPaymentConsent = withContext(NonCancellable) {
                preparations++
                gate.await()
                WalletDemoPaymentConsent("late", "en", null, null, "Pay", null, true, emptyList())
            }
        }
        val controller = unlockedControllerWith(wallet, this)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(WalletDemoPaymentReview.Loading, controller.state.value.paymentReview)
        controller.submitPresentation()
        runCurrent()
        assertEquals(0, base.submitPresentationCalls)
        controller.lock()
        gate.complete(Unit)
        runCurrent()
        assertEquals(WalletDemoPaymentReview.NotRequired, controller.state.value.paymentReview)
        assertNull(controller.state.value.presentationPreview)
    }

    @Test
    fun lockDiscardsPresentationPreviewResolvedAfterCancellation() = runTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewGate = previewGate,
            ignorePresentationPreviewCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        val resetKeyBeforeLock = controller.state.value.presentationNavigationResetKey

        controller.lock()
        previewGate.complete(Unit)
        runCurrent()

        assertEquals(resetKeyBeforeLock + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
    }

    @Test
    fun presentationDisclosureSelectionDefaultsOffAndSubmitsSelectedPaths() = runTest {
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "PID",
                    issuer = "Example Issuer",
                    format = "vc+sd-jwt",
                    credentialDataJson = "{}",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "Given name",
                            path = "$.given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Family name",
                            path = "$.family_name",
                            valueJson = "\"Lovelace\"",
                            displayValue = "Lovelace",
                            selectivelyDisclosable = true,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Age over 18",
                            path = "$.age_over_18",
                            valueJson = "true",
                            displayValue = "true",
                            selectivelyDisclosable = true,
                            required = true,
                            selectable = false,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Credential type",
                            path = "$.vct",
                            valueJson = "\"PID\"",
                            displayValue = "PID",
                            selectivelyDisclosable = false,
                        ),
                    ),
                )
            ),
            credentialRequirements = listOf(WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        val givenName = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.given_name")
        val familyName = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.family_name")
        val ageOver18 = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.age_over_18")
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(givenName in controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(familyName in controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(ageOver18 in controller.state.value.selectedPresentationDisclosureOptions)

        controller.updatePresentationRequestUrl("openid4vp://other")

        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        controller.togglePresentationDisclosure(familyName)
        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), wallet.submittedCredentialOptions)
        assertEquals(listOf(familyName), wallet.submittedDisclosureOptions)
    }

    @Test
    fun presentationCredentialOptionsWithSameCredentialIdToggleIndependently() = runTest {
        val first = WalletDemoPresentationCredentialOption(
            queryId = "identity",
            credentialId = "cred-1",
            label = "PID identity",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val second = first.copy(queryId = "age", label = "PID age")
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("identity", "age")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(first.selection)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())
        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )

        controller.togglePresentationCredential(first.selection)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())
        controller.submitPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), wallet.submittedCredentialOptions?.toSet())
    }

    @Test
    fun presentationPreviewSelectsOneCredentialOptionPerQuery() = runTest {
        val first = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "PID one",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val second = first.copy(credentialId = "cred-2", label = "PID two")
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(second.selection)

        assertEquals(setOf(second.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(second.selection), wallet.submittedCredentialOptions)
    }

    @Test
    fun presentationPreviewCanSelectMultipleCredentialsForOneQueryWhenAllowed() = runTest {
        val firstDisclosure = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.given_name")
        val secondDisclosure = WalletDemoPresentationDisclosureSelection("pid", "cred-2", "$.given_name")
        val first = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            multiple = true,
            label = "PID one",
            issuer = "Example Issuer",
            format = "vc+sd-jwt",
            credentialDataJson = "{}",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Given name",
                    path = firstDisclosure.path,
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val second = first.copy(
            credentialId = "cred-2",
            label = "PID two",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Given name",
                    path = secondDisclosure.path,
                    valueJson = "\"Grace\"",
                    displayValue = "Grace",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)

        controller.togglePresentationDisclosure(firstDisclosure)

        assertEquals(setOf(firstDisclosure), controller.state.value.selectedPresentationDisclosureOptions)

        controller.togglePresentationCredential(second.selection)

        assertEquals(
            setOf(first.selection, second.selection),
            controller.state.value.selectedPresentationCredentialOptions,
        )
        assertEquals(setOf(firstDisclosure), controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(secondDisclosure in controller.state.value.selectedPresentationDisclosureOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationDisclosure(secondDisclosure)
        controller.submitPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), wallet.submittedCredentialOptions?.toSet())
        assertEquals(setOf(firstDisclosure, secondDisclosure), wallet.submittedDisclosureOptions?.toSet())
    }

    @Test
    fun presentationPreviewSelectsFirstSatisfiableRequirementAlternativeOnly() = runTest {
        val mdl = WalletDemoPresentationCredentialOption(
            queryId = "mdl-id",
            credentialId = "cred-1",
            label = "mDL",
            issuer = "Example Issuer",
            format = "mso_mdoc",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val photoId = mdl.copy(queryId = "photo-id", credentialId = "cred-2", label = "Photo ID")
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(mdl, photoId),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("mdl-id"), listOf("photo-id")))
            ),
        )
        val controller = unlockedControllerWith(FakeDemoWallet(presentationPreview = preview), this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(mdl.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())
    }

    @Test
    fun presentationSelectionRequiresNonEmptySelectionWhenRequirementsAreEmpty() = runTest {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "optional-address",
            credentialId = "cred-1",
            label = "Address",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(option),
            credentialRequirements = emptyList(),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(option.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(option.selection)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentationCredentialSelectionRequiresQueriesWithoutVisibleOptions() = runTest {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "identity",
            credentialId = "cred-1",
            label = "PID identity",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(option),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(
                    options = listOf(listOf("identity", "age"))
                )
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(option.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentIgnoresBlankRequestUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("   ")
        controller.present()
        runCurrent()

        assertEquals(null, wallet.presentedRequestUrl)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun externalEntryCannotReplaceAnotherMobileFlow() = runTest {
        var available = false
        val controller = WalletDemoController(FakeDemoWallet(), InMemoryDemoPinStore(),
            canOpenExternalRequest = { available }, scope = backgroundScope, dispatcher = StandardTestDispatcher(testScheduler))
        controller.handleDeepLink("openid4vp://external")
        assertEquals(null, controller.state.value.externalFlow)
        assertTrue(controller.state.value.incomingLinkNotice != null)
        available = true
        controller.dismissIncomingLinkNotice()
        controller.handleDeepLink("openid4vp://external")
        assertTrue(controller.state.value.externalFlow is WalletExternalFlow.Pending)
    }

    @Test
    fun callbackWithoutOriginalSessionExplainsRecoveryWithoutReplayingAnOffer() = runTest {
        val wallet = FakeDemoWallet()
        val controller = controllerWith(wallet, this)
        controller.handleDeepLink("openid://callback?code=orphan&state=lost")
        assertTrue(controller.state.value.externalFlow is WalletExternalFlow.UnavailableCallback)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        controller.prepareExternalFlow()
        runCurrent()
        assertEquals(0, wallet.startIssuanceCalls)
        assertEquals(0, wallet.receiveCalls)
        assertTrue(controller.closeExternalFlow())
    }

    @Test
    fun externalOfferWaitsForUnlockPreparesOnceAndKeepsReceiptUntilClosed() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = controllerWith(wallet, this)
        val url = "openid-credential-offer://external"
        controller.handleDeepLink(url)
        controller.prepareExternalFlow()
        runCurrent()
        assertEquals(0, wallet.startIssuanceCalls)
        assertTrue(controller.state.value.externalFlow is WalletExternalFlow.Pending)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        runCurrent()
        repeat(2) { controller.prepareExternalFlow(); runCurrent() }
        controller.handleDeepLink(url)
        controller.prepareExternalFlow()
        runCurrent()
        assertEquals(1, wallet.startIssuanceCalls)
        assertEquals(0, wallet.receiveCalls)
        controller.acceptOffer()
        runCurrent()
        assertEquals(1, wallet.receiveCalls)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertTrue(controller.state.value.issuanceReceipt != null)
        assertTrue(controller.closeExternalFlow())
        assertFalse(controller.closeExternalFlow())
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
    }

    @Test
    fun incomingLinkAndDismissCannotInterruptReceiving() = runTest {
        val gate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential), receiveGate = gate)
        val controller = unlockedControllerWith(wallet, this)
        val url = "openid-credential-offer://external"
        controller.handleDeepLink(url)
        controller.prepareExternalFlow()
        runCurrent()
        controller.acceptOffer()
        runCurrent()
        assertEquals(WalletOperationState.Receiving, controller.state.value.operation)
        assertFalse(controller.closeExternalFlow())
        controller.handleDeepLink("openid4vp://replacement")
        assertEquals(url, controller.state.value.externalFlow?.url)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertTrue(controller.state.value.incomingLinkNotice != null)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, wallet.receiveCalls)
        assertTrue(controller.state.value.issuanceReceipt != null)
        assertTrue(controller.closeExternalFlow())
    }

    @Test
    fun closingExternalPreviewDiscardsLateResolutionWithoutReceiving() = runTest {
        val gate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(startIssuanceGate = gate, ignoreStartIssuanceCancellation = true)
        val controller = unlockedControllerWith(wallet, this)
        controller.handleDeepLink("openid-credential-offer://external")
        controller.prepareExternalFlow()
        runCurrent()
        assertTrue(controller.closeExternalFlow())
        gate.complete(Unit)
        runCurrent()
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals(null, controller.state.value.externalFlow)
        assertEquals(0, wallet.receiveCalls)
        assertEquals(listOf("issuance-session"), wallet.cancelledIssuanceSessionIds)
    }

    @Test
    fun handleDeepLinkRoutesCredentialOffersAndPresentationRequests() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        controller.handleDeepLink(presentationUrl)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
    }

    @Test
    fun handleDeepLinkResetsCompletedReceiveAndPresentationState() = runTest {
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = WalletDemoPresentationPreview(
                previewHandle = presentationPreviewHandle,
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
                verifierMetadata = verifierMetadata("Example Verifier"),
                clientId = "https://verifier.example",
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = "cred-1",
                        label = "Example Credential",
                        issuer = "Example Issuer",
                        format = "jwt_vc_json",
                        credentialDataJson = "{}",
                        disclosures = emptyList(),
                    )
                ),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
                ),
            )
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl(offerUrl)
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)

        controller.updatePresentationRequestUrl(presentationUrl)
        controller.previewPresentation()
        runCurrent()
        controller.submitPresentation()
        runCurrent()
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)

        val presentationResetKeyBeforeOfferLink = controller.state.value.presentationNavigationResetKey
        controller.handleDeepLink(offerUrl)

        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(2, controller.state.value.receiveNavigationResetKey)
        assertEquals(presentationResetKeyBeforeOfferLink + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertTrue(controller.state.value.receiveActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        val presentationResetKeyBeforePresentationLink = controller.state.value.presentationNavigationResetKey
        val receiveResetKeyBeforePresentationLink = controller.state.value.receiveNavigationResetKey
        controller.handleDeepLink(presentationUrl)

        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(receiveResetKeyBeforePresentationLink + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(presentationResetKeyBeforePresentationLink + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationPreviewActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.handleDeepLink(presentationUrl)

        // Duplicate delivery preserves the same external request and its review.
        assertEquals(receiveResetKeyBeforePresentationLink + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(presentationResetKeyBeforePresentationLink + 1, controller.state.value.presentationNavigationResetKey)
    }

    @Test
    fun receiveCompletionTracksReceivedCredentialsAndCanStartNewFlow() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = unlockedControllerWith(wallet, this)

        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        assertTrue(controller.state.value.receiveActionEnabled)

        wallet.credentials = listOf(sampleCredential)
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertFalse(controller.state.value.receiveCompleted)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals("", controller.state.value.requestDrafts.offerUrl)
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)

        controller.selectTab(WalletDemoTab.Receive)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertEquals("", controller.state.value.requestDrafts.offerUrl)
        assertFalse(controller.state.value.receiveActionEnabled)
    }

    @Test
    fun receiveCompletionDerivesNewCredentialsWhenWalletReturnsNoIds() = runTest {
        val existingCredential = sampleCredential.copy(id = "old-cred", label = "Existing Credential")
        val newCredential = sampleCredential.copy(id = "new-cred", label = "New Credential")
        val wallet = FakeDemoWallet(credentials = listOf(existingCredential), receivedCredentialIds = emptyList())
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(existingCredential, newCredential)
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals(listOf("new-cred"), controller.state.value.lastReceivedCredentialIds)
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf(newCredential), controller.state.value.receivedCredentials())
    }

    @Test
    fun receiveDoesNotCompleteWhenNoDisplayableCredentialIsAvailable() = runTest {
        val wallet = FakeDemoWallet(credentials = emptyList(), receivedCredentialIds = listOf("missing-cred"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertEquals(
            WalletOperationState.Failed(
                "Receive failed: received credentials are not available locally",
                WalletDemoTab.Receive,
            ),
            controller.state.value.operation,
        )
        assertEquals(
            "Receive failed: received credentials are not available locally",
            controller.state.value.statusText,
        )
    }

    @Test
    fun presentationCompletionReturnsToDefaultEntry() = runTest {
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    credentialDataJson = "{}",
                    disclosures = emptyList(),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationPreviewActionEnabled)

        controller.previewPresentation()
        runCurrent()
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)

        controller.submitPresentation()
        runCurrent()

        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals("Presentation sent", controller.state.value.statusText)
    }

    @Test
    fun rejectionSurfacesVerifierContinuationExactlyOnce() = runTest {
        val continuationUrl = "wallet-demo://presentation-complete"
        val wallet = FakeDemoWallet(
            rejectionResult = WalletDemoOperationResult.Success(
                WalletDisplayText.PresentationRejected,
                WalletDemoPresentationContinuation.Url(continuationUrl),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(
            WalletDemoPresentationContinuation.Url(continuationUrl),
            controller.state.value.pendingPresentationContinuation?.continuation,
        )
        assertFalse(controller.state.value.presentationCompleted)
        assertTrue(controller.state.value.operation is WalletOperationState.DecliningPresentation)

        controller.completePresentationContinuation()

        assertEquals(null, controller.state.value.pendingPresentationContinuation)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertEquals(
            WalletOperationState.Succeeded(WalletDisplayText.PresentationRejected, WalletDemoTab.Present),
            controller.state.value.operation,
        )
    }

    @Test
    fun formPostRejectionRemainsPendingUntilDeliveryAndSurfacesFailure() = runTest {
        val html = "<form method=\"post\" action=\"https://verifier.example/response\"></form>"
        val wallet = FakeDemoWallet(
            rejectionResult = WalletDemoOperationResult.Success(
                WalletDisplayText.PresentationRejected,
                WalletDemoPresentationContinuation.FormPostHtml(html),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(
            WalletDemoPresentationContinuation.FormPostHtml(html),
            controller.state.value.pendingPresentationContinuation?.continuation,
        )
        assertFalse(controller.state.value.presentationCompleted)

        controller.failPresentationContinuation("network unavailable")

        assertEquals(null, controller.state.value.pendingPresentationContinuation)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(
            WalletOperationState.Failed(
                "Could not deliver the verifier response: network unavailable",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    private fun controllerWith(
        wallet: DemoWallet,
        scope: TestScope,
        pinStore: DemoPinStore = InMemoryDemoPinStore(),
        biometricAuthenticator: DemoBiometricAuthenticator = UnavailableDemoBiometricAuthenticator,
        signingProtectionMode: WalletDemoSigningProtectionMode = WalletDemoSigningProtectionMode.Optional,
        signingProtectionStore: WalletDemoSigningProtectionStore = InMemoryWalletDemoSigningProtectionStore(),
        sharingSettings: DemoSharingSettingsStore = InMemoryDemoSharingSettingsStore(),
    ): WalletDemoController =
        WalletDemoController(
            wallet = wallet,
            pinStore = pinStore,
            biometricAuthenticator = biometricAuthenticator,
            signingProtectionMode = signingProtectionMode,
            signingProtectionStore = signingProtectionStore,
            sharingSettings = sharingSettings,
            scope = scope.backgroundScope,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
        )

    private fun runManualRefresh(
        wallet: DemoWallet,
        dispatcher: ManualDispatcher = ManualDispatcher(),
        body: (WalletDemoController, ManualDispatcher) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val controller = WalletDemoController(
                wallet = wallet,
                pinStore = InMemoryDemoPinStore(),
                scope = scope,
                dispatcher = dispatcher,
            )
            dispatcher.drain()
            body(controller, dispatcher)
        } finally {
            scope.cancel()
        }
    }

    private fun unlockedControllerWith(wallet: DemoWallet, scope: TestScope): WalletDemoController {
        val controller = controllerWith(wallet, scope)
        controller.updatePin("123456")
        controller.submitPin()
        controller.updatePinConfirmation("123456")
        scope.runCurrent()
        return controller
    }

    private companion object {
        val sampleCredential = WalletDemoCredential(
            id = "cred-1",
            format = "jwt_vc_json",
            issuer = "Example Issuer",
            subject = "did:key:holder",
            label = "Example Credential",
            addedAt = "2026-06-17",
            credentialDataJson = WalletDemoSampleCredentialData.credentialDataJsonWithPortrait,
        )
    }
}

private class ManualDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    var executing: Boolean = false
        private set

    override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean = true

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runLatest() {
        check(queue.isNotEmpty()) { "expected a queued biometric refresh" }
        run(queue.removeLast())
    }

    fun drain() {
        var steps = 0
        while (queue.isNotEmpty()) {
            run(queue.removeFirst())
            steps += 1
            check(steps < 100) { "biometric refresh dispatcher did not settle" }
        }
    }

    private fun run(block: Runnable) {
        executing = true
        try {
            block.run()
        } finally {
            executing = false
        }
    }
}

private class FailingClearDemoPinStore : DemoPinStore {
    private var configuredPin: String? = null

    override fun hasPin(): Boolean = configuredPin != null

    override suspend fun setPin(pin: String) {
        configuredPin = pin
    }

    override suspend fun verifyPin(pin: String): Boolean = configuredPin == pin

    override fun isBiometricUnlockEnabled(): Boolean = false

    override fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

    override fun clear() {
        error("PIN verifier could not be cleared")
    }
}

private class RecoverableDemoPinStore : DemoPinStore {
    var isAvailable = false
    var setPinCalls = 0

    override fun hasPin(): Boolean {
        check(isAvailable) { "PIN storage is unavailable" }
        return true
    }

    override suspend fun setPin(pin: String) {
        setPinCalls += 1
    }

    override suspend fun verifyPin(pin: String): Boolean = true

    override fun isBiometricUnlockEnabled(): Boolean = false

    override fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

    override fun clear() = Unit
}

private class FakeDemoBiometricAuthenticator(
    var available: Boolean = true,
    var result: DemoBiometricResult = DemoBiometricResult.Succeeded,
) : DemoBiometricAuthenticator {
    var authenticateCalls = 0

    override fun isAvailable(): Boolean = available

    override suspend fun authenticate(reason: String): DemoBiometricResult {
        authenticateCalls += 1
        return result
    }
}

private fun offerPreview(
    transactionCode: WalletDemoTransactionCodeRequirement? = null,
): WalletDemoOfferPreview = WalletDemoOfferPreview(
    issuer = WalletDemoIssuerMetadata(
        credentialIssuer = "https://issuer.example",
        display = WalletDemoMetadataDisplay(
            name = "Example Issuer",
            logoUri = null,
            logoAltText = null,
        ),
    ),
    offeredCredentials = listOf(
        WalletDemoOfferedCredentialMetadata(
            configurationId = "ExampleCredential",
            format = "vc+sd-jwt",
            vct = "ExampleCredential",
            doctype = null,
            display = null,
            claims = emptyList(),
        )
    ),
    transactionCode = transactionCode,
)

private fun textTransactionCode(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = WalletDemoTransactionCodeInputMode.Text,
        length = null,
        description = "Enter the code from the issuer",
    )

private fun verifierMetadata(name: String): WalletDemoVerifierMetadata =
    WalletDemoVerifierMetadata(
        display = WalletDemoMetadataDisplay(
            name = name,
            logoUri = null,
            logoAltText = null,
        ),
        clientUri = "https://verifier.example",
        policyUri = null,
        termsOfServiceUri = null,
    )

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    var pendingCredentials: List<WalletDemoDeferredCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val offerResolution: WalletDemoOfferPreview = offerPreview(),
    private val issuanceGrant: WalletDemoIssuanceGrant = WalletDemoIssuanceGrant.PreAuthorizedCode,
    private val preAuthorizedOutcome: WalletDemoIssuanceOutcome? = null,
    private val authorizationOutcome: WalletDemoIssuanceOutcome =
        WalletDemoIssuanceOutcome.Failed("Authorization code is not configured"),
    private val deferredOutcome: WalletDemoIssuanceOutcome =
        WalletDemoIssuanceOutcome.Failed("Deferred issuance is not configured"),
    private val deferredGate: CompletableDeferred<Unit>? = null,
    private val startIssuanceGate: CompletableDeferred<Unit>? = null,
    private val ignoreStartIssuanceCancellation: Boolean = false,
    private val startIssuanceError: Throwable? = null,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val ignoreReceiveCancellation: Boolean = false,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent),
    private val rejectionResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationRejected),
    private val presentationPreviewGate: CompletableDeferred<Unit>? = null,
    private val ignorePresentationPreviewCancellation: Boolean = false,
    private val presentationSubmitGate: CompletableDeferred<Unit>? = null,
    private val ignorePresentationSubmitCancellation: Boolean = false,
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoPresentationPreview(
        previewHandle = presentationPreviewHandle,
        responseEncryption = WalletDemoResponseEncryption.NotRequired,
        verifierMetadata = null,
        clientId = null,
        credentialOptions = emptyList(),
    ),
    private val presentationError: WalletDemoPresentationError? = null,
) : DemoWallet {
    var listDeferredError: Throwable? = null
    var bootstrapCalls = 0
    var bootstrapError: Throwable? = null
    var reportedSigningProtection: WalletDemoSigningProtection? = null
    var signingProtectionAvailability = WalletDemoSigningProtectionAvailability.Available
    var signingProtectionAvailabilityError: Throwable? = null
    val bootstrappedSigningProtections = mutableListOf<WalletDemoSigningProtection>()
    val preflightedSigningProtections = mutableListOf<WalletDemoSigningProtection>()
    var startIssuanceCalls = 0
    var resolvedOfferUrl: String? = null
    var receivedTxCode: String? = null
    var receiveCalls = 0
    val receivedIssuanceSelections = mutableListOf<List<WalletDemoCredentialSelection>>()
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var previewPresentationCalls = 0
    var submitPresentationCalls = 0
    var submittedPreviewHandle: WalletDemoPresentationPreviewHandle? = null
    var submittedCredentialOptions: List<WalletDemoPresentationCredentialSelection>? = null
    var submittedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>? = null
    val cancelledIssuanceSessionIds = mutableListOf<String>()
    val authorizationCallbackUris = mutableListOf<String>()
    val resumedDeferredCredentialIds = mutableListOf<String>()
    val discardedPresentationPreviewHandles = mutableListOf<WalletDemoPresentationPreviewHandle>()
    val rejectedPresentationPreviewHandles = mutableListOf<WalletDemoPresentationPreviewHandle>()
    val deletedCredentialIds = mutableListOf<String>()
    var deleteWalletCalls = 0
    var deleteWalletError: Throwable? = null

    override suspend fun bootstrap(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        bootstrappedSigningProtections += signingProtection
        bootstrapError?.let { throw it }
        return WalletDemoBootstrapResult(
            keyId = "key-1",
            did = "did:key:test",
            publicJwk = """{"kty":"OKP","crv":"Ed25519","x":"test"}""",
            signingProtection = reportedSigningProtection ?: signingProtection,
        )
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability {
        preflightedSigningProtections += signingProtection
        signingProtectionAvailabilityError?.let { throw it }
        return signingProtectionAvailability
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession {
        startIssuanceCalls += 1
        resolvedOfferUrl = offerUrl
        if (ignoreStartIssuanceCancellation) {
            withContext(NonCancellable) { startIssuanceGate?.await() }
        } else {
            startIssuanceGate?.await()
        }
        startIssuanceError?.let { throw it }
        return WalletDemoIssuanceSession(
            id = "issuance-session",
            grant = issuanceGrant,
            preview = offerResolution,
        )
    }

    override suspend fun beginAuthorizationIssuance(sessionId: String, credentials: List<WalletDemoCredentialSelection>): WalletDemoIssuanceAuthorization {
        receivedIssuanceSelections += credentials
        return WalletDemoIssuanceAuthorization("openid://authorization")
    }

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceOutcome {
        receiveCalls += 1
        receivedIssuanceSelections += credentials
        receivedTxCode = transactionCode
        if (ignoreReceiveCancellation) {
            withContext(NonCancellable) { receiveGate?.await() }
        } else {
            receiveGate?.await()
        }
        return preAuthorizedOutcome ?: WalletDemoIssuanceOutcome.Stored(receivedCredentialIds)
    }

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome {
        authorizationCallbackUris += callbackUri
        return authorizationOutcome
    }

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome {
        cancelledIssuanceSessionIds += sessionId
        return WalletDemoIssuanceOutcome.Cancelled
    }


    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> {
        listDeferredError?.let { throw it }
        return pendingCredentials
    }

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome {
        resumedDeferredCredentialIds += deferredCredentialId
        withContext(NonCancellable) { deferredGate?.await() }
        return deferredOutcome
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
        previewPresentationCalls += 1
        previewedRequestUrl = requestUrl
        if (ignorePresentationPreviewCancellation) {
            withContext(NonCancellable) { presentationPreviewGate?.await() }
        } else {
            presentationPreviewGate?.await()
        }
        return presentationError?.let(WalletDemoPresentationPreviewResult::Invalid)
            ?: WalletDemoPresentationPreviewResult.Ready(presentationPreview)
    }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ): WalletDemoOperationResult {
        submitPresentationCalls += 1
        submittedPreviewHandle = previewHandle
        submittedCredentialOptions = selectedCredentialOptions
        submittedDisclosureOptions = selectedDisclosureOptions
        if (ignorePresentationSubmitCancellation) {
            withContext(NonCancellable) { presentationSubmitGate?.await() }
        } else {
            presentationSubmitGate?.await()
        }
        return presentationResult
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) {
        discardedPresentationPreviewHandles += previewHandle
    }

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult {
        rejectedPresentationPreviewHandles += previewHandle
        return rejectionResult
    }

    override suspend fun deleteCredential(credentialId: String): Boolean {
        deletedCredentialIds += credentialId
        val remaining = credentials.filterNot { it.id == credentialId }
        val removed = remaining.size != credentials.size
        credentials = remaining
        return removed
    }

    var identityDetailsValue: WalletDemoIdentityDetails? = null
    var identityDetailsGate: CompletableDeferred<Unit>? = null
    var identityDetailsError: Exception? = null
    var identityDetailsCalls = 0
    override suspend fun identityDetails(): WalletDemoIdentityDetails? {
        identityDetailsCalls++
        identityDetailsGate?.await()
        identityDetailsError?.let { throw it }
        return identityDetailsValue
    }

    var identitySetupValue: WalletDemoIdentitySetup? = null
    var identitySetupGate: CompletableDeferred<Unit>? = null
    var identitySetupError: Exception? = null
    var identitySetupCalls = 0
    override suspend fun identitySetup(): WalletDemoIdentitySetup? {
        identitySetupCalls++
        identitySetupGate?.await()
        identitySetupError?.let { throw it }
        return identitySetupValue
    }

    override suspend fun deleteWallet() {
        deleteWalletCalls += 1
        deleteWalletError?.let { throw it }
        credentials = emptyList()
    }
}
