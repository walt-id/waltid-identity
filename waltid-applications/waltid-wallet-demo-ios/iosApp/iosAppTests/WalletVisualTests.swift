import SnapshotTesting
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK
@testable import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletVisualTests: XCTestCase {
    func testKeySummary() throws { try keySetup(.summary) }
    func testKeyRecovery() throws { try keySetup(.recovery) }
    func testKeyStorage() throws { try keySetup(.storage) }
    func testKeyApproval() throws { try keySetup(.approval) }

    private func keySetup(_ step: WalletIdentityScreenModel.Step) throws {
        let options = try WalletVisualFixtures().keySetupOptions()
        let selected = try XCTUnwrap(options.first)
        try capture(NavigationView {
            List { SigningKeySetupContent(options: options, selected: selected, step: step, onSelect: { _ in }, onEdit: { _ in }) }
                .navigationTitle("Set up your wallet").navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    WalletActionBar(primary: WalletAction(step == .summary ? "Create signing key" : "Done", perform: {}),
                        secondary: step == .summary ? nil : WalletAction("Back", perform: {}))
                }
        }.navigationViewStyle(.stack), id: "onboarding.key.\(step)")
    }

    func testPinSetup() async throws { try await pin("setup") }
    func testPinMismatch() async throws { try await pin("mismatch") }
    func testPinBiometrics() async throws { try await pin("biometrics_enabled") }

    private func pin(_ state: String) async throws {
        let model = makeModel(biometricsAvailable: state == "biometrics_enabled")
        await model.readerTrustSettings.awaitPendingOperations()
        if state != "setup" {
            model.pin = "1234"
            model.pinConfirmation = state == "mismatch" ? "4321" : "1234"
        }
        if state == "mismatch" { model.submitPin() }
        if state == "biometrics_enabled" { model.useBiometrics = true }
        XCTAssertEqual(model.auth, .setup)
        if state == "mismatch" { XCTAssertEqual(model.pinError, "PIN confirmation does not match") }
        try capture(PinView(viewModel: model), id: "onboarding.pin.\(state)")
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
        let host = UIHostingController(rootView: content)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let originalKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil; originalKeyWindow?.makeKey() }
        // A placeholder has the same dimensions as a thumbnail. Wait for actual decoded views.
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline && !items.allSatisfy({ readyImages.contains($0.path.id) }) {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        guard items.allSatisfy({ readyImages.contains($0.path.id) }) else {
            return XCTFail("Image rows did not render their thumbnails: \(readyImages)")
        }
        try capture(host, id: "credential.media.loaded")
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

    func testNearbyReady() async throws {
        let model = try await makeWalletVisualProximityModel(qrPayload: WalletVisualFixtures().nearbyQrPayload())
        defer { model.proximityPresentation.dismiss() }
        XCTAssertNotNil(model.proximityPresentation.qrPayload)
        try capture(PresentView(viewModel: model, onOpenSettings: {}), id: "nearby.ready.qr")
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

    private func makeModel(biometricsAvailable: Bool = false) -> WalletViewModel {
        WalletViewModel(
            walletID: "visual-settings",
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(),
            walletClient: MockWalletClient(),
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {},
            pinStore: InMemoryDemoPinStore(),
            biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: biometricsAvailable)
        )
    }

    private func capture<Content: View>(_ view: Content, id: String, config: ViewImageConfig = .iPhone13,
                                      colorScheme: ColorScheme = .light, sizeCategory: ContentSizeCategory = .large,
                                      file: StaticString = #filePath, line: UInt = #line) throws {
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
        .environment(\.colorScheme, colorScheme)
        .environment(\.sizeCategory, sizeCategory)
        .environment(\.walletDemoBranding, .default)
        .background(Color(.systemGroupedBackground))

        try capture(UIHostingController(rootView: content), id: id, config: config, file: file, line: line)
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
