import WalletDemoSharingUI
import XCTest

final class CredentialCardArtOverlayTests: XCTestCase {
    func testPendingMetadataArtDoesNotShowConstructedFallback() {
        XCTAssertFalse(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: false,
                metadataArtFailed: false
            )
        )
        XCTAssertFalse(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: true,
                metadataArtFailed: false
            )
        )
    }

    func testConstructedFallbackShowsWhenArtIsMissingOrRejected() {
        XCTAssertTrue(
            showsConstructedCardArtOverlay(
                backgroundImageURI: nil,
                hasLoadedMetadataArt: false,
                metadataArtFailed: false
            )
        )
        XCTAssertTrue(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: false,
                metadataArtFailed: true
            )
        )
    }
}
