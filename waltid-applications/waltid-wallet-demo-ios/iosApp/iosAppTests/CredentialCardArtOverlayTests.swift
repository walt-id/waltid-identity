import WalletDemoSharingUI
import XCTest

final class CredentialCardArtOverlayTests: XCTestCase {
    func testCredentialLogoUsesHTTPSMetadataAndOtherwiseFallsBack() throws {
        XCTAssertEqual(
            credentialCardLogoSource("https://issuer.example/credential.png"),
            .metadata(try XCTUnwrap(URL(string: "https://issuer.example/credential.png")))
        )
        XCTAssertEqual(credentialCardLogoSource("http://issuer.example/logo.png"), .bundledWalt)
        XCTAssertEqual(credentialCardLogoSource(nil), .bundledWalt)
    }

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
