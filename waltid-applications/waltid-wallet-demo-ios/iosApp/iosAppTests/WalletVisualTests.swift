import CryptoKit
import SnapshotTesting
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK
@testable import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletVisualTests: XCTestCase {
    func testPinCreate() async throws { try await pin(.create) }
    func testPinConfirm() async throws { try await pin(.confirm) }
    func testPinBiometrics() async throws { try await pin(.biometrics) }

    private func pin(_ page: PinView.SetupPage) async throws {
        let model = makeModel()
        await model.readerTrustSettings.awaitPendingOperations()
        if page != .create { model.pin = "123456" }
        if page == .biometrics { model.pinConfirmation = "123456" }
        XCTAssertEqual(model.auth, .setup)
        XCTAssertFalse(model.isBiometricUnlockAvailable)
        let name = page == .create ? "create" : page == .confirm ? "confirm" : "biometrics"
        try capture(PinView(viewModel: model, initialPage: page), id: "onboarding.pin.\(name)")
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
        // Each row has a short label; only its decoded 112pt thumbnail makes it this tall.
        // The pixel assertion below then verifies the actual portrait/signature content.
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline && !items.allSatisfy({ (heights[$0.path.id] ?? 0) >= 120 }) {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        guard items.allSatisfy({ (heights[$0.path.id] ?? 0) >= 120 }) else {
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
            pinStore: InMemoryDemoPinStore(),
            biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false)
        )
    }

    private func capture<Content: View>(_ view: Content, id: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let content = view.environment(\.locale, Locale(identifier: "en_US"))
        .environment(\.colorScheme, .light)
        .environment(\.sizeCategory, .large)
        .environment(\.walletDemoBranding, .default)
        .background(Color(.systemGroupedBackground))

        try capture(UIHostingController(rootView: content), id: id, file: file, line: line)
    }

    private func capture(_ controller: UIViewController, id: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let environment = ProcessInfo.processInfo.environment
        let record = environment["WALLET_VISUAL_RECORD"] == "1"
        let isCI = ["CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION"]
            .contains { environment[$0] != nil }
        XCTAssertFalse(record && isCI, "CI must only verify reviewed baselines", file: file, line: line)
        guard !(record && isCI) else { return }
        var strategy = Snapshotting<UIViewController, UIImage>.image(
            on: .iPhone13, drawHierarchyInKeyWindow: true, precision: 1)
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
        let failure = verifySnapshot(of: controller, as: strategy,
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
            "scale": actualScale ?? 0, "locale": "en-US", "theme": "light", "textSize": "large",
            "timezone": TimeZone.current.identifier, "runtime": UIDevice.current.systemVersion,
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

private struct ImageRowHeightKey: PreferenceKey {
    static let defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
    }
}

/// Every channel must stay within the measured iOS 27 SF Symbol edge noise (6/255); dimensions must match.
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
        if abs(Int(a[index]) - Int(b[index])) > 6 { return false }
        index += 1
    }
    return true
}

@MainActor
final class WalletVisualComparatorTests: XCTestCase {
    func testChannelNoiseHasAnExactBound() throws {
        let reference = try image([100])
        XCTAssertTrue(matchesWithinRasterNoise(reference, try image([100])))
        XCTAssertTrue(matchesWithinRasterNoise(reference, try image([106])))
        XCTAssertFalse(matchesWithinRasterNoise(reference, try image([107])))
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
