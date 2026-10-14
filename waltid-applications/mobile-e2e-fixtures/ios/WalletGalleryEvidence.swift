import CryptoKit
import XCTest

/// Opt-in wallet-gallery/v1 attachments shared by the two public-demo UI test targets.
@MainActor
extension XCTestCase {
    func captureGallery(_ app: XCUIApplication, name: String) {
        guard ProcessInfo.processInfo.environment["WALLET_GALLERY_CAPTURE"] == "1" else { return }
        guard app.state == .runningForeground else {
            return XCTFail("Gallery app is not foreground")
        }
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func recordGalleryRequest(_ kind: String, value: String) {
        guard ProcessInfo.processInfo.environment["WALLET_GALLERY_CAPTURE"] == "1" else { return }
        let digest = SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
        let attachment = XCTAttachment(string: digest)
        attachment.name = "WALLET_GALLERY_REQUEST_DIGEST-\(kind)-\(digest)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
