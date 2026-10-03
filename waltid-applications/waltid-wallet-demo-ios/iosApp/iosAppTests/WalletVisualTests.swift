import SnapshotTesting
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK
@testable import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletVisualTests: XCTestCase {
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

    func testBatchOffer() async throws {
        try await batchOffer(noneSelected: false)
    }

    func testBatchOfferWithNothingSelected() async throws {
        try await batchOffer(noneSelected: true)
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
        var heights: [String: CGFloat] = [:]
        let content = ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(items) { item in
                    ClaimValueRow(item: item).background(GeometryReader { geometry in
                        Color.clear.preference(key: ImageRowHeightKey.self, value: [item.path.id: geometry.size.height])
                    })
                }
            }.padding(20)
        }.onPreferenceChange(ImageRowHeightKey.self) { heights = $0 }
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
        // Each row has three short text lines; only its decoded 112pt thumbnail makes it this tall.
        // The pixel assertion below then verifies the actual portrait/signature content.
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline && !items.allSatisfy({ (heights[$0.path.id] ?? 0) >= 150 }) {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        guard items.allSatisfy({ (heights[$0.path.id] ?? 0) >= 150 }) else {
            return XCTFail("Image rows did not render their thumbnails: \(heights)")
        }
        try capture(host, id: "credential.media.loaded")
    }

    func testPartialBatchResult() async throws {
        let fixtures = try WalletVisualFixtures()
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        model.isReady = true
        model.selectedTab = .receive
        model.statusMessage = try fixtures.partialResultStatus()
        model.credentials = [try fixtures.credential()]
        model.lastReceivedCredentialIDs = model.credentials.map(\.id)
        model.deferredCredentials = [try fixtures.deferredCredential()]
        XCTAssertEqual(model.credentials.count, 1)
        XCTAssertEqual(model.deferredCredentials.count, 1)
        try capture(ReceiveView(viewModel: model, onOpenSettings: {}), id: "batch.result.saved_and_deferred")
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

    private func makeModel() -> WalletViewModel {
        WalletViewModel(
            walletID: "visual-settings",
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(),
            walletClient: MockWalletClient(),
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {},
            pinStore: InMemoryDemoPinStore()
        )
    }

    private func capture<Content: View>(_ view: Content, id: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
        .environment(\.colorScheme, .light)
        .environment(\.sizeCategory, .large)
        .environment(\.walletDemoBranding, .default)

        try capture(UIHostingController(rootView: content), id: id, file: file, line: line)
    }

    private func capture(_ controller: UIViewController, id: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let environment = ProcessInfo.processInfo.environment
        let record = environment["WALLET_VISUAL_RECORD"] == "1"
        let isCI = ["CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION"]
            .contains { environment[$0] != nil }
        XCTAssertFalse(record && isCI, "CI must only verify reviewed baselines", file: file, line: line)
        guard !(record && isCI) else { return }
        withSnapshotTesting(record: record ? .all : .never) {
            assertSnapshot(
                of: controller,
                as: .image(on: .iPhone13, drawHierarchyInKeyWindow: true),
                named: "native-ios26_5-phone-en-light",
                file: file, testName: id, line: line
            )
        }
    }

}

private struct ImageRowHeightKey: PreferenceKey {
    static let defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
    }
}
