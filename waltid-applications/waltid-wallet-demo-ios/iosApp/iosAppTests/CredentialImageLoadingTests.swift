import SwiftUI
import UIKit
@testable import WalletDemoSharingUI
import XCTest

final class CredentialImageLoadingTests: XCTestCase {
    @MainActor
    func testOffscreenAndCollapsedImagesDoNotDecode() async throws {
        let counter = ImageDecodeCounter()
        let image = DeferredCredentialImage {
            XCTAssertFalse(Thread.isMainThread)
            counter.increment()
            return .text("Decoded image")
        }
        let item = ClaimItem(path: .topLevel("portrait"), label: "Portrait",
                             value: .deferredImage(image), rawValue: nil, roles: [.image])
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let originalKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            originalKeyWindow?.makeKey()
        }

        let precedingItems = (0..<40).map { index in
            ClaimItem(path: .topLevel("text\(index)"), label: "Claim \(index)", value: .text("Value"), rawValue: nil)
        }
        for group in [
            ClaimGroup(title: "Images", items: precedingItems + [item]),
            ClaimGroup(title: "Images", items: [item], initiallyExpanded: false)
        ] {
            let details = CredentialDetails(id: "test", title: "Test", issuer: nil, subject: nil,
                                            format: "vc+sd-jwt", addedAt: nil, groups: [group])
            _ = details.cardSummary
            XCTAssertEqual(counter.value, 0)
            let view = ScrollView { CredentialDetailsView(details: details) }
            window.rootViewController = UIHostingController(rootView: view)
            window.isHidden = false
            try await Task.sleep(nanoseconds: 300_000_000)
            XCTAssertEqual(counter.value, 0)
        }

        window.rootViewController = UIHostingController(rootView: ClaimValueRow(item: item))
        window.isHidden = false
        for _ in 0..<50 where counter.value == 0 {
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertEqual(counter.value, 1)
    }
}

private final class ImageDecodeCounter {
    private let lock = NSLock()
    private var count = 0

    var value: Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }

    func increment() {
        lock.lock()
        defer { lock.unlock() }
        count += 1
    }
}
