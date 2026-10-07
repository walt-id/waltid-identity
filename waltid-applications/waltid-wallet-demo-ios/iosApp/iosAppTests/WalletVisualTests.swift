import SnapshotTesting
import CoreImage
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK
@testable import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletVisualTests: XCTestCase {
    private var previousTimeZone: String?
    private var savedSharingPreferences: [(String, Any?)] = []
    override func setUp() {
        super.setUp()
        previousTimeZone = ProcessInfo.processInfo.environment["TZ"]
        setenv("TZ", "UTC", 1)
        tzset()
        NSTimeZone.resetSystemTimeZone()
        let defaults = UserDefaults(suiteName: IdentityDocumentSharedConfiguration.appGroupIdentifier)!
        savedSharingPreferences = [DemoSharingSettings.showDcApiPresentationPreviewKey,
            DemoSharingSettings.proximityTransportProfileKey, DemoSharingSettings.proximityApprovalModeKey]
            .map { ($0, defaults.object(forKey: $0)) }
        savedSharingPreferences.forEach { defaults.removeObject(forKey: $0.0) }
    }
    override func tearDown() {
        let defaults = UserDefaults(suiteName: IdentityDocumentSharedConfiguration.appGroupIdentifier)!
        savedSharingPreferences.forEach { defaults.set($0.1, forKey: $0.0) }
        if let previousTimeZone { setenv("TZ", previousTimeZone, 1) } else { unsetenv("TZ") }
        tzset()
        NSTimeZone.resetSystemTimeZone()
        super.tearDown()
    }

    func testControls() throws { try capture(WalletControlsPreview(), id: "components.controls.default") }
    func testControlsRtl() throws {
        try capture(WalletControlsPreview().environment(\.layoutDirection, .rightToLeft), id: "components.controls.rtl")
    }

    func testExternalReceiving() async throws { try await externalReceiving(unavailable: false) }
    func testExternalUnavailableCallback() async throws { try await externalReceiving(unavailable: true) }

    private func externalReceiving(unavailable: Bool) async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.statusMessage = ""
        model.externalFlow = unavailable ? .unavailableCallback(URL(string: "openid://callback")!)
            : .active(URL(string: "openid-credential-offer://fixture")!, .offer)
        model.selectedTab = .receive
        if !unavailable {
            let fixtures = try WalletVisualFixtures()
            model.offerPreview = try fixtures.offer()
            model.issuanceCopyCounts = try fixtures.copies()
        }
        // The real native sheet/window is covered by the URL-launch UI journey; this pins its content.
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}, onBack: {}).environment(\.walletOpenInApp, {}),
            id: unavailable ? "external.callback.unavailable" : "external.receiving.review", config: .iPhoneSe)
    }

    func testKeySummary() throws { try keySetup(.summary) }
    func testKeyRecovery() throws { try keySetup(.recovery) }
    func testKeyStorage() throws { try keySetup(.storage) }
    func testKeyApproval() throws { try keySetup(.approval) }

    private func keySetup(_ step: WalletIdentityScreenModel.Step) throws {
        let options = try WalletVisualFixtures().keySetupOptions()
        let selected = try XCTUnwrap(options.first)
        try capture(NavigationView {
            List { SigningKeySetupContent(options: options, selected: selected, step: step, onSelect: { _ in }, onEdit: { _ in }) }
                .navigationTitle(step == .summary ? "Set up your wallet" : step.title).navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    WalletActionBar(primary: WalletAction(step == .summary ? "Create signing key" : "Done", perform: {}),
                        secondary: step == .summary ? nil : WalletAction("Back", perform: {}))
                }
        }.navigationViewStyle(.stack), id: "onboarding.key.\(step)")
    }

    func testPinSetup() async throws { try await pin("setup") }
    func testPinMismatch() async throws { try await pin("mismatch") }
    func testPinConfirmation() async throws { try await pin("confirmation") }
    func testPinBiometricPrompt() async throws { try await pin("biometric_prompt") }
    func testPinUnlock() async throws { try await pin("unlock") }
    func testPinRtl() async throws { try await pin("rtl") }
    func testPinCompact() async throws { try await pin("compact_dark_large_text") }
    func testBiometricCancelled() async throws { try await biometricSetup(unavailable: false) }
    func testBiometricUnavailable() async throws { try await biometricSetup(unavailable: true) }

    private func biometricSetup(unavailable: Bool) async throws {
        let model = makeModel(biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: !unavailable))
        await model.readerTrustSettings.awaitPendingOperations()
        model.auth = .biometricSetup(unavailable ? .unavailable : .cancelled)
        try capture(BiometricSetupView(viewModel: model),
            id: "onboarding.biometric.\(unavailable ? "unavailable" : "cancelled")")
    }

    private func pin(_ state: String) async throws {
        let gate = DemoBiometricTestGate()
        let biometric = FakeDemoBiometricAuthenticator(isAvailable: state != "setup", gate: gate)
        let model = makeModel(biometricAuthenticator: biometric)
        await model.readerTrustSettings.awaitPendingOperations()
        if state == "unlock" { model.auth = .login; model.pin = "12" }
        else if state != "setup" {
            model.updatePin("1234")
            model.submitPin()
            if state == "mismatch" { model.updatePinConfirmation("4321") }
            if state == "compact_dark_large_text" { model.updatePinConfirmation("123") }
            if state == "biometric_prompt" {
                model.updatePinConfirmation("1234")
                let deadline = DispatchTime.now().uptimeNanoseconds + 20_000_000_000
                while biometric.authenticateCalls == 0 && DispatchTime.now().uptimeNanoseconds < deadline {
                    try await Task.sleep(nanoseconds: 10_000_000)
                }
                XCTAssertEqual(biometric.authenticateCalls, 1)
                XCTAssertTrue(model.isAuthenticating)
            }
        }
        if state == "mismatch" { XCTAssertEqual(model.pinError, "PIN confirmation does not match") }
        let compact = state == "compact_dark_large_text"
        if state == "biometric_prompt" {
            XCTAssertEqual(model.auth, .biometricSetup(nil))
            try capture(BiometricSetupView(viewModel: model), id: "onboarding.pin.\(state)")
        } else {
            try capture(PinView(viewModel: model)
                .environment(\.layoutDirection, state == "rtl" ? .rightToLeft : .leftToRight), id: "onboarding.pin.\(state)",
                config: compact ? .iPhoneSe : .iPhone13,
                colorScheme: compact ? .dark : .light, sizeCategory: compact ? .accessibilityMedium : .large)
        }
        await gate.complete(.failed)
    }

    func testAccessRejected() async throws { try await walletAccess("rejected") }
    func testAccessBiometricFallback() async throws { try await walletAccess("biometric_fallback") }
    func testAccessBiometricLockout() async throws { try await walletAccess("biometric_lockout") }
    func testAccessSettings() async throws { try await walletAccess("default") }
    func testAccessCurrentPin() async throws { try await walletAccess("current_pin") }
    func testAccessNewPin() async throws { try await walletAccess("new_pin") }
    func testAccessConfirmation() async throws { try await walletAccess("confirmation") }
    func testAccessSaveFailure() async throws { try await walletAccess("save_failure") }
    func testAccessPinChanged() async throws { try await walletAccess("pin_changed") }

    private func walletAccess(_ state: String) async throws {
        let store = VisualAccessPinStore()
        store.isBiometricUnlockEnabled = state.hasPrefix("biometric")
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: state.hasPrefix("biometric"),
            result: state == "biometric_lockout" ? .lockedOut : .failed)
        let model = makeModel(biometricAuthenticator: biometrics, pinStore: store)
        await model.readerTrustSettings.awaitPendingOperations()
        let unlocking = ["rejected", "biometric_fallback", "biometric_lockout"].contains(state)
        if unlocking {
            if state == "rejected" { model.updatePin("0000") }
            else { model.unlockWithBiometrics() }
            await settleAccess(model)
            try capture(PinView(viewModel: model), id: "access.unlock.\(state)")
        } else {
            model.auth = .unlocked
            if state != "default" {
                model.startPinChange()
                if state != "current_pin" {
                    model.updatePin("1234")
                    await settleAccess(model)
                    if state != "new_pin" {
                        model.updatePin("5678")
                        if ["save_failure", "pin_changed"].contains(state) {
                            store.failSaving = state == "save_failure"
                            model.updatePinConfirmation("5678")
                            await settleAccess(model)
                        }
                    }
                }
            }
            try capture(NavigationView { WalletAccessSettingsView(viewModel: model) }.navigationViewStyle(.stack),
                id: "settings.access.\(state)")
        }
    }

    private func settleAccess(_ model: WalletViewModel) async {
        let deadline = DispatchTime.now().uptimeNanoseconds + 5_000_000_000
        while model.isAuthenticating && DispatchTime.now().uptimeNanoseconds < deadline { await Task.yield() }
        XCTAssertFalse(model.isAuthenticating, "Fixture access operation did not finish")
    }

    func testHomeEmpty() async throws { try await home(empty: true) }
    func testHomeCredential() async throws { try await home(empty: false) }

    func testScanEmpty() throws {
        try capture(WalletScanView(onBack: {}, onOpen: { _, _ in }), id: "wallet.scan.empty")
    }

    func testScanUnsupported() throws {
        try capture(WalletScanView(input: "FIDO:/0123456789", onBack: {}, onOpen: { _, _ in }), id: "wallet.scan.unsupported")
    }

    func testScanWebLink() throws {
        try capture(WalletScanView(input: "https://example.test/request", onBack: {}, onOpen: { _, _ in }), id: "wallet.scan.link")
    }

    private func home(empty: Bool) async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.statusMessage = ""
        model.credentials = empty ? [] : [try WalletVisualFixtures().credential()]
        let cards = await CredentialDisplayNormalizer.cards(for: model.credentials)
        XCTAssertEqual(cards.count, model.credentials.count)
        try capture(CredentialsTabView(viewModel: model, selectedDetailsID: .constant(nil), cards: cards,
            onOpenSettings: {}, onScan: {}, onShareNearby: {}),
            id: empty ? "wallet.home.empty" : "wallet.home.credential")
    }

    func testSettingsRoot() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: IdentityDocumentSharedConfiguration.appGroupIdentifier))
        let keys = [DemoSharingSettings.showDcApiPresentationPreviewKey, DemoSharingSettings.proximityTransportProfileKey,
                    DemoSharingSettings.proximityApprovalModeKey]
        let saved = keys.map { ($0, defaults.object(forKey: $0)) }
        defer { for (key, value) in saved { defaults.set(value, forKey: key) } }
        keys.forEach { defaults.removeObject(forKey: $0) }
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        XCTAssertTrue(model.showDcApiPresentationPreview)
        XCTAssertEqual(model.proximityTransportProfile, .defaultProfile)
        try capture(NavigationView { SettingsView(viewModel: model) }.navigationViewStyle(.stack), id: "settings.root.default")
    }

    func testSettingsReader() async throws { try await readerSettings(required: false) }
    func testSettingsReaderRequired() async throws { try await readerSettings(required: true) }
    private func readerSettings(required: Bool) async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.readerTrustSettings.setReaderPolicy(required ? .requireTrusted : .allowAnonymousOrUntrusted)
        await model.readerTrustSettings.awaitPendingOperations()
        try capture(NavigationView { ReaderTrustSettingsView(controller: model.readerTrustSettings) }.navigationViewStyle(.stack),
            id: required ? "settings.reader_required.default" : "settings.reader.default")
    }
    func testReaderTrustImport() throws {
        try capture(ReaderTrustImportReviewView(preview: WalletVisualFixtures().readerTrustImport(), confirm: {}, cancel: {}),
            id: "settings.reader.import_review")
    }

    func testSettingsDcApiEnabled() throws { try dcApiSettings(enabled: true) }
    func testSettingsDcApiDisabled() throws { try dcApiSettings(enabled: false) }

    private func dcApiSettings(enabled: Bool) throws {
        try capture(NavigationView { DigitalCredentialsSettingsView(showWalletReview: .constant(enabled)) }
            .navigationViewStyle(.stack), id: enabled ? "settings.dc_api.enabled" : "settings.dc_api.disabled")
    }

    func testSettingsNearby() async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        try capture(NavigationView { NearbySettingsView(viewModel: model) }.navigationViewStyle(.stack), id: "settings.nearby.default")
    }

    func testSettingsConnection() async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        try capture(NavigationView { ConnectionSettingsView(viewModel: model) }.navigationViewStyle(.stack), id: "settings.connection.default")
    }

    func testSettingsTechnical() async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        try capture(NavigationView { TechnicalDetailsView(viewModel: model) }.navigationViewStyle(.stack), id: "settings.technical.unavailable")
    }

    func testCredentialDetails() throws {
        let details = try WalletVisualFixtures().credentialDetails()
        XCTAssertEqual(details.id, "visual-resident-card")
        XCTAssertFalse(details.groups.isEmpty)
        try capture(ScrollView { CredentialDetailsView(details: details).padding(20) }, id: "credential.details.identity")
    }

    func testLocalizedCredentialDetails() throws {
        let details = try WalletVisualFixtures().localizedCredentialDetails()
        XCTAssertEqual(details.groups.flatMap(\.items).prefix(4).map(\.label), ["Familienname", "Vorname", "Name im Namensraum", "Straße"])
        try capture(ScrollView { CredentialDetailsView(details: details).padding(20) }, id: "credential.details.localized_metadata")
    }

    func testBatchOffer() async throws {
        try await batchOffer(noneSelected: false)
    }

    func testSingleOffer() async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        let offer = try WalletVisualFixtures().offer()
        let credential = try XCTUnwrap(offer.credentials.first)
        model.isReady = true
        model.statusMessage = ""
        model.offerPreview = IssuanceOfferPreview(grant: offer.grant, issuer: offer.issuer,
            credentials: [credential], transactionCode: offer.transactionCode, batchSize: offer.batchSize)
        model.issuanceCopyCounts = [credential.configurationID: 1]
        XCTAssertTrue(model.acceptOfferEnabled)
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}), id: "batch.offer.single_full_art")
    }

    func testOfferDefinitions() throws {
        let offer = try WalletVisualFixtures().offer()
        let credential = try XCTUnwrap(offer.credentials.first)
        XCTAssertEqual(StoredCredentialMetadataParser.claims(from: credential.metadataJSON).map(\.name), ["Given name", "Family name"])
        try capture(ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OfferedCredentialDetails(credential: credential, issuerName: offer.issuer.name!, issuerIdentifier: offer.issuer.identifier)
            }.padding(20)
        }, id: "batch.offer.definitions")
    }

    func testBatchOfferWithNothingSelected() async throws {
        try await batchOffer(noneSelected: true)
    }

    func testCompactBatchOffer() async throws {
        let fixtures = try WalletVisualFixtures()
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.statusMessage = ""
        model.offerPreview = try fixtures.offer()
        model.issuanceCopyCounts = try fixtures.copies()
        XCTAssertTrue(model.acceptOfferEnabled)
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}), id: "batch.offer.compact_dark_large_text",
                    config: .iPhoneSe, colorScheme: .dark, sizeCategory: .accessibilityMedium)
    }

    func testProviderSharingReview() async throws { try await providerSharingReview() }
    func testSharingCredentialInformation() throws {
        let review = try WalletVisualFixtures().sharingReview()
        let option = try XCTUnwrap(review.credentialOptions.first)
        let details = CredentialDisplayNormalizer.details(for: option)
        let screen = SharingClaimsSheet(option: option, details: details, credentialSelected: true,
            selectedDisclosureOptions: [], requestedDisclosureItems: details.groups.first { $0.id == "requested" }?.items ?? [],
            isLoading: false, isReadOnly: false, onToggleDisclosure: { _ in }, onDismiss: {})
        try capture(screen, id: "sharing.credential_information")
    }
    func testCompactProviderSharingReview() async throws { try await providerSharingReview(compact: true) }
    func testCompactProviderSharingReviewLastRow() async throws {
        try await providerSharingReview(compact: true, scrollToBottom: true)
    }

    private func providerSharingReview(compact: Bool = false, scrollToBottom: Bool = false) async throws {
        let review = try WalletVisualFixtures().sharingReview()
        let selection = SharingSelection(credentials: review.defaultCredentialSelection())
        XCTAssertTrue(review.hasCompleteCredentialSelection(selection.credentials))
        let screen = SharingReviewScreen(title: "Share documents", review: review, selection: selection,
            selectionComplete: true, onToggleCredential: { _ in }, onToggleDisclosure: { _ in }, onSubmit: {}, onCancel: {})
        let id = (compact ? "sharing.provider.compact_dark_large_text" : "sharing.provider.review")
            + (scrollToBottom ? ".last" : "")
        try await captureReview(screen, id: id,
            config: compact ? .iPhoneSe : .iPhone13, colorScheme: compact ? .dark : .light,
            sizeCategory: compact ? .accessibilityMedium : .large,
            expected: Set(review.credentialOptions.map { $0.selection.id }), scrollToBottom: scrollToBottom)
    }

    func testProviderPreparing() throws { try providerStatus(failure: nil) }
    func testProviderFailure() throws { try providerStatus(failure: "The request could not be verified.") }

    private func providerStatus(failure: String?) throws {
        try capture(SharingReviewScreen(title: "Share documents", review: nil, selection: SharingSelection(),
            selectionComplete: false, failure: failure,
            onToggleCredential: { _ in }, onToggleDisclosure: { _ in }, onSubmit: {}, onCancel: {}),
            id: failure == nil ? "sharing.provider.preparing" : "sharing.provider.failure")
    }

    func testMixedPaymentReview() async throws {
        let fixtures = try WalletVisualFixtures()
        let review = try fixtures.sharingReview(payment: true)
        let selection = SharingSelection(credentials: review.defaultCredentialSelection())
        XCTAssertEqual(selection.credentials.count, 2)
        XCTAssertTrue(review.hasCompleteCredentialSelection(selection.credentials))
        try await captureReview(SharingReviewScreen(title: "Payment", review: review, selection: selection,
            selectionComplete: true, paymentReview: .ready(try fixtures.payment()),
            onToggleCredential: { _ in }, onToggleDisclosure: { _ in }, onSubmit: {}, onCancel: {}),
            id: "payment.mixed_credentials.main", expected: Set(review.credentialOptions.map { $0.selection.id }))
    }

    func testPaymentLoading() async throws { try await paymentStatus(.loading, id: "payment.loading") }
    func testPaymentBlocked() async throws {
        try await paymentStatus(.blocked("Required issuer payment labels are missing."), id: "payment.blocked")
    }
    private func paymentStatus(_ state: PaymentReviewState, id: String) async throws {
        let review = try WalletVisualFixtures().sharingReview(payment: true)
        try await captureReview(SharingReviewScreen(title: "Payment", review: review,
            selection: SharingSelection(credentials: review.defaultCredentialSelection()),
            selectionComplete: true, paymentReview: state,
            onToggleCredential: { _ in }, onToggleDisclosure: { _ in }, onSubmit: {}, onCancel: {}),
            id: id, expected: Set(review.credentialOptions.map { $0.selection.id }))
        XCTAssertFalse(state.canConfirm)
    }

    func testPaymentLocalizedCompact() throws {
        let state = PaymentReviewState.ready(try WalletVisualFixtures().payment(localized: true))
        try capture(WalletReviewScaffold {
            PaymentConsentView(state: state)
        } actions: {
            ReviewActions(selectionComplete: true, isLoading: false, onSubmit: {}, onReject: nil,
                onCancel: {}, paymentReview: state)
        }, id: "payment.localized.compact_large_text", config: .iPhoneSe, sizeCategory: .accessibilityMedium)
    }

    func testPaymentConsent() throws {
        let consent = try WalletVisualFixtures().payment()
        XCTAssertEqual(consent.fields.count, 4)
        XCTAssertEqual(consent.fields.first?.value, "11.56 EUR")
        try capture(ScrollView { PaymentConsentView(state: .ready(consent)).padding(20) }, id: "payment.consent.main")
    }

    func testCredentialImages() async throws {
        let items = try WalletVisualFixtures().credentialImages()
        for item in items {
            guard case .image(_, let data, _, _) = item.value else { return XCTFail("Expected a resolved image") }
            XCTAssertNotNil(UIImage(data: data))
        }
        var readyImages: Set<String> = []
        let content = ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(items) { item in
                    ClaimValueRow(item: item)
                }
            }.padding(20)
        }.onPreferenceChange(CredentialImageReadinessKey.self) { readyImages = $0 }
            .environment(\.locale, Locale(identifier: "en_US"))
            .environment(\.colorScheme, .light)
            .environment(\.sizeCategory, .large)
        try await captureWhenReady(content, id: "credential.media.loaded", isReady: {
            items.allSatisfy { readyImages.contains($0.path.id) }
        }, failure: "Image rows did not render their thumbnails", readinessDescription: {
            "Expected \(items.map { $0.path.id }.sorted()), rendered \(readyImages.sorted())"
        })
    }

    func testPartialBatchResult() async throws { try await batchResult() }
    func testLocalSaveResult() async throws { try await batchResult(status: .awaitingLocalSave, id: "local_save_pending") }
    func testRemoteUncertainResult() async throws { try await batchResult(status: .remoteOutcomeUncertain, id: "remote_uncertain") }
    func testStorageUncertainResult() async throws { try await batchResult(status: .storageOutcomeUncertain, id: "storage_uncertain") }
    func testPartialFailureResult() async throws { try await batchResult(id: "partial_failure", failure: true) }

    private func batchResult(status: IssuanceContinuationStatus = .awaitingIssuer, id: String = "saved_and_deferred", failure: Bool = false) async throws {
        let fixtures = try WalletVisualFixtures()
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.selectedTab = .receive
        model.statusMessage = try fixtures.partialResultStatus()
        model.credentials = [try fixtures.credential()]
        model.lastReceivedCredentialIDs = model.credentials.map(\.id)
        let pending = try fixtures.deferredCredential()
        model.deferredCredentials = failure ? [] : [DeferredCredential(id: pending.id,
            credentialConfigurationID: pending.credentialConfigurationID, intervalSeconds: pending.intervalSeconds,
            status: status, displayMetadataJSON: pending.displayMetadataJSON)]
        let problem: IssuanceFailure? = failure ? .init(code: .issuerResponse, message: "The issuer could not finish this request.",
            targetFailure: .init(target: .init(configurationID: "failed"), stage: .request,
                notAttempted: [.init(configurationID: "unattempted-1"), .init(configurationID: "unattempted-2")])) : nil
        model.issuanceReceipt = IssuanceReceipt(issuer: try fixtures.offer().issuer, pendingIDs: Set(model.deferredCredentials.map(\.id)), problem: problem)
        if failure { model.statusMessage = problem?.message ?? ""; model.isError = true }
        XCTAssertEqual(model.credentials.count, 1)
        XCTAssertEqual(model.deferredCredentials.count, failure ? 0 : 1)
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}), id: "batch.result.\(id)")
    }

    func testNearbyPermission() async throws { try await nearbyState("permission") }
    func testNearbyReview() async throws { try await nearbyState("review") }
    func testNearbyReviewDisclosures() async throws { try await nearbyState("review", showDisclosures: true) }
    func testNearbyExpired() async throws { try await nearbyState("expired") }
    func testNearbyReceipt() async throws { try await nearbyState("receipt") }

    private func nearbyState(_ kind: String, showDisclosures: Bool = false) async throws {
        let model = try await makeWalletVisualProximityState(kind)
        defer { model.proximityPresentation.dismiss() }
        let details = CredentialDisplayNormalizer.details(for: try WalletVisualFixtures().nearbyCredential())
        let screen = NavigationView {
            ProximityPresentationView(viewModel: model.proximityPresentation, approvalMode: .constant(.askEachTime),
                headerOwnsClose: true,
                credentialDetailsByID: [details.id: details])
                .navigationTitle("Share nearby").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button(action: model.proximityPresentation.requestClose) {
                            Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44)
                        }.accessibilityLabel("Close nearby sharing")
                    }
                }
        }.navigationViewStyle(.stack)
            // Pin the receipt's hour cycle independently of the machine's 12/24-hour preference.
            .environment(\.locale, Locale(identifier: "en_US@hours=h23"))
            .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
        if kind == "review" {
            XCTAssertTrue(model.proximityPresentation.canApprove)
            XCTAssertEqual(details.groups.flatMap(\.items).first?.label, "Given name")
        } else if kind == "expired" || kind == "receipt" { XCTAssertTrue(model.proximityPresentation.isTerminal) }
        if showDisclosures {
            let content = screen.environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
                .environment(\.locale, Locale(identifier: "en_US")).environment(\.sizeCategory, .accessibilityMedium)
            try await captureWhenReady(content, id: "nearby.review.disclosures", config: .iPhoneSe, isReady: { true }, failure: "Review not ready", scrollToBottom: true, scrollFraction: 0.55)
        } else {
            try capture(screen, id: "nearby.\(kind)", config: kind == "review" ? .iPhoneSe : .iPhone13,
                sizeCategory: kind == "review" ? .accessibilityMedium : .large)
        }
    }

    func testNearbyReady() async throws {
        let model = try await makeWalletVisualProximityModel(qrPayload: WalletVisualFixtures().nearbyQrPayload())
        defer { model.proximityPresentation.dismiss() }
        XCTAssertNotNil(model.proximityPresentation.qrPayload)
        let detector = try XCTUnwrap(CIDetector(ofType: CIDetectorTypeQRCode, context: nil,
            options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]))
        let screen = PresentView(viewModel: model, onOpenSettings: {})
            .environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
            .environment(\.locale, Locale(identifier: "en_US")).environment(\.sizeCategory, .large)
        try await captureWhenReady(screen, id: "nearby.ready.qr", isReady: { model.proximityPresentation.qrPayload != nil },
            isImageReady: { image in
                guard let pixels = CIImage(image: image) else { return false }
                return detector.features(in: pixels).contains {
                    ($0 as? CIQRCodeFeature)?.messageString == model.proximityPresentation.qrPayload
                }
            }, failure: "The engagement QR must actually render and decode before capture")
    }

    private func batchOffer(noneSelected: Bool) async throws {
        let fixtures = try WalletVisualFixtures()
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.statusMessage = ""
        model.offerPreview = try fixtures.offer()
        model.issuanceCopyCounts = try fixtures.copies().mapValues { noneSelected ? 0 : $0 }
        XCTAssertEqual(model.acceptOfferEnabled, !noneSelected)
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}),
                    id: noneSelected ? "batch.offer.none_selected" : "batch.offer.two_targets_three_copies")
    }

    private func makeModel(biometricAuthenticator: (any DemoBiometricAuthenticator)? = nil, pinStore: (any DemoPinStore)? = nil) -> WalletViewModel {
        WalletViewModel(
            walletID: "visual-settings",
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(),
            walletClient: MockWalletClient(),
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore ?? InMemoryDemoPinStore(),
            biometricAuthenticator: biometricAuthenticator ?? FakeDemoBiometricAuthenticator(isAvailable: false)
        )
    }

    private func captureReview<Content: View>(_ view: Content, id: String, config: ViewImageConfig = .iPhone13,
                                              colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                              expected: Set<String>, scrollToBottom: Bool = false) async throws {
        var ready: Set<String> = []
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
            .environment(\.colorScheme, colorScheme).environment(\.sizeCategory, sizeCategory)
            .environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
            .onPreferenceChange(SharingReviewReadinessKey.self) { ready = $0 }
        try await captureWhenReady(content, id: id, config: config,
            isReady: { expected.isSubset(of: ready) }, failure: "Requested credential rows did not finish loading",
            readinessDescription: { "Expected \(expected.sorted()), rendered \(ready.sorted())" }, scrollToBottom: scrollToBottom)
    }

    private func captureWhenReady<Content: View>(_ content: Content, id: String, config: ViewImageConfig = .iPhone13,
                                                 isReady: () -> Bool, isImageReady: ((UIImage) -> Bool)? = nil,
                                                 failure: String, readinessDescription: () -> String = { "" },
                                                 scrollToBottom: Bool = false, scrollFraction: CGFloat = 1) async throws {
        let size = try XCTUnwrap(config.size)
        let host = WalletVisualHostingController(rootView: content)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let originalKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: size)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil; originalKeyWindow?.makeKey() }
        // Mount and lay out before waiting for SwiftUI tasks/preferences. Cold hosted simulators
        // can take longer than five seconds to schedule that first update. Readiness, not elapsed
        // time, still gates every capture; the deadline only bounds a genuine failure.
        window.layoutIfNeeded()
        host.view.layoutIfNeeded()
        func renderedReady() -> Bool {
            guard isReady() else { return false }
            guard let isImageReady else { return true }
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            return isImageReady(image)
        }
        let deadline = ProcessInfo.processInfo.systemUptime + 30
        while !renderedReady() && ProcessInfo.processInfo.systemUptime < deadline {
            host.view.layoutIfNeeded()
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        guard renderedReady() else {
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let attachment = XCTAttachment(image: image)
            attachment.name = "readiness-\(id)"
            attachment.lifetime = .keepAlways
            add(attachment)
            return XCTFail("\(failure). \(readinessDescription())")
        }
        // SnapshotTesting applies its device traits and geometry during capture. Scroll after that
        // layout, rather than using the live simulator scene's larger viewport.
        host.scrollToBottom = scrollToBottom
        host.scrollFraction = scrollFraction
        try capture(host, id: id, config: config)
        if scrollToBottom {
            XCTAssertTrue(host.didScroll, "The compact fixture must overflow and scroll to its requested offset")
        }
    }

    private func capture<Content: View>(_ view: Content, id: String, config: ViewImageConfig = .iPhone13,
                                      colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                      file: StaticString = #filePath, line: UInt = #line) throws {
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
        .environment(\.colorScheme, colorScheme)
        .environment(\.sizeCategory, sizeCategory)
        .environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
        .background(Color(.systemGroupedBackground))

        try capture(WalletVisualHostingController(rootView: content), id: id, config: config, file: file, line: line)
    }

    private func capture(_ controller: UIViewController, id: String, config: ViewImageConfig = .iPhone13,
                         file: StaticString = #filePath, line: UInt = #line) throws {
        let environment = ProcessInfo.processInfo.environment
        let record = environment["WALLET_VISUAL_RECORD"] == "1"
        let isCI = ["CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION"]
            .contains { environment[$0] != nil }
        XCTAssertFalse(record && isCI, "CI must only verify reviewed baselines", file: file, line: line)
        guard !(record && isCI) else { return }
        var strategy = Snapshotting<UIViewController, UIImage>.image(
            on: config, drawHierarchyInKeyWindow: true, precision: 1)
        let diffing = strategy.diffing
        // Compare the same PNG representation on both sides. Core Image's perceptual path is
        // inconsistent on the pinned simulator. Bound measured edge noise in each sRGB channel;
        // unlike a percentage tolerance this never ignores a small missing label or icon.
        strategy.diffing.diffV2 = { reference, actual in
            let decoded = diffing.fromData(diffing.toData(actual))
            if matchesWithinRasterNoise(reference, decoded) { return nil }
            return diffing.diffV2(reference, decoded)
        }
        withSnapshotTesting(record: record ? .all : .never) {
            assertSnapshot(
                of: controller,
                as: strategy,
                named: "native-ios26_5-phone-en-light",
                file: file, testName: id, line: line
            )
        }
    }

}

/// Applies a test interaction after SnapshotTesting lays out the actual snapshot viewport.
@MainActor
private final class WalletVisualHostingController<Content: View>: UIHostingController<Content> {
    var scrollToBottom = false
    var scrollFraction: CGFloat = 1
    private(set) var didScroll = false

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Core Animation spinners run outside SwiftUI transactions. Freeze their layer clocks at
        // one frame only in the snapshot host; production loading indicators keep animating.
        freezeSpinners(view)
        guard scrollToBottom, let scroll = findScrollView(view) else { return }
        let bottom = scroll.contentSize.height - scroll.bounds.height + scroll.adjustedContentInset.bottom
        guard bottom > -scroll.adjustedContentInset.top else { return }
        let top = -scroll.adjustedContentInset.top
        let target = top + (bottom - top) * scrollFraction
        if scroll.contentOffset.y != target {
            scroll.setContentOffset(CGPoint(x: 0, y: target), animated: false)
        }
        didScroll = true
    }

    private func freezeSpinners(_ view: UIView) {
        if view is UIActivityIndicatorView {
            view.layer.speed = 0
            view.layer.timeOffset = 0
        }
        view.subviews.forEach(freezeSpinners)
    }

    private func findScrollView(_ view: UIView) -> UIScrollView? {
        if let scroll = view as? UIScrollView { return scroll }
        return view.subviews.lazy.compactMap { self.findScrollView($0) }.first
    }
}

/// Every channel must stay within the measured SF Symbol edge noise (5/255); dimensions must match.
private func matchesWithinRasterNoise(_ reference: UIImage, _ actual: UIImage) -> Bool {
    guard let lhs = reference.cgImage, let rhs = actual.cgImage,
          lhs.width == rhs.width, lhs.height == rhs.height,
          let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { return false }
    let byteCount = lhs.width * lhs.height * 4
    func pixels(_ image: CGImage) -> [UInt8]? {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let drawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4, space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        return drawn ? bytes : nil
    }
    guard let a = pixels(lhs), let b = pixels(rhs) else { return false }
    var index = 0
    while index < byteCount {
        if abs(Int(a[index]) - Int(b[index])) > 5 { return false }
        index += 1
    }
    return true
}

private final class VisualAccessPinStore: DemoPinStore {
    private var pin = "1234"
    var failSaving = false
    var hasPin: Bool { true }
    var isBiometricUnlockEnabled = false
    var isBiometricSetupPending = false
    func verifyPin(_ value: String) async -> Bool { value == pin }
    func setPin(_ value: String) async throws {
        if failSaving { throw DemoPinRecordError.derivationFailed }
        pin = value
    }
    func clear() { pin = "" }
}
