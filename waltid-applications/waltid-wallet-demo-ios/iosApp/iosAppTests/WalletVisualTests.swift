import CryptoKit
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

    func testWalletOpening() async throws { try await walletOpening(failure: false) }
    func testWalletOpeningFailure() async throws { try await walletOpening(failure: true) }
    private func walletOpening(failure: Bool) async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.auth = .unlocked
        model.isReady = false
        model.isLoading = !failure
        model.statusMessage = "Local storage could not be read. Try again."
        try capture(HomeView(viewModel: model), id: failure ? "wallet.opening.failure" : "wallet.opening.loading")
    }
    func testPinStorageUnavailable() async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.auth = .storageUnavailable("Secure PIN storage could not be read")
        try capture(ContentView(viewModel: model), id: "access.storage.unavailable")
    }

    func testControls() throws { try capture(WalletControlsPreview(), id: "components.controls.default") }
    func testControlsRtl() throws {
        try capture(WalletControlsPreview().environment(\.layoutDirection, .rightToLeft), id: "components.controls.rtl", layoutDirection: .rightToLeft)
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
        try capture(ReceiveView(viewModel: model, onBack: {}),
            id: unavailable ? "external.callback.unavailable" : "external.receiving.review", config: .iPhoneSe)
    }

    func testKeySummary() throws { try keySetup(.summary) }
    func testKeyRecovery() throws { try keySetup(.recovery) }
    func testKeyStorage() throws { try keySetup(.storage) }
    func testKeyApproval() throws { try keySetup(.approval) }

    func testKeyApprovalUnavailable() throws {
        let options = try WalletVisualFixtures().keySetupOptions()
        let requested = try XCTUnwrap(options.first).approval
        let plain = options.filter { $0.approval.id != requested.id }
        let selected = try XCTUnwrap(plain.first)
        try capture(NavigationView {
            List {
                SigningKeySetupContent(options: plain, selected: selected, step: .summary,
                    requestedApproval: requested, onSelect: { _ in }, onEdit: { _ in })
                BiometricRecoverySection(availability: .unavailable, kind: .faceID, showSettingsAction: false)
            }.navigationTitle("Set up your wallet").navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    WalletFooter {
                        WalletActions(primary: WalletAction("Create signing key", enabled: false, perform: {}),
                            secondary: WalletAction("Open Settings", perform: {}))
                    }
                }
        }.navigationViewStyle(.stack), id: "onboarding.key.approval_unavailable")
    }

    private func keySetup(_ step: WalletIdentityScreenModel.Step) throws {
        let options = try WalletVisualFixtures().keySetupOptions()
        let selected = try XCTUnwrap(options.first)
        try capture(NavigationView {
            List { SigningKeySetupContent(options: options, selected: selected, step: step, onSelect: { _ in }, onEdit: { _ in }) }
                .navigationTitle(step == .summary ? "Set up your wallet" : step.title).navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    WalletFooter {
                        WalletActions(primary: WalletAction(step == .summary ? "Create signing key" : "Done", perform: {}),
                            secondary: step == .summary ? nil : WalletAction("Back", perform: {}))
                    }
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
                colorScheme: compact ? .dark : .light, sizeCategory: compact ? .accessibilityMedium : .large,
                layoutDirection: state == "rtl" ? .rightToLeft : .leftToRight)
        }
        await gate.complete(.failed)
    }

    func testAccessRejected() async throws { try await walletAccess("rejected") }
    func testAccessBiometricFallback() async throws { try await walletAccess("biometric_fallback") }
    func testAccessBiometricUnavailable() async throws { try await walletAccess("biometric_unavailable") }
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
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: state.hasPrefix("biometric") && state != "biometric_unavailable",
            result: state == "biometric_lockout" ? .lockedOut : .failed)
        if state == "biometric_unavailable" { biometrics.kind = .faceID }
        if state == "biometric_lockout" { biometrics.isAvailable = false; biometrics.unavailableReason = .lockedOut }
        let model = makeModel(biometricAuthenticator: biometrics, pinStore: store)
        await model.readerTrustSettings.awaitPendingOperations()
        let unlocking = ["rejected", "biometric_fallback", "biometric_lockout", "biometric_unavailable"].contains(state)
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

    func testScanFido() throws {
        try capture(WalletScanView(input: "FIDO:/0123456789", onBack: {}, onOpen: { _, _ in }), id: "wallet.scan.fido")
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
            credentials: [credential], transactionCode: offer.transactionCode, batchSize: nil)
        model.issuanceCopyCounts = [credential.configurationID: 1]
        XCTAssertTrue(model.acceptOfferEnabled)
        try capture(ReceiveView(viewModel: model), id: "batch.offer.single_full_art")
    }

    func testOfferDefinitions() throws {
        let offer = try WalletVisualFixtures().offer()
        let credential = try XCTUnwrap(offer.credentials.first)
        let definitions = StoredCredentialMetadataParser.claims(from: credential.metadataJSON)
        XCTAssertEqual(definitions.map(\.name), ["Given name", "Family name", "Contact email"])
        XCTAssertEqual(definitions.filter { $0.mandatory == true }.count, 2)
        XCTAssertEqual(definitions.filter { $0.mandatory != true }.map(\.name), ["Contact email"])
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
        try capture(ReceiveView(viewModel: model), id: "batch.offer.compact_dark_large_text",
                    config: .iPhoneSe, colorScheme: .dark, sizeCategory: .accessibilityMedium)
    }

    func testProviderSharingReview() async throws { try await providerSharingReview() }
    func testSharingInformationSelected() async throws { try await sharingInformation() }
    func testSharingInformationAlternative() async throws { try await sharingInformation(alternative: true) }
    func testSharingInformationMultiple() async throws { try await sharingInformation(multiple: true) }

    private func sharingInformation(alternative: Bool = false, multiple: Bool = false) async throws {
        let review = try WalletVisualFixtures().informationReview(multiple: multiple)
        var credentials = review.defaultCredentialSelection()
        if alternative { credentials = [review.credentialOptions[1].selection] }
        let selection = SharingSelection(credentials: credentials)
        XCTAssertTrue(review.hasCompleteCredentialSelection(credentials))
        let details = review.credentialOptions.map { CredentialDisplayNormalizer.details(for: $0) }
        let groups = review.informationToShare(selection: selection, details: details)
        XCTAssertEqual(groups.count, multiple ? 2 : 1)
        if alternative { XCTAssertTrue(groups[0].fields.contains { $0.alwaysIncluded }) }
        let screen = SharingReviewScreen(title: "Share credentials", review: review, selection: selection,
            selectionComplete: true, onToggleCredential: { _ in }, onToggleDisclosure: { _ in }, onSubmit: {}, onCancel: {})
        try await captureReview(screen, id: "sharing.information." + (multiple ? "multiple" : alternative ? "alternative" : "selected"),
            expected: Set(review.credentialOptions.map { $0.selection.id }), scrollToBottom: multiple)
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
    func testMissingSavedDetails() async throws { try await batchResult(id: "missing_details", missingDetails: true) }

    private func batchResult(status: IssuanceContinuationStatus = .awaitingIssuer, id: String = "saved_and_deferred",
                             failure: Bool = false, missingDetails: Bool = false) async throws {
        let fixtures = try WalletVisualFixtures()
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.selectedTab = .receive
        model.statusMessage = try fixtures.partialResultStatus()
        if missingDetails { model.statusMessage = "Received 2 credential(s)" }
        model.credentials = [try fixtures.credential()]
        model.lastReceivedCredentialIDs = model.credentials.map(\.id)
        if missingDetails { model.lastReceivedCredentialIDs.append("missing-credential") }
        let pending = try fixtures.deferredCredential()
        model.deferredCredentials = failure || missingDetails ? [] : [DeferredCredential(id: pending.id,
            credentialConfigurationID: pending.credentialConfigurationID, intervalSeconds: pending.intervalSeconds,
            status: status, displayMetadataJSON: pending.displayMetadataJSON)]
        let problem: IssuanceFailure? = failure ? .init(code: .issuerResponse, message: "The issuer could not finish this request.",
            targetFailure: .init(target: .init(configurationID: "failed"), stage: .request,
                notAttempted: [.init(configurationID: "unattempted-1"), .init(configurationID: "unattempted-2")])) : nil
        model.issuanceReceipt = IssuanceReceipt(issuer: try fixtures.offer().issuer, pendingIDs: Set(model.deferredCredentials.map(\.id)), problem: problem)
        if failure { model.statusMessage = problem?.message ?? ""; model.isError = true }
        XCTAssertEqual(model.credentials.count, 1)
        XCTAssertEqual(model.deferredCredentials.count, failure || missingDetails ? 0 : 1)
        if missingDetails {
            XCTAssertTrue(model.receivedDetailsUnavailable)
            XCTAssertFalse(model.issuanceReceiptComplete)
        }
        try capture(ReceiveView(viewModel: model), id: "batch.result.\(id)")
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
                    ToolbarItem(placement: .navigationBarTrailing) {
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
            try await captureWhenReady(content, id: "nearby.review.disclosures", config: .iPhoneSe, sizeCategory: .accessibilityMedium, isReady: { true }, failure: "Review not ready", scrollToBottom: true)
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
        let screen = PresentView(viewModel: model)
            .environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
            .environment(\.locale, Locale(identifier: "en_US")).environment(\.sizeCategory, .large)
        try await captureWhenReady(screen, id: "nearby.ready.qr", isReady: { model.proximityPresentation.qrPayload != nil },
            isImageReady: { image in
                guard let pixels = CIImage(image: image) else { return false }
                return detector.features(in: pixels).contains {
                    ($0 as? CIQRCodeFeature)?.messageString == model.proximityPresentation.qrPayload
                }
            }, failure: "The engagement QR must actually render and decode before capture")
        model.proximityPresentation.showEngagement(.nfc)
        XCTAssertEqual(model.proximityPresentation.displayedEngagement, .nfc)
        try capture(screen.environment(\.scenePhase, .inactive), id: "nearby.ready.nfc")
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
        try capture(ReceiveView(viewModel: model),
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
            .onPreferenceChange(SharingReviewReadinessKey.self) { ready = Set($0.map(\.selection.id)) }
        try await captureWhenReady(content, id: id, config: config, colorScheme: colorScheme, sizeCategory: sizeCategory,
            isReady: { expected.isSubset(of: ready) }, failure: "Requested credential rows did not finish loading",
            readinessDescription: { "Expected \(expected.sorted()), rendered \(ready.sorted())" }, scrollToBottom: scrollToBottom)
    }

    private func captureWhenReady<Content: View>(_ content: Content, id: String, config: ViewImageConfig = .iPhone13,
                                                 colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                                 isReady: () -> Bool, isImageReady: ((UIImage) -> Bool)? = nil,
                                                 failure: String, readinessDescription: () -> String = { "" },
                                                 scrollToBottom: Bool = false, scrollFraction: CGFloat = 1) async throws {
        // A hosted viewport change must not snapshot an intermediate native toolbar position.
        // Limit the UIKit override to this settled-state capture and restore the caller's setting.
        let animationsEnabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(false)
        defer { UIView.setAnimationsEnabled(animationsEnabled) }
        let size = try XCTUnwrap(config.size)
        let host = WalletVisualHostingController(rootView: content)
        host.scrollToBottom = scrollToBottom
        host.scrollFraction = scrollFraction
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
        let started = ProcessInfo.processInfo.systemUptime
        var readiness: [[String: Any]] = []
        var lastImage: UIImage?
        var frameGate = WalletVisualFrameGate()
        func renderAndCheckReadiness() -> Bool {
            host.view.layoutIfNeeded()
            let before = isReady()
            var rendered = false
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                rendered = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            lastImage = image
            // Drawing commits pending SwiftUI preferences. Readiness must describe this rendered
            // frame, rather than a cached preference followed by a different diagnostic frame.
            let after = isReady()
            let contentReady = rendered && after && (isImageReady?(image) ?? true)
            let ready = frameGate.accept(image, contentReady: contentReady)
            let observation: [String: Any] = ["beforeRender": before, "afterRender": after,
                "rendered": rendered, "contentReady": contentReady, "ready": ready, "description": readinessDescription(),
                "elapsedMs": Int((ProcessInfo.processInfo.systemUptime - started) * 1000)]
            if readiness.last?["ready"] as? Bool != ready || before != after || readiness.isEmpty {
                readiness.append(observation)
            }
            return ready
        }
        let deadline = started + 30
        var ready = renderAndCheckReadiness()
        while !ready && ProcessInfo.processInfo.systemUptime < deadline {
            try await Task.sleep(nanoseconds: 10_000_000)
            ready = renderAndCheckReadiness()
        }
        let environment = ProcessInfo.processInfo.environment
        let diagnostic: [String: Any] = ["schemaVersion": 1, "id": id,
            "runId": environment["WALLET_VISUAL_RUN_ID"] ?? "",
            "sha": environment["WALLET_VISUAL_SHA"] ?? "",
            "fingerprint": environment["WALLET_VISUAL_FINGERPRINT"] ?? "",
            "ready": ready, "observations": readiness]
        let metadata = XCTAttachment(data: try JSONSerialization.data(withJSONObject: diagnostic, options: [.sortedKeys]),
            uniformTypeIdentifier: "public.json")
        metadata.name = "wallet-visual-readiness-\(id.replacingOccurrences(of: ".", with: "-")).json"
        metadata.lifetime = .keepAlways
        add(metadata)
        guard ready else {
            if let lastImage {
                let attachment = XCTAttachment(image: lastImage)
                attachment.name = "readiness-\(id)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
            return XCTFail("\(failure). \(readinessDescription())")
        }
        // Readiness must describe the requested scrolled frame, including its scroll-edge materials.
        if scrollToBottom {
            XCTAssertTrue(host.didScroll, "The compact fixture must overflow and scroll to its requested offset")
        }
        // Compare exactly the settled frame that passed readiness. Snapshotting the controller
        // again relays it out and can restart native scroll-edge/glass transitions.
        try capture(XCTUnwrap(lastImage), as: .image(precision: 1), id: id,
            colorScheme: colorScheme, sizeCategory: sizeCategory)
    }

    private func capture<Content: View>(_ view: Content, id: String, config: ViewImageConfig = .iPhone13,
                                      colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                      layoutDirection: LayoutDirection = .leftToRight,
                                      file: StaticString = #filePath, line: UInt = #line) throws {
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
        .environment(\.colorScheme, colorScheme)
        .environment(\.sizeCategory, sizeCategory)
        .environment(\.walletDemoBranding, .default).tint(WalletDemoBranding.default.primary)
        .background(Color(.systemGroupedBackground))

        try capture(WalletVisualHostingController(rootView: content), id: id, config: config, colorScheme: colorScheme, sizeCategory: sizeCategory, layoutDirection: layoutDirection, file: file, line: line)
    }

    private func capture(_ controller: UIViewController, id: String, config: ViewImageConfig = .iPhone13,
                         colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                         layoutDirection: LayoutDirection = .leftToRight,
                         file: StaticString = #filePath, line: UInt = #line) throws {
        try capture(controller, as: .image(on: config, drawHierarchyInKeyWindow: true, precision: 1),
            id: id, colorScheme: colorScheme, sizeCategory: sizeCategory, layoutDirection: layoutDirection,
            file: file, line: line)
    }

    private func capture<Value>(_ value: Value, as strategy: Snapshotting<Value, UIImage>, id: String,
                                colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                layoutDirection: LayoutDirection = .leftToRight,
                                file: StaticString = #filePath, line: UInt = #line) throws {
        let environment = ProcessInfo.processInfo.environment
        let record = environment["WALLET_VISUAL_RECORD"] == "1"
        let isCI = ["CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION"]
            .contains { environment[$0] != nil }
        XCTAssertFalse(record && isCI, "CI must only verify reviewed baselines", file: file, line: line)
        guard !(record && isCI) else { return }
        var strategy = strategy
        let diffing = strategy.diffing
        let baseline = URL(fileURLWithPath: "\(file)").deletingLastPathComponent()
            .appendingPathComponent("__Snapshots__/WalletVisualTests")
            .appendingPathComponent("\(id.replacingOccurrences(of: ".", with: "-")).native-ios27_0-phone-en-light.png")
        let baselineData = try? Data(contentsOf: baseline)
        var actualData: Data?
        var actualSize: CGSize?
        var actualScale: CGFloat?
        let snapshot = strategy.snapshot
        strategy.snapshot = { value in
            snapshot(value).map { image in
                actualData = diffing.toData(image)
                actualSize = image.size
                actualScale = image.scale
                return image
            }
        }
        var compared = false
        // Compare the same PNG representation on both sides. Core Image's perceptual path is
        // inconsistent on the pinned simulator. Bound measured edge noise in each sRGB channel;
        // unlike a percentage tolerance this never ignores a small missing label or icon.
        strategy.diffing.diffV2 = { reference, actual in
            compared = true
            let decoded = diffing.fromData(diffing.toData(actual))
            if matchesWithinRasterNoise(reference, decoded) { return nil }
            return diffing.diffV2(reference, decoded)
        }
        let failure = verifySnapshot(of: value, as: strategy,
            named: "native-ios27_0-phone-en-light", record: record ? .all : .never,
            file: file, testName: id, line: line)
        if let actualData {
            let attachment = XCTAttachment(data: actualData, uniformTypeIdentifier: "public.png")
            attachment.name = "wallet-visual-actual-\(id.replacingOccurrences(of: ".", with: "-")).png"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        let receipt: [String: Any] = [
            "schemaVersion": 1, "id": id, "renderer": "swiftui", "kind": "deterministic",
            "test": name, "runId": environment["WALLET_VISUAL_RUN_ID"] ?? "",
            "sha": environment["WALLET_VISUAL_SHA"] ?? "",
            "fingerprint": environment["WALLET_VISUAL_FINGERPRINT"] ?? "",
            "mode": record ? "record" : "verify", "compared": compared,
            "result": record ? "recorded" : (failure == nil && compared ? "passed" : "failed"),
            "baselineDigest": baselineData.map(visualDigest) ?? "",
            "actualDigest": actualData.map(visualDigest) ?? "",
            "viewport": [actualSize?.width ?? 0, actualSize?.height ?? 0],
            "scale": actualScale ?? 0, "locale": "en-US", "theme": colorScheme == .dark ? "dark" : "light",
            "textSize": sizeCategory == .large ? "large" : sizeCategory == .accessibilityMedium ? "accessibilityMedium" : String(describing: sizeCategory),
            "timezone": TimeZone.current.identifier, "runtime": UIDevice.current.systemVersion,
            "layoutDirection": layoutDirection == .rightToLeft ? "rtl" : "ltr",
            "reducedMotion": UIAccessibility.isReduceMotionEnabled,
            "diagnostic": failure ?? ""
        ]
        let attachment = XCTAttachment(data: try JSONSerialization.data(withJSONObject: receipt, options: [.sortedKeys]),
            uniformTypeIdentifier: "public.json")
        attachment.name = "wallet-visual-receipt-\(id.replacingOccurrences(of: ".", with: "-")).json"
        attachment.lifetime = .keepAlways
        add(attachment)
        if let failure { XCTFail(failure, file: file, line: line) }
    }

}

private func visualDigest(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
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
        // Initial mounting and nonanimated scrolling can animate native toolbar materials.
        // Static fixtures must capture their final frame; production motion is unchanged.
        defer { finishAnimations(view.layer) }
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

    private func finishAnimations(_ layer: CALayer) {
        for key in layer.animationKeys() ?? [] {
            guard let animation = layer.animation(forKey: key)?.copy() as? CAAnimation,
                  animation.duration > 0,
                  animation.repeatCount == 0, animation.repeatDuration == 0, !animation.autoreverses else { continue }
            // Keep the animation's final presentation value: removing it can erase native glass.
            animation.speed = 0
            // The exact duration boundary wraps a paused animation to its initial frame.
            animation.timeOffset = animation.duration.nextDown
            animation.fillMode = .forwards
            animation.isRemovedOnCompletion = false
            layer.add(animation, forKey: key)
        }
        layer.sublayers?.forEach(finishAnimations)
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

/// Every channel must stay within the measured iOS 27 SF Symbol edge noise (11/255); dimensions must match.
private func matchesWithinRasterNoise(_ reference: UIImage, _ actual: UIImage, maximumChannelDifference: Int = 11) -> Bool {
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
        if abs(Int(a[index]) - Int(b[index])) > maximumChannelDifference { return false }
        index += 1
    }
    return true
}

/// Content readiness alone does not establish that native materials have finished drawing.
private struct WalletVisualFrameGate {
    private var previous: UIImage?

    mutating func accept(_ image: UIImage, contentReady: Bool) -> Bool {
        defer { previous = contentReady ? image : nil }
        // Keep the same-run settling bound stricter than cross-run reference comparison.
        return contentReady && previous.map { matchesWithinRasterNoise($0, image, maximumChannelDifference: 6) } == true
    }
}

@MainActor
final class WalletVisualHostingTests: XCTestCase {
    func testMountAndScrollSettleTheSiblingToolbarBackdrop() throws {
        let scroll = UIScrollView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        scroll.contentSize = CGSize(width: 100, height: 200)
        let backdrop = UIView(frame: CGRect(x: 150, y: 150, width: 20, height: 20))
        backdrop.backgroundColor = .black
        let content = UIView()
        content.backgroundColor = .white
        content.addSubview(scroll)
        content.addSubview(backdrop)
        let host = WalletVisualHostingController(rootView: VisualUIKitFixture(view: content).ignoresSafeArea())
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let originalKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 200, height: 200)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil; originalKeyWindow?.makeKey() }
        window.layoutIfNeeded()

        let fade = CABasicAnimation(keyPath: "opacity")
        fade.fromValue = 0
        fade.toValue = 1
        fade.duration = 60
        let finalFrame = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { context in
            UIColor.black.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        for scrollToBottom in [false, true] {
            backdrop.layer.add(fade, forKey: "toolbarBackdrop")
            host.scrollToBottom = scrollToBottom
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            let rendered = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                XCTAssertTrue(window.drawHierarchy(in: window.bounds, afterScreenUpdates: true))
            }
            XCTAssertEqual(scroll.contentOffset.y, scrollToBottom ? 100 : -scroll.adjustedContentInset.top)
            let point = backdrop.convert(CGPoint(x: 10, y: 10), to: window)
            let pixel = try XCTUnwrap(rendered.cgImage?.cropping(to: CGRect(
                x: point.x * rendered.scale, y: point.y * rendered.scale, width: rendered.scale, height: rendered.scale)))
            XCTAssertTrue(matchesWithinRasterNoise(finalFrame, UIImage(cgImage: pixel)),
                "A toolbar outside the scroll view must settle on mount and after scrolling")
        }
    }
}

private struct VisualUIKitFixture: UIViewRepresentable {
    let view: UIView
    func makeUIView(context: Context) -> UIView { view }
    func updateUIView(_ uiView: UIView, context: Context) {}
}

@MainActor
final class WalletVisualComparatorTests: XCTestCase {
    func testFrameGateRequiresConsecutiveReadyFrames() throws {
        var gate = WalletVisualFrameGate()
        let settled = try image([100])
        XCTAssertFalse(gate.accept(settled, contentReady: true))
        XCTAssertTrue(gate.accept(settled, contentReady: true))
        XCTAssertFalse(gate.accept(settled, contentReady: false))
        XCTAssertFalse(gate.accept(settled, contentReady: true), "Lost readiness must discard the earlier frame")
        XCTAssertTrue(gate.accept(settled, contentReady: true))
    }

    func testFrameGateRejectsChangingMaterials() throws {
        var gate = WalletVisualFrameGate()
        XCTAssertFalse(gate.accept(try image([100]), contentReady: true))
        XCTAssertFalse(gate.accept(try image([107]), contentReady: true))
        XCTAssertTrue(gate.accept(try image([107]), contentReady: true))
    }

    func testChannelNoiseHasAnExactBound() throws {
        let reference = try image([100])
        XCTAssertTrue(matchesWithinRasterNoise(reference, try image([100])))
        XCTAssertTrue(matchesWithinRasterNoise(reference, try image([111])))
        XCTAssertFalse(matchesWithinRasterNoise(reference, try image([112])))
    }

    func testOneMissingDetailCannotHideInMatchingPixels() throws {
        let reference = Array(repeating: UInt8(100), count: 64)
        var actual = reference
        actual[63] = 200
        XCTAssertFalse(matchesWithinRasterNoise(try image(reference), try image(actual)))
    }

    func testDifferentDimensionsFail() throws {
        XCTAssertFalse(matchesWithinRasterNoise(try image([100]), try image([100, 100])))
    }

    private func image(_ levels: [UInt8]) throws -> UIImage {
        let pixels = levels.flatMap { level -> [UInt8] in [level, level, level, 255] }
        let provider = try XCTUnwrap(CGDataProvider(data: Data(pixels) as CFData))
        let colorSpace = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let cgImage = try XCTUnwrap(CGImage(width: levels.count, height: 1, bitsPerComponent: 8,
            bitsPerPixel: 32, bytesPerRow: levels.count * 4, space: colorSpace,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
        return UIImage(cgImage: cgImage)
    }
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
